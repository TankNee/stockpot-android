package cn.neeto.stockpot.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import cn.neeto.stockpot.R
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.ui.theme.PotColors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 小组件的画。
 *
 * 没用 RemoteViews 那套 TextView 拼——那样字距、字重、基线都由系统说了算，
 * 大数字一放上去就是「把 App 缩小一遍」。这里直接画在一张图上：
 *
 * - 金额用 Barlow（App 里金额用的同一支），整数大、小数小，中间不断开；
 * - 中文标签逐字加字距（`11 月 支 出` 那种呼吸感），系统字本来没这个参数；
 * - 迷你柱状图、分隔线、分类色点都按格子算，尺寸变了重画，不是拉伸一张图。
 *
 * 尺寸大了字就跟着长：4×2 是三十来号，拉到 4×4 能到六十号以上。
 */
object WidgetRenderer {

    /** 一张图最多这么多像素。再大也只会被桌面的格子浪费掉，还占内存。 */
    private const val MAX_PIXELS = 900_000

    /* ------------------------------ 本月支出 ------------------------------ */

    fun month(context: Context, widthPx: Int, heightPx: Int, snap: WidgetSnapshot, colors: PotColors): Bitmap {
        val d = context.resources.displayMetrics.density
        val card = openCard(widthPx, heightPx, d, colors)
        val canvas = card.canvas
        val pad = 16f * d
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()

        val ink = colors.ink.toArgb()
        val ink2 = colors.ink2.toArgb()
        val ink3 = colors.ink3.toArgb()

        val labelPaint = textPaint(sans(), 10.5f * d, ink3)
        val smallPaint = textPaint(sans(), 10.5f * d, ink3)
        val symbolPaint = textPaint(sans(), 13f * d, ink3)
        val number = numberTypeface(context)
        val bigPaint = textPaint(number, 40f * d, ink)
        val fracPaint = textPaint(number, 20f * d, ink2)

        // 标签行：左边「11 月支出」，右上角一根迷你柱
        val labelSize = labelPaint.textSize
        val labelY = pad + labelSize * 0.88f
        drawTracked(canvas, "${snap.monthLabel}支出", pad, labelY, labelPaint, 1.1f * d)

        val barW = 54f * d
        val barH = 18f * d
        drawBars(canvas, w - pad - barW, pad + (labelSize - barH) / 2f, barW, barH, snap.weekBars, colors, d)

        // 大数字：整数 + 小数共用一个基线
        val bottomRowH = smallPaint.textSize * 1.6f
        val textTop = labelY + 7f * d
        val textBottom = h - pad - bottomRowH
        val availH = (textBottom - textTop).coerceAtLeast(10f * d)
        val availW = w - pad * 2 - 16f * d

        val money = Fmt.cents(snap.expense)
        val intPart = money.substringBeforeLast('.')
        val fracPart = "." + money.substringAfterLast('.')

        var size = minOf(availH * 0.92f, 72f * d)
        bigPaint.textSize = size
        fracPaint.textSize = size * 0.5f
        // 太宽就一小步一小步往回收，直到这一行塞得下
        while (size > 16f * d &&
            bigPaint.measureText(intPart) + fracPaint.measureText(fracPart) + 6f * d > availW
        ) {
            size -= 1f * d
            bigPaint.textSize = size
            fracPaint.textSize = size * 0.5f
        }

        val fm = bigPaint.fontMetrics
        val baseline = textTop + (availH - (fm.descent - fm.ascent)) / 2f - fm.ascent
        canvas.drawText("¥", pad, baseline - size * 0.08f, symbolPaint)
        var x = pad + symbolPaint.measureText("¥") + 3f * d
        canvas.drawText(intPart, x, baseline, bigPaint)
        x += bigPaint.measureText(intPart)
        canvas.drawText(fracPart, x, baseline, fracPaint)

        // 底行：收入、结余。放不下就只留收入，别硬挤成两行
        val incomeText = "收入 " + Fmt.cents(snap.income)
        val full = incomeText + " · 结余 " + signed(snap.net)
        val bottomY = h - pad - smallPaint.textSize * 0.34f
        val shown = if (smallPaint.measureText(full) <= w - pad * 2) full else incomeText
        canvas.drawText(shown, pad, bottomY, smallPaint)

        return card.bitmap
    }

    /* ------------------------------ 最近流水 ------------------------------ */

    fun recent(context: Context, widthPx: Int, heightPx: Int, snap: WidgetSnapshot, colors: PotColors): Bitmap {
        val d = context.resources.displayMetrics.density
        val card = openCard(widthPx, heightPx, d, colors)
        val canvas = card.canvas
        val pad = 18f * d
        val w = widthPx.toFloat()
        val h = heightPx.toFloat()

        val ink = colors.ink.toArgb()
        val ink2 = colors.ink2.toArgb()
        val ink3 = colors.ink3.toArgb()

        val labelPaint = textPaint(sans(), 11f * d, ink3)
        val titlePaint = textPaint(sans(), 13f * d, ink)
        val amountPaint = textPaint(numberTypeface(context), 13.5f * d, ink)
        val numberPaint = textPaint(numberTypeface(context), 11.5f * d, ink2)

        val headerY = pad + labelPaint.textSize * 0.88f
        drawTracked(canvas, "最近", pad, headerY, labelPaint, 1.1f * d)
        canvas.drawText(
            snap.monthLabel,
            w - pad - labelPaint.measureText(snap.monthLabel),
            headerY,
            labelPaint,
        )

        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.line.toArgb()
            strokeWidth = max(1f, 0.7f * d)
        }
        val topDividerY = headerY + 8f * d
        canvas.drawLine(pad, topDividerY, w - pad, topDividerY, divider)

        val footerSize = labelPaint.textSize
        val footerY = h - pad - footerSize * 0.34f
        val bottomDividerY = footerY - footerSize * 1.6f

        val rowH = 30f * d
        val rowsTop = topDividerY + 6f * d
        val rowsBottom = bottomDividerY - 6f * d
        val fit = ((rowsBottom - rowsTop) / rowH).toInt().coerceIn(1, 6)
        val rows = snap.recent.take(fit)

        rows.forEachIndexed { index, row ->
            val centerY = rowsTop + rowH * index + rowH / 2f
            // 分类色点：比一整块色块安静，又能看出是哪个类目
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = categoryColor(row.category, colors)
            }
            canvas.drawRoundRect(
                RectF(pad, centerY - 6f * d, pad + 3f * d, centerY + 6f * d),
                1.5f * d,
                1.5f * d,
                dotPaint,
            )

            amountPaint.color = when (row.kind) {
                Kind.EXPENSE -> colors.expense.toArgb()
                Kind.INCOME -> colors.income.toArgb()
                else -> ink2
            }
            val amountText = signed(row.amount)
            val amountW = amountPaint.measureText(amountText)
            val textLeft = pad + 11f * d
            val textMax = w - pad - amountW - 10f * d - textLeft
            val title = TextUtils.ellipsize(
                row.title,
                TextPaint(titlePaint),
                textMax.coerceAtLeast(8f * d),
                TextUtils.TruncateAt.END,
            )
            val textBaseline = centerY + titlePaint.textSize * 0.35f
            canvas.drawText(title.toString(), textLeft, textBaseline, titlePaint)
            canvas.drawText(amountText, w - pad - amountW, centerY + amountPaint.textSize * 0.35f, amountPaint)
        }

        if (rows.isEmpty()) {
            val emptyPaint = textPaint(sans(), 12f * d, ink3)
            canvas.drawText("还没有记账", pad, rowsTop + rowH * 0.7f, emptyPaint)
        }

        canvas.drawLine(pad, bottomDividerY, w - pad, bottomDividerY, divider)
        val footer = buildString {
            append("本月 ").append(Fmt.cents(snap.expense))
            if (snap.activeDays > 0) append(" · ").append(snap.activeDays).append(" 天有记账")
        }
        canvas.drawText(footer, pad, footerY, numberPaint)

        return card.bitmap
    }

    /* ------------------------------ 画布 ------------------------------ */

    private class Card(val bitmap: Bitmap, val canvas: Canvas)

    /**
     * 开一张纸：圆角底 + 一圈极细的边（桌面壁纸深浅不一，没边会糊上去）。
     * 像素太多就整体缩一点——4×4 在高密度屏上能到两三兆，传进 RemoteViews 太浪费。
     */
    private fun openCard(widthPx: Int, heightPx: Int, density: Float, colors: PotColors): Card {
        val total = widthPx.toLong() * heightPx
        val scale = if (total > MAX_PIXELS) sqrt(MAX_PIXELS.toDouble() / total).toFloat() else 1f
        val w = (widthPx * scale).roundToInt().coerceAtLeast(1)
        val h = (heightPx * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        // 先缩，再按原始像素坐标画，后面所有尺寸都写 dp 不用管缩放
        canvas.scale(scale, scale)

        val radius = 22f * density
        val rect = RectF(0.5f, 0.5f, widthPx - 0.5f, heightPx - 0.5f)
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.panel.toArgb()
        })
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = colors.line.toArgb()
        })
        return Card(bitmap, canvas)
    }

    /* ------------------------------ 小工具 ------------------------------ */

    private fun sans(): Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    private fun textPaint(typeface: Typeface, size: Float, color: Int): TextPaint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
        }

    private fun numberTypeface(context: Context): Typeface =
        ResourcesCompat.getFont(context, R.font.barlow_semibold) ?: Typeface.DEFAULT_BOLD

    /** 逐字画，字间再加 [tracking]：Canvas 没有 letterSpacing 这个开关。 */
    private fun drawTracked(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, tracking: Float) {
        var cursor = x
        text.forEach { ch ->
            val piece = ch.toString()
            canvas.drawText(piece, cursor, y, paint)
            cursor += paint.measureText(piece) + tracking
        }
    }

    /** 近七天的柱子：今天那根用品牌色，其余压淡。 */
    private fun drawBars(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        values: List<Long>,
        colors: PotColors,
        density: Float,
    ) {
        if (values.isEmpty()) return
        val gap = 3f * density
        val barW = ((width - gap * (values.size - 1)) / values.size).coerceAtLeast(2f * density)
        val peak = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        values.forEachIndexed { index, value ->
            val ratio = value.toFloat() / peak
            val barH = max(2.5f * density, (height - 2.5f * density) * ratio)
            val x = left + index * (barW + gap)
            val base = if (index == values.lastIndex) colors.brand.toArgb() else colors.ink3.toArgb()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (index == values.lastIndex) base else ColorUtils.setAlphaComponent(base, 58)
            }
            canvas.drawRoundRect(
                RectF(x, top + height - barH, x + barW, top + height),
                barW / 2f,
                barW / 2f,
                paint,
            )
        }
    }

    /** 分类色：和 App 里那个头像圆同一组色带，看着是同一套东西。 */
    private fun categoryColor(category: String, colors: PotColors): Int {
        val palette = colors.chartPalette
        if (palette.isEmpty()) return colors.ink3.toArgb()
        val key = category.substringAfterLast('/').ifEmpty { category }
        val index = (key.hashCode().let { if (it < 0) -it else it }) % palette.size
        return palette[index].toArgb()
    }

    /** `384250` → `3,842.50`；负数前面一横，正数带「+」。 */
    private fun signed(cents: Long): String {
        val sign = when {
            cents < 0 -> "−"
            cents > 0 -> "+"
            else -> ""
        }
        return sign + Fmt.cents(abs(cents))
    }
}
