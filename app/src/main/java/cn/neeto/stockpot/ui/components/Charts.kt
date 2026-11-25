package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.luminance
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
import cn.neeto.stockpot.core.data.StockMath
import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlin.math.ceil
import kotlin.math.max

/**
 * 每日支出柱状图。
 *
 * 只看，不点——具体某一天花了什么由下面的日历负责，柱子上再挂一层点击
 * 容易让人以为点错了。柱子的颜色、比例和刻度文字都在 remember 里算好，
 * 滚动时只做 draw：画布里再调 Fmt / copy(alpha) 会拖慢每一帧。
 */
@Composable
fun DailyBars(
    trend: List<DayTotal>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val measurer = rememberTextMeasurer()
    val today = remember { Fmt.today() }

    val maxValue = remember(trend) { max(1L, trend.maxOfOrNull { it.expense } ?: 0L) }
    val isCurrentMonth = remember(trend, today) { trend.firstOrNull()?.date?.take(7) == today.take(7) }
    val todayDay = remember(today) { Fmt.dayOfMonth(today) }

    data class Bar(val ratio: Float, val color: Color)
    val bars = remember(trend, maxValue, todayDay, isCurrentMonth, colors) {
        // 半透明压在深底上会二次变暗：浅色纸 0.55 刚好，深色纸要抬到 0.72，
        // 否则整排柱子糊成背景的一部分。
        val quiet = colors.brand.copy(alpha = if (colors.canvas.luminance() < 0.5f) 0.72f else 0.55f)
        trend.map { day ->
            val isToday = isCurrentMonth && day.day == todayDay
            val color = when {
                isToday -> colors.brand
                day.expense > 0 -> quiet
                else -> colors.line
            }
            Bar(day.expense.toFloat() / maxValue, color)
        }
    }

    // 刻度是必须读的信息，不是装饰：10sp 起步、走 ink2。9sp 的 ink3 放在
    // 深色纸上对比度够、字重却太细，扫一眼根本读不出来。
    val tickStyle = remember(colors.ink2) { TextStyle(fontSize = 10.sp, color = colors.ink2) }
    val ticks = remember(trend, tickStyle) { dayTicks(trend, measurer, tickStyle) }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight),
    ) {
        if (trend.isEmpty()) return@Canvas
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

/** 柱状图和曲线图共用一个高度，切换时卡片不会忽然长高或缩一截。 */
private val ChartHeight = 138.dp

/** 1 / 10 / 20 / 月末四个刻度，柱子铺在格子上、曲线铺在两点之间，位置各算各的。 */
private fun dayTicks(
    trend: List<DayTotal>,
    measurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
): List<Pair<Int, androidx.compose.ui.text.TextLayoutResult>> {
    if (trend.isEmpty()) return emptyList()
    return buildList {
        add(1)
        if (trend.size >= 10) add(10)
        if (trend.size >= 20) add(20)
        add(trend.size)
    }.distinct().map { day -> day to measurer.measure(AnnotatedString(day.toString()), style) }
}

/**
 * 每日支出曲线：同一份数据的另一种看法。
 *
 * 柱子看「哪几天花得多」，曲线看「这个月是怎么一路走下来的」——
 * 月底那根抬头还是慢慢爬上去，曲线比柱子清楚。零花钱的日子就落在底线上，
 * 不断开，不然整条线会碎成一串孤岛。
 */
@Composable
fun DailyLine(
    trend: List<DayTotal>,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val measurer = rememberTextMeasurer()
    val today = remember { Fmt.today() }
    val maxValue = remember(trend) { max(1L, trend.maxOfOrNull { it.expense } ?: 0L) }
    val isCurrentMonth = remember(trend, today) { trend.firstOrNull()?.date?.take(7) == today.take(7) }
    val todayDay = remember(today) { Fmt.dayOfMonth(today) }
    // 刻度是必须读的信息，不是装饰：10sp 起步、走 ink2。9sp 的 ink3 放在
    // 深色纸上对比度够、字重却太细，扫一眼根本读不出来。
    val tickStyle = remember(colors.ink2) { TextStyle(fontSize = 10.sp, color = colors.ink2) }
    val ticks = remember(trend, tickStyle) { dayTicks(trend, measurer, tickStyle) }
    val lineColor = colors.brand
    val gradient = remember(lineColor) {
        listOf(lineColor.copy(alpha = 0.22f), lineColor.copy(alpha = 0.01f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(ChartHeight),
    ) {
        if (trend.size < 2) return@Canvas
        val chartBottom = size.height - 18.dp.toPx()
        val chartTop = 6.dp.toPx()
        val chartHeight = chartBottom - chartTop
        fun xOf(index: Int): Float = size.width * index / (trend.size - 1).toFloat()
        fun yOf(value: Long): Float = chartBottom - chartHeight * (value.toFloat() / maxValue)

        for (fraction in listOf(0.5f, 1f)) {
            val y = chartBottom - chartHeight * fraction
            drawLine(colors.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        }

        val line = androidx.compose.ui.graphics.Path()
        trend.forEachIndexed { index, day ->
            if (index == 0) line.moveTo(xOf(0), yOf(day.expense)) else line.lineTo(xOf(index), yOf(day.expense))
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(xOf(trend.lastIndex), chartBottom)
            lineTo(0f, chartBottom)
            close()
        }
        drawPath(
            path = area,
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                gradient,
                startY = chartTop,
                endY = chartBottom,
            ),
        )
        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(
                width = 1.9.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )

        // 有支出的日子点一颗小点：一屏 30 天，光看线分不清是花了 0 还是花了一点点
        trend.forEachIndexed { index, day ->
            if (day.expense <= 0) return@forEachIndexed
            drawCircle(lineColor.copy(alpha = 0.55f), radius = 1.8.dp.toPx(), center = Offset(xOf(index), yOf(day.expense)))
        }
        val todayIndex = if (isCurrentMonth) trend.indexOfFirst { it.day == todayDay } else -1
        if (todayIndex >= 0) {
            val center = Offset(xOf(todayIndex), yOf(trend[todayIndex].expense))
            drawCircle(colors.brand, radius = 4.dp.toPx(), center = center)
            drawCircle(colors.panel, radius = 1.8.dp.toPx(), center = center)
        }

        // 曲线铺在两个点之间，刻度往端点对齐，1 号不会跑到左边一半
        ticks.forEach { (day, layout) ->
            val x = (xOf(day - 1) - layout.size.width / 2f)
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
    // 刻度是必须读的信息，不是装饰：10sp 起步、走 ink2。9sp 的 ink3 放在
    // 深色纸上对比度够、字重却太细，扫一眼根本读不出来。
    val tickStyle = remember(colors.ink2) { TextStyle(fontSize = 10.sp, color = colors.ink2) }
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
        val quiet = colors.brand.copy(alpha = if (colors.canvas.luminance() < 0.5f) 0.72f else 0.42f)
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
        // 热力格是「一眼扫量级」用的，深色纸上底色本来就沉，
        // 同一档 alpha 会比浅色纸淡一截，所以整体上调。
        colors.canvas.luminance() < 0.5f -> colors.brand.copy(alpha = 0.14f + 0.46f * ratio)
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

/* ------------------------------ 股票曲线 ------------------------------ */

/**
 * 按住选一个下标，左右拖动可以一直看，抬手回 null。
 *
 * 用 awaitEachGesture 而不是 detectTapGestures：单点长按看不了中间那段，
 * 而看盘的时候手指是要拖着走的。下滑动会跟列表冲突，所以跟手时 consume 掉。
 */
private fun Modifier.pickIndex(count: Int, onPick: ((Int?) -> Unit)?): Modifier {
    if (onPick == null || count < 2) return this
    return this.pointerInput(count) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            fun at(x: Float): Int {
                val ratio = (x / size.width).coerceIn(0f, 1f)
                return (ratio * count).toInt().coerceIn(0, count - 1)
            }
            onPick(at(down.position.x))
            val id = down.id
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == id } ?: break
                if (!change.pressed) break
                onPick(at(change.position.x))
                change.consume()
            }
            onPick(null)
        }
    }
}

/**
 * 折线图，分时、资产走势、盈亏曲线共用。`baseline` 画一条虚线当参照
 * （昨收 / 期初资产 / 0 轴），`onPick` 把手指拖到的下标传回去，外面拿它显示这一点的值。
 */
@Composable
fun LineChart(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp,
    baseline: Long? = null,
    selected: Int? = null,
    onPick: ((Int?) -> Unit)? = null,
    /** 是否把曲线下方也填一层渐变。盈亏曲线跨 0 轴时填色会糊，可以关掉。 */
    fill: Boolean = true,
) {
    val colors = LocalPotColors.current
    val bounds = remember(values, baseline) {
        var low = values.minOrNull() ?: 0L
        var high = values.maxOrNull() ?: 0L
        if (baseline != null) {
            low = minOf(low, baseline)
            high = maxOf(high, baseline)
        }
        if (high <= low) high = low + 1
        low to high
    }
    val gradient = remember(color) {
        listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0f))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pickIndex(values.size, onPick),
    ) {
        if (values.size < 2) return@Canvas
        val (low, high) = bounds
        val top = 6.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val span = (high - low).toFloat().coerceAtLeast(1f)

        fun xOf(index: Int): Float = size.width * index / (values.size - 1).toFloat()
        fun yOf(value: Long): Float = bottom - (value - low) / span * (bottom - top)

        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = top + (bottom - top) * fraction
            drawLine(colors.line.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (baseline != null) {
            val y = yOf(baseline)
            drawLine(
                color = colors.lineStrong,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = androidx.compose.ui.graphics.PathEffect
                    .dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
        }

        val line = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            if (index == 0) line.moveTo(xOf(0), yOf(value)) else line.lineTo(xOf(index), yOf(value))
        }
        if (fill) {
            val area = androidx.compose.ui.graphics.Path().apply {
                addPath(line)
                lineTo(xOf(values.lastIndex), bottom)
                lineTo(xOf(0), bottom)
                close()
            }
            drawPath(
                path = area,
                brush = androidx.compose.ui.graphics.Brush.verticalGradient(gradient, startY = top, endY = bottom),
            )
        }
        drawPath(
            path = line,
            color = color,
            style = Stroke(
                width = 1.9.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )

        // 最后一个点永远标出来；手指按着的时候标手指那个点
        val focus = selected?.coerceIn(0, values.lastIndex) ?: values.lastIndex
        val fx = xOf(focus)
        val fy = yOf(values[focus])
        if (selected != null) {
            drawLine(colors.ink3.copy(alpha = 0.5f), Offset(fx, top), Offset(fx, bottom), 1.dp.toPx())
        }
        drawCircle(color, radius = 3.2.dp.toPx(), center = Offset(fx, fy))
        drawCircle(colors.panel, radius = 1.4.dp.toPx(), center = Offset(fx, fy))
    }
}

/**
 * 日 K：上影线 + 实体，叠 MA5 / MA20 两条均线。涨红跌绿，跟国内看盘的习惯一致。
 * `onPick` 传回手指拖着的那根，外侧显示当日开高低收。
 */
@Composable
fun CandleChart(
    candles: List<Candle>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 168.dp,
    selected: Int? = null,
    onPick: ((Int?) -> Unit)? = null,
    showMa: Boolean = true,
) {
    val colors = LocalPotColors.current
    val bounds = remember(candles) {
        if (candles.isEmpty()) 0L to 1L else {
            val low = candles.minOf { it.lowE4 }
            val high = candles.maxOf { it.highE4 }
            low to (if (high > low) high else low + 1)
        }
    }
    val ma5 = remember(candles) {
        if (showMa) StockMath.movingAverage(candles.map { it.closeE4 }, 5) else emptyList()
    }
    val ma20 = remember(candles) {
        if (showMa) StockMath.movingAverage(candles.map { it.closeE4 }, 20) else emptyList()
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pickIndex(candles.size, onPick),
    ) {
        if (candles.isEmpty()) return@Canvas
        val (low, high) = bounds
        val top = 6.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val span = (high - low).toFloat().coerceAtLeast(1f)
        fun yOf(value: Long): Float = bottom - (value - low) / span * (bottom - top)

        for (fraction in listOf(0f, 0.5f, 1f)) {
            val y = top + (bottom - top) * fraction
            drawLine(colors.line.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y), 1f)
        }

        val slot = size.width / candles.size
        val bodyWidth = (slot * 0.62f).coerceAtLeast(1.5.dp.toPx())
        candles.forEachIndexed { index, candle ->
            val centerX = slot * index + slot / 2f
            val up = candle.closeE4 >= candle.openE4
            val tone = if (up) colors.expense else colors.income
            drawLine(tone, Offset(centerX, yOf(candle.highE4)), Offset(centerX, yOf(candle.lowE4)), 1.dp.toPx())
            val openY = yOf(candle.openE4)
            val closeY = yOf(candle.closeE4)
            val bodyTop = minOf(openY, closeY)
            val bodyHeight = maxOf(1.dp.toPx(), kotlin.math.abs(closeY - openY))
            drawRect(
                color = tone,
                topLeft = Offset(centerX - bodyWidth / 2f, bodyTop),
                size = Size(bodyWidth, bodyHeight),
            )
        }

        // 均线画在 K 上面：先有蜡烛的实体，再走细线，不然会被影线盖得像断续的
        fun drawMa(values: List<Long?>, tone: Color) {
            if (values.isEmpty()) return
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, value ->
                if (value == null) return@forEachIndexed
                val x = slot * index + slot / 2f
                if (path.isEmpty) path.moveTo(x, yOf(value)) else path.lineTo(x, yOf(value))
            }
            drawPath(path, tone, style = Stroke(width = 1.1.dp.toPx()))
        }
        drawMa(ma5, colors.brand.copy(alpha = 0.85f))
        drawMa(ma20, colors.ink3.copy(alpha = 0.65f))

        val focus = selected?.coerceIn(0, candles.lastIndex)
        if (focus != null) {
            val candle = candles[focus]
            val x = slot * focus + slot / 2f
            val dash = androidx.compose.ui.graphics.PathEffect
                .dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            drawLine(colors.ink3.copy(alpha = 0.55f), Offset(x, top), Offset(x, bottom), 1.dp.toPx(), pathEffect = dash)
            drawLine(
                colors.ink3.copy(alpha = 0.4f),
                Offset(0f, yOf(candle.closeE4)),
                Offset(size.width, yOf(candle.closeE4)),
                1.dp.toPx(),
                pathEffect = dash,
            )
        }
    }
}

/** 首页用的迷你走势：没有坐标轴，只有一条线和一层渐变。 */
@Composable
fun Sparkline(
    values: List<Long>,
    color: Color,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 42.dp,
) {
    val gradient = remember(color) {
        listOf(color.copy(alpha = 0.24f), color.copy(alpha = 0f))
    }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (values.size < 2) return@Canvas
        val low = values.minOrNull() ?: 0L
        val high = values.maxOrNull() ?: 0L
        val span = (high - low).toFloat().coerceAtLeast(1f)
        val top = 3.dp.toPx()
        val bottom = size.height - 3.dp.toPx()
        fun xOf(index: Int): Float = size.width * index / (values.size - 1).toFloat()
        fun yOf(value: Long): Float = bottom - (value - low) / span * (bottom - top)

        val line = androidx.compose.ui.graphics.Path()
        values.forEachIndexed { index, value ->
            if (index == 0) line.moveTo(0f, yOf(value)) else line.lineTo(xOf(index), yOf(value))
        }
        val area = androidx.compose.ui.graphics.Path().apply {
            addPath(line)
            lineTo(size.width, bottom)
            lineTo(0f, bottom)
            close()
        }
        drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(gradient, startY = top, endY = bottom))
        drawPath(
            line,
            color,
            style = Stroke(
                width = 1.6.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            ),
        )
        // 最新的点标出来，一眼能看出曲线停在哪儿
        val lastX = (size.width - 2.dp.toPx()).coerceAtLeast(0f)
        drawCircle(color, radius = 2.4.dp.toPx(), center = Offset(lastX, yOf(values.last())))
    }
}
