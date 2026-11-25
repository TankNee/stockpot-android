package cn.neeto.stockpot.ui

/**
 * 下拉刷新之后那句提示。
 *
 * 单独拎出来是因为它以前撒过谎：同步和另一轮撞在一起时立刻报「云端没对上」，
 * 其实云端那一轮正在好好跑。现在 `synced` 是那一轮*真实*的结果，
 * 失败才带出错因，文案按结果拼，纯函数，单测直接喂参数就能断言。
 */
object RefreshNote {

    /** 错误文案可能很长（HTTP 报文），塞进吐司里会顶穿一行。 */
    private const val MAX_ERROR = 40

    /**
     * @param synced 云端那一轮的结果；`null` 表示没登录、压根没对账
     * @param syncError 失败原因，没有就用「网络不通」兜底
     * @param stocks 本地有几只票，决定要不要报「行情已刷新」
     */
    fun of(synced: Boolean?, syncError: String?, stocks: Int): String {
        val parts = mutableListOf<String>()
        when (synced) {
            true -> parts += "已和云端对齐"
            false -> parts += "云端没对上：${short(syncError)}"
            null -> Unit
        }
        if (stocks > 0) parts += "行情已刷新"
        return if (parts.isEmpty()) "已经是最新的" else parts.joinToString(" · ")
    }

    private fun short(error: String?): String {
        val clean = error?.trim().orEmpty()
        if (clean.isEmpty()) return "网络不通"
        return if (clean.length <= MAX_ERROR) clean else clean.take(MAX_ERROR - 1) + "…"
    }
}
