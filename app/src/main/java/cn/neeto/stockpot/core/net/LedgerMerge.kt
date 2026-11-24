package cn.neeto.stockpot.core.net

import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.model.normalized

/**
 * 把本机和云端两份账本合成一份。
 *
 * 账本没有逐条的修改时间（桌面端也没有），所以冲突只能按"谁先动"来定：
 * - [preferRemote] = 本机自上次同步后没动过 → 同一条以云端为准
 * - 否则以本机为准（本地刚改的东西不该被一份旧快照盖掉）
 *
 * 两边的条目取并集，只有**删除**需要靠墓碑（`"entries:id" -> 时间`）来传播，
 * 否则在 A 上删掉的账，会在 B 下次同步时又冒回来。
 */
object LedgerMerge {

    const val ENTRIES = "entries"
    const val PROJECTS = "projects"
    const val WALLETS = "wallets"
    const val STOCK_ACCOUNTS = "stockAccounts"
    const val STOCKS = "stocks"
    const val IMPORTS = "imports"

    /** 参与墓碑对账的集合名，和服务端的 TOMBSTONE_COLLECTIONS 一一对应。 */
    val COLLECTIONS = listOf(ENTRIES, PROJECTS, WALLETS, STOCK_ACCOUNTS, STOCKS, IMPORTS)

    fun merge(
        local: Ledger,
        remote: Ledger,
        preferRemote: Boolean,
        deleted: Map<String, String>,
    ): Ledger {
        val base = if (preferRemote) remote else local
        val other = if (preferRemote) local else remote

        val entries = mergeEntries(base, other, deleted)
        val projects = mergeById(base.projects, other.projects, deleted, PROJECTS, id = { it.id })
        val wallets = mergeById(
            base.wallets, other.wallets, deleted, WALLETS,
            id = { it.id }, stamp = { it.updatedAt },
        )
        val imports = mergeById(
            base.imports, other.imports, deleted, IMPORTS,
            id = { it.id }, stamp = { it.importedAt },
        )
        val stocks = mergeStocks(base.stocks, other.stocks, deleted)
        val stockAccounts = mergeStockAccounts(base.stockAccounts, other.stockAccounts, deleted)

        // 分类和规则不参与墓碑：改名、删分类顶多让设置页多一行，
        // 但按 id/名字取并集能让"在另一台机器上加的分类"也能用上。
        val categories = (base.categories + other.categories)
            .distinctBy { it.parent + "\u0000" + it.name }
        val rules = (base.rules + other.rules)
            .distinctBy { it.pattern + "\u0000" + it.category }
        val accounts = (base.accounts + other.accounts).distinct()

        return base.copy(
            entries = entries,
            categories = categories,
            accounts = accounts,
            imports = imports,
            wallets = wallets,
            projects = projects,
            rules = rules,
            ignoredDuplicates = (base.ignoredDuplicates + other.ignoredDuplicates).distinct(),
            ignoredRepayments = (base.ignoredRepayments + other.ignoredRepayments).distinct(),
            stocks = stocks,
            stockAccounts = stockAccounts,
        ).normalized()
    }

    private fun mergeEntries(
        base: Ledger,
        other: Ledger,
        deleted: Map<String, String>,
    ): List<Entry> {
        val merged = LinkedHashMap<String, Entry>(base.entries.size + other.entries.size)
        base.entries.forEach { merged[it.id] = it }
        // 同一条在 base 里已经有了就以 base 为准；只补别的
        other.entries.forEach { if (it.id !in merged) merged[it.id] = it }
        return merged.values
            .filter { alive(it.id, ENTRIES, deleted, it.createdAt) }
            .sortedWith(entryOrder)
    }

    private fun mergeStocks(
        base: List<Stock>,
        other: List<Stock>,
        deleted: Map<String, String>,
    ): List<Stock> {
        val merged = LinkedHashMap<String, Stock>(base.size + other.size)
        base.forEach { merged[it.id] = it }
        other.forEach { incoming ->
            val current = merged[incoming.id]
            merged[incoming.id] = when {
                current == null -> incoming
                // 名字是行情补进来的：谁有名字用谁的，别让一次同步把它抹成空
                current.name.isEmpty() && incoming.name.isNotEmpty() -> current.copy(name = incoming.name)
                else -> current
            }
        }
        return merged.values
            .filter { alive(it.id, STOCKS, deleted, it.updatedAt) }
            .sortedBy { it.code }
    }

    private fun mergeStockAccounts(
        base: List<StockAccount>,
        other: List<StockAccount>,
        deleted: Map<String, String>,
    ): List<StockAccount> {
        val merged = LinkedHashMap<String, StockAccount>(base.size + other.size)
        base.forEach { merged[it.id] = it }
        other.forEach { incoming ->
            val current = merged[incoming.id]
            merged[incoming.id] = if (current == null) {
                incoming
            } else {
                // base 的持仓优先，另一边多出来的补上
                current.copy(entries = incoming.entries + current.entries)
            }
        }
        return merged.values
            .filter { alive(it.id, STOCK_ACCOUNTS, deleted, it.updatedAt) }
    }

    private fun <T> mergeById(
        base: List<T>,
        other: List<T>,
        deleted: Map<String, String>,
        collection: String,
        id: (T) -> String,
        stamp: (T) -> String = { "" },
    ): List<T> {
        val merged = LinkedHashMap<String, T>(base.size + other.size)
        base.forEach { merged[id(it)] = it }
        other.forEach { if (id(it) !in merged) merged[id(it)] = it }
        return merged.values.filter { alive(id(it), collection, deleted, stamp(it)) }
    }

    /**
     * 墓碑比记录更晚才算死。id 是随机串，正常不会重复使用；
     * 这一条是为了「删掉之后又重新加回来」不被误伤。
     */
    fun alive(
        id: String,
        collection: String,
        deleted: Map<String, String>,
        stamp: String,
    ): Boolean {
        val deadAt = deleted["$collection:$id"] ?: return true
        return stamp.isNotEmpty() && stamp > deadAt
    }

    private val entryOrder: Comparator<Entry> =
        compareByDescending<Entry> { it.date }
            .thenByDescending { it.time }
            .thenByDescending { it.createdAt }
}
