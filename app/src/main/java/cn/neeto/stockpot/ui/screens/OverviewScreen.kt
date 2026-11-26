package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material3.Icon
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
import cn.neeto.stockpot.core.data.LedgerPhase
import cn.neeto.stockpot.core.data.MonthSummary
import cn.neeto.stockpot.core.data.YearMonthTotal
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.QuietButton
import cn.neeto.stockpot.ui.components.DailyBars
import cn.neeto.stockpot.ui.components.DailyLine
import cn.neeto.stockpot.ui.components.Donut
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.EntryRow
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.LedgerLoading
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.MonthCalendar
import cn.neeto.stockpot.ui.components.MoneyText
import cn.neeto.stockpot.ui.components.MonthStepper
import cn.neeto.stockpot.ui.components.MonthSwipe
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.components.PotPullToRefresh
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.Sparkline
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.components.YearBars
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

private data class ProjectPeek(val id: String, val name: String, val expense: Long, val count: Int)

@Composable
fun OverviewScreen(
    ledger: Ledger,
    /** 还在读库/等第一轮同步时别摆空状态，见 [LedgerPhase] */
    phase: LedgerPhase,
    onOpenEntry: (Entry) -> Unit,
    onLongPressEntry: (Entry) -> Unit,
    onOpenDay: (String) -> Unit,
    onOpenMonth: (String) -> Unit,
    onOpenImport: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenProject: (String) -> Unit,
    onAddEntry: () -> Unit,
    onLoadDemo: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    stockValue: Long,
    stockPnl: Long,
    stockDayPnl: Long,
    stockAccountCount: Int,
    stockTrend: List<Long>,
    onOpenStocks: () -> Unit,
) {
    val colors = LocalPotColors.current
    val entries = ledger.entries
    var month by rememberSaveable { mutableStateOf(LedgerMath.latestMonth(entries, Fmt.currentMonth())) }
    var range by rememberSaveable { mutableStateOf("month") }

    // 左右滑月和点箭头走同一条路，省得两边各写一遍方向。
    // 在「年」视角下翻一屏就是一整年：翻一个月的话，年汇总根本不会变，像是没反应
    fun shift(delta: Long) {
        month = Fmt.shiftMonth(month, if (range == "year") delta * 12 else delta)
    }

    if (entries.isEmpty()) {
        Column(Modifier.fillMaxWidth()) {
            MonthStepper(month, ::shift, Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            if (phase == LedgerPhase.LOADING) {
                // 还没读完库，或者刚登录正在拉云端那份：这会儿的空不作数
                LedgerLoading(Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            } else {
                EmptyState(
                    title = "账本还是空的",
                    body = "记一笔试试，或者直接把微信、支付宝的账单导进来。所有数据只存在这台手机上。",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PrimaryButton("记一笔", onAddEntry)
                            GhostButton("导入账单", onOpenImport)
                        }
                        Spacer(Modifier.height(6.dp))
                        // 第三颗退成纯文字：同字号、同高度，只是没有底色，
                        // 不再是另起一套「小胶囊」，三个按钮看着像一家人
                        QuietButton("先看看示例账本", onLoadDemo, Icons.Outlined.AutoAwesome)
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
    val projectPeek = remember(entries, ledger.projects) {
        val totals = HashMap<String, Long>()
        val counts = HashMap<String, Int>()
        entries.forEach { entry ->
            if (entry.projectId.isNotEmpty() && entry.countsTowardTotals && entry.kind == Kind.EXPENSE) {
                totals[entry.projectId] = (totals[entry.projectId] ?: 0L) + entry.effectiveAmount
                counts[entry.projectId] = (counts[entry.projectId] ?: 0) + 1
            }
        }
        ledger.projects
            .sortedByDescending { it.createdAt }
            .map { ProjectPeek(it.id, it.name, totals[it.id] ?: 0L, counts[it.id] ?: 0) }
    }

    val activeDays = trend.count { it.expense > 0 }
    val dailyAverage = if (activeDays > 0) summary.expense / activeDays else 0L
    val biggest = trend.maxByOrNull { it.expense }

    Column(Modifier.fillMaxWidth()) {
        // 月份条、月/年切换留在上面不动：滑的是下面的内容，翻月时标签立刻跟着变，
        // 不会连标签一起晃，也不至于让人以为整页在乱动
        MonthStepper(
            month = month,
            onShift = ::shift,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
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

        PotPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            MonthSwipe(
                month = month,
                onShift = { shift(it.toLong()) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {

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
            RecentCard(
                recent = recent,
                onOpenEntry = onOpenEntry,
                onLongPressEntry = onLongPressEntry,
                onOpenAll = { onOpenMonth(month) },
            )
        }

        item(key = "stocks", contentType = "card") {
            StocksCard(
                value = stockValue,
                pnl = stockPnl,
                dayPnl = stockDayPnl,
                accounts = stockAccountCount,
                trend = stockTrend,
                onClick = onOpenStocks,
            )
        }

        item(key = "projects", contentType = "card") {
            ProjectsCard(
                projects = projectPeek,
                onOpenProjects = onOpenProjects,
                onOpenProject = onOpenProject,
            )
        }
                }
            }
        }
    }
}

/** 总览里的股票入口：市值、盈亏，外加最近一个月的市值走势。 */
@Composable
private fun StocksCard(
    value: Long,
    pnl: Long,
    dayPnl: Long,
    accounts: Int,
    trend: List<Long>,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val change = remember(trend) {
        if (trend.size < 2) 0L else trend.last() - trend.first()
    }
    val changePercent = remember(trend, change) {
        if (trend.size < 2 || trend.first() <= 0) 0.0 else change * 100.0 / trend.first()
    }
    val tone = when {
        change > 0 -> colors.expense
        change < 0 -> colors.income
        else -> colors.brand
    }

    PotCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brandTint),
                contentAlignment = Alignment.Center,
            ) {
                Text("📈", fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("股票", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (accounts == 0) {
                        "建个券商账户，把持仓记进来"
                    } else {
                        "$accounts 个账户 · 市值 ${Fmt.yuan(value)}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (accounts > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (pnl > 0) "+" else if (pnl < 0) "-" else "") +
                            Fmt.yuan(if (pnl < 0) -pnl else pnl),
                        style = MoneyStyle.copy(fontSize = 15.sp),
                        color = when {
                            pnl > 0 -> colors.expense
                            pnl < 0 -> colors.income
                            else -> colors.ink3
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "今日 " + (if (dayPnl > 0) "+" else if (dayPnl < 0) "-" else "") +
                            Fmt.yuan(if (dayPnl < 0) -dayPnl else dayPnl),
                        fontSize = 11.sp,
                        color = colors.ink3,
                    )
                }
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.ink3,
                modifier = Modifier.padding(start = 6.dp).size(17.dp),
            )
        }

        if (trend.size >= 2) {
            Spacer(Modifier.height(12.dp))
            Sparkline(values = trend, color = tone, height = 46.dp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "近 1 个月",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = (if (change >= 0) "+" else "-") + Fmt.yuan(if (change < 0) -change else change) +
                        "（" + (if (changePercent >= 0) "+" else "-") +
                        "%.2f".format(if (changePercent < 0) -changePercent else changePercent) + "%）",
                    style = MaterialTheme.typography.labelSmall,
                    color = tone,
                )
            }
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
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    // 柱子和曲线是同一份数据的两种看法：柱子看「哪天花得多」，曲线看「这个月怎么走下来的」。
    // 默认柱子，点一下（或右上角那个小图标）换成曲线，翻月、切 tab 回来还是上次选的那种。
    var line by rememberSaveable { mutableStateOf(false) }

    PotCard {
        SectionTitle("每日支出", trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (peak != null) "最高 ${peak.first} 日 ${Fmt.yuan(peak.second)}" else "这个月还没有支出",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.width(8.dp))
                // 图标画的是「点下去会变成什么」，不是当前状态，跟播放/暂停一个路子
                Icon(
                    imageVector = if (line) Icons.Outlined.BarChart else Icons.Outlined.ShowChart,
                    contentDescription = if (line) "换成柱状图" else "换成曲线图",
                    tint = colors.ink3,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable {
                            haptics(Haptic.Tap)
                            line = !line
                        },
                )
            }
        })
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                // 整块图表都能点：小图标太小，手指落在柱子上也该有反应
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    haptics(Haptic.Tap)
                    line = !line
                },
        ) {
            if (line) DailyLine(trend = trend) else DailyBars(trend = trend)
        }
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
                            style = MaterialTheme.typography.labelSmall,
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
private fun RecentCard(
    recent: List<Entry>,
    onOpenEntry: (Entry) -> Unit,
    onLongPressEntry: (Entry) -> Unit,
    onOpenAll: () -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard(padding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("最近记录", modifier = Modifier.weight(1f))
            PotPillButton(
                text = "全部",
                icon = Icons.Outlined.ChevronRight,
                tone = PillTone.Ghost,
                onClick = onOpenAll,
            )
        }
        recent.forEachIndexed { index, entry ->
            EntryRow(
                entry = entry,
                onClick = { onOpenEntry(entry) },
                onLongClick = { onLongPressEntry(entry) },
            )
            if (index < recent.lastIndex) {
                Box(Modifier.padding(start = 64.dp)) { Hairline() }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 首页只露三个项目，点「全部」进项目页；点某个项目直接进它的详情。 */
@Composable
private fun ProjectsCard(
    projects: List<ProjectPeek>,
    onOpenProjects: () -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val shown = remember(projects) { projects.take(3) }
    PotCard(padding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SectionTitle("项目", modifier = Modifier.weight(1f))
            PotPillButton(
                text = if (projects.isEmpty()) "去创建" else "全部 ${projects.size} 个",
                icon = if (projects.isEmpty()) Icons.Outlined.Add else Icons.Outlined.ChevronRight,
                tone = PillTone.Ghost,
                onClick = onOpenProjects,
            )
        }
        if (shown.isEmpty()) {
            Text(
                text = "把一次旅行、一次搬家、一次装修的账目收在一起，看这件事总共花了多少。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.ink3,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
            )
        } else {
            shown.forEachIndexed { index, project ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenProject(project.id) }
                        .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.brandTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("📁", fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            project.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${project.count} 笔支出",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                        )
                    }
                    Text(
                        Fmt.yuan(project.expense),
                        style = MoneyStyle.copy(fontSize = 15.sp),
                        color = colors.expense,
                    )
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.ink3,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (index < shown.lastIndex) Hairline(Modifier.padding(start = 59.dp))
            }
            Spacer(Modifier.height(6.dp))
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
        // 大金额也按宽度收：八位数加千分位在 34sp 下会顶出去
        MoneyText(
            text = Fmt.yuan(expense),
            color = colors.ink,
            maxSize = 34.sp,
            minSize = 20.sp,
            weight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth(),
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
            Column(Modifier.weight(0.95f)) {
                Text("收入", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                MoneyText(
                    text = Fmt.yuan(income),
                    color = colors.income,
                    maxSize = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            // 结余带正负号，比收入多半位，给它多一档宽度
            Column(Modifier.weight(1.25f)) {
                Text("结余", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(3.dp))
                MoneyText(
                    text = Fmt.yuan(net, signed = true),
                    color = if (net >= 0) colors.income else colors.expense,
                    maxSize = 17.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(0.6f)) {
                Text(
                    if (range == "month") "笔数" else "活跃月",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (range == "month") count.toString() else activeMonths.toString(),
                    style = MoneyStyle.copy(fontSize = 17.sp),
                    color = colors.ink,
                    maxLines = 1,
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
                fontSize = 13.sp,
                color = colors.ink2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(item.share * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = Fmt.compact(item.total),
                style = MoneyStyle.copy(fontSize = 13.sp),
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
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(3.dp))
        Text(value, style = MoneyStyle.copy(fontSize = 16.sp), color = colors.ink)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        }
    }
}
