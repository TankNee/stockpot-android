package cn.neeto.stockpot.perf

import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Source
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.model.builtinIcon
import cn.neeto.stockpot.core.model.defaultRules
import java.time.LocalDate

/**
 * 造账本样本。
 *
 * 形状尽量贴近真人五年的账：每天三五笔、工作日多周末少、分类长尾、
 * 一小撮退款和退过款的消费、少量归档行、几个账户。生成是确定性的
 * （种子固定），这样两次跑的数字才能对比——压测最怕样本自己抖。
 */
object LedgerFixtures {

    private val leaves = listOf(
        "餐饮/午餐" to 1_800,
        "餐饮/晚餐" to 3_600,
        "餐饮/咖啡" to 2_600,
        "餐饮/外卖" to 4_200,
        "交通/地铁公交" to 500,
        "交通/打车" to 2_900,
        "购物/日用" to 6_800,
        "购物/服饰" to 19_900,
        "居住/房租" to 268_000,
        "居住/水电燃气" to 12_800,
        "娱乐/影音" to 2_500,
        "医疗/药品" to 5_600,
        "通讯/话费" to 3_000,
        "教育/书籍" to 8_900,
    )

    private val accounts = listOf("微信零钱", "支付宝", "招商银行", "羊城通", "信用卡", "医保卡")

    private val shops = listOf(
        "楼下小馆", "美团外卖", "瑞幸咖啡", "盒马鲜生", "广州地铁", "滴滴出行",
        "永辉超市", "中国移动", "当当网", "大参林药房", "万达影城", "优衣库",
    )

    /**
     * @param size 账目条数
     * @param seed 随机种子，同一条数下不同种子给出不同的分布
     */
    fun ledger(size: Int, seed: Long = 20_261_125L): Ledger {
        val today = LocalDate.of(2026, 11, 25)
        val entries = ArrayList<Entry>(size)
        var state = seed
        fun next(bound: Int): Int {
            // xorshift：不依赖 Random 的实现，跨 JDK 版本结果一致
            state = state xor (state shl 13)
            state = state xor (state ushr 7)
            state = state xor (state shl 17)
            return ((state ushr 1) % bound).toInt().let { if (it < 0) it + bound else it }
        }

        // 五年账：一天大概 size/1800 笔，最老的排在前面（和 App 里的顺序一致，倒序不需要）
        val days = 1_826
        val perDay = (size / days).coerceAtLeast(1)
        var made = 0
        var dayOffset = days
        while (made < size) {
            val date = today.minusDays(dayOffset.toLong()).toString()
            // 周末两天少一点，工作日多一点：一天几十条的时候才有这个形状
            val count = if (dayOffset % 7 < 2) perDay else perDay + 1
            repeat(count) {
                if (made >= size) return@repeat
                val (path, base) = leaves[next(leaves.size)]
                val amount = (base + next(base / 2) - base / 4).coerceAtLeast(100).toLong()
                val income = next(40) == 0
                val archived = next(60) == 0
                val refunded = if (!income && next(50) == 0) amount / 2 else 0L
                entries += Entry(
                    id = "e_${made.toString().padStart(7, '0')}",
                    date = date,
                    time = "${(7 + next(15)).toString().padStart(2, '0')}:${next(60).toString().padStart(2, '0')}",
                    kind = if (income) Kind.INCOME else Kind.EXPENSE,
                    amount = if (income) amount * 8 else amount,
                    category = if (income) "工资/基本工资" else path,
                    account = accounts[next(accounts.size)],
                    counterparty = shops[next(shops.size)],
                    note = if (next(3) == 0) "第 ${next(900)} 天的账" else "",
                    source = when (next(4)) {
                        0 -> Source.WECHAT
                        1 -> Source.ALIPAY
                        2 -> Source.IMPORTED
                        else -> Source.MANUAL
                    },
                    externalId = if (next(5) == 0) "tb_${made}" else "",
                    excluded = refunded > 0 && next(2) == 0,
                    archived = archived,
                    refundedAmount = refunded,
                    refundedAt = if (refunded > 0) date else "",
                    createdAt = "${date}T12:00:00Z",
                )
                made++
            }
            dayOffset--
            if (dayOffset < 0) dayOffset = days
        }

        val catDefs = (leaves.map { it.first } + "工资/基本工资" + "退款")
            .distinct()
            .flatMap { path ->
                val parent = path.substringBefore('/')
                val leaf = path.substringAfter('/')
                if (parent == leaf) listOf(CategoryDef(parent, icon = builtinIcon(parent))) else listOf(
                    CategoryDef(parent, icon = builtinIcon(parent)),
                    CategoryDef(leaf, parent = parent, icon = builtinIcon(parent)),
                )
            }
            .distinctBy { it.parent + "/" + it.name }

        val wallets = accounts.mapIndexed { index, name ->
            Wallet(
                id = "wlt_$index",
                name = name,
                kind = if (name == "信用卡") "credit" else "debit",
                institution = name,
                balance = 100_000L * (index + 1) - if (index == 4) 400_000 else 0,
                updatedAt = "2026-11-25T10:00:00Z",
            )
        }

        val projects = (0 until 6).map { index ->
            Project(
                id = "prj_$index",
                name = "项目 $index",
                startDate = today.minusDays((index * 30 + 10).toLong()).toString(),
                endDate = today.minusDays((index * 30).toLong()).toString(),
                createdAt = "2026-01-01T00:00:00Z",
            )
        }

        val stocks = listOf(
            Stock("sh600519", "600519", "贵州茅台", "a", "2026-11-25T10:00:00Z"),
            Stock("sz000001", "000001", "平安银行", "a", "2026-11-25T10:00:00Z"),
            Stock("hk00700", "00700", "腾讯控股", "hk", "2026-11-25T10:00:00Z"),
        )
        val stockAccounts = listOf(
            StockAccount(
                id = "sacc_0",
                name = "华泰",
                entries = mapOf(
                    "sh600519" to Holding(costE4 = 15_000_000, shares = 100),
                    "sz000001" to Holding(costE4 = 1_200_000, shares = 2_000),
                ),
                updatedAt = "2026-11-25T10:00:00Z",
            ),
            StockAccount(
                id = "sacc_1",
                name = "富途",
                entries = mapOf("hk00700" to Holding(costE4 = 32_000_000, shares = 200)),
                updatedAt = "2026-11-25T10:00:00Z",
            ),
        )

        return Ledger(
            entries = entries,
            categories = catDefs,
            accounts = accounts,
            wallets = wallets,
            projects = projects,
            rules = defaultRules(),
            stocks = stocks,
            stockAccounts = stockAccounts,
        )
    }

    /** 云端那份：大部分一样，少量被别的设备改过、少量是新增的。 */
    fun remoteOf(local: Ledger): Ledger {
        val entries = local.entries.mapIndexed { index, entry ->
            when {
                index % 137 == 0 -> entry.copy(note = "另一台设备改过", amount = entry.amount + 100)
                else -> entry
            }
        } + (0 until 20).map { index ->
            Entry(
                id = "remote_$index",
                date = "2026-11-2${index % 5}",
                kind = Kind.EXPENSE,
                amount = 1_000L + index,
                category = "餐饮",
                account = "支付宝",
                createdAt = "2026-11-25T09:00:00Z",
            )
        }
        return local.copy(entries = entries)
    }
}
