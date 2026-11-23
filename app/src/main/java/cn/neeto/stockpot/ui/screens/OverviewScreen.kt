package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.CategoryTotal
import cn.neeto.stockpot.core.data.DayTotal
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerMath
import cn.neeto.stockpot.core.data.MonthSummary
import cn.neeto.stockpot.core.data.YearMonthTotal
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.ui.components.DailyBars
import cn.neeto.stockpot.ui.components.Donut
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.EntryRow
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.MonthCalendar
import cn.neeto.stockpot.ui.components.MonthStepper
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.YearBars
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

@Composable
fun OverviewScreen(
    ledger: Ledger,
    onOpenEntry: (Entry) -> Unit,
    onOpenDay: (String) -> Unit,
    onOpenMonth: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenWallets: () -> Unit,
    onAddEntry: () -> Unit,
    onLoadDemo: () -> Unit,
) {
    val colors = LocalPotColors.current
    val entries = ledger.entries
    var month by rememberSaveable { mutableStateOf(LedgerMath.latestMonth(entries, Fmt.currentMonth())) }
    var range by rememberSaveable { mutableStateOf("month") }

    if (entries.isEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            MonthStepper(month, { month = Fmt.shiftMonth(month, it) }, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            EmptyState(
                title = "账本还是空的",
                body = "记一笔试试，或者直接把微信、支付宝的账单导进来。所有数据只存在这台手机上。",
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton("记一笔", onAddEntry)
                        GhostButton("导入账单", onOpenImport)
                    }
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = onLoadDemo) {
                        Text("先看看示例账本", fontSize = 12.sp, color = colors.brandInk)
                    }
                }
            }
        }
        return
    }

    val summary = remember(entries, month) { LedgerMath.summarize(entries, month) }
    val trend = remember(entries, month) { LedgerMath.dailyTrend(entries, month) }
    val yearRows = remember(entries, month) { LedgerMath.yearMonths(entries, Fmt.yearOf(month)) }
    val recent = remember(entries, month) { LedgerMath.monthEntries(entries, month).take(5) }
    val allTimeNet = remember(entries) { LedgerMath.allTimeNet(entries) }
    val walletsTotal = remember(ledger.wallets) { LedgerMath.walletsTotal(ledger) }

    val activeDays = trend.count { it.expense > 0 }
    val dailyAverage = if (activeDays > 0) summary.expense / activeDays else 0L
    val biggest = trend.maxByOrNull { it.expense }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "stepper", contentType = "bar") {
            MonthStepper(
                month = month,
                onShift = { month = Fmt.shiftMonth(month, it) },
                modifier = Modifier.padding(horizontal = 0.dp),
                trailing = {
                    Box(Modifier.width(112.dp)) {
                        Segmented(
                            options = listOf("month" to "月", "year" to "年"),
                            selected = range,
                            onSelect = { range = it },
                        )
                    }
                },
            )
        }

        item(key = "hero", contentType = "card") {
            HeroCard(
                summary = summary,
                range = range,
                yearExpense = yearRows.sumOf { it.expense },
                yearIncome = yearRows.sumOf { it.income },
                activeMonths = yearRows.count { it.expense > 0 },
                dailyAverage = dailyAverage,
                count = summary.count,
            )
        }

        if (range == "year") {
            item(key = "yearbars", contentType = "card") {
                YearBarsCard(yearRows = yearRows)
            }
            item(key = "yearstat", contentType = "card") {
                YearStatCard(yearRows = yearRows)
            }
        } else {
            item(key = "daily", contentType = "card") {
                DailyCard(
                    trend = trend,
                    peak = biggest?.takeIf { it.expense > 0 }?.let { it.day to it.expense },
                    onOpenDay = onOpenDay,
                )
            }

            item(key = "category", contentType = "card") {
                CategoryCard(summary = summary)
            }

            item(key = "calendar", contentType = "card") {
                CalendarCard(month = month, trend = trend, onOpenDay = onOpenDay)
            }
        }

        item(key = "recent", contentType = "card") {
            RecentCard(recent = recent, onOpenEntry = onOpenEntry, onOpenAll = { onOpenMonth(month) })
        }

        item(key = "wallets", contentType = "card") {
            WalletsCard(
                total = walletsTotal,
                count = ledger.wallets.size,
                allTimeNet = allTimeNet,
                onOpen = onOpenWallets,
            )
        }
    }
}

@Composable
private fun YearBarsCard(yearRows: List<YearMonthTotal>) {
    val colors = LocalPotColors.current
    val total = remember(yearRows) { yearRows.sumOf { it.expense } }
    val values = remember(yearRows) { yearRows.map { it.expense } }
    PotCard {
        SectionTitle("按月的支出", trailing = {
            Text(
                text = "全年 ${Fmt.yuan(total)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        })
        Spacer(Modifier.height(10.dp))
        YearBars(values = values)
    }
}

@Composable
private fun YearStatCard(yearRows: List<YearMonthTotal>) {
    val best = remember(yearRows) { yearRows.maxByOrNull { it.expense } }
    val activeMonths = remember(yearRows) { yearRows.count { it.expense > 0 } }
    val total = remember(yearRows) { yearRows.sumOf { it.expense } }
    PotCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            YearStat("花得最多", best?.let { "${it.month}月" } ?: "—", best?.let { Fmt.yuan(it.expense) } ?: "¥0.00")
            YearStat("有支出的月份", "$activeMonths / 12", null)
            YearStat("月均支出", Fmt.yuan(total / 12), null)
        }
    }
}

@Composable
private fun DailyCard(
    trend: List<DayTotal>,
    peak: Pair<Int, Long>?,
    onOpenDay: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard {
        SectionTitle("每日支出", trailing = {
            Text(
                text = if (peak != null) "最高 ${peak.first} 日 ${Fmt.yuan(peak.second)}" else "这个月还没有支出",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        })
        Spacer(Modifier.height(8.dp))
        DailyBars(
            trend = trend,
            selectedDate = null,
            onPick = { onOpenDay(it.date) },
        )
    }
}

@Composable
private fun CategoryCard(summary: MonthSummary) {
    val colors = LocalPotColors.current
    val shown = remember(summary) { summary.byCategory.take(5) }
    PotCard {
        SectionTitle("支出分类", trailing = {
            Text(
                text = "${summary.byCategory.size} 类",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        })
        Spacer(Modifier.height(12.dp))
        if (summary.expense == 0L) {
            Text(
                "这个月还没有支出记录",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                modifier = Modifier.padding(vertical = 18.dp),
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    Donut(
                        shares = summary.byCategory.take(6).map { it.share },
                        modifier = Modifier.size(116.dp),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "支出",
                            fontSize = 10.sp,
                            color = colors.ink3,
                        )
                        Text(
                            text = Fmt.compact(summary.expense),
                            style = MoneyStyle.copy(fontSize = 15.sp),
                            color = colors.ink,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    shown.forEachIndexed { index, item ->
                        CategoryLegendRow(item, colors.chartPalette[index % colors.chartPalette.size])
                        if (index < shown.lastIndex) Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(
    month: String,
    trend: List<DayTotal>,
    onOpenDay: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard {
        SectionTitle("日历", trailing = {
            Text(
                text = "点格子看当天流水",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        })
        Spacer(Modifier.height(10.dp))
        MonthCalendar(
            month = month,
            trend = trend,
            selectedDate = null,
            onPick = { onOpenDay(it.date) },
        )
    }
}

@Composable
private fun RecentCard(recent: List<Entry>, onOpenEntry: (Entry) -> Unit, onOpenAll: () -> Unit) {
    val colors = LocalPotColors.current
    PotCard(padding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("最近记录", modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenAll) {
                Text("全部", fontSize = 12.sp, color = colors.brandInk)
            }
        }
        recent.forEachIndexed { index, entry ->
            EntryRow(entry = entry, onClick = { onOpenEntry(entry) })
            if (index < recent.lastIndex) {
                Box(Modifier.padding(start = 64.dp)) { Hairline() }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun WalletsCard(total: Long, count: Int, allTimeNet: Long, onOpen: () -> Unit) {
    val colors = LocalPotColors.current
    PotCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("资产", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(5.dp))
                Text(
                    text = Fmt.yuan(total),
                    style = MoneyStyle.copy(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$count 个账户  ·  账本累计净 ${Fmt.yuan(allTimeNet, signed = true)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }
            Text("管理", fontSize = 12.sp, color = colors.brandInk)
        }
    }
}

@Composable
private fun HeroCard(
    summary: MonthSummary,
    range: String,
    yearExpense: Long,
    yearIncome: Long,
    activeMonths: Int,
    dailyAverage: Long,
    count: Int,
) {
    val colors = LocalPotColors.current
    val expense = if (range == "month") summary.expense else yearExpense
    val income = if (range == "month") summary.income else yearIncome
    val net = income - expense
    val label = if (range == "month") "本月支出" else "本年支出"

    PotCard(padding = PaddingValues(18.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(6.dp))
        Text(
            text = Fmt.yuan(expense),
            style = MoneyStyle.copy(fontSize = 34.sp, fontWeight = FontWeight.SemiBold),
            color = colors.ink,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                range == "year" && activeMonths > 0 -> "月均 ${Fmt.yuan(yearExpense / 12)}  ·  ${activeMonths} 个月有支出"
                range == "month" && count > 0 -> "日均 ${Fmt.yuan(dailyAverage)}  ·  $count 笔"
                else -> "还没有记录"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("收入", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Fmt.yuan(income),
                    style = MoneyStyle.copy(fontSize = 17.sp),
                    color = colors.income,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("结余", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = Fmt.yuan(net, signed = true),
                    style = MoneyStyle.copy(fontSize = 17.sp),
                    color = if (net >= 0) colors.income else colors.expense,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (range == "month") "笔数" else "活跃月",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (range == "month") count.toString() else activeMonths.toString(),
                    style = MoneyStyle.copy(fontSize = 17.sp),
                    color = colors.ink,
                )
            }
        }
    }
}

@Composable
private fun CategoryLegendRow(item: CategoryTotal, color: Color) {
    val colors = LocalPotColors.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = item.category.replace("/", " · "),
                fontSize = 12.sp,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(item.share * 100).toInt()}%",
                fontSize = 10.sp,
                color = colors.ink3,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = Fmt.compact(item.total),
                style = MoneyStyle.copy(fontSize = 11.sp),
                color = colors.ink,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.panel2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(item.share.coerceIn(0.02f, 1f))
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color.copy(alpha = 0.7f)),
            )
        }
    }
}

@Composable
private fun YearStat(label: String, value: String, hint: String?) {
    val colors = LocalPotColors.current
    Column {
        Text(label, fontSize = 10.sp, color = colors.ink3)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MoneyStyle.copy(fontSize = 15.sp), color = colors.ink)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, fontSize = 10.sp, color = colors.ink3)
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.brand)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(text, color = colors.onBrand, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 11.dp),
    ) {
        Text(text, color = colors.ink2, fontSize = 13.sp)
    }
}
