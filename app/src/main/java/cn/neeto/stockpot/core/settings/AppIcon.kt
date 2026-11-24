package cn.neeto.stockpot.core.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import cn.neeto.stockpot.R

/**
 * 应用图标的样式与切换。
 *
 * 安卓没法在运行时替换自己的图标，能做的是「准备一堆图标，只让其中一个显示」：
 * manifest 里每种样式对应一个 `activity-alias`（都指向 MainActivity，只有一个 enabled），
 * 切换就是开关这些别名的状态。所以这里的 id 必须和 manifest 与 mipmap 文件名一一对上。
 */
object AppIcon {

    data class Style(
        val id: String,
        val label: String,
        /** 底色，ARGB。界面上画预览也要用，所以放成 Long 而不是 Color，避免 core 层依赖 Compose */
        val background: Long,
        @DrawableRes val foreground: Int,
        @DrawableRes val foregroundMasked: Int,
        /** activity-alias 的类名后缀（不含包名） */
        val alias: String,
        val aliasMask: String,
    )

    val styles: List<Style> = listOf(
        Style(
            id = "cinnabar",
            label = "朱砂",
            background = 0xFFE1543AL,
            foreground = R.drawable.ic_fg_cinnabar,
            foregroundMasked = R.drawable.ic_fg_cinnabar_mask,
            alias = "LauncherCinnabar",
            aliasMask = "LauncherCinnabarMask",
        ),
        Style(
            id = "ink",
            label = "墨",
            background = 0xFF1C1A17L,
            foreground = R.drawable.ic_fg_ink,
            foregroundMasked = R.drawable.ic_fg_ink_mask,
            alias = "LauncherInk",
            aliasMask = "LauncherInkMask",
        ),
        Style(
            id = "cream",
            label = "米白",
            background = 0xFFF2E9DAL,
            foreground = R.drawable.ic_fg_cream,
            foregroundMasked = R.drawable.ic_fg_cream_mask,
            alias = "LauncherCream",
            aliasMask = "LauncherCreamMask",
        ),
        Style(
            id = "pine",
            label = "松绿",
            background = 0xFF1F6B57L,
            foreground = R.drawable.ic_fg_pine,
            foregroundMasked = R.drawable.ic_fg_pine_mask,
            alias = "LauncherPine",
            aliasMask = "LauncherPineMask",
        ),
    )

    const val DEFAULT_STYLE = "cinnabar"
    const val DEFAULT_MASK = false

    fun style(id: String): Style = styles.firstOrNull { it.id == id } ?: styles.first()

    fun aliasOf(styleId: String, masked: Boolean): String {
        val s = style(styleId)
        return if (masked) s.aliasMask else s.alias
    }

    /**
     * 把桌面图标换成指定样式。先把目标启用、再关掉其它，顺序不能反——
     * 反过来的话中间会有一瞬间一个图标都没有，桌面会以为应用被卸载了。
     *
     * DONT_KILL_APP：禁用别名不在应用进程上开刀，用户切完还能接着用。
     * 返回 false 表示系统不让改（个别 ROM 会拦），界面据此提示。
     */
    fun apply(context: Context, styleId: String, masked: Boolean): Boolean {
        val pm = context.packageManager
        val pkg = context.packageName
        val target = ComponentName(pkg, "$pkg.${aliasOf(styleId, masked)}")

        runCatching {
            pm.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }.onFailure { return false }

        for (style in styles) {
            for (name in listOf(style.alias, style.aliasMask)) {
                val component = ComponentName(pkg, "$pkg.$name")
                if (component == target) continue
                runCatching {
                    pm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP,
                    )
                }
            }
        }
        return true
    }

    /**
     * 当前实际生效的样式（styleId to 是否遮罩），没有就用 null。
     *
     * 注意 [PackageManager.getComponentEnabledSetting] 的语义：manifest 里写 enabled="true"
     * 的那个别名报的是 DEFAULT(0) 而不是 ENABLED(1)，所以 0 和 1 都算开着。
     */
    fun current(context: Context): Pair<String, Boolean>? {
        val pm = context.packageManager
        val pkg = context.packageName
        for (style in styles) {
            for ((name, masked) in listOf(style.alias to false, style.aliasMask to true)) {
                val state = runCatching {
                    pm.getComponentEnabledSetting(ComponentName(pkg, "$pkg.$name"))
                }.getOrNull() ?: continue
                if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                ) {
                    return style.id to masked
                }
            }
        }
        return null
    }

    /** 让系统里生效的图标和设置对齐；已经一致就什么都不做，免得每次启动都惊动桌面。 */
    fun reconcile(context: Context, styleId: String, masked: Boolean) {
        val active = current(context)
        if (active?.first == styleId && active.second == masked) return
        apply(context, styleId, masked)
    }
}
