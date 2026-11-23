package cn.neeto.stockpot.core.importer

import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Source
import java.nio.charset.Charset
import java.time.LocalDate

/** 账单里的一行，已归一到账本字段。金额单位是分。 */
data class ParsedRow(
    val date: String,
    val time: String,
    val kind: String,
    val amount: Long,
    val category: String = "",
    val account: String = "",
    val counterparty: String = "",
    val note: String = "",
    val externalId: String = "",
    /** 这一行本身就是被退款的原始消费（微信会改原行状态）。 */
    val selfRefunded: Boolean = false,
    /** 这一行是退回来的钱，需要去找原始消费。 */
    val refundIncoming: Boolean = false,
    val refundedAmount: Long = 0,
    /** 交易对方 + 商品 + 备注 + 平台分类，给关键词规则用。 */
    val haystack: String = "",
    val hint: String = "",
)

data class ParsedBill(
    val source: String = "",
    val rows: List<ParsedRow> = emptyList(),
    /** 文件里读到但不导入的行（已关闭、金额为 0、格式坏掉）。 */
    val ignored: Int = 0,
    val detected: Boolean = false,
)

/**
 * 账单解析。和桌面端一样，**按表头名字找列，从不按位置**：
 * 用户会改文件名，平台也会改格式。
 *
 * 支持：
 * - 微信支付账单明细（.xlsx）
 * - 支付宝交易明细（.csv，通常 GBK）
 * - iCost 导出（xlsx/csv，日期/类型/金额/一级分类…）
 * - 通用 CSV（日期 + 金额，可选类型/分类/账户/备注），银行流水多能进来
 */
object BillParser {

    private val GB18030: Charset = Charset.forName("GB18030")

    /** 微信优先 UTF-8（常带 BOM），支付宝是 GBK；GB18030 是 GBK 的超集。 */
    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val utf8 = runCatching { Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
        return utf8.getOrElse { String(bytes, GB18030) }
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    fun parseBytes(bytes: ByteArray): ParsedBill {
        if (isZip(bytes)) {
            val rows = XlsxReader.read(bytes) ?: return ParsedBill()
            return parseRows(rows)
        }
        val text = decode(bytes)
        if (text.trimStart().startsWith("{")) return ParsedBill() // 是账本 JSON，由导入向导另走一路
        return parseRows(Csv.rows(text))
    }

    /**
     * 表头 → 列号。找不到的列记 -1，读的时候返回空串，不抛异常。
     */
    private data class Columns(
        val date: Int,
        val paidAt: Int,
        val kind: Int,
        val amount: Int,
        val expense: Int,
        val income: Int,
        val account: Int,
        val counterparty: Int,
        val product: Int,
        val note: Int,
        val status: Int,
        val externalId: Int,
        val hint: Int,
    ) {
        fun get(row: List<String>, index: Int): String =
            if (index < 0) "" else row.getOrElse(index) { "" }

        companion object {
            /**
             * 先按整格相等找，再退回包含匹配。否则微信账单里的「交易类型」
             * 会抢走 `类型` 这一列，把「商户消费」当成收支方向。
             */
            fun find(cols: List<String>, needles: List<String>): Int {
                for (needle in needles) {
                    val exact = cols.indexOfFirst { it.trim() == needle }
                    if (exact >= 0) return exact
                }
                for (needle in needles) {
                    val loose = cols.indexOfFirst { it.contains(needle) }
                    if (loose >= 0) return loose
                }
                return -1
            }

            fun from(header: List<String>): Columns = Columns(
                date = find(header, listOf("交易创建时间", "交易时间", "记账时间", "日期", "时间")),
                paidAt = find(header, listOf("付款时间")),
                kind = find(header, listOf("收/支", "收支类型", "收支", "类型")),
                amount = find(header, listOf("金额(元)", "交易金额", "金额")),
                expense = find(header, listOf("支出金额", "支出")),
                income = find(header, listOf("收入金额", "收入")),
                account = find(header, listOf("收/付款方式", "支付方式", "付款方式", "账户", "银行卡")),
                counterparty = find(header, listOf("交易对方", "对方", "商家", "商户", "收款方", "交易对象")),
                product = find(header, listOf("商品名称", "商品说明", "商品", "摘要")),
                note = find(header, listOf("备注", "说明")),
                status = find(header, listOf("当前状态", "交易状态", "状态")),
                externalId = find(header, listOf("交易单号", "交易订单号", "交易号", "订单号")),
                hint = find(header, listOf("交易分类", "分类", "类别")),
            )

            fun isHeader(cols: List<String>): Boolean {
                val index = from(cols)
                val hasDate = index.date >= 0
                val hasMoney = index.amount >= 0 || index.expense >= 0 || index.income >= 0
                return hasDate && hasMoney
            }
        }
    }

    fun parseRows(rows: List<List<String>>, forced: String? = null): ParsedBill {
        val icostLayout = { row: List<String> ->
            row.any { it == "一级分类" } && row.any { it == "二级分类" }
        }
        if (rows.firstOrNull()?.let(icostLayout) == true) {
            return parseIcost(rows)
        }
        if (forced == Source.ICOST) {
            val at = rows.indexOfFirst { icostLayout(it) }
            if (at >= 0) return parseIcost(rows.subList(at, rows.size))
        }

        val headerIndex = rows.indexOfFirst { Columns.isHeader(it) }
        if (headerIndex < 0) return ParsedBill()

        val header = rows[headerIndex]
        val source = forced?.takeIf { it.isNotBlank() } ?: detectSource(header) ?: Source.GENERIC
        val index = Columns.from(header)
        val generic = source == Source.GENERIC
        val buffer = ArrayList<ParsedRow>()
        var ignored = 0

        for (rowIndex in headerIndex + 1 until rows.size) {
            val row = rows[rowIndex]
            if (row.size < 2 || row.all { it.isBlank() }) continue
            // 后面的「---------」分隔线和汇总说明不是数据
            if (row.count { it.isNotBlank() } < 2) continue

            val counterparty = clean(index.get(row, index.counterparty))
            val product = clean(index.get(row, index.product))
            val note = clean(index.get(row, index.note))
            val hint = clean(index.get(row, index.hint))
            val haystack = listOf(counterparty, product, note, hint).filter { it.isNotEmpty() }.joinToString(" ")

            val kindRaw = index.get(row, index.kind)
            val expenseRaw = index.get(row, index.expense)
            val incomeRaw = index.get(row, index.income)
            val rawAmount = index.get(row, index.amount)

            var kind = when {
                kindRaw.isNotBlank() -> parseKind(kindRaw)
                generic -> null
                // 微信/支付宝的空方向格就是「不计收支」
                else -> Kind.TRANSFER
            }
            if (kind == null && generic) {
                kind = when {
                    expenseRaw.isNotBlank() && incomeRaw.isBlank() -> Kind.EXPENSE
                    incomeRaw.isNotBlank() && expenseRaw.isBlank() -> Kind.INCOME
                    rawAmount.isNotBlank() -> Kind.EXPENSE
                    else -> null
                }
            }
            if (kind == null) {
                ignored++
                continue
            }

            val status = index.get(row, index.status)
            if (statusIsClosed(status)) {
                ignored++
                continue
            }

            val chosenRaw = when {
                kind == Kind.EXPENSE && expenseRaw.isNotBlank() -> expenseRaw
                kind == Kind.INCOME && incomeRaw.isNotBlank() -> incomeRaw
                rawAmount.isNotBlank() -> rawAmount
                else -> ""
            }
            val amount = parseAmountCents(chosenRaw)
            if (amount == null || amount == 0L) {
                ignored++
                continue
            }

            val paid = index.get(row, index.paidAt)
            val rawStamp = if (paid.isBlank()) index.get(row, index.date) else paid
            val stamp = normalizeDate(rawStamp)
            if (stamp == null) {
                ignored++
                continue
            }
            val (date, time) = splitDateTime(stamp)

            // 「/」在两家导出里都表示账户互转；先认出来，再看是不是理财收益
            val effectiveKind = if (kind == Kind.TRANSFER && isFundEarning(haystack)) Kind.INCOME else kind
            val refunded = statusIsRefund(status)

            buffer += ParsedRow(
                date = date,
                time = time,
                kind = effectiveKind,
                amount = amount,
                account = clean(index.get(row, index.account)),
                counterparty = counterparty,
                note = listOf(product, note).filter { it.isNotEmpty() }.joinToString(" "),
                externalId = clean(index.get(row, index.externalId)),
                selfRefunded = refunded && effectiveKind == Kind.EXPENSE,
                refundIncoming = refunded && effectiveKind != Kind.EXPENSE,
                haystack = haystack,
                hint = hint,
            )
        }

        return ParsedBill(source = source, rows = buffer, ignored = ignored, detected = true)
    }

    /** iCost 自带「一级分类/二级分类」，保留用户自己的分类树。 */
    private fun parseIcost(rows: List<List<String>>): ParsedBill {
        if (rows.isEmpty()) return ParsedBill()
        val header = rows.first()
        fun at(name: String) = header.indexOf(name)
        fun cell(row: List<String>, index: Int) = if (index < 0) "" else row.getOrElse(index) { "" }

        val dateAt = at("日期")
        val kindAt = at("类型")
        val amountAt = at("金额")
        val groupAt = at("一级分类")
        val leafAt = at("二级分类")
        val accountAt = at("账户1")
        val noteAt = at("备注")
        val refundAt = at("退款")
        val buffer = ArrayList<ParsedRow>()
        var ignored = 0

        for (rowIndex in 1 until rows.size) {
            val row = rows[rowIndex]
            if (row.all { it.isBlank() }) continue
            val kind = parseKind(cell(row, kindAt))
            if (kind == null) {
                ignored++
                continue
            }
            val stamp = normalizeDate(cell(row, dateAt))
            if (stamp == null) {
                ignored++
                continue
            }
            val amount = parseAmountCents(cell(row, amountAt))
            if (amount == null || amount == 0L) {
                ignored++
                continue
            }
            val group = cell(row, groupAt).trim()
            val leaf = cell(row, leafAt).trim()
            val category = when {
                group.isNotEmpty() && leaf.isNotEmpty() -> "$group/$leaf"
                group.isNotEmpty() -> group
                else -> "其他"
            }
            val note = cell(row, noteAt).trim()
            val refunded = parseAmountCents(cell(row, refundAt)) ?: 0L
            val (date, time) = splitDateTime(stamp)
            buffer += ParsedRow(
                date = date,
                time = time,
                kind = kind,
                amount = amount,
                category = category,
                account = cell(row, accountAt).trim(),
                counterparty = note,
                note = note,
                externalId = "icost:" + row.joinToString("\u001f"),
                selfRefunded = kind == Kind.EXPENSE && refunded > 0,
                refundedAmount = refunded,
                haystack = "$note $group",
            )
        }
        return ParsedBill(source = Source.ICOST, rows = buffer, ignored = ignored, detected = true)
    }

    private fun detectSource(header: List<String>): String? {
        fun has(needle: String) = header.any { it.contains(needle) }
        if (has("商户单号") || has("当前状态") || has("微信")) return Source.WECHAT
        if (has("交易订单号") || has("收/付款方式") || has("交易分类") || has("对方账号") || has("交易号")) {
            return Source.ALIPAY
        }
        if (has("支付方式")) return Source.WECHAT
        return null
    }

    fun parseKind(raw: String): String? = when (raw.trim()) {
        "支出", "支", "消费", "支出金额", "借方" -> Kind.EXPENSE
        "收入", "收", "收入金额", "贷方" -> Kind.INCOME
        "不计收支", "/", "-", "", "转账", "中性" -> Kind.TRANSFER
        else -> null
    }

    /** `¥1,234.50`、`-1234.50`、`1234` → 分。四舍五入，别吃掉一分钱。 */
    fun parseAmountCents(raw: String): Long? {
        val cleaned = raw.filter { it.isDigit() || it == '.' || it == '-' }
        if (cleaned.isEmpty() || cleaned == "-" || cleaned == ".") return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        return Math.round(kotlin.math.abs(value) * 100.0)
    }

    fun statusIsClosed(raw: String): Boolean {
        val text = raw.trim()
        return text.contains("交易关闭") || text.contains("已关闭") ||
            text.contains("失败") || text.contains("已撤销") || text.contains("已取消")
    }

    fun statusIsRefund(raw: String): Boolean {
        val text = raw.trim()
        return text.contains("退款") || text.contains("已退还")
    }

    /**
     * 微信新导出的 `交易时间` 是 Excel 日期序列号（`46348.4575`）。
     * 把数字当文本会让整张账单都解析失败。
     */
    fun normalizeDate(raw: String): String? {
        excelSerial(raw)?.let { return it }
        val cleaned = raw.trim()
            .replace('/', '-')
            .replace("年", "-")
            .replace("月", "-")
            .replace("日", "")
            .replace('T', ' ')
        if (cleaned.isEmpty()) return null
        val (datePart, timePart) = if (cleaned.contains(' ')) {
            cleaned.substringBefore(' ') to cleaned.substringAfter(' ')
        } else {
            cleaned to ""
        }
        val parts = datePart.split('-').filter { it.isNotBlank() }
        if (parts.size != 3) return null
        val year: Int
        val month: Int
        val day: Int
        if (parts[0].length == 4) {
            year = parts[0].toIntOrNull() ?: return null
            month = parts[1].toIntOrNull() ?: return null
            day = parts[2].toIntOrNull() ?: return null
        } else if (parts[2].length == 4) {
            // M/D/YYYY 的老式导出
            month = parts[0].toIntOrNull() ?: return null
            day = parts[1].toIntOrNull() ?: return null
            year = parts[2].toIntOrNull() ?: return null
        } else {
            return null
        }
        if (year < 1990 || year > 2100 || month !in 1..12 || day !in 1..31) return null
        val time = timePart.trim().take(5).takeIf { it.length >= 4 } ?: ""
        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
            if (time.isNotEmpty()) {
                append(' ')
                append(if (time.length == 4) "0$time" else time)
            }
        }
    }

    /** `46348.45753472222` → `2026-11-22 10:58`（Excel 1900 日期系统）。 */
    private fun excelSerial(raw: String): String? {
        val text = raw.trim()
        if (text.isEmpty()) return null
        if (!text.all { it.isDigit() || it == '.' || it == ',' }) return null
        if (text.count { it == '.' } > 1) return null
        val value = text.replace(',', '.').toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 20000.0 || value > 100000.0) return null
        val days = value.toLong()
        val seconds = Math.round((value - days) * 86400.0)
        val minutes = (seconds / 60).coerceIn(0, 1439)
        val date = runCatching { LocalDate.of(1899, 12, 30).plusDays(days) }.getOrNull() ?: return null
        return "%s %02d:%02d".format(date.toString(), minutes / 60, minutes % 60)
    }

    fun splitDateTime(stamp: String): Pair<String, String> {
        val date = stamp.substringBefore(' ')
        val time = if (stamp.contains(' ')) stamp.substringAfter(' ') else ""
        return date to time
    }

    /** `/` 和 `-` 在两家导出里都表示「这里没有内容」。 */
    private fun clean(raw: String): String {
        val trimmed = raw.trim().trim('\t').trim()
        return if (trimmed == "/" || trimmed == "-") "" else trimmed
    }

    /**
     * 余额宝/零钱通/理财通的「收益发放」被平台记成不计收支，
     * 但它是实打实的收入。本金转入转出不算——那只是换个口袋。
     */
    private fun isFundEarning(text: String): Boolean {
        val fund = text.contains("余额宝") || text.contains("零钱通") ||
            text.contains("理财通") || text.contains("基金")
        return fund && (text.contains("收益") || text.contains("分红") || text.contains("发放"))
    }
}
