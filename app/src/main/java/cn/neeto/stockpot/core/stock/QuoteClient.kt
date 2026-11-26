package cn.neeto.stockpot.core.stock

import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.core.model.CurvePoint
import cn.neeto.stockpot.core.model.Market
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Stock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
/**
 * 名称搜索的一条结果。[id] 已经按账本里的写法算好了（sh600519 / hk00700 / usaapl），
 * 选中之后不用再猜市场。
 */
data class StockSearchHit(
    val id: String,
    val code: String,
    val name: String,
    val market: String,
    /** 腾讯给的类型标记：GP-A / GP / ETF，用来在候选行里标一下 */
    val kind: String,
)

object QuoteClient {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

    private val gb18030 = Charset.forName("GB18030")

    suspend fun fetch(stocks: List<Stock>): Map<String, Quote> = withContext(Dispatchers.IO) {
        if (stocks.isEmpty()) return@withContext emptyMap()

        val out = mutableMapOf<String, Quote>()
        // 场外基金不在腾讯的行情接口里，走东财的净值接口
        val funds = stocks.filter { it.market == Market.FUND }
        if (funds.isNotEmpty()) out += fetchFunds(funds)

        stocks.groupBy { it.market }.forEach { (market, list) ->
            if (market == Market.FUND) return@forEach
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
     * 按名称 / 代码 / 拼音搜股票。腾讯的 smartbox 接口，返回
     * `v_hint="sh~600519~\u8d35\u5dde\u8305\u53f0~gzmt~GP-A^..."`，
     * 一条用 `^` 隔、字段用 `~` 隔，名字是 `\uXXXX` 转义。
     *
     * 搜不到就是空列表；网络断了也返回空，让用户继续手填代码，不要卡在那儿转圈。
     */
    suspend fun search(query: String, limit: Int = 8): List<StockSearchHit> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        // 股票和场外基金是两个源，并发问，谁也不拖慢谁
        val (stocks, funds) = coroutineScope {
            val equities = async {
                runCatching {
                    val encoded = java.net.URLEncoder.encode(q, "UTF-8").replace("+", "%20")
                    parseSearch(get("https://smartbox.gtimg.cn/s3/?t=all&q=$encoded"))
                }.getOrDefault(emptyList())
            }
            val openEnded = async {
                runCatching { searchFunds(q) }.getOrDefault(emptyList())
            }
            equities.await() to openEnded.await()
        }
        (stocks + funds).distinctBy { it.id }.take(limit)
    }

    /**
     * 场外基金搜索：东财 `fundsuggest`，返回 `Datas[{CODE, NAME, FundBaseInfo}]`。
     * 腾讯的 smartbox 只有场内品种，基金得单独问。
     */
    suspend fun searchFunds(query: String, limit: Int = 6): List<StockSearchHit> {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        val body = get(
            "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx?m=1&key=$encoded",
            referer = "https://fund.eastmoney.com/",
        )
        return parseFundSearch(body, limit)
    }

    fun parseFundSearch(body: String, limit: Int = 6): List<StockSearchHit> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyList()
        val rows = root["Datas"] as? JsonArray ?: return emptyList()
        return rows.mapNotNull { row ->
            val obj = row as? JsonObject ?: return@mapNotNull null
            val code = (obj["CODE"] as? JsonPrimitive)?.contentOrNull?.trim() ?: return@mapNotNull null
            val name = (obj["NAME"] as? JsonPrimitive)?.contentOrNull?.trim() ?: return@mapNotNull null
            // 场外基金代码一定是 6 位数字，别把基金经理、基金公司也当标的
            if (code.length != 6 || !code.all { it.isDigit() }) return@mapNotNull null
            // 这个接口不只返基金：搜 600519 会把「贵州茅台」也带回来（CATEGORY=100）。
            // 基金那一类的 CATEGORY 是 700，FundBaseInfo.FTYPE 也有值；两个都拿来卡。
            val category = (obj["CATEGORY"] as? JsonPrimitive)?.contentOrNull?.trim()
            val type = ((obj["FundBaseInfo"] as? JsonObject)?.get("FTYPE") as? JsonPrimitive)
                ?.contentOrNull?.trim()
            val looksLikeFund = category == "700" || !type.isNullOrEmpty()
            if (!looksLikeFund) return@mapNotNull null
            StockSearchHit(
                id = "fund$code",
                code = code,
                name = name,
                market = Market.FUND,
                kind = type?.takeIf { it.isNotEmpty() } ?: "场外基金",
            )
        }.distinctBy { it.id }.take(limit)
    }

    /**
     * 基金净值和盘中估值。东财移动端接口一次能带一批代码，
     * `NAV` 是最近一个交易日净值，`GSZ` 只有盘中才有；
     * 收盘后 GSZ 为空，用 `NAVCHGRT`（净值日涨跌%）反推昨净值，
     * 否则今天涨了 1% 也会显示成 0。
     */
    private fun fetchFunds(funds: List<Stock>): Map<String, Quote> {
        val out = mutableMapOf<String, Quote>()
        funds.chunked(40).forEach { chunk ->
            val codes = chunk.map { it.code }.joinToString(",")
            val url = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo" +
                "?Fcodes=$codes&pageIndex=1&pageSize=${chunk.size}" +
                "&plat=Android&appType=ttjj&product=EFund&Version=1&deviceid=1"
            val body = runCatching { get(url, referer = "https://fund.eastmoney.com/") }
                .getOrNull() ?: return@forEach
            val parsed = parseFund(body)
            chunk.forEach { stock ->
                parsed["fund${stock.code}"]?.let { out[stock.id] = it.copy(id = stock.id) }
            }
        }
        return out
    }

    fun parseFund(body: String): Map<String, Quote> {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return emptyMap()
        val rows = root["Datas"] as? JsonArray ?: return emptyMap()
        val out = mutableMapOf<String, Quote>()
        rows.forEach { row ->
            val obj = row as? JsonObject ?: return@forEach
            val code = (obj["FCODE"] as? JsonPrimitive)?.contentOrNull ?: return@forEach
            val nav = (obj["NAV"] as? JsonPrimitive)?.contentOrNull
                ?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@forEach
            val estimate = (obj["GSZ"] as? JsonPrimitive)?.contentOrNull
                ?.toDoubleOrNull()?.takeIf { it > 0 }
            val pct = (obj["NAVCHGRT"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
            val price = estimate ?: nav
            val prev = if (estimate != null) {
                nav
            } else if (pct != null && pct > -100.0) {
                nav * 100.0 / (100.0 + pct)
            } else {
                nav
            }
            val time = (obj["GZTIME"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
                ?: (obj["PDATE"] as? JsonPrimitive)?.contentOrNull.orEmpty()
            out["fund$code"] = Quote(
                id = "fund$code",
                name = (obj["SHORTNAME"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                priceE4 = (price * 10_000).roundToLong(),
                prevCloseE4 = (prev * 10_000).roundToLong(),
                time = time,
                source = "eastmoney-fund",
            )
        }
        return out
    }

    fun parseSearch(body: String): List<StockSearchHit> {
        val open = body.indexOf("=\"")
        if (open < 0) return emptyList()
        val close = body.lastIndexOf('"')
        if (close <= open + 2) return emptyList()
        val payload = body.substring(open + 2, close)
        if (payload.isBlank()) return emptyList()

        return payload.split('^').mapNotNull { row ->
            val fields = row.split('~')
            if (fields.size < 5) return@mapNotNull null
            val prefix = fields[0].trim().lowercase()
            val rawCode = fields[1].trim()
            val name = unescape(fields[2]).trim()
            val kind = fields[4].trim()
            if (rawCode.isEmpty() || name.isEmpty()) return@mapNotNull null

            // 指数、债券、期货不是「持仓」，混进候选只会碍事
            if (!(kind.startsWith("GP") || kind.startsWith("ETF") || kind.startsWith("LOF"))) {
                return@mapNotNull null
            }

            val market = when (prefix) {
                "hk" -> Market.HK
                "us" -> Market.US
                else -> Market.A
            }
            val code = when (market) {
                // 美股代码长这样：aapl.oq / tcehy.ps，后缀是交易所，本地不用
                Market.US -> rawCode.substringBefore('.').uppercase()
                // 港股统一成五位，00700 而不是 700
                Market.HK -> rawCode.padStart(5, '0')
                else -> rawCode
            }
            val id = when (market) {
                Market.HK -> "hk$code"
                Market.US -> "us" + code.lowercase()
                else -> prefix + code
            }
            StockSearchHit(id = id, code = code, name = name, market = market, kind = kind)
        }.distinctBy { it.id }
    }

    /** 把 `\u8d35\u5dde` 这种转义还原成中文；没转义就原样返回。 */
    private fun unescape(raw: String): String {
        if (!raw.contains("\\u")) return raw
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            if (ch == '\\' && i + 5 < raw.length && raw[i + 1] == 'u') {
                val value = raw.substring(i + 2, i + 6).toIntOrNull(16)
                if (value != null) {
                    out.append(value.toChar())
                    i += 6
                    continue
                }
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }

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
        // 场外基金不走腾讯批量行情，净值另外拉
        Market.FUND -> null
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
            val bytes = conn.inputStream.use { it.readBytes() }
            decode(bytes, conn.contentType)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 按接口自己的编码解。以前这里一律 gb18030：腾讯的行情是 GBK，正好；
     * 但东财的搜索和基金接口是 UTF-8，名字就解成乱码了。
     *
     * 顺序：响应头声明的 charset → 严格 UTF-8 → 退回 GB18030。
     * 中间的严格解码是给「声明缺失或声明错了」的老接口兜底：GBK 的字节
     * 拿去严格解 UTF-8 基本都会失败，所以不会把 GBK 误判成 UTF-8。
     */
    internal fun decode(bytes: ByteArray, contentType: String?): String {
        val declared = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
            ?.let { name -> runCatching { Charset.forName(name) }.getOrNull() }
        if (declared != null) {
            return runCatching { bytes.toString(declared) }
                .getOrElse { bytes.toString(gb18030) }
        }
        val strict = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return runCatching { strict.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { bytes.toString(gb18030) }
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
