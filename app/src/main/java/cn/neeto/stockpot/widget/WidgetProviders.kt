package cn.neeto.stockpot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.widget.RemoteViews
import cn.neeto.stockpot.AppShortcuts
import cn.neeto.stockpot.MainActivity
import cn.neeto.stockpot.PotApp
import cn.neeto.stockpot.R
import cn.neeto.stockpot.ui.theme.PotColors
import cn.neeto.stockpot.ui.theme.PotPalette
import cn.neeto.stockpot.ui.theme.previewColors

/**
 * 小组件的公共壳子：算数据、按当前格子的大小重画、挂一个「点开 App」。
 *
 * 每张卡都只有一张 ImageView——字和图形全是画出来的，见 [WidgetRenderer]。
 */
abstract class LedgerWidgetProvider : AppWidgetProvider() {

    /** 桌面没报尺寸时按这个来（和 info xml 里的 minWidth/minHeight 对齐）。 */
    abstract val fallbackWidthDp: Int
    abstract val fallbackHeightDp: Int

    abstract fun draw(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        snapshot: WidgetSnapshot,
        colors: PotColors,
    ): Bitmap

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { render(context, appWidgetManager, it) }
    }

    /** 桌面改了格子大小：按新的宽高重画，不是把老图拉一下。 */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        render(context, appWidgetManager, appWidgetId)
    }

    private fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
        val options = runCatching { manager.getAppWidgetOptions(widgetId) }.getOrNull()
        val widthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
            ?.takeIf { it > 0 } ?: fallbackWidthDp
        val heightDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            ?.takeIf { it > 0 } ?: fallbackHeightDp
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(1)

        val bitmap = runCatching {
            draw(context, widthPx, heightPx, WidgetStore.snapshot(context), WidgetStore.colors(context))
        }.getOrNull() ?: return

        val views = RemoteViews(context.packageName, R.layout.widget_card)
        views.setImageViewBitmap(R.id.widget_image, bitmap)
        views.setOnClickPendingIntent(R.id.widget_image, openApp(context, widgetId))
        manager.updateAppWidget(widgetId, views)
    }

    private fun openApp(context: Context, widgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = AppShortcuts.OPEN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            widgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** 本月支出：一张卡只看一件事。 */
class MonthWidgetProvider : LedgerWidgetProvider() {
    override val fallbackWidthDp: Int get() = 250
    override val fallbackHeightDp: Int get() = 110

    override fun draw(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        snapshot: WidgetSnapshot,
        colors: PotColors,
    ): Bitmap = WidgetRenderer.month(context, widthPx, heightPx, snapshot, colors)

    companion object {
        fun refresh(context: Context) = WidgetRefresh.broadcast(context, MonthWidgetProvider::class.java)
    }
}

/** 最近流水：几笔账 + 一行小计。 */
class RecentWidgetProvider : LedgerWidgetProvider() {
    override val fallbackWidthDp: Int get() = 250
    override val fallbackHeightDp: Int get() = 180

    override fun draw(
        context: Context,
        widthPx: Int,
        heightPx: Int,
        snapshot: WidgetSnapshot,
        colors: PotColors,
    ): Bitmap = WidgetRenderer.recent(context, widthPx, heightPx, snapshot, colors)

    companion object {
        fun refresh(context: Context) = WidgetRefresh.broadcast(context, RecentWidgetProvider::class.java)
    }
}

/** 账本一变就把桌面上的几张卡重画一遍。 */
object WidgetRefresh {

    fun all(context: Context) {
        MonthWidgetProvider.refresh(context)
        RecentWidgetProvider.refresh(context)
    }

    internal fun broadcast(context: Context, provider: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = runCatching {
            manager.getAppWidgetIds(ComponentName(context, provider))
        }.getOrDefault(IntArray(0))
        if (ids.isEmpty()) return
        val intent = Intent(context, provider).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
        context.sendBroadcast(intent)
    }
}

/** 小组件读数据的地方：账本从进程里的那一份拿，配色跟设置里的主题走。 */
object WidgetStore {

    fun snapshot(context: Context): WidgetSnapshot {
        val app = context.applicationContext as? PotApp ?: return WidgetSnapshot.empty
        val ledger = runCatching { app.store.data.value }.getOrNull() ?: return WidgetSnapshot.empty
        return WidgetSnapshot.of(ledger)
    }

    fun colors(context: Context): PotColors {
        val prefs = context.getSharedPreferences("stockpot_settings", Context.MODE_PRIVATE)
        val palette = PotPalette.of(prefs.getString("palette", "pistachio"))
        val dark = when (prefs.getString("theme", "system")) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        }
        return previewColors(palette, dark)
    }
}
