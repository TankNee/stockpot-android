package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.importer.BillParser
import cn.neeto.stockpot.core.importer.ImportEngine
import cn.neeto.stockpot.core.importer.ParsedBill
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.model.emptyLedger
import cn.neeto.stockpot.core.model.normalized
import cn.neeto.stockpot.core.model.signedBalance
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerTest {

    private val header = listOf(
        "交易时间", "交易对方", "商品", "收/支", "金额", "支付方式", "当前状态", "交易单号",
    )

    private fun rows(vararg data: List<String>) = ParsedBill(
        source = "wechat",
        rows = data.mapNotNull { row ->
            BillParser.parseRows(listOf(header, row)).rows.firstOrNull()
        },
        detected = true,
    )

    @Test
    fun `refunds reduce the effective cost without a second row`() {
        val bill = rows(
            listOf("2026-11-01 10:00", "淘宝", "耳机", "支出", "99.00", "零钱", "已全额退款", "W1"),
        )
        val entry = ImportEngine.apply(emptyLedger(), bill, "a.xlsx").ledger.entries.first()
        assertEquals(9900L, entry.amount)
        assertEquals(9900L, entry.refundedAmount)
        assertEquals(0L, entry.effectiveAmount)
    }

    @Test
    fun `separate refund row is matched to the purchase`() {
        val ledger = emptyLedger()
        val purchase = rows(
            listOf("2026-11-01 10:00", "京东", "键盘", "支出", "300.00", "零钱", "支付成功", "W1"),
        )
        val afterPurchase = ImportEngine.apply(ledger, purchase, "a.xlsx").ledger
        val refund = rows(
            listOf("2026-11-03 09:00", "京东", "键盘", "收入", "300.00", "零钱", "退款", "W2"),
        )
        val outcome = ImportEngine.apply(afterPurchase, refund, "b.xlsx")
        assertEquals(1, outcome.refundsLinked)
        assertEquals(2, outcome.ledger.entries.size)
        val linked = outcome.ledger.entries.first { it.externalId == "W1" }
        assertEquals(30000L, linked.refundedAmount)
        // 退款行留着，两边互相指认，点一下能跳到对面
        val refundRow = outcome.ledger.entries.first { it.externalId == "W2" }
        assertEquals(linked.id, refundRow.refundLinkId)
        assertEquals(refundRow.id, linked.refundLinkId)
        assertTrue(refundRow.excluded)
        // 钱只算一次：合计是 0，不是 -300 + 300
        assertEquals(0L, LedgerMath.summarize(outcome.ledger.entries, "2026-11").expense)
        assertEquals(0L, LedgerMath.summarize(outcome.ledger.entries, "2026-11").income)
    }

    @Test
    fun `linking a refund by hand keeps the net the same and unlinking restores it`() {
        fun spend(id: String, amount: Long) = Entry(
            id = id, date = "2026-11-01", kind = Kind.EXPENSE, amount = amount, category = "购物",
        )
        fun refund(id: String, amount: Long) = Entry(
            id = id, date = "2026-11-05", kind = Kind.INCOME, amount = amount, category = "退款",
        )
        val ledger = emptyLedger().copy(entries = listOf(spend("e1", 30_000), refund("r1", 10_000)))
        val before = LedgerMath.allTimeNet(ledger.entries)
        assertEquals(-30_000L + 10_000L, before)

        val linked = LedgerOps.linkRefund(ledger, "e1", "r1")
        assertEquals("r1", linked.entries.first { it.id == "e1" }.refundLinkId)
        assertEquals("e1", linked.entries.first { it.id == "r1" }.refundLinkId)
        assertEquals(10_000L, linked.entries.first { it.id == "e1" }.refundedAmount)
        // 退款行改为不计入，支出只算剩下的 200：净额还是 -200
        assertEquals(-20_000L, LedgerMath.allTimeNet(linked.entries))

        val unlinked = LedgerOps.unlinkRefund(linked, "r1")
        assertEquals("", unlinked.entries.first { it.id == "e1" }.refundLinkId)
        assertEquals(0L, unlinked.entries.first { it.id == "e1" }.refundedAmount)
        assertEquals(-20_000L, LedgerMath.allTimeNet(unlinked.entries))
    }

    @Test
    fun `refund overview splits pending refunds, refundable expenses and pairs`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 30_000),
                // 已经退完的支出不该出现在「可退」里
                Entry(id = "e2", date = "2026-11-02", kind = Kind.EXPENSE, amount = 5_000, refundedAmount = 5_000),
                Entry(
                    id = "e3", date = "2026-11-03", kind = Kind.EXPENSE, amount = 12_000,
                    refundedAmount = 6_000, refundLinkId = "r2",
                ),
                Entry(id = "r1", date = "2026-11-05", kind = Kind.INCOME, amount = 10_000, category = "退款", excluded = true),
                Entry(
                    id = "r2", date = "2026-11-06", kind = Kind.INCOME, amount = 6_000,
                    category = "退款", excluded = true, refundLinkId = "e3",
                ),
                // 账户互转也是「不计入合计」，但它不是退款
                Entry(id = "t1", date = "2026-11-07", kind = Kind.TRANSFER, amount = 1_000, excluded = true),
            ),
        )
        val overview = LedgerOps.refundOverview(ledger)
        assertEquals(listOf("r1"), overview.pendingRefunds.map { it.id })
        assertEquals(listOf("e3", "e1"), overview.openExpenses.map { it.id })
        assertEquals(listOf("e3"), overview.pairs.map { it.expense.id })
        assertEquals("r2", overview.pairs.single().refund.id)
    }

    @Test
    fun `manual pairing can look past a mismatched amount`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 25_000),
                Entry(id = "r1", date = "2026-11-03", kind = Kind.INCOME, amount = 30_000, category = "退款", excluded = true),
            ),
        )
        val expense = ledger.entries.first { it.id == "e1" }
        // 默认只认分毫不差的；多退的 50 块得人来判断
        assertTrue(LedgerOps.refundCandidates(ledger, expense).isEmpty())
        assertTrue(LedgerOps.refundCandidates(ledger, expense, match = LedgerOps.RefundMatch.Partial).isEmpty())
        assertEquals(
            listOf("r1"),
            LedgerOps.refundCandidates(ledger, expense, match = LedgerOps.RefundMatch.Any).map { it.id },
        )
    }

    @Test
    fun `auto pairing only takes unambiguous exact matches`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 10_000),
                Entry(id = "e2", date = "2026-11-02", kind = Kind.EXPENSE, amount = 20_000),
                Entry(id = "r1", date = "2026-11-03", kind = Kind.INCOME, amount = 10_000, category = "退款", excluded = true),
                Entry(id = "r2", date = "2026-11-04", kind = Kind.INCOME, amount = 15_000, category = "退款", excluded = true),
            ),
        )
        val (next, count) = LedgerOps.autoPairRefunds(ledger)
        assertEquals(1, count)
        assertEquals("r1", next.entries.first { it.id == "e1" }.refundLinkId)
        assertEquals(10_000L, next.entries.first { it.id == "e1" }.refundedAmount)
        // 150 那笔没有金额刚好的消费，留着让人挑
        assertEquals("", next.entries.first { it.id == "r2" }.refundLinkId)
        assertEquals(-20_000L, LedgerMath.allTimeNet(next.entries))
    }

    @Test
    fun `auto pairing skips the refund when two expenses look the same`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 10_000),
                Entry(id = "e2", date = "2026-11-02", kind = Kind.EXPENSE, amount = 10_000),
                Entry(id = "r1", date = "2026-11-03", kind = Kind.INCOME, amount = 10_000, category = "退款", excluded = true),
            ),
        )
        val (next, count) = LedgerOps.autoPairRefunds(ledger)
        assertEquals(0, count)
        assertEquals("", next.entries.first { it.id == "r1" }.refundLinkId)
    }

    @Test
    fun `deleting a refund row gives the money back to the purchase`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 30_000),
                Entry(id = "r1", date = "2026-11-05", kind = Kind.INCOME, amount = 10_000),
            ),
        )
        val linked = LedgerOps.linkRefund(ledger, "e1", "r1")
        val after = LedgerOps.deleteEntry(linked, "r1")
        assertEquals(1, after.entries.size)
        val expense = after.entries.single()
        assertEquals(0L, expense.refundedAmount)
        assertEquals("", expense.refundLinkId)
        assertEquals(-30_000L, LedgerMath.allTimeNet(after.entries))
    }

    @Test
    fun `refund candidates only show unlinked rows that fit`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 30_000),
                Entry(id = "small", date = "2026-11-02", kind = Kind.INCOME, amount = 10_000),
                Entry(id = "big", date = "2026-11-02", kind = Kind.INCOME, amount = 50_000),
                // 金额正好，但已经配给别人了
                Entry(id = "taken", date = "2026-11-03", kind = Kind.INCOME, amount = 30_000, refundLinkId = "x"),
            ),
        )
        val expense = ledger.entries.first { it.id == "e1" }
        // 默认只认金额分毫不差的：30 块这笔没有可配的（正好那笔已被占）
        assertTrue(LedgerOps.refundCandidates(ledger, expense).isEmpty())
        // 放宽到部分退款，10 块那笔才出现；50 块比花的还多，只在「全部」里
        assertEquals(
            listOf("small"),
            LedgerOps.refundCandidates(ledger, expense, match = LedgerOps.RefundMatch.Partial).map { it.id },
        )
        assertEquals(
            listOf("small", "big"),
            LedgerOps.refundCandidates(ledger, expense, match = LedgerOps.RefundMatch.Any).map { it.id },
        )

        // 退款行反过来：默认挑剩下余额正好等于这笔退款的消费
        val refundRow = ledger.entries.first { it.id == "small" }
        assertTrue(LedgerOps.refundCandidates(ledger, refundRow).isEmpty())
        assertEquals(
            listOf("e1"),
            LedgerOps.refundCandidates(ledger, refundRow, match = LedgerOps.RefundMatch.Partial).map { it.id },
        )
    }

    @Test
    fun `fund earnings and money moving are not refunds`() {
        val ledger = emptyLedger().copy(
            entries = listOf(
                Entry(id = "e1", date = "2026-11-01", kind = Kind.EXPENSE, amount = 10_000),
                // 余额宝的收益发放：是收入、也不计入合计，但它是本金在生息
                Entry(
                    id = "y1", date = "2026-11-02", kind = Kind.INCOME, amount = 128,
                    category = "理财收益", counterparty = "余额宝", excluded = true,
                ),
                // 换个口袋放钱，也不是退款
                Entry(
                    id = "y2", date = "2026-11-03", kind = Kind.INCOME, amount = 5_000,
                    category = "其他", note = "余额宝转出", excluded = true,
                ),
                // 标了不计入、但既不是退款也不像退款的收入，不该混进来
                Entry(id = "y3", date = "2026-11-03", kind = Kind.INCOME, amount = 2_000, excluded = true),
                // 真退款还留着
                Entry(id = "r1", date = "2026-11-04", kind = Kind.INCOME, amount = 3_000, category = "退款", excluded = true),
            ),
        )
        assertEquals(listOf("r1"), LedgerOps.refundOverview(ledger).pendingRefunds.map { it.id })

        // 挑对面的时候也不该把余额宝的收益摆出来（没写分类的 y3 还能当候选，让人自己认）
        val expense = ledger.entries.first { it.id == "e1" }
        val loose = LedgerOps.refundCandidates(ledger, expense, match = LedgerOps.RefundMatch.Any).map { it.id }
        assertTrue(loose.none { it == "y1" || it == "y2" })
        assertTrue(loose.contains("r1"))
    }

    @Test
    fun `reimporting the same bill adds nothing`() {
        val bill = rows(
            listOf("2026-11-01 10:00", "瑞幸", "咖啡", "支出", "19.50", "零钱", "支付成功", "W9"),
        )
        val first = ImportEngine.apply(emptyLedger(), bill, "a.xlsx")
        val second = ImportEngine.apply(first.ledger, bill, "a.xlsx")
        assertEquals(1, first.added)
        assertEquals(0, second.added)
        assertEquals(1, second.duplicates)
        assertEquals(1, second.ledger.entries.size)
    }

    @Test
    fun `keywords file the row into a leaf category`() {
        val bill = rows(
            listOf("2026-11-01 08:00", "瑞幸咖啡", "生椰拿铁", "支出", "19.50", "零钱", "支付成功", "W1"),
        )
        val entry = ImportEngine.apply(emptyLedger(), bill, "a.xlsx").ledger.entries.first()
        assertEquals("餐饮/咖啡", entry.category)
        assertTrue(entry.categoryLocked.not())
    }

    @Test
    fun `summary ignores excluded and archived rows`() {
        val ledger = emptyLedger()
        var next = LedgerOps.withEntry(
            ledger,
            LedgerOps.entry(
                EntryInput(date = "2026-11-01", amount = 10000, category = "餐饮", kind = "expense"),
            ),
        )
        next = LedgerOps.withEntry(
            next,
            LedgerOps.entry(
                EntryInput(date = "2026-11-02", amount = 5000, category = "餐饮", kind = "expense", excluded = true),
            ),
        )
        next = LedgerOps.withEntry(
            next,
            LedgerOps.entry(
                EntryInput(date = "2026-11-03", amount = 200000, category = "工资", kind = "income"),
            ),
        )
        val summary = LedgerMath.summarize(next.entries, "2026-11")
        assertEquals(10000L, summary.expense)
        assertEquals(200000L, summary.income)
        assertEquals(190000L, summary.net)
        assertEquals(2, summary.count)
    }

    @Test
    fun `daily trend buckets by day and ignores other months`() {
        var ledger = emptyLedger()
        ledger = LedgerOps.withEntry(
            ledger,
            LedgerOps.entry(EntryInput(date = "2026-11-01", amount = 1000, category = "餐饮", kind = "expense")),
        )
        ledger = LedgerOps.withEntry(
            ledger,
            LedgerOps.entry(EntryInput(date = "2026-11-01", amount = 2500, category = "交通", kind = "expense")),
        )
        ledger = LedgerOps.withEntry(
            ledger,
            LedgerOps.entry(EntryInput(date = "2026-12-01", amount = 9900, category = "餐饮", kind = "expense")),
        )
        val trend = LedgerMath.dailyTrend(ledger.entries, "2026-11")
        assertEquals(30, trend.size)
        assertEquals(3500L, trend[0].expense)
        assertEquals(0L, trend[1].expense)
    }

    @Test
    fun `deleting a project detaches rows but keeps them`() {
        var ledger = emptyLedger()
        ledger = LedgerOps.withEntry(
            ledger,
            LedgerOps.entry(EntryInput(date = "2026-11-01", amount = 1000, category = "餐饮", kind = "expense")),
        )
        val (projects, id) = LedgerOps.addProject(ledger.projects, "出差", "", "", "")
        ledger = ledger.copy(projects = projects)
        ledger = LedgerOps.attach(ledger, id, setOf(ledger.entries.first().id))
        assertEquals(id, ledger.entries.first().projectId)
        ledger = LedgerOps.deleteProject(ledger, id)
        assertEquals(1, ledger.entries.size)
        assertEquals("", ledger.entries.first().projectId)
        assertTrue(ledger.projects.isEmpty())
    }

    @Test
    fun `learn category creates the missing parent`() {
        val categories = LedgerOps.learnCategory(emptyLedger().categories, "宠物/猫粮", "expense")
        assertNotNull(categories.firstOrNull { it.path == "宠物" })
        assertNotNull(categories.firstOrNull { it.path == "宠物/猫粮" })
    }

    @Test
    fun `renaming a group moves its entries and rules`() {
        var ledger = emptyLedger()
        ledger = LedgerOps.withEntry(
            ledger,
            LedgerOps.entry(EntryInput(date = "2026-11-01", amount = 2200, category = "餐饮/咖啡", kind = "expense")),
        )
        val group = ledger.categories.first { it.path == "餐饮" }
        val renamed = LedgerOps.renameCategory(ledger, group, "吃饭", "", "expense")
        assertEquals("吃饭/咖啡", renamed.entries.first().category)
        assertNotNull(renamed.categories.firstOrNull { it.path == "吃饭/咖啡" })
        assertTrue(renamed.categories.none { it.path == "餐饮/咖啡" })
    }

    @Test
    fun `date picker millis round trip in utc`() {
        val date = "2026-11-23"
        assertEquals(date, Fmt.fromEpochMillis(Fmt.epochMillis(date)))
        // DatePicker 的毫秒是 UTC 零点：东八区的本地零点会少一天
        assertEquals(0L, Fmt.epochMillis(date) % 86_400_000L)
    }

    @Test
    fun `parse yuan accepts messy input`() {
        assertEquals(1250L, Fmt.parseYuan("12.5"))
        assertEquals(1250L, Fmt.parseYuan("¥12.50"))
        assertEquals(0L, Fmt.parseYuan("0"))
        assertEquals(null, Fmt.parseYuan(""))
        assertEquals(-23860L, Fmt.parseYuan("-238.6"))
        assertEquals(1250L, Fmt.parseYuan("12..5"))
    }

    @Test
    fun `money formatting keeps grouping and cents`() {
        assertEquals("0.00", Fmt.cents(0))
        assertEquals("0.05", Fmt.cents(5))
        assertEquals("12.50", Fmt.cents(1250))
        assertEquals("1,234.56", Fmt.cents(123456))
        assertEquals("1,000,000.00", Fmt.cents(100_000_000))
        assertEquals("¥12.50", Fmt.yuan(1250))
        assertEquals("+¥12.50", Fmt.yuan(1250, signed = true))
        assertEquals("-¥12.50", Fmt.yuan(-1250))
    }

    @Test
    fun `compact amount reads well on calendar cells`() {
        assertEquals("0", Fmt.compact(0))
        assertEquals("8", Fmt.compact(800))
        assertEquals("8.5", Fmt.compact(850))
        assertEquals("123", Fmt.compact(12_300))
        assertEquals("123.5", Fmt.compact(12_345))
        assertEquals("1.2k", Fmt.compact(123_456))
        assertEquals("23k", Fmt.compact(2_300_000))
        assertEquals("1.2m", Fmt.compact(123_456_789))
    }

    @Test
    fun `short date and day label stay cheap but correct`() {
        assertEquals("11-23", Fmt.shortDate("2026-11-23"))
        assertEquals("坏日期", Fmt.shortDate("坏日期"))
        assertEquals("11月23日 周一", Fmt.dayLabel("2026-11-23"))
        assertEquals("周一", Fmt.weekday("2026-11-23"))
    }

    @Test
    fun `desktop ledger json opens, keeps unknown fields ignorable and round trips`() {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
        val legacy = """
            {
              "entries": [
                {"id":"e1","date":"2026-11-01","kind":"expense","amount":1234,
                 "category":"餐饮","createdAt":"2026-11-01T10:00:00Z","unknownFutureField":1}
              ],
              "categories": ["餐饮", "交通"],
              "schemaVersion": 4
            }
        """.trimIndent()
        val ledger = json.decodeFromString<Ledger>(legacy)
        assertEquals(2, ledger.categories.size)
        assertEquals("餐饮", ledger.categories[0].name)
        assertEquals(1234L, ledger.entries[0].amount)

        val encoded = json.encodeToString(Ledger.serializer(), ledger)
        assertTrue(encoded.contains("\"amount\": 1234"))
        val again = json.decodeFromString<Ledger>(encoded)
        assertEquals(1, again.entries.size)
        assertEquals("餐饮", again.entries[0].category)
    }

    // 信用卡填的是「下月待还」，用户不会自己加负号，载入时统一翻成负债
    @Test
    fun `信用卡填的正数在载入时翻成负债`() {
        val ledger = emptyLedger().copy(
            wallets = listOf(
                Wallet(id = "w1", name = "招行信用卡", kind = "credit", balance = 884_142),
                Wallet(id = "w2", name = "储蓄卡", kind = "debit", balance = 100_000),
            ),
        )
        val fixed = ledger.normalized()
        assertEquals(-884_142L, fixed.wallets[0].balance)
        assertEquals(100_000L, fixed.wallets[1].balance)
        // 净资产把欠款扣掉
        assertEquals(100_000L - 884_142L, fixed.wallets.sumOf { it.balance })
        // 已经记成负数的原样保留
        assertEquals(-884_142L, fixed.normalized().wallets[0].balance)
        assertEquals(-884_142L, fixed.wallets[0].signedBalance())
        // 非信用卡的负余额（比如透支）不动
        assertEquals(-50_000L, Wallet(id = "w3", name = "余额", kind = "debit", balance = -50_000).signedBalance())
    }
}
