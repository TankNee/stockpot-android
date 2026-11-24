package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Entry
import kotlin.math.abs

/**
 * 流水页的筛选条件。
 *
 * 以前这些条件散在页面上：一行类型 chip、一行账户 chip、右上角一个排序按钮。
 * 账户一多，两行横滑就把列表挤没了。现在统一收进「筛选」弹层，页面上只留
 * 搜索框（高频）和一行「已生效条件」，点了就能撤。
 *
 * 时间范围的语义：一旦给了起止日期，就**不再看月份**。跨月区间是筛选能表达、
 * 月份翻页表达不了的东西，两者同时生效只会让人算不明白。
 */
data class EntryFilter(
    val kind: String = ALL,
    /** 完整类目名，`餐饮/外卖` 这种带斜杠的也照原样存，比较时同样按全名比 */
    val category: String? = null,
    val account: String? = null,
    val minCents: Long? = null,
    val maxCents: Long? = null,
    /** `YYYY-MM-DD`，含当天 */
    val from: String? = null,
    val to: String? = null,
    val sort: String = TIME_DESC,
) {
    val hasRange: Boolean get() = from != null || to != null

    /** 除了排序，有几个条件在生效（筛选按钮上的角标用它）。 */
    val activeCount: Int
        get() = listOf(
            kind != ALL,
            category != null,
            account != null,
            minCents != null || maxCents != null,
            hasRange,
            sort != TIME_DESC,
        ).count { it }

    val isEmpty: Boolean get() = activeCount == 0

    /**
     * 过滤 + 排序。`month` 只在没有自定义区间时参与；`day` 是首页日历里点下来的那一天。
     */
    fun apply(
        entries: List<Entry>,
        month: String,
        day: String? = null,
        query: String = "",
    ): List<Entry> {
        val needle = query.trim()
        // 上下限填反了也照常工作，别让人对着空列表发愣
        val low = if (minCents != null && maxCents != null) minOf(minCents, maxCents) else minCents
        val high = if (minCents != null && maxCents != null) maxOf(minCents, maxCents) else maxCents

        return entries.asSequence()
            .filter { !it.archived }
            .filter { if (hasRange) inRange(it.date) else Fmt.monthOf(it.date) == month }
            .filter { day == null || it.date == day }
            .filter { kind == ALL || it.kind == kind }
            // 类目按全名比：`餐饮/外卖` 和 `餐饮` 是两个不同的筛选口径，
            // 想要「整个餐饮」得选父级那一条——账单里父级才是常见写法
            .filter { category == null || it.category == category }
            .filter { account == null || it.account.trim() == account }
            .filter { entry ->
                val value = abs(entry.effectiveAmount)
                (low == null || value >= low) && (high == null || value <= high)
            }
            .filter { entry -> needle.isEmpty() || matchesText(entry, needle) }
            .sortedWith(comparator())
            .toList()
    }

    /**
     * 搜一条流水。文字那四栏照旧，金额也能搜：
     * `25`、`25.5`、`¥1,200`、`1200.00` 都认，千分位和货币符号先剥掉再说。
     *
     * 金额认两种命中：一是分毫不差（`25` 命中 25.00），二是从元的那一位开始对上
     * （`25` 也能找到 25.30）。不做「随便包含」——`25` 不该把 125.00 也捞出来，
     * 那样搜金额就没意义了。退过款的那笔两种口径都试：净额和原来的金额。
     */
    private fun matchesText(entry: Entry, needle: String): Boolean {
        if (entry.counterparty.contains(needle, ignoreCase = true) ||
            entry.note.contains(needle, ignoreCase = true) ||
            entry.category.contains(needle, ignoreCase = true) ||
            entry.account.contains(needle, ignoreCase = true)
        ) {
            return true
        }
        val money = moneyNeedle(needle) ?: return false
        val (cents, digits) = money
        val net = abs(entry.effectiveAmount)
        if (net == cents || abs(entry.amount) == cents) return true
        return Fmt.cents(net).replace(",", "").startsWith(digits)
    }

    /** 「25」「25.5」「¥1,200」「1200.00」→ 分 + 剥干净的数字串；不像金额就返回 null。 */
    private fun moneyNeedle(text: String): Pair<Long, String>? {
        val cleaned = text.trim()
            .removePrefix("¥")
            .removePrefix("￥")
            .replace(",", "")
            .replace("，", "")
        if (cleaned.isEmpty()) return null
        if (cleaned.any { !it.isDigit() && it != '.' }) return null
        if (cleaned.count { it == '.' } > 1) return null
        val cents = Fmt.parseYuan(cleaned)?.takeIf { it > 0 } ?: return null
        return cents to cleaned
    }

    private fun inRange(date: String): Boolean =
        (from == null || date >= from) && (to == null || date <= to)

    private fun comparator(): Comparator<Entry> = when (sort) {
        AMOUNT_DESC -> compareByDescending<Entry> { it.effectiveAmount }.thenByDescending { it.date }
        AMOUNT_ASC -> compareBy<Entry> { it.effectiveAmount }.thenByDescending { it.date }
        TIME_ASC -> compareBy<Entry> { it.date }.thenBy { it.time }
        else -> compareByDescending<Entry> { it.date }.thenByDescending { it.time }
    }

    companion object {
        const val ALL = "all"
        const val TIME_DESC = "timeDesc"
        const val TIME_ASC = "timeAsc"
        const val AMOUNT_DESC = "amountDesc"
        const val AMOUNT_ASC = "amountAsc"

        /** 输入框里的「元」转成分；空串、0、非法输入都当没填。 */
        fun cents(text: String): Long? {
            if (text.isBlank()) return null
            val value = Fmt.parseYuan(text) ?: return null
            return if (value <= 0) null else value
        }
    }
}
