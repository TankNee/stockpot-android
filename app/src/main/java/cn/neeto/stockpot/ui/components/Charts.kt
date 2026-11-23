package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.DayTotal
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlin.math.ceil
import kotlin.math.max

/** 每日支出柱状图。点一根柱子可以跳到当天的流水。 */
@Composable
fun DailyBars(
    trend: List<DayTotal>,
    selectedDate: String?,
    onPick: (DayTotal) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val measurer = rememberTextMeasurer()
    val maxValue = max(1L, trend.maxOfOrNull { it.expense } ?: 0L)
    val labelStyle = TextStyle(fontSize = 9.sp, color = colors.ink3)
    val todayDay = Fmt.dayOfMonth(Fmt.today())
    val isCurrentMonth = trend.firstOrNull()?.date?.take(7) == Fmt.today().take(7)

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

        trend.forEachIndexed { index, day ->
            val centerX = slot * index + slot / 2f
            val left = centerX - barWidth / 2f
            val ratio = day.expense.toFloat() / maxValue
            val selected = selectedDate == day.date
            val isToday = isCurrentMonth && day.day == todayDay
            val barColor = when {
                selected -> colors.brandInk
                isToday -> colors.brand
                day.expense > 0 -> colors.brand.copy(alpha = 0.55f)
                else -> colors.line
            }
            if (day.expense > 0) {
                val height = (chartHeight * ratio).coerceAtLeast(3.dp.toPx())
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, chartBottom - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                )
            } else {
                drawRoundRect(
                    color = colors.line,
                    topLeft = Offset(left, chartBottom - 2.dp.toPx()),
                    size = Size(barWidth, 2.dp.toPx()),
                    cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                )
            }
        }

        // 只画 1 / 10 / 20 / 月末四个刻度，够定位又不挤
        val labels = buildList {
            add(1)
            if (trend.size >= 10) add(10)
            if (trend.size >= 20) add(20)
            add(trend.size)
        }.distinct()
        labels.forEach { day ->
            val layout = measurer.measure(AnnotatedString(day.toString()), labelStyle)
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
        var start = -90f
        shares.forEachIndexed { index, share ->
            if (share <= 0f) return@forEachIndexed
            val sweep = share * 360f
            val gap = if (shares.size > 1) minOf(2.2f, sweep * 0.18f) else 0f
            drawArc(
                color = palette[index % palette.size],
                startAngle = start + gap / 2f,
                sweepAngle = (sweep - gap).coerceAtLeast(0.8f),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = width),
            )
            start += sweep
        }
    }
}

/** 年度 12 根柱子，收入在上、支出在下也可以，这里只画支出。 */
@Composable
fun YearBars(
    values: List<Long>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val measurer = rememberTextMeasurer()
    val maxValue = max(1L, values.maxOrNull() ?: 0L)
    val labelStyle = TextStyle(fontSize = 9.sp, color = colors.ink3)
    val currentMonth = Fmt.today().substring(5, 7).toIntOrNull() ?: 0

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

        drawLine(colors.line, Offset(0f, bottom), Offset(size.width, bottom), 1.dp.toPx())

        values.forEachIndexed { index, value ->
            val ratio = value.toFloat() / maxValue
            val x = slot * index + (slot - barWidth) / 2f
            val barHeight = (height * ratio).coerceAtLeast(if (value > 0) 3.dp.toPx() else 2.dp.toPx())
            drawRoundRect(
                color = if (index + 1 == currentMonth) colors.brand else colors.brand.copy(alpha = 0.42f),
                topLeft = Offset(x, bottom - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            )
            if ((index + 1) % 3 == 1) {
                val layout = measurer.measure(AnnotatedString("${index + 1}"), labelStyle)
                drawText(layout, topLeft = Offset(x + barWidth / 2f - layout.size.width / 2f, bottom + 4.dp.toPx()))
            }
        }
    }
}

/**
 * 月度日历：格子深浅表示当天花销，收入用右下角绿点标记。
 * 点一天可以看当天流水。
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
    val offset = Fmt.firstWeekdayOffset(month)
    val maxValue = max(1L, trend.maxOfOrNull { it.expense } ?: 0L)
    val today = Fmt.today()
    val cells: List<DayTotal?> = List(offset) { null } + trend
    val rows = ceil(cells.size / 7.0).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    fontSize = 10.sp,
                    color = colors.ink3,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (column in 0 until 7) {
                    val index = row * 7 + column
                    val day = cells.getOrNull(index)
                    Box(modifier = Modifier.weight(1f)) {
                        if (day != null) {
                            CalendarCell(
                                day = day,
                                maxValue = maxValue,
                                isToday = day.date == today,
                                isSelected = day.date == selectedDate,
                                onClick = { onPick(day) },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CalendarCell(
    day: DayTotal,
    maxValue: Long,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val ratio = day.expense.toFloat() / maxValue
    val tint = when {
        day.expense <= 0 -> colors.panel2
        else -> colors.brand.copy(alpha = 0.07f + 0.36f * ratio)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tint)
            .then(
                if (isSelected || isToday) {
                    Modifier.border(
                        width = if (isSelected) 1.4.dp else 1.dp,
                        color = if (isSelected) colors.brand else colors.lineStrong,
                        shape = RoundedCornerShape(9.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable { onClick() }
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = day.day.toString(),
                fontSize = 10.sp,
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
