package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.importer.BillParser
import cn.neeto.stockpot.core.importer.ImportEngine
import cn.neeto.stockpot.core.importer.ParsedBill
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.emptyLedger
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
        val linked = outcome.ledger.entries.first { it.externalId == "W1" }
        assertEquals(30000L, linked.refundedAmount)
        assertEquals(1, outcome.ledger.entries.size)
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
    fun `parse yuan accepts messy input`() {
        assertEquals(1250L, Fmt.parseYuan("12.5"))
        assertEquals(1250L, Fmt.parseYuan("¥12.50"))
        assertEquals(0L, Fmt.parseYuan("0"))
        assertEquals(null, Fmt.parseYuan(""))
        assertEquals(-23860L, Fmt.parseYuan("-238.6"))
        assertEquals(1250L, Fmt.parseYuan("12..5"))
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
}
