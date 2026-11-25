package cn.neeto.stockpot.core.net

import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.ImportRecord
import cn.neeto.stockpot.core.model.Project
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
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

    @Test
    fun `自选股按 id 取并集，删掉的那只不会复活`() {
        val local = Ledger(
            stocks = listOf(
                Stock(id = "sh600000", code = "600000"),
                Stock(id = "hk00700", code = "00700"),
            ),
        )
        val remote = Ledger(
            stocks = listOf(
                Stock(id = "sh600000", code = "600000"),
                Stock(id = "usaapl", code = "AAPL"),
            ),
        )
        val merged = LedgerMerge.merge(local, remote, preferRemote = false, deleted = emptyMap())
        val ids = merged.stocks.map { it.id }.toSet()
        assertTrue("本地独有的还在", ids.contains("hk00700"))
        assertTrue("云端独有的也进来", ids.contains("usaapl"))

        val buried = LedgerMerge.merge(
            local,
            remote,
            preferRemote = false,
            deleted = mapOf("stocks:hk00700" to "2026-11-25T10:00:00Z"),
        )
        assertFalse("墓碑比记录晚就不该留着", buried.stocks.any { it.id == "hk00700" })
    }

    @Test
    fun `券商账户合并时持仓跟着账户走`() {
        val local = Ledger(
            stockAccounts = listOf(
                StockAccount(
                    id = "acc1",
                    name = "主账户",
                    entries = mapOf("sh600000" to Holding(costE4 = 95_500, shares = 100)),
                ),
            ),
        )
        val remote = Ledger(
            stockAccounts = listOf(
                StockAccount(
                    id = "acc2",
                    name = "另一个",
                    entries = mapOf("hk00700" to Holding(costE4 = 30_000, shares = 200)),
                ),
            ),
        )
        val merged = LedgerMerge.merge(local, remote, preferRemote = false, deleted = emptyMap())
        assertEquals(2, merged.stockAccounts.size)
        assertEquals(95_500L, merged.stockAccounts.first { it.id == "acc1" }.entries["sh600000"]?.costE4)
    }

    @Test
    fun `preferRemote 反过来时以云端那份为准`() {
        val local = Ledger(entries = listOf(entry("a", 1_000, "2026-11-20T00:00:00Z")))
        val remote = Ledger(entries = listOf(entry("a", 2_000, "2026-11-20T00:00:00Z")))
        val mine = LedgerMerge.merge(local, remote, preferRemote = false, deleted = emptyMap())
        val theirs = LedgerMerge.merge(local, remote, preferRemote = true, deleted = emptyMap())
        assertEquals(1_000, mine.entries.first().amount)
        assertEquals(2_000, theirs.entries.first().amount)
    }

    @Test
    fun `导入记录按 id 合并，同一个文件不会列两遍`() {
        val record = ImportRecord(
            id = "imp1",
            file = "alipay.csv",
            source = "alipay",
            added = 10,
            skipped = 0,
            importedAt = "2026-11-01T00:00:00Z",
        )
        val merged = LedgerMerge.merge(
            Ledger(imports = listOf(record)),
            Ledger(imports = listOf(record)),
            preferRemote = false,
            deleted = emptyMap(),
        )
        assertEquals(1, merged.imports.size)
    }

    @Test
    fun `规则按关键词加分类去重，两边都加了也只留一条`() {
        val rule = Rule(id = "r1", pattern = "瑞幸", category = "餐饮/咖啡")
        val merged = LedgerMerge.merge(
            Ledger(rules = listOf(rule)),
            Ledger(rules = listOf(rule)),
            preferRemote = false,
            deleted = emptyMap(),
        )
        assertEquals(1, merged.rules.size)
    }
}
