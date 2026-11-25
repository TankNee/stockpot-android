package cn.neeto.stockpot.core.net

import cn.neeto.stockpot.core.model.Ledger

/**
 * 从「改之前」和「改之后」两份账本里算出哪些行被删了。
 *
 * 单独拎出来是因为它是纯函数：不碰 SharedPreferences、不看时钟以外的任何东西，
 * 单测直接喂两份 Ledger 就能断言。以前嵌在 SyncStateStore 里，跟着 Context 走，
 * 想测就得先起一个 Android 环境。
 *
 * 键的格式是 `集合名:id`，和服务端的 TOMBSTONE_COLLECTIONS 必须一致——
 * 那边按同样的前缀把删掉的行从别人的快照里抹掉。
 */
object SyncTombstones {

    /**
     * @param stamp 这次删除的时间戳（ISO 串）。合并时拿它和记录的修改时间比，
     *              比记录晚才认「这是删掉了」，早的话说明那条是删除之后又加回来的。
     */
    fun removals(before: Ledger, after: Ledger, stamp: String): Map<String, String> {
        val fresh = LinkedHashMap<String, String>()
        collect(before.entries, after.entries, LedgerMerge.ENTRIES) { it.id }.forEach { fresh[it] = stamp }
        collect(before.projects, after.projects, LedgerMerge.PROJECTS) { it.id }.forEach { fresh[it] = stamp }
        collect(before.wallets, after.wallets, LedgerMerge.WALLETS) { it.id }.forEach { fresh[it] = stamp }
        collect(before.stockAccounts, after.stockAccounts, LedgerMerge.STOCK_ACCOUNTS) { it.id }
            .forEach { fresh[it] = stamp }
        collect(before.stocks, after.stocks, LedgerMerge.STOCKS) { it.id }.forEach { fresh[it] = stamp }
        collect(before.imports, after.imports, LedgerMerge.IMPORTS) { it.id }.forEach { fresh[it] = stamp }
        return fresh
    }

    /** 以前在、现在不在的那些 id。空 id 不算——没有身份的行本来就不参与对账。 */
    private fun <T> collect(
        before: List<T>,
        after: List<T>,
        collection: String,
        id: (T) -> String,
    ): List<String> {
        if (before.isEmpty()) return emptyList()
        val alive = after.mapTo(HashSet(after.size)) { id(it) }
        return before.asSequence()
            .map { id(it) }
            .filter { it.isNotEmpty() && it !in alive }
            .map { "$collection:$it" }
            .toList()
    }
}

/**
 * 推失败之后等多久再试。
 *
 * 网络抖一下不该让本地改动一直躺在机器里——以前只有「下一次编辑」才会重推，
 * 于是断网时记的账，可能到第二天才上去。现在按这个节奏退避重试几次。
 */
object SyncBackoff {

    /** 三次：8 秒、30 秒、2 分钟。再长就没意义了，等用户下次编辑或进前台。 */
    private val STEPS = longArrayOf(8_000L, 30_000L, 120_000L)

    val maxAttempts: Int get() = STEPS.size

    /** @param attempt 第几次重试，从 1 开始；超出范围返回 null 表示别再试了。 */
    fun next(attempt: Int): Long? = STEPS.getOrNull(attempt - 1)
}
