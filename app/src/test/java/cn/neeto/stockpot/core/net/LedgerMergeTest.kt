package cn.neeto.stockpot.core.net

import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Wallet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerMergeTest {

    private fun entry(id: String, amount: Long, createdAt: String, note: String = "") = Entry(
        id = id,
        date = "2026-11-20",
        amount = amount,
        createdAt = createdAt,
        note = note,
    )

    private fun ledger(vararg entries: Entry) = Ledger(entries = entries.toList())

    @Test
    fun `两边多出来的条目都会留下`() {
        val local = ledger(entry("a", 100, "2026-11-20T10:00:00Z"))
        val remote = ledger(entry("b", 200, "2026-11-20T09:00:00Z"))

        val merged = LedgerMerge.merge(local, remote, preferRemote = false, deleted = emptyMap())

        assertEquals(listOf("a", "b"), merged.entries.map { it.id }.sorted())
    }

    @Test
    fun `同一条冲突时按 preferRemote 决定听谁的`() {
        val local = ledger(entry("a", 100, "2026-11-20T10:00:00Z", note = "本机改的"))
        val remote = ledger(entry("a", 999, "2026-11-20T10:00:00Z", note = "云端改的"))

        val keepLocal = LedgerMerge.merge(local, remote, preferRemote = false, deleted = emptyMap())
        assertEquals("本机改的", keepLocal.entries.single().note)

        val keepRemote = LedgerMerge.merge(local, remote, preferRemote = true, deleted = emptyMap())
        assertEquals("云端改的", keepRemote.entries.single().note)
    }

    @Test
    fun `墓碑比记录晚就把这条清掉`() {
        val local = ledger(entry("a", 100, "2026-11-20T10:00:00Z"))
        val remote = ledger()

        val merged = LedgerMerge.merge(
            local,
            remote,
            preferRemote = false,
            deleted = mapOf("entries:a" to "2026-11-21T00:00:00Z"),
        )

        assertTrue(merged.entries.isEmpty())
    }

    @Test
    fun `墓碑比记录早则不动它`() {
        val local = ledger(entry("a", 100, "2026-11-22T10:00:00Z"))
        val merged = LedgerMerge.merge(
            local,
            ledger(),
            preferRemote = false,
            deleted = mapOf("entries:a" to "2026-11-21T00:00:00Z"),
        )
        assertEquals(1, merged.entries.size)
    }

    @Test
    fun `钱包和项目按 id 取并集，分类按路径去重`() {
        val local = Ledger(
            wallets = listOf(Wallet(id = "w1", name = "招行")),
            projects = listOf(Project(id = "p1", name = "装修")),
            categories = listOf(CategoryDef(name = "餐饮", kind = "expense")),
        )
        val remote = Ledger(
            wallets = listOf(Wallet(id = "w1", name = "招商银行"), Wallet(id = "w2", name = "现金")),
            projects = listOf(Project(id = "p2", name = "旅行")),
            categories = listOf(
                CategoryDef(name = "餐饮", kind = "expense"),
                CategoryDef(name = "旅行", kind = "expense"),
            ),
        )

        val merged = LedgerMerge.merge(local, remote, preferRemote = false, deleted = emptyMap())

        assertEquals(2, merged.wallets.size)
        assertEquals("招行", merged.wallets.first { it.id == "w1" }.name)
        assertEquals(2, merged.projects.size)
        assertEquals(2, merged.categories.count { it.parent.isEmpty() })
    }

    @Test
    fun `alive 只认更晚的墓碑`() {
        val deleted = mapOf("entries:x" to "2026-11-20T00:00:00Z")
        assertFalse(LedgerMerge.alive("x", LedgerMerge.ENTRIES, deleted, "2026-11-19T23:59:59Z"))
        assertFalse(LedgerMerge.alive("x", LedgerMerge.ENTRIES, deleted, ""))
        assertTrue(LedgerMerge.alive("x", LedgerMerge.ENTRIES, deleted, "2026-11-20T00:00:01Z"))
        assertTrue(LedgerMerge.alive("y", LedgerMerge.ENTRIES, deleted, ""))
    }
}
