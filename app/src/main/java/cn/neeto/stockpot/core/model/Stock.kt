package cn.neeto.stockpot.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * 股票持仓，模型对着桌面端 `stockpot.json` 的 stocks / accounts：
 * 一个券商账户里有多只票，同一只票可能出现在多个账户里。
 *
 * 价格用 **万分之一元**（Long）存：ETF 的 0.472、港股的碎股都不会被浮点吃掉。
 */
@Serializable
@Immutable
data class Stock(
    /** sh600396 / sz000001 / hk00700 / usaapl */
    val id: String,
    val code: String,
    val name: String = "",
    /** a | hk | us */
    val market: String = "a",
    val updatedAt: String = "",
)

@Serializable
@Immutable
data class Holding(
    /** 成本价，万分之一元 */
    val costE4: Long = 0,
    val shares: Long = 0,
) {
    val costYuan: Double get() = costE4 / 10000.0
}

@Serializable
@Immutable
data class StockAccount(
    val id: String,
    val name: String,
    /** stockId -> 持仓 */
    val entries: Map<String, Holding> = emptyMap(),
    val note: String = "",
    val updatedAt: String = "",
) {
    val holdingCount: Int get() = entries.count { it.value.shares > 0 }
}

object Market {
    const val A = "a"
    const val HK = "hk"
    const val US = "us"

    fun label(market: String): String = when (market) {
        HK -> "港股"
        US -> "美股"
        else -> "A 股"
    }
}

/** 一次行情快照，不落盘，进程重建后重新拉。 */
@Immutable
data class Quote(
    val id: String,
    val name: String = "",
    /** 万分之一元 */
    val priceE4: Long = 0,
    val prevCloseE4: Long = 0,
    val time: String = "",
    val source: String = "",
) {
    val changeE4: Long get() = priceE4 - prevCloseE4
    val changePercent: Double
        get() = if (prevCloseE4 <= 0) 0.0 else changeE4 * 100.0 / prevCloseE4
}

/**
 * 港币、美元的持仓要折成人民币才能和银行卡余额相加。
 * 默认值是保底用的近似价，能拉到实时汇率就覆盖掉。
 */
@Immutable
data class Rates(
    val usdCny: Double = 7.10,
    val usdHkd: Double = 7.80,
) {
    val hkdCny: Double get() = if (usdHkd > 0) usdCny / usdHkd else 1.0

    fun toCny(market: String): Double = when (market) {
        Market.HK -> hkdCny
        Market.US -> usdCny
        else -> 1.0
    }

    val isLive: Boolean get() = usdCny > 0 && usdCny != 7.10
}
