package cn.neeto.stockpot.core.data

import androidx.compose.runtime.Immutable
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 金额与日期的显示格式。所有金额入参都是「分」。
 *
 * 这些函数在滚动时会被每帧调用几十次，所以全部走字符串拼接：
 * 以前每次 new 一个 DecimalFormat + BigDecimal，一屏列表就是几毫秒的卡顿。
 */
object Fmt {

    private fun money(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        val yuan = abs / 100
        val fen = (abs % 100).toInt()
        val digits = yuan.toString()
        val builder = StringBuilder(digits.length + 4 + if (fen < 10) 1 else 0)
        val len = digits.length
        var i = 0
        while (i < len) {
            if (i > 0 && (len - i) % 3 == 0) builder.append(',')
            builder.append(digits[i])
            i++
        }
        builder.append('.')
        if (fen < 10) builder.append('0')
        builder.append(fen)
        return builder.toString()
    }

    /** `1234.5` 元 → `1,234.50` */
    fun cents(cents: Long): String = money(cents)

    /** `¥1,234.50`；`signed` 为真时负数带 −、正数带 +。 */
    fun yuan(cents: Long, signed: Boolean = false): String {
        val sign = when {
            cents < 0 -> "-"
            signed && cents > 0 -> "+"
            else -> ""
        }
        val body = money(if (cents < 0) -cents else cents)
        return StringBuilder(sign.length + body.length + 1).append(sign).append('¥').append(body).toString()
    }

    /** 紧凑写法：850、1.1k、23k、1.2m，给日历格子和图表用。 */
    fun compact(cents: Long): String {
        val abs = if (cents < 0) -cents else cents
        return when {
            // 1000 元以下：整数元，小数额保留一位小数
            abs < 100_000 -> {
                if (abs % 100 == 0L) (abs / 100).toString() else oneDecimal(abs, 10L, "")
            }
            // 100 万元以上
            abs >= 100_000_000L -> oneDecimal(abs, 10_000_000L, "m")
            // 10 万元以上：整数 k
            abs >= 10_000_000L -> "${abs / 100_000}k"
            else -> oneDecimal(abs, 10_000L, "k")
        }
    }

    /**
     * 一位小数。`unitCents` 是「0.1 个单位」值多少分：
     * 0.1 元 = 10 分，0.1k = 10000 分，0.1m = 10000000 分。
     */
    private fun oneDecimal(cents: Long, unitCents: Long, suffix: String): String {
        val tenths = (cents + unitCents / 2) / unitCents
        val whole = tenths / 10
        val rest = tenths % 10
        return if (rest == 0L) "$whole$suffix" else "$whole.${rest}$suffix"
    }

    /** 输入框里的「12.5」→ 1250 分。容忍 ¥、逗号和负号。 */
    fun parseYuan(text: String): Long? {
        val negative = text.trim().startsWith("-")
        val digits = text.filter { it.isDigit() || it == '.' }
        if (digits.isEmpty() || digits == ".") return null
        val parts = digits.split('.')
        val normalized = if (parts.size == 1) {
            parts[0]
        } else {
            parts[0] + "." + parts.drop(1).joinToString("")
        }
        val value = normalized.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        val cents = kotlin.math.round(value * 100.0).toLong()
        return if (negative) -cents else cents
    }

    /** 输入框回显：123450 → `1234.5`（去掉无意义的零）。 */
    fun editYuan(cents: Long): String {
        if (cents == 0L) return ""
        val value = BigDecimal.valueOf(cents).movePointLeft(2).stripTrailingZeros()
        return value.toPlainString()
    }

    fun monthOf(date: String): String = date.take(7)

    fun currentMonth(): String = YearMonth.now().toString()

    fun shiftMonth(month: String, delta: Long): String =
        runCatching { YearMonth.parse(month).plusMonths(delta).toString() }.getOrDefault(currentMonth())

    fun monthLabel(month: String): String =
        runCatching { YearMonth.parse(month) }.map { "${it.year}年${it.monthValue}月" }.getOrDefault(month)

    fun yearOf(month: String): String = month.take(4)

    fun daysIn(month: String): Int =
        runCatching { YearMonth.parse(month).lengthOfMonth() }.getOrDefault(30)

    fun today(): String = LocalDate.now().toString()

    fun dayOfMonth(date: String): Int = date.takeLast(2).toIntOrNull() ?: 0

    private val weekdayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    /** 列表一屏几十行，日期文案缓存一下，别每次都 parse。 */
    private val dayLabelCache = ConcurrentHashMap<String, String>(128)
    private val weekdayCache = ConcurrentHashMap<String, String>(128)

    fun weekday(date: String): String {
        weekdayCache[date]?.let { return it }
        val label = runCatching {
            weekdayNames[LocalDate.parse(date).dayOfWeek.value - 1]
        }.getOrDefault("")
        if (weekdayCache.size < 512) weekdayCache[date] = label
        return label
    }

    /** `11月22日 周六` */
    fun dayLabel(date: String): String {
        dayLabelCache[date]?.let { return it }
        val label = runCatching {
            val parsed = LocalDate.parse(date)
            "${parsed.monthValue}月${parsed.dayOfMonth}日 ${weekdayNames[parsed.dayOfWeek.value - 1]}"
        }.getOrDefault(date)
        if (dayLabelCache.size < 512) dayLabelCache[date] = label
        return label
    }

    /** 当月第几天是周几（周一为 0），用于日历首行留白。 */
    fun firstWeekdayOffset(month: String): Int = runCatching {
        val first = YearMonth.parse(month).atDay(1)
        first.dayOfWeek.value - 1
    }.getOrDefault(0)

    fun stamp(): String = Instant.now().toString()

    /**
     * Material3 的 DatePicker 用的是「UTC 零点」的毫秒数。
     * 用本地时区换算会让东八区的日期整整差一天。
     */
    fun epochMillis(date: String): Long = runCatching {
        LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    fun fromEpochMillis(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()

    /** `2026-11-22` → `11-22`，纯字符串切片，不用 formatter。 */
    fun shortDate(date: String): String =
        if (date.length >= 10) "${date.substring(5, 7)}-${date.substring(8, 10)}" else date

    /**
     * 搜索结果里的时间：`11-22 14:23`。
     *
     * 跨月搜索没有日期分块顶着，每一行得自己说清是哪天；时间可空（手记的账多半没填）。
     * 不是今年的再补上年份 `25-11-22`，否则「11-22」到底是哪年说不清。
     */
    fun dateTimeLabel(date: String, time: String): String {
        val year = today().take(4)
        val day = when {
            date.length < 10 -> date
            date.startsWith(year) -> shortDate(date)
            else -> date.substring(2, 4) + "-" + date.substring(5, 7) + "-" + date.substring(8, 10)
        }
        return if (time.isNotBlank()) "$day $time" else day
    }

    private val stampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA)

    /** 导入历史里显示的时间戳。 */
    fun stampLabel(raw: String): String = runCatching {
        val instant = Instant.parse(raw)
        instant.atZone(ZoneId.systemDefault()).format(stampFormatter)
    }.getOrDefault(raw.take(16))
}

@Immutable
data class CategoryTotal(val category: String, val total: Long, val share: Float)

@Immutable
data class MonthSummary(
    val month: String,
    val expense: Long,
    val income: Long,
    val net: Long,
    val byCategory: List<CategoryTotal>,
    val count: Int,
)

@Immutable
data class DayTotal(val day: Int, val date: String, val expense: Long, val income: Long)

@Immutable
data class YearMonthTotal(val month: Int, val expense: Long, val income: Long)

object LedgerMath {

    /** 该月真正计入统计的行。 */
    fun monthEntries(entries: List<Entry>, month: String): List<Entry> =
        entries.filter { it.countsTowardTotals && Fmt.monthOf(it.date) == month }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })

    fun summarize(entries: List<Entry>, month: String): MonthSummary {
        var expense = 0L
        var income = 0L
        val buckets = LinkedHashMap<String, Long>()
        val rows = monthEntries(entries, month)
        for (row in rows) {
            when (row.kind) {
                Kind.EXPENSE -> {
                    val amount = row.effectiveAmount
                    expense += amount
                    buckets[row.category.ifEmpty { "其他" }] = (buckets[row.category.ifEmpty { "其他" }] ?: 0L) + amount
                }
                Kind.INCOME -> income += row.amount
            }
        }
        val byCategory = buckets.entries
            .sortedByDescending { it.value }
            .map { (category, total) ->
                CategoryTotal(category, total, if (expense > 0) total.toFloat() / expense else 0f)
            }
        return MonthSummary(month, expense, income, income - expense, byCategory, rows.size)
    }

    /** 一个月每天的花销/收入，长度固定为当月天数。 */
    fun dailyTrend(entries: List<Entry>, month: String): List<DayTotal> {
        val days = Fmt.daysIn(month)
        val expense = LongArray(days)
        val income = LongArray(days)
        for (row in entries) {
            if (!row.countsTowardTotals || Fmt.monthOf(row.date) != month) continue
            val index = Fmt.dayOfMonth(row.date) - 1
            if (index !in 0 until days) continue
            when (row.kind) {
                Kind.EXPENSE -> expense[index] += row.effectiveAmount
                Kind.INCOME -> income[index] += row.amount
            }
        }
        return (0 until days).map { i ->
            DayTotal(i + 1, "$month-${(i + 1).toString().padStart(2, '0')}", expense[i], income[i])
        }
    }

    fun yearMonths(entries: List<Entry>, year: String): List<YearMonthTotal> {
        val rows = (1..12).map { YearMonthTotal(it, 0, 0) }.toMutableList()
        for (row in entries) {
            if (!row.countsTowardTotals || !row.date.startsWith(year)) continue
            val index = (row.date.substring(5, 7).toIntOrNull() ?: 0) - 1
            if (index !in 0..11) continue
            val current = rows[index]
            rows[index] = when (row.kind) {
                Kind.EXPENSE -> current.copy(expense = current.expense + row.effectiveAmount)
                Kind.INCOME -> current.copy(income = current.income + row.amount)
                else -> current
            }
        }
        return rows
    }

    /** 账本全部时间的净额（收入 − 支出），资产卡用。 */
    fun allTimeNet(entries: List<Entry>): Long = entries.sumOf { row ->
        if (!row.countsTowardTotals) 0L
        else when (row.kind) {
            Kind.INCOME -> row.amount
            Kind.EXPENSE -> -row.effectiveAmount
            else -> 0L
        }
    }

    fun walletsTotal(ledger: Ledger): Long = ledger.wallets.sumOf { it.balance }

    /** 有数据的最新月份；一条都没有时才用本月。 */
    fun latestMonth(entries: List<Entry>, fallback: String): String {
        val best = entries.map { Fmt.monthOf(it.date) }
            .filter { it.length == 7 }
            .maxOrNull()
        return best ?: fallback
    }
}
