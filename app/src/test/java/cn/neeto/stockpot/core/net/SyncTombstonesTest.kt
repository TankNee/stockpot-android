package cn.neeto.stockpot.core.net

import cn.neeto.stockpot.core.model.emptyLedger
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.core.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 墓碑计算：删了什么、没删什么、键长什么样。
 *
 * 这块以前藏在 SyncStateStore 里跟着 Context 走，一条测试都没有；而它决定的
 * 是「在 A 机删掉的账，会不会被 B 机的旧快照合回来」——这种事错了很难发现。
 */
class SyncTombstonesTest {

    private val stamp = "2026-11-25T10:00:00Z"

    private fun entry(id: String) = Entry(
        id = id,
        date = "2026-11-01",
        kind = Kind.EXPENSE,
        amount = 1_000,
    )

    @Test
    fun `删掉一条账目就记一条墓碑`() {
        val before = emptyLedger().copy(entries = listOf(entry("a"), entry("b")))
        val after = emptyLedger().copy(entries = listOf(entry("a")))
        val marks = SyncTombstones.removals(before, after, stamp)
        assertEquals(setOf("entries:b"), marks.keys)
        assertEquals(stamp, marks["entries:b"])
    }

    @Test
    fun `一条都没删就什么都不记`() {
        val before = emptyLedger().copy(entries = listOf(entry("a")))
        val after = before.copy(entries = before.entries + entry("c"))
        assertTrue(SyncTombstones.removals(before, after, stamp).isEmpty())
    }

    @Test
    fun `改内容不算删`() {
        val before = emptyLedger().copy(entries = listOf(entry("a")))
        val after = emptyLedger().copy(entries = listOf(entry("a").copy(amount = 9_900)))
        assertTrue(SyncTombstones.removals(before, after, stamp).isEmpty())
    }

    @Test
    fun `项目和钱包按各自集合名记账`() {
        val before = emptyLedger().copy(
            projects = listOf(Project(id = "p1", name = "装修")),
            wallets = listOf(Wallet(id = "w1", name = "现金")),
        )
        val marks = SyncTombstones.removals(before, emptyLedger(), stamp)
        assertEquals(setOf("projects:p1", "wallets:w1"), marks.keys)
    }

    @Test
    fun `自选股和券商账户删掉也会记`() {
        val before = emptyLedger().copy(
            stocks = listOf(Stock(id = "sh600000", code = "600000")),
            stockAccounts = listOf(StockAccount(id = "acc1", name = "主账户")),
        )
        val marks = SyncTombstones.removals(before, emptyLedger(), stamp)
        assertEquals(setOf("stocks:sh600000", "stockAccounts:acc1"), marks.keys)
    }

    @Test
    fun `没有 id 的行不会产生空键`() {
        val before = emptyLedger().copy(entries = listOf(entry("")))
        val marks = SyncTombstones.removals(before, emptyLedger(), stamp)
        assertTrue(marks.isEmpty())
    }

    @Test
    fun `清空整本账会把所有行都记下来`() {
        val before = emptyLedger().copy(
            entries = listOf(entry("a"), entry("b"), entry("c")),
            projects = listOf(Project(id = "p1", name = "旅行")),
        )
        val marks = SyncTombstones.removals(before, emptyLedger(), stamp)
        assertEquals(4, marks.size)
        assertTrue(marks.containsKey("entries:a"))
        assertTrue(marks.containsKey("entries:b"))
        assertTrue(marks.containsKey("entries:c"))
        assertTrue(marks.containsKey("projects:p1"))
    }

    @Test
    fun `同一行在两次删除里都出现也只有一条`() {
        val before = emptyLedger().copy(entries = listOf(entry("a")))
        val marks = SyncTombstones.removals(before, emptyLedger(), stamp)
        assertEquals(1, SyncTombstones.removals(before, emptyLedger(), stamp).size)
        assertEquals(marks.keys, SyncTombstones.removals(before, emptyLedger(), stamp).keys)
    }
}

/** 退避节奏：8s → 30s → 2min，之后不再自动试。 */
class SyncBackoffTest {

    @Test
    fun `前三次各有各的等待时间`() {
        assertEquals(8_000L, SyncBackoff.next(1))
        assertEquals(30_000L, SyncBackoff.next(2))
        assertEquals(120_000L, SyncBackoff.next(3))
    }

    @Test
    fun `超过次数就返回空，交给下次编辑`() {
        assertNull(SyncBackoff.next(4))
        assertNull(SyncBackoff.next(0))
        assertEquals(3, SyncBackoff.maxAttempts)
    }
}
