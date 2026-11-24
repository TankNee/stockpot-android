package cn.neeto.stockpot.widget

import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerMath
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import java.time.LocalDate

/**
 * 小组件要的那点数据。
 *
 * 小组件跑在广播里，不能等人点开 App 再算；这里一次性把「本月合计、近七天、
 * 最近几条」都挑出来，画图的人只管摆位置。
 */
data class WidgetRow(
    val title: String,
    val category: String,
    /** 带符号的显示金额：支出是负的，收入和转账按原样 */
    val amount: Long,
    val kind: String,
    val date: String,
    val time: String,
)

data class WidgetSnapshot(
    /** 「11 月」 */
    val monthLabel: String,
    val expense: Long,
    val income: Long,
    val net: Long,
    /** 本月记了几笔 */
    val count: Int,
    /** 本月有记账的天数 */
    val activeDays: Int,
    /** 近七天每天的支出，从最早到最近一天（最后一个是今天） */
    val weekBars: List<Long>,
    /** 最近的几笔，新的在前 */
    val recent: List<WidgetRow>,
) {
    val hasData: Boolean get() = count > 0 || recent.isNotEmpty()

    companion object {

        /** 账本还没读出来时用它顶着，卡片照样画得出来（空数）。 */
        val empty: WidgetSnapshot
            get() = WidgetSnapshot(
                monthLabel = "${LocalDate.now().monthValue} 月",
                expense = 0,
                income = 0,
                net = 0,
                count = 0,
                activeDays = 0,
                weekBars = List(7) { 0L },
                recent = emptyList(),
            )

        fun of(ledger: Ledger, today: LocalDate = LocalDate.now()): WidgetSnapshot {
            val month = today.toString().take(7)
            val summary = LedgerMath.summarize(ledger.entries, month)
            val live = ledger.entries.filter { it.countsTowardTotals }

            val weekBars = (6 downTo 0).map { offset ->
                val day = today.minusDays(offset.toLong()).toString()
                live.asSequence()
                    .filter { it.date == day && it.kind == Kind.EXPENSE }
                    .sumOf { it.effectiveAmount }
            }

            val activeDays = live.asSequence()
                .filter { Fmt.monthOf(it.date) == month && it.kind == Kind.EXPENSE }
                .map { it.date }
                .toSet()
                .size

            val recent = live.asSequence()
                .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })
                .take(12)
                .map { row ->
                    WidgetRow(
                        title = row.counterparty.ifEmpty {
                            row.note.ifEmpty { row.category.ifEmpty { "未命名" } }
                        },
                        category = row.category.ifEmpty { "其他" },
                        amount = if (row.kind == Kind.EXPENSE) -row.effectiveAmount else row.amount,
                        kind = row.kind,
                        date = row.date,
                        time = row.time,
                    )
                }
                .toList()

            return WidgetSnapshot(
                monthLabel = "${today.monthValue} 月",
                expense = summary.expense,
                income = summary.income,
                net = summary.net,
                count = summary.count,
                activeDays = activeDays,
                weekBars = weekBars,
                recent = recent,
            )
        }
    }
}
