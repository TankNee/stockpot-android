package cn.neeto.stockpot.core.stock

import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.core.model.CurvePoint
import cn.neeto.stockpot.core.model.Market
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import kotlin.math.roundToLong

/**
 * 行情：腾讯的 `qt.gtimg.cn`，和桌面端用的是同一个源，不需要 key。
 *
 * A 股 `sh600396`、港股要 `r_hk00700`、美股 `usAAPL`，返回是 GBK
 * 的 `v_sh600396="1~华电辽能~600396~3.42~3.40~..."`，字段 1 名称、
 * 3 现价、4 昨收、30 时间。
 */
object QuoteClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val gb18030 = Charset.forName("GB18030")

    suspend fun fetch(stocks: List<Stock>): Map<String, Quote> = withContext(Dispatchers.IO) {
        if (stocks.isEmpty()) return@withContext emptyMap()

        val out = mutableMapOf<String, Quote>()
        stocks.groupBy { it.market }.forEach { (_, list) ->
            val symbols = list.mapNotNull { wireSymbol(it) }
            if (symbols.isEmpty()) return@forEach
            val body = runCatching {
                get("https://qt.gtimg.cn/q=" + symbols.joinToString(","))
            }.getOrNull() ?: return@forEach
            val quotes = parse(body)
            // 行情源给的 wire id 未必和本地 id 完全一致，按代码再兜一次
            list.forEach { stock ->
                val hit = quotes[stock.id]
                    ?: quotes.values.firstOrNull { it.id.endsWith(stock.code, ignoreCase = true) }
                if (hit != null) out[stock.id] = hit.copy(id = stock.id)
            }
        }
        out
    }

    /** 单只想查名字：拿不到就是拿不到，不重试。 */
    suspend fun probe(stock: Stock): Quote? = fetch(listOf(stock))[stock.id]

    /**
     * 汇率：新浪的 `fx_susdcny` / `fx_susdhkd`，payload 里第一个正浮点就是汇率。
     * 拉不到就返回 null，调用方用 Rates 里的保底值。
     */
    suspend fun fetchRates(): Rates? = withContext(Dispatchers.IO) {
        runCatching {
            val body = get(
                "https://hq.sinajs.cn/list=fx_susdcny,fx_susdhkd",
                referer = "https://finance.sina.com.cn/",
            )
            parseRates(body)
        }.getOrNull()
    }

    fun parseRates(body: String): Rates? {
        var usdCny = 0.0
        var usdHkd = 0.0
        body.split(';').forEach { raw ->
            val line = raw.trim()
            val open = line.indexOf("=\"")
            if (open <= 0) return@forEach
            val close = line.lastIndexOf('"')
            if (close <= open + 2) return@forEach
            val value = line.substring(open + 2, close)
                .split(',')
                .mapNotNull { it.trim().toDoubleOrNull() }
                .firstOrNull { it > 0 } ?: return@forEach
            when {
                line.contains("fx_susdcny") -> usdCny = value
                line.contains("fx_susdhkd") -> usdHkd = value
            }
        }
        if (usdCny <= 0.0) return null
        return Rates(usdCny = usdCny, usdHkd = if (usdHkd > 0) usdHkd else 7.8)
    }

    private fun wireSymbol(stock: Stock): String? = when (stock.market) {
        Market.HK -> "r_" + stock.id
        Market.US -> "us" + stock.id.removePrefix("us").uppercase()
        else -> stock.id.ifEmpty { null }
    }

    /* ------------------------------ 分时 / 日 K ------------------------------ */

    /**
     * 当日分时。腾讯 `appstock/app/minute/query`，返回 JSON，
     * `data.<code>.data.data` 里是 `"0930 3.42 1234 ..."` 这样的行。
     */
    suspend fun fetchMinute(stock: Stock): List<CurvePoint> = withContext(Dispatchers.IO) {
        val code = wireCode(stock)
        runCatching {
            parseMinute(get("https://web.ifzq.gtimg.cn/appstock/app/minute/query?code=$code"), code)
        }.getOrDefault(emptyList())
    }

    /**
     * 日 K（前复权）。`fqkline/get` 的 `param` 是 `code,day,起,止,条数,qfq`，
     * 起止留空就取最近 N 根。默认要 260 根（一年交易日），
     * 资产走势的「1 年」那一档才有东西可画。
     */
    suspend fun fetchDaily(stock: Stock, days: Int = 260): List<Candle> = withContext(Dispatchers.IO) {
        val code = wireCode(stock)
        runCatching {
            val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$code,day,,,$days,qfq"
            parseDaily(get(url), code)
        }.getOrDefault(emptyList())
    }

    /**
     * 分时和 K 线用的代码：港股不带 r_ 前缀，美股是 us + 大写代码。
     * 从 id 推，别从用户输入的 code 推 —— 有人会直接敲 hk00700。
     */
    fun wireCode(stock: Stock): String {
        val id = stock.id.lowercase()
        return when {
            id.startsWith("hk") -> "hk" + id.removePrefix("hk").padStart(5, '0')
            id.startsWith("us") -> "us" + id.removePrefix("us").uppercase()
            else -> id
        }
    }

    fun parseMinute(body: String, code: String): List<CurvePoint> {
        val rows = node(body, code)?.get("data")?.let { it as? JsonObject }
            ?.get("data") as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val raw = (row as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val parts = raw.trim().split(' ')
            if (parts.size < 2) return@mapNotNull null
            val price = parts[1].toDoubleOrNull() ?: return@mapNotNull null
            if (price <= 0.0) return@mapNotNull null
            CurvePoint(label = parts[0], value = (price * 10_000).roundToLong())
        }
    }

    fun parseDaily(body: String, code: String): List<Candle> {
        val node = node(body, code) ?: return emptyList()
        val rows = (node["qfqday"] as? JsonArray) ?: (node["day"] as? JsonArray) ?: return emptyList()
        return rows.mapNotNull { row ->
            val arr = row as? JsonArray ?: return@mapNotNull null
            if (arr.size < 5) return@mapNotNull null
            val day = (arr[0] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            val open = (arr[1] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val close = (arr[2] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val high = (arr[3] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            val low = (arr[4] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: return@mapNotNull null
            if (close <= 0.0) return@mapNotNull null
            Candle(
                day = day,
                openE4 = (open * 10_000).roundToLong(),
                closeE4 = (close * 10_000).roundToLong(),
                highE4 = (high * 10_000).roundToLong(),
                lowE4 = (low * 10_000).roundToLong(),
            )
        }
    }

    /** 腾讯这两个接口都是 `{"code":0,"data":{"<code>":{...}}}`，把那一层抓出来。 */
    private fun node(body: String, code: String): JsonObject? = runCatching {
        val data = Json.parseToJsonElement(body).jsonObject["data"]?.jsonObject ?: return null
        val exact = data[code] ?: data.entries.firstOrNull { it.key.equals(code, ignoreCase = true) }?.value
        (exact as? JsonObject) ?: data.values.firstOrNull { it is JsonObject } as? JsonObject
    }.getOrNull()

    private fun get(url: String, referer: String = "https://gu.qq.com/"): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", referer)
        }
        return try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { it.readBytes() }.toString(gb18030)
        } finally {
            conn.disconnect()
        }
    }

    fun parse(body: String): Map<String, Quote> {
        val out = mutableMapOf<String, Quote>()
        body.split(';').forEach { raw ->
            val line = raw.trim()
            val open = line.indexOf("=\"")
            if (open <= 0) return@forEach
            val close = line.lastIndexOf('"')
            if (close <= open + 2) return@forEach

            val wire = line.substring(0, open).removePrefix("v_")
            val fields = line.substring(open + 2, close).split('~')
            if (fields.size < 5) return@forEach

            val price = fields[3].toDoubleOrNull() ?: return@forEach
            val prev = fields[4].toDoubleOrNull() ?: return@forEach
            if (price <= 0.0 || prev <= 0.0) return@forEach

            val id = normalizeId(wire)
            out[id] = Quote(
                id = id,
                name = fields[1],
                priceE4 = (price * 10_000).roundToLong(),
                prevCloseE4 = (prev * 10_000).roundToLong(),
                time = fields.getOrNull(30).orEmpty(),
                source = "tencent",
            )
        }
        return out
    }

    private fun normalizeId(wire: String): String = when {
        wire.startsWith("r_hk") -> wire.removePrefix("r_")
        wire.startsWith("us") -> wire.lowercase()
        else -> wire
    }
}
