package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.DayTotal
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlin.math.ceil
import kotlin.math.max

/**
 * 每日支出柱状图。点一根柱子可以跳到当天的流水。
 *
 * 柱子的颜色、比例和刻度文字都在 remember 里算好，滚动时只做 draw：
 * 画布里再调 Fmt / copy(alpha) 会拖慢每一帧。
 */
@Composable
fun DailyBars(
    trend: List<DayTotal>,
    selectedDate: String?,
    onPick: (DayTotal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val measurer = rememberTextMeasurer()
    val today = remember { Fmt.today() }

    val maxValue = remember(trend) { max(1L, trend.maxOfOrNull { it.expense } ?: 0L) }
    val isCurrentMonth = remember(trend, today) { trend.firstOrNull()?.date?.take(7) == today.take(7) }
    val todayDay = remember(today) { Fmt.dayOfMonth(today) }

    data class Bar(val ratio: Float, val color: Color)
    val bars = remember(trend, selectedDate, maxValue, todayDay, isCurrentMonth, colors) {
        val quiet = colors.brand.copy(alpha = 0.55f)
        trend.map { day ->
            val selected = selectedDate == day.date
            val isToday = isCurrentMonth && day.day == todayDay
            val color = when {
                selected -> colors.brandInk
                isToday -> colors.brand
                day.expense > 0 -> quiet
                else -> colors.line
            }
            Bar(day.expense.toFloat() / maxValue, color)
        }
    }

    val tickStyle = remember(colors.ink3) { TextStyle(fontSize = 9.sp, color = colors.ink3) }
    val ticks = remember(trend, tickStyle) {
        buildList {
            add(1)
            if (trend.size >= 10) add(10)
            if (trend.size >= 20) add(20)
            add(trend.size)
        }.distinct().map { day -> day to measurer.measure(AnnotatedString(day.toString()), tickStyle) }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(138.dp)
            .pointerInput(trend) {
                detectTapGestures { offset ->
                    if (trend.isEmpty()) return@detectTapGestures
                    val slot = size.width.toFloat() / trend.size
                    val index = (offset.x / slot).toInt().coerceIn(0, trend.size - 1)
                    trend.getOrNull(index)?.let(onPick)
                }
            },
    ) {
        val chartBottom = size.height - 18.dp.toPx()
        val chartTop = 6.dp.toPx()
        val chartHeight = chartBottom - chartTop
        val slot = size.width / trend.size
        val barWidth = (slot * 0.58f).coerceAtLeast(2.dp.toPx())
        val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        val minBar = 3.dp.toPx()
        val stub = 2.dp.toPx()

        // 两条参考线：中线和顶线，虚线太吵，用极浅的实线
        for (fraction in listOf(0.5f, 1f)) {
            val y = chartBottom - chartHeight * fraction
            drawLine(
                color = colors.line,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        bars.forEachIndexed { index, bar ->
            val left = slot * index + (slot - barWidth) / 2f
            if (bar.ratio > 0f) {
                val height = (chartHeight * bar.ratio).coerceAtLeast(minBar)
                drawRoundRect(
                    color = bar.color,
                    topLeft = Offset(left, chartBottom - height),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            } else {
                drawRoundRect(
                    color = bar.color,
                    topLeft = Offset(left, chartBottom - stub),
                    size = Size(barWidth, stub),
                    cornerRadius = radius,
                )
            }
        }

        // 只画 1 / 10 / 20 / 月末四个刻度，够定位又不挤
        ticks.forEach { (day, layout) ->
            val x = (slot * (day - 1) + slot / 2f - layout.size.width / 2f)
                .coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(x, chartBottom + 5.dp.toPx()))
        }
    }
}

/** 分类占比环形图。 */
@Composable
fun Donut(
    shares: List<Float>,
    modifier: Modifier = Modifier,
    trackColor: Color? = null,
) {
    val colors = LocalPotColors.current
    val palette = colors.chartPalette
    val track = trackColor ?: colors.panel2
    val stroke = 17.dp

    // 弧段只在数据或配色变化时算一次
    val arcs = remember(shares, colors) {
        var start = -90f
        shares.mapIndexed { index, share ->
            val sweep = share * 360f
            val gap = if (shares.size > 1) minOf(2.2f, sweep * 0.18f) else 0f
            val arc = start + gap / 2f to (sweep - gap).coerceAtLeast(0.8f)
            start += sweep
            if (share <= 0f) null else palette[index % palette.size] to arc
        }.filterNotNull()
    }

    Canvas(modifier = modifier) {
        val width = stroke.toPx()
        val inset = width / 2f
        val arcSize = Size(size.width - width, size.height - width)
        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = width),
        )
        arcs.forEach { (color, angles) ->
            drawArc(
                color = color,
                startAngle = angles.first,
                sweepAngle = angles.second,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width),
            )
        }
    }
}

/** 年度 12 根柱子，只画支出。 */
@Composable
fun YearBars(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val measurer = rememberTextMeasurer()
    val maxValue = remember(values) { max(1L, values.maxOrNull() ?: 0L) }
    val currentMonth = remember { Fmt.today().substring(5, 7).toIntOrNull() ?: 0 }
    val tickStyle = remember(colors.ink3) { TextStyle(fontSize = 9.sp, color = colors.ink3) }
    val ticks = remember(tickStyle) {
        (1..12).filter { it % 3 == 1 }.map { month ->
            month to measurer.measure(AnnotatedString("$month"), tickStyle)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp),
    ) {
        val bottom = size.height - 16.dp.toPx()
        val top = 4.dp.toPx()
        val height = bottom - top
        val slot = size.width / 12f
        val barWidth = slot * 0.52f
        val quiet = colors.brand.copy(alpha = 0.42f)
        val radius = CornerRadius(2.dp.toPx(), 2.dp.toPx())

        drawLine(colors.line, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())

        values.forEachIndexed { index, value ->
            val ratio = value.toFloat() / maxValue
            val x = slot * index + (slot - barWidth) / 2f
            val barHeight = (height * ratio).coerceAtLeast(if (value > 0) 3.dp.toPx() else 2.dp.toPx())
            drawRoundRect(
                color = if (index + 1 == currentMonth) colors.brand else quiet,
                topLeft = Offset(x, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = radius,
            )
        }

        ticks.forEach { (month, layout) ->
            val x = slot * (month - 1) + (slot - layout.size.width) / 2f
            drawText(layout, topLeft = Offset(x, bottom + 4.dp.toPx()))
        }
    }
}

/**
 * 月度日历：格子深浅表示当天花销，收入用右下角绿点标记。
 * 点一天可以看当天流水。
 *
 * 42 个格子共用一个点击手势：以前每个格子一个 Modifier.clickable，
 * 一屏就是 42 个 ripple 节点，滑动时明显掉帧。
 */
@Composable
fun MonthCalendar(
    month: String,
    trend: List<DayTotal>,
    selectedDate: String?,
    onPick: (DayTotal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val offset = remember(month) { Fmt.firstWeekdayOffset(month) }
    val maxValue = remember(trend) { max(1L, trend.maxOfOrNull { it.expense } ?: 0L) }
    val today = remember { Fmt.today() }
    val cells = remember(offset, trend) { List(offset) { null } + trend }
    val rows = remember(cells) { ceil(cells.size / 7.0).toInt() }

    val cellGap = 4.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = colors.ink3,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .pointerInput(cells, rows) {
                    val gapPx = cellGap.toPx()
                    val rowHeight = 50.dp.toPx() + gapPx
                    detectTapGestures { tap ->
                        val columnWidth = (size.width - gapPx * 6) / 7f
                        val unit = columnWidth + gapPx
                        val column = ((tap.x + gapPx / 2f) / unit).toInt().coerceIn(0, 6)
                        val row = (tap.y / rowHeight).toInt().coerceIn(0, rows - 1)
                        cells.getOrNull(row * 7 + column)?.let(onPick)
                    }
                },
        ) {
            Column(Modifier.fillMaxWidth()) {
                for (row in 0 until rows) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(cellGap),
                    ) {
                        for (column in 0 until 7) {
                            val day = cells.getOrNull(row * 7 + column)
                            Box(modifier = Modifier.weight(1f)) {
                                if (day != null) {
                                    CalendarCell(
                                        day = day,
                                        maxValue = maxValue,
                                        isToday = day.date == today,
                                        isSelected = day.date == selectedDate,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(cellGap))
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(
    day: DayTotal,
    maxValue: Long,
    isToday: Boolean,
    isSelected: Boolean,
) {
    val colors = LocalPotColors.current
    val ratio = day.expense.toFloat() / maxValue
    val tint = when {
        day.expense <= 0 -> colors.panel2
        else -> colors.brand.copy(alpha = 0.07f + 0.36f * ratio)
    }
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .then(if (isSelected || isToday) Modifier.border(1.dp, if (isSelected) colors.brand else colors.lineStrong, shape) else Modifier)
            .background(tint, shape)
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = day.day.toString(),
                fontSize = 11.sp,
                color = if (isToday) colors.brandInk else colors.ink2,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (day.expense > 0) Fmt.compact(day.expense) else "",
                fontSize = 9.sp,
                color = colors.ink2,
                maxLines = 1,
            )
        }
    }
}
