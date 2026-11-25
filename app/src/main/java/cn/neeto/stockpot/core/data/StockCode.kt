package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Market
import kotlin.math.abs

/**
 * 股票代码与价格的小工具，从界面里挪出来，单测能直接跑。
 *
 * 价格一律用 **万分之一元**（Long）传，字符串自己拆，不让浮点把 0.472 变成 0.4719。
 */
object StockCode {

    fun parsePriceE4(text: String): Long? {
        val raw = text.trim().replace(",", "")
        if (raw.isEmpty() || raw == "-") return null
        val negative = raw.startsWith("-")
        val body = raw.removePrefix("-").removePrefix("+")
        if (body.isEmpty()) return null
        val dot = body.indexOf('.')
        val intPart = (if (dot >= 0) body.substring(0, dot) else body).ifEmpty { "0" }
        val fracPart = if (dot >= 0) body.substring(dot + 1).take(4) else ""
        val int = intPart.toLongOrNull() ?: return null
        val frac = fracPart.padEnd(4, '0').toLongOrNull() ?: 0L
        val value = int * 10_000 + frac
        return if (negative) -value else value
    }

    /** 0.472 就显示 0.472，3.42 就两位，不硬凑。 */
    fun priceText(e4: Long): String {
        val sign = if (e4 < 0) "-" else ""
        val v = abs(e4)
        val whole = v / 10_000
        val frac = v % 10_000
        return when {
            frac == 0L -> "$sign$whole.00"
            frac % 100 == 0L -> "$sign$whole." + (frac / 100).toString().padStart(2, '0')
            frac % 10 == 0L -> "$sign$whole." + (frac / 10).toString().padStart(3, '0')
            else -> "$sign$whole." + frac.toString().padStart(4, '0')
        }
    }

    fun guessMarket(code: String): String {
        val c = code.trim().lowercase()
        return when {
            c.startsWith("fund") -> Market.FUND
            c.startsWith("hk") -> Market.HK
            c.startsWith("us") -> Market.US
            c.startsWith("sh") || c.startsWith("sz") || c.startsWith("bj") -> Market.A
            c.all { it.isDigit() } && c.length == 5 -> Market.HK
            c.all { it.isLetter() } -> Market.US
            else -> Market.A
        }
    }

    fun stockIdFor(code: String, market: String): String {
        val c = code.trim()
        val lower = c.lowercase()
        return when (market) {
            Market.FUND -> "fund" + c.removePrefix("fund")
            Market.HK -> if (lower.startsWith("hk")) lower else "hk" + c.padStart(5, '0')
            Market.US -> if (lower.startsWith("us")) lower else "us" + lower
            else -> when {
                lower.startsWith("sh") || lower.startsWith("sz") || lower.startsWith("bj") -> lower
                lower.startsWith("6") -> "sh$c"
                lower.startsWith("0") || lower.startsWith("3") -> "sz$c"
                else -> "sh$c"
            }
        }
    }

    /**
     * 场外基金的 6 位代码和 A 股同号（000001 既是平安银行也是华夏成长），
     * 光看代码分不出来，只能由搜索结果显式告诉我们是基金。
     */
    fun isFundId(id: String): Boolean = id.trim().lowercase().startsWith("fund")
}
