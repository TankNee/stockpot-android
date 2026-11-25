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
 * 账本压测：账单攒到几千、几万条之后，那些每次进页面都要跑一遍的纯计算还撑不撑得住。
 *
 * 测的是「点开流水页、切月份、看首页汇总、同步往返」这几件真事在 JVM 上的耗时。
 * 手机 CPU 大概比 CI 的 x86 慢 2~3 倍，所以预算按实测的 4 倍往上留：
 * 只用来兜住「某次改动把 O(n) 写成了 O(n²)」这种量级劣化，不做微基准的精度追求。
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
     * 每个场景在每个量级下的预算（毫秒）。
     *
     * 取本机实测的 4~6 倍：CI 的机器比开发机慢，JIT 也没那么热，
     * 但真把 O(n) 写成 O(n²) 的时候，50k 条会从几十毫秒变成几秒，照样拦得住。
     */
    private val budgets: Map<String, Map<Int, Double>> = mapOf(
        "流水页·当月列表" to mapOf(1_000 to 15.0, 20_000 to 40.0, 50_000 to 90.0),
        "流水页·全量区间" to mapOf(1_000 to 25.0, 20_000 to 90.0, 50_000 to 200.0),
        "流水页·当月搜索" to mapOf(1_000 to 15.0, 20_000 to 40.0, 50_000 to 90.0),
        "流水页·全量搜索" to mapOf(1_000 to 25.0, 20_000 to 90.0, 50_000 to 200.0),
        "首页·月度汇总" to mapOf(1_000 to 15.0, 20_000 to 40.0, 50_000 to 90.0),
        "首页·当月每日柱" to mapOf(1_000 to 15.0, 20_000 to 40.0, 50_000 to 80.0),
        "首页·年度月份柱" to mapOf(1_000 to 15.0, 20_000 to 40.0, 50_000 to 80.0),
        "资产·全时段净额" to mapOf(1_000 to 15.0, 20_000 to 30.0, 50_000 to 60.0),
        "编辑·插入一笔" to mapOf(1_000 to 25.0, 20_000 to 60.0, 50_000 to 120.0),
        "退款页·总览" to mapOf(1_000 to 30.0, 20_000 to 80.0, 50_000 to 160.0),
        "退款页·候选" to mapOf(1_000 to 30.0, 20_000 to 80.0, 50_000 to 160.0),
        "同步·合并两份" to mapOf(1_000 to 40.0, 20_000 to 150.0, 50_000 to 320.0),
        "同步·墓碑比对" to mapOf(1_000 to 25.0, 20_000 to 60.0, 50_000 to 120.0),
        "同步·JSON 编码" to mapOf(1_000 to 40.0, 20_000 to 140.0, 50_000 to 260.0),
        "同步·JSON 解码" to mapOf(1_000 to 50.0, 20_000 to 160.0, 50_000 to 300.0),
    )

    @Test
    fun `账本在 1k 20k 50k 条下的耗时`() {
        val fixtures = sizes.associateWith { LedgerFixtures.ledger(it) }
        val cells = mutableListOf<Bench.Cell>()

        for (size in sizes) {
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

            val scenarios: List<Pair<String, () -> Any?>> = listOf(
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

            for ((name, block) in scenarios) {
                val stats = Bench.measure(rounds = 3, warmup = 1, block = block)
                cells += Bench.Cell(
                    scenario = name,
                    size = size,
                    stats = stats,
                    budgetMs = budgets[name]?.get(size) ?: Double.MAX_VALUE,
                )
            }
        }

        val dir = File(System.getProperty("perf.report.dir") ?: "build/reports/perf")
        Bench.writeReport(dir, cells)
        println()
        println("账本压测（毫秒）：")
        println(Bench.header())
        println("|" + "---|".repeat(6))
        cells.forEach { println(Bench.row(it)) }
        println("报告：${File(dir, "ledger-perf.md").absolutePath}")

        val over = cells.filter { !it.pass }
        assertTrue(
            "这些场景超预算了：\n" + over.joinToString("\n") { Bench.row(it) },
            over.isEmpty(),
        )
    }
}
