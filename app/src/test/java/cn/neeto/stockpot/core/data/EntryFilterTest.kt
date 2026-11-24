package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryFilterTest {

    private fun entry(
        id: String,
        date: String,
        amount: Long,
        kind: String = Kind.EXPENSE,
        account: String = "招行",
        counterparty: String = "",
        refunded: Long = 0,
        archived: Boolean = false,
    ) = Entry(
        id = id,
        date = date,
        time = "12:00",
        kind = kind,
        amount = amount,
        account = account,
        counterparty = counterparty,
        refundedAmount = refunded,
        archived = archived,
    )

    private val entries = listOf(
        entry("1", "2026-11-01", 5_000, account = "招行", counterparty = "瑞幸"),
        entry("2", "2026-11-15", 120_000, account = "中信", counterparty = "耐克"),
        entry("3", "2026-11-20", 3_000, kind = Kind.INCOME, account = "招行", counterparty = "工资"),
        entry("4", "2026-10-31", 80_000, account = "招行", counterparty = "上月"),
        entry("5", "2026-11-10", 20_000, account = "中信", counterparty = "退了", refunded = 15_000),
    )

    @Test
    fun `默认只看本月且不看归档`() {
        val list = EntryFilter().apply(entries, "2026-11")
        assertEquals(listOf("1", "2", "3", "5").sorted(), list.map { it.id }.sorted())
        assertTrue(EntryFilter().apply(entries + entry("9", "2026-11-05", 100, archived = true), "2026-11").none { it.id == "9" })
    }

    @Test
    fun `类型与账户可以叠加`() {
        val onlyExpense = EntryFilter(kind = Kind.EXPENSE).apply(entries, "2026-11")
        assertEquals(3, onlyExpense.size)

        val account = EntryFilter(account = "招行").apply(entries, "2026-11")
        assertEquals(listOf("1", "3"), account.map { it.id }.sorted())

        val both = EntryFilter(kind = Kind.EXPENSE, account = "中信").apply(entries, "2026-11")
        assertEquals(listOf("2", "5"), both.map { it.id }.sorted())
    }

    @Test
    fun `金额区间按实付算，退款会抵掉`() {
        // 5 号那笔 200 元被退 150，实付 50，落在 40~60 里
        val list = EntryFilter(minCents = 4_000, maxCents = 6_000).apply(entries, "2026-11")
        assertEquals(listOf("1", "5"), list.map { it.id }.sorted())
    }

    @Test
    fun `上下限填反了也按区间来`() {
        val list = EntryFilter(minCents = 6_000, maxCents = 4_000).apply(entries, "2026-11")
        assertEquals(listOf("1", "5"), list.map { it.id }.sorted())
    }

    @Test
    fun `给了日期区间就不再按月`() {
        val list = EntryFilter(from = "2026-10-01", to = "2026-11-05").apply(entries, "2026-11")
        assertEquals(listOf("1", "4"), list.map { it.id }.sorted())
    }

    @Test
    fun `只给起始日期就到今天`() {
        val list = EntryFilter(from = "2026-11-16").apply(entries, "2026-11")
        assertEquals(listOf("3"), list.map { it.id })
    }

    @Test
    fun `排序四种都对`() {
        val desc = EntryFilter(sort = EntryFilter.AMOUNT_DESC).apply(entries, "2026-11")
        assertEquals(listOf("2", "5", "1", "3"), desc.map { it.id })

        val asc = EntryFilter(sort = EntryFilter.AMOUNT_ASC).apply(entries, "2026-11")
        // 1 和 5 都是 50 元，同额时日期新的在前
        assertEquals(listOf("3", "5", "1", "2"), asc.map { it.id })

        val early = EntryFilter(sort = EntryFilter.TIME_ASC).apply(entries, "2026-11")
        assertEquals(listOf("1", "5", "2", "3"), early.map { it.id })

        val latest = EntryFilter().apply(entries, "2026-11")
        assertEquals(listOf("3", "2", "5", "1"), latest.map { it.id })
    }

    @Test
    fun `搜索能命中商家和账户`() {
        val byName = EntryFilter().apply(entries, "2026-11", query = "瑞幸")
        assertEquals(listOf("1"), byName.map { it.id })

        val byAccount = EntryFilter().apply(entries, "2026-11", query = "中信")
        assertEquals(listOf("2", "5"), byAccount.map { it.id }.sorted())
    }

    @Test
    fun `搜索能命中金额，几种写法都认`() {
        // 1200 元那笔：整数、带千分位、带货币符号、带小数都该找得到
        listOf("1200", "1,200", "¥1200", "1200.00", "1200.0").forEach { needle ->
            assertEquals(
                "搜「$needle」",
                listOf("2"),
                EntryFilter().apply(entries, "2026-11", query = needle).map { it.id },
            )
        }
        // 30 元那笔
        assertEquals(listOf("3"), EntryFilter().apply(entries, "2026-11", query = "30").map { it.id })
    }

    @Test
    fun `金额搜索不会把尾数一样的也捞出来`() {
        // 「25」不该命中 125.00：搜金额得是个准数，随便包含就没意义了
        assertTrue(EntryFilter().apply(entries, "2026-11", query = "25").isEmpty())
        // 但同一位数往后带小数的要认：50 命中 50.00
        assertEquals(listOf("1", "5"), EntryFilter().apply(entries, "2026-11", query = "50").map { it.id }.sorted())
    }

    @Test
    fun `退过款的那笔净额和原价都能搜到`() {
        // id 5：200 元退掉 150，净额 50
        assertEquals(listOf("5"), EntryFilter().apply(entries, "2026-11", query = "200").map { it.id })
        assertEquals(listOf("1", "5"), EntryFilter().apply(entries, "2026-11", query = "50").map { it.id }.sorted())
    }

    @Test
    fun `角标只数真正的条件`() {
        assertEquals(0, EntryFilter().activeCount)
        assertEquals(4, EntryFilter(kind = Kind.EXPENSE, account = "招行", minCents = 1_000, sort = "amountAsc").activeCount)
        assertEquals(1, EntryFilter(category = "餐饮").activeCount)
        assertTrue(EntryFilter().isEmpty)
    }

    @Test
    fun `类目按全名比，父级不会顺带把子类带上`() {
        val list = entries.map {
            it.copy(category = if (it.id == "1") "餐饮/咖啡" else if (it.id == "2") "购物/鞋" else "餐饮")
        }
        assertEquals(listOf("1"), EntryFilter(category = "餐饮/咖啡").apply(list, "2026-11").map { it.id })
        assertEquals(listOf("3", "5"), EntryFilter(category = "餐饮").apply(list, "2026-11").map { it.id }.sorted())
        assertEquals(4, EntryFilter().apply(list, "2026-11").size)
    }

    @Test
    fun `金额输入转分，空串和零都算没填`() {
        assertEquals(5_000L, EntryFilter.cents("50"))
        assertEquals(5_050L, EntryFilter.cents("50.5"))
        assertEquals(null, EntryFilter.cents(""))
        assertEquals(null, EntryFilter.cents("0"))
        assertEquals(null, EntryFilter.cents("abc"))
    }
}
