package cn.neeto.stockpot

/**
 * 桌面长按图标弹出来的那几个入口。
 *
 * 和 `res/xml/shortcuts.xml` 里的 action 一一对应：那边是外面世界能发进来的字符串，
 * 这边是 App 自己认的名字，两边改一处就得改另一处。
 */
object AppShortcuts {
    const val NEW_ENTRY = "cn.neeto.stockpot.action.NEW_ENTRY"
    const val ENTRIES = "cn.neeto.stockpot.action.ENTRIES"
    const val REFUNDS = "cn.neeto.stockpot.action.REFUNDS"
    const val IMPORT = "cn.neeto.stockpot.action.IMPORT"

    /** 小组件点一下就是「打开 App」，不用落到某个具体页面。 */
    const val OPEN = "cn.neeto.stockpot.action.OPEN"

    /** 只认自己那四个；别的手势（分享、打开文件）不算。 */
    fun of(action: String?): String? = when (action) {
        NEW_ENTRY, ENTRIES, REFUNDS, IMPORT -> action
        else -> null
    }
}

/**
 * 一次「从外面点进来」的请求。
 *
 * 带个自增的 [id] 是因为 action 字符串可能是同一个：人已经在 App 里，
 * 再长按点一次「记一笔」，字符串没变，Compose 那边就以为没事发生。
 */
data class LaunchAction(val name: String, val id: Long)
