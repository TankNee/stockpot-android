package cn.neeto.stockpot.core.data

/**
 * 总览、流水、资产这几个「空着就要说话」的页面，现在到底该说什么。
 *
 * 以前只有两种：有数据 / 没数据。于是冷启动第一帧（账本还在读库）、以及
 * 登录过的设备刚打开（本机空、云端有、正在拉）这两个窗口里，界面都会先摆出
 * 「账本还是空的，记一笔试试」——用户看到的第一眼是账丢了。
 *
 * 所以中间补一档 [LOADING]：这时候什么都不主张，显示骨架。
 */
enum class LedgerPhase {
    /** 还不知道有没有：正在读库，或者正在等第一轮云端对账。别下结论。 */
    LOADING,

    /** 确实一笔都没有。这时候才该请人记一笔。 */
    EMPTY,

    /** 有数据，正常画。 */
    READY,
}

/**
 * @param ready 本机账本读完了没有（[cn.neeto.stockpot.core.data.LedgerStore.ready]）
 * @param entries 当前账目条数
 * @param signedIn 登录了没有
 * @param syncing 正在跟云端对账
 */
fun ledgerPhase(ready: Boolean, entries: Int, signedIn: Boolean, syncing: Boolean): LedgerPhase = when {
    // 库还没读出来。这个空是「不知道」，不是「没有」。
    !ready -> LedgerPhase.LOADING
    entries > 0 -> LedgerPhase.READY
    // 本机确实一笔没有，但刚启动正在拉云端那份：等它落地再说话，
    // 不然登录用户每次冷启动都要看一眼「账本还是空的」
    signedIn && syncing -> LedgerPhase.LOADING
    else -> LedgerPhase.EMPTY
}
