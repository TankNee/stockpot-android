package cn.neeto.stockpot.core.settings

import android.content.Context
import cn.neeto.stockpot.BuildConfig
import cn.neeto.stockpot.core.data.SwapRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** system | light | dark */
    val theme: String = "system",
    /** 纸样（配色），见 PotPalette；pistachio | butter | peach | lemon-ash | mint */
    val palette: String = "pistachio",
    /** 打开条目编辑页的动画：fold 折叠 | zoom 缩放 | slide 推入 */
    val editorAnim: String = "fold",
    /**
     * 「条目标题整理」里打开着的关键词。空集表示一条都没开。
     * 存的是词而不是规则 id：以后加规则、改规则，用户勾过的东西不用跟着迁移。
     */
    val swapWords: Set<String> = SwapRule.defaultWords,
    /** 同步服务器的地址；不登录的话这个值用不上，但换服务器时得能改 */
    val serverUrl: String = DEFAULT_SERVER,
    /** 桌面图标样式，见 [AppIcon.styles] */
    val iconStyle: String = AppIcon.DEFAULT_STYLE,
    /** 给图形加圆形遮罩：图形缩进圆里，被系统主题强制圆形裁剪时不会切到边 */
    val iconMask: Boolean = AppIcon.DEFAULT_MASK,
) {
    companion object {
        val DEFAULT_SERVER: String = BuildConfig.DEFAULT_SERVER
    }
}

class SettingsStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("stockpot_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettings(
            theme = prefs.getString("theme", "system") ?: "system",
            palette = prefs.getString("palette", "pistachio") ?: "pistachio",
            editorAnim = prefs.getString("editorAnim", "fold") ?: "fold",
            swapWords = readWords(),
            serverUrl = readServer(),
            iconStyle = prefs.getString("iconStyle", AppIcon.DEFAULT_STYLE) ?: AppIcon.DEFAULT_STYLE,
            iconMask = prefs.getBoolean("iconMask", AppIcon.DEFAULT_MASK),
        ),
    )
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _state.value = _state.value.copy(theme = theme)
    }

    /** 换一套纸（配色）。深浅是另外一档，不在这儿动。 */
    fun setPalette(id: String) {
        prefs.edit().putString("palette", id).apply()
        _state.value = _state.value.copy(palette = id)
    }

    /** 换打开条目的动画。只影响观感，下次打开条目就生效。 */
    fun setEditorAnim(id: String) {
        prefs.edit().putString("editorAnim", id).apply()
        _state.value = _state.value.copy(editorAnim = id)
    }

    fun setSwapWords(words: Set<String>) {
        // 关键词里可能带逗号，所以用 \u0001（SOH）当分隔符，比逗号稳
        prefs.edit().putString("swapWords", words.joinToString(SEP)).apply()
        _state.value = _state.value.copy(swapWords = words)
    }

    fun setServerUrl(url: String) {
        val normalized = url.trim().trimEnd('/').ifBlank { AppSettings.DEFAULT_SERVER }
        prefs.edit().putString("serverUrl", normalized).apply()
        _state.value = _state.value.copy(serverUrl = normalized)
    }

    /** 换图标样式。返回 false 表示系统拒绝（个别 ROM 会拦），调用方负责提示。 */
    fun setIconStyle(id: String): Boolean {
        val next = AppIcon.style(id).id
        prefs.edit().putString("iconStyle", next).apply()
        _state.value = _state.value.copy(iconStyle = next)
        return AppIcon.apply(appContext, next, _state.value.iconMask)
    }

    /** 圆形遮罩开关。 */
    fun setIconMask(on: Boolean): Boolean {
        prefs.edit().putBoolean("iconMask", on).apply()
        _state.value = _state.value.copy(iconMask = on)
        return AppIcon.apply(appContext, _state.value.iconStyle, on)
    }

    private fun readWords(): Set<String> {
        // 没存过（首次安装）用默认全开；存过空串就是用户主动全关了，别又给他打开
        val raw = prefs.getString("swapWords", null) ?: return SwapRule.defaultWords
        return raw.split(SEP).filter { it.isNotBlank() }.toSet()
    }

    private fun readServer(): String =
        prefs.getString("serverUrl", null)?.trim()?.trimEnd('/')?.ifBlank { null }
            ?: AppSettings.DEFAULT_SERVER

    val serverUrl: String get() = _state.value.serverUrl

    private companion object {
        const val SEP = "\u0001"
    }
}
