package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 金额与日期的显示格式。所有金额入参都是「分」。 */
object Fmt {

    private fun money(cents: Long): String {
        val symbols = DecimalFormatSymbols(Locale.CHINA)
        val format = DecimalFormat("#,##0.00", symbols)
        return format.format(BigDecimal.valueOf(cents).movePointLeft(2))
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
        return "$sign¥${money(kotlin.math.abs(cents))}"
    }

    /** 紧凑写法：850、1.1k、23k、1.2m，给日历格子和图表用。 */
    fun compact(cents: Long): String {
        val yuan = kotlin.math.abs(cents) / 100.0
        return when {
            yuan < 1000 -> if (yuan < 10) {
                if (yuan % 1.0 == 0.0) yuan.toInt().toString() else String.format(Locale.CHINA, "%.1f", yuan)
            } else {
                yuan.roundToIntLike().toString()
            }
            yuan >= 1_000_000 -> String.format(Locale.CHINA, "%.1fm", yuan / 1_000_000)
            yuan >= 100_000 -> "${(yuan / 1000).roundToIntLike()}k"
            else -> String.format(Locale.CHINA, "%.1fk", yuan / 1000)
        }
    }

    private fun Double.roundToIntLike(): Int = kotlin.math.round(this).toInt()

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

    fun weekday(date: String): String = runCatching {
        weekdayNames[LocalDate.parse(date).dayOfWeek.value - 1]
    }.getOrDefault("")

    /** `11月22日 周六` */
    fun dayLabel(date: String): String = runCatching {
        val parsed = LocalDate.parse(date)
        "${parsed.monthValue}月${parsed.dayOfMonth}日 ${weekday(date)}"
    }.getOrDefault(date)

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

    fun shortDate(date: String): String = runCatching {
        val parsed = LocalDate.parse(date)
        parsed.format(DateTimeFormatter.ofPattern("MM-dd", Locale.CHINA))
    }.getOrDefault(date)

    /** 导入历史里显示的时间戳。 */
    fun stampLabel(raw: String): String = runCatching {
        val instant = Instant.parse(raw)
        val local = instant.atZone(ZoneId.systemDefault())
        local.format(DateTimeFormatter.ofPattern("MM-dd HH:mm", Locale.CHINA))
    }.getOrDefault(raw.take(16))
}

data class CategoryTotal(val category: String, val total: Long, val share: Float)

data class MonthSummary(
    val month: String,
    val expense: Long,
    val income: Long,
    val net: Long,
    val byCategory: List<CategoryTotal>,
    val count: Int,
)

data class DayTotal(val day: Int, val date: String, val expense: Long, val income: Long)

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
