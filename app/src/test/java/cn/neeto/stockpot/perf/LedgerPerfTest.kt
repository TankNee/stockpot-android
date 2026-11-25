package cn.neeto.stockpot.perf

import cn.neeto.stockpot.core.data.EntryFilter
import cn.neeto.stockpot.core.data.LedgerMath
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.net.LedgerMerge
import cn.neeto.stockpot.core.net.SyncTombstones
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 账单量级压测：账攒到几千、几万条之后，那些每次进页面都要跑一遍的纯计算还撑不撑得住。
 *
 * 测的是「点开流水页、切月份、看首页汇总、同步往返」这几件真事在 JVM 上的耗时。
 *
 * 两道闸，各管一件事：
 * - **预算**：拦「整体慢了一大截」的常数级劣化。数字取自本机实测的 5 倍，
 *   CI 的共享 runner 大约比开发机慢两倍，剩下的余量留给 GC 和邻居。
 * - **线性度**：拦「O(n) 写成 O(n²)」。这一条不看机器快慢，只看 50k 相对 1k
 *   的倍数有没有跳出线性应有的范围，在没有性能基线的 CI 上才是真正靠得住的那道。
 *
 * 数字会写进 `build/reports/perf/ledger-perf.md`，CI 直接贴进 job summary。
 */
class LedgerPerfTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
        isLenient = true
    }

    /** 日常、重度、极端（五年账单）三档。 */
    private val sizes = listOf(1_000, 20_000, 50_000)

    /**
     * 每个场景在每个量级下的预算（毫秒）：本机实测中位数 × 6，且不低于档位下限
     * （1k 按 1ms、20k 按 5ms、50k 按 10ms）。
     *
     * 6 倍是留给「CI 的 runner 比开发机慢一倍」加上跑与跑之间的漂移（同一份代码
     * 两次能差 1.5 倍）。下限是因为几十微秒、一两毫秒的操作测出来多少都不值得
     * 报警——预算写成 0.2ms 只会让人天天跟红叉搏斗。中位数本身已经扛得住偶发
     * GC 停顿，剩下的就只是量级判断。
     *
     * 想临时放宽（换了台更慢的机器、或者 CI 那天特别吵）就传
     * `-Dperf.budget.scale=1.5`，不用改代码。
     */
    private val budgets: Map<String, Map<Int, Double>> = mapOf(
        "流水页·当月列表" to mapOf(1_000 to 1.0, 20_000 to 5.0, 50_000 to 15.0),
        "流水页·全量区间" to mapOf(1_000 to 2.0, 20_000 to 40.0, 50_000 to 95.0),
        "流水页·当月搜索" to mapOf(1_000 to 1.0, 20_000 to 5.0, 50_000 to 15.0),
        "流水页·全量搜索" to mapOf(1_000 to 3.5, 20_000 to 70.0, 50_000 to 200.0),
        "首页·月度汇总" to mapOf(1_000 to 1.0, 20_000 to 5.0, 50_000 to 10.0),
        "首页·当月每日柱" to mapOf(1_000 to 1.0, 20_000 to 5.0, 50_000 to 10.0),
        "首页·年度月份柱" to mapOf(1_000 to 1.0, 20_000 to 6.0, 50_000 to 10.0),
        "资产·全时段净额" to mapOf(1_000 to 1.0, 20_000 to 5.0, 50_000 to 10.0),
        "编辑·插入一笔" to mapOf(1_000 to 1.5, 20_000 to 25.0, 50_000 to 65.0),
        "退款页·总览" to mapOf(1_000 to 1.5, 20_000 to 30.0, 50_000 to 80.0),
        "退款页·候选" to mapOf(1_000 to 1.0, 20_000 to 5.0, 50_000 to 15.0),
        "同步·合并两份" to mapOf(1_000 to 2.5, 20_000 to 40.0, 50_000 to 150.0),
        "同步·墓碑比对" to mapOf(1_000 to 1.0, 20_000 to 8.5, 50_000 to 20.0),
        "同步·JSON 编码" to mapOf(1_000 to 15.0, 20_000 to 250.0, 50_000 to 550.0),
        "同步·JSON 解码" to mapOf(1_000 to 15.0, 20_000 to 200.0, 50_000 to 500.0),
    )

    /** 50 倍数据量，按 O(n) 算是 50 倍耗时；留三倍给 log 项、常数项和抖动。 */
    private val scalingCap = 150.0

    private val fixtures by lazy { sizes.associateWith { LedgerFixtures.ledger(it) } }

    /** 一个量级下的全部场景。计时之外的准备（造 fixture、预编码）都在这里做完。 */
    private fun scenariosOf(size: Int): List<Pair<String, () -> Any?>> {
        val ledger = fixtures.getValue(size)
        val entries = ledger.entries
        val remote = LedgerFixtures.remoteOf(ledger)
        val stamp = "2026-11-25T12:00:00Z"
        val deleted = mapOf("entries:e_0000001" to stamp)
        val after = ledger.copy(entries = entries.drop(1))
        val encoded = json.encodeToString(Ledger.serializer(), ledger)
        // 一屏之外的那些账：用户在筛选里把区间拉到「全部」时走的就是这条
        val all = EntryFilter(from = "1990-01-01", to = "2030-12-31")
        val sample = entries[entries.size / 2]

        return listOf(
            "流水页·当月列表" to { EntryFilter().apply(entries, "2026-11") },
            "流水页·全量区间" to { all.apply(entries, "2026-11") },
            "流水页·当月搜索" to { EntryFilter().apply(entries, "2026-11", query = "美团") },
            "流水页·全量搜索" to { all.apply(entries, "2026-11", query = "美团") },
            "首页·月度汇总" to { LedgerMath.summarize(entries, "2026-11") },
            "首页·当月每日柱" to { LedgerMath.dailyTrend(entries, "2026-11") },
            "首页·年度月份柱" to { LedgerMath.yearMonths(entries, "2026") },
            "资产·全时段净额" to { LedgerMath.allTimeNet(entries) },
            "编辑·插入一笔" to {
                LedgerOps.withEntry(
                    ledger,
                    Entry(
                        id = "bench-new",
                        date = "2026-11-25",
                        kind = Kind.EXPENSE,
                        amount = 2_500,
                        category = "餐饮/午餐",
                        account = "支付宝",
                    ),
                )
            },
            "退款页·总览" to { LedgerOps.refundOverview(ledger) },
            "退款页·候选" to { LedgerOps.refundCandidates(ledger, sample) },
            "同步·合并两份" to {
                LedgerMerge.merge(local = ledger, remote = remote, preferRemote = false, deleted = deleted)
            },
            "同步·墓碑比对" to { SyncTombstones.removals(ledger, after, stamp) },
            "同步·JSON 编码" to { json.encodeToString(Ledger.serializer(), ledger) },
            "同步·JSON 解码" to { json.decodeFromString(Ledger.serializer(), encoded) },
        )
    }

    @Test
    fun `账本在 1k 20k 50k 条下的耗时`() {
        // 先把所有场景在最大量级空跑两遍。JIT 编译和堆扩容的成本必须落在计时之外：
        // 少了这一步，20k 的 JSON 解码会测出 255ms，比 50k 的 219ms 还慢——那是在替
        // 50k 的那一轮交编译费，不是真的慢了。
        val largest = sizes.last()
        val primer = scenariosOf(largest)
        repeat(2) { primer.forEach { (_, block) -> block() } }

        val scale = System.getProperty("perf.budget.scale")?.toDoubleOrNull() ?: 1.0
        val cells = mutableListOf<Bench.Cell>()
        for (size in sizes) {
            for ((name, block) in scenariosOf(size)) {
                cells += Bench.Cell(
                    scenario = name,
                    size = size,
                    stats = Bench.measure(block = block),
                    budgetMs = (budgets[name]?.get(size) ?: Double.MAX_VALUE) * scale,
                )
            }
        }
        val scaling = Bench.scaling(cells, from = sizes.first(), to = largest, cap = scalingCap)

        val dir = File(System.getProperty("perf.report.dir") ?: "build/reports/perf")
        Bench.writeReport(dir, cells, scaling, sizes.first(), largest)
        println()
        println("账本压测（毫秒）· ${Bench.machine()}")
        println(Bench.header())
        println("|" + "---|".repeat(6))
        cells.forEach { println(Bench.row(it)) }

        val over = cells.filter { !it.pass }
        val nonlinear = scaling.filter { !it.pass }
        assertTrue(
            buildString {
                if (over.isNotEmpty()) {
                    appendLine("这些场景超预算了：")
                    over.forEach { appendLine(Bench.row(it)) }
                }
                if (nonlinear.isNotEmpty()) {
                    appendLine("这些场景的耗时随账单增长不再是线性的（疑似 O(n²)）：")
                    nonlinear.forEach { appendLine(Bench.scalingRow(it)) }
                }
                appendLine()
                appendLine("完整报告：${File(dir, "ledger-perf.md").absolutePath}")
                appendLine("如果只是这台机器慢（而不是代码退化），加 -Dperf.budget.scale=1.3 重跑一遍复核。")
            },
            over.isEmpty() && nonlinear.isEmpty(),
        )
    }
}
