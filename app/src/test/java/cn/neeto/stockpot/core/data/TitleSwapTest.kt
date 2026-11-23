package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleSwapTest {

    private val all = SwapRule.defaultWords

    private fun entry(
        id: String,
        counterparty: String,
        note: String,
        date: String = "2026-11-01",
        time: String = "12:00",
        archived: Boolean = false,
    ) = Entry(
        id = id,
        date = date,
        time = time,
        amount = 2_800,
        counterparty = counterparty,
        note = note,
        archived = archived,
    )

    @Test
    fun `平台名当标题、备注有内容的才命中`() {
        val hit = entry("1", "美团平台商户", "蜜雪冰城 珍珠奶茶x2")
        val noNote = entry("2", "美团平台商户", "")
        val notPlatform = entry("3", "蜜雪冰城", "两杯奶茶")
        val same = entry("4", "美团", "美团")

        assertTrue(TitleSwap.matches(hit, all))
        assertFalse(TitleSwap.matches(noNote, all))
        assertFalse(TitleSwap.matches(notPlatform, all))
        assertFalse(TitleSwap.matches(same, all))
    }

    @Test
    fun `关掉规则就不认了`() {
        val e = entry("1", "淘宝闪购", "良品铺子 每日坚果")
        assertTrue(TitleSwap.matches(e, all))
        assertFalse(TitleSwap.matches(e, emptySet()))
        assertFalse(TitleSwap.matches(e, setOf("美团")))
    }

    @Test
    fun `扫描结果按日期倒序，已收起的不算`() {
        val list = listOf(
            entry("a", "美团", "黄焖鸡", date = "2026-11-02"),
            entry("b", "淘宝闪购", "坚果", date = "2026-11-09"),
            entry("c", "美团", "奶茶", date = "2026-11-20", archived = true),
            entry("d", "瑞幸", "拿铁", date = "2026-11-25"),
        )
        val hits = TitleSwap.scan(list, all)

        assertEquals(listOf("b", "a"), hits.map { it.entry.id })
        assertEquals(listOf("淘宝闪购", "美团"), hits.map { it.ruleLabel })
    }

    @Test
    fun `交换后标题是备注，备注是原标题，别的字段不动`() {
        val e = entry("1", "美团", "小象超市 青菜").copy(amount = 5_480, category = "买菜")
        val swapped = TitleSwap.swapped(e)

        assertEquals("小象超市 青菜", swapped.counterparty)
        assertEquals("美团", swapped.note)
        assertEquals(5_480, swapped.amount)
        assertEquals("买菜", swapped.category)
        assertEquals(e.id, swapped.id)
        assertEquals(e.date, swapped.date)
    }

    @Test
    fun `换两次就回到原样`() {
        val e = entry("1", "美团", "小象超市 青菜")
        assertEquals(e, TitleSwap.swapped(TitleSwap.swapped(e)))
    }

    @Test
    fun `备注为空时不换，免得把标题弄丢`() {
        val e = entry("1", "美团", "")
        assertEquals(e, TitleSwap.swapped(e))
    }

    @Test
    fun `批量交换只动选中的那些`() {
        val ledger = cn.neeto.stockpot.core.model.Ledger(
            entries = listOf(
                entry("1", "美团", "黄焖鸡"),
                entry("2", "美团", "麻辣烫"),
            ),
        )
        val after = LedgerOps.swapTitles(ledger, setOf("1"))

        assertEquals("黄焖鸡", after.entries.first { it.id == "1" }.counterparty)
        assertEquals("美团", after.entries.first { it.id == "2" }.counterparty)
    }

    @Test
    fun `交换不动金额与合计`() {
        val ledger = cn.neeto.stockpot.core.model.Ledger(
            entries = listOf(entry("1", "美团", "黄焖鸡")),
        )
        val before = ledger.entries.sumOf { it.effectiveAmount }
        val after = LedgerOps.swapTitles(ledger, setOf("1"))
        assertEquals(before, after.entries.sumOf { it.effectiveAmount })
    }
}
