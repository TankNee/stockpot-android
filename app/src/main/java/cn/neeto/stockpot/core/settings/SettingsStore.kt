package cn.neeto.stockpot.core.settings

import android.content.Context
import cn.neeto.stockpot.core.data.SwapRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** system | light | dark */
    val theme: String = "system",
    /**
     * 「条目标题整理」里打开着的关键词。空集表示一条都没开。
     * 存的是词而不是规则 id：以后加规则、改规则，用户勾过的东西不用跟着迁移。
     */
    val swapWords: Set<String> = SwapRule.defaultWords,
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("stockpot_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettings(
            theme = prefs.getString("theme", "system") ?: "system",
            swapWords = readWords(),
        ),
    )
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _state.value = _state.value.copy(theme = theme)
    }

    fun setSwapWords(words: Set<String>) {
        // 关键词里可能带逗号，所以用 \u0001（SOH）当分隔符，比逗号稳
        prefs.edit().putString("swapWords", words.joinToString(SEP)).apply()
        _state.value = _state.value.copy(swapWords = words)
    }

    private fun readWords(): Set<String> {
        // 没存过（首次安装）用默认全开；存过空串就是用户主动全关了，别又给他打开
        val raw = prefs.getString("swapWords", null) ?: return SwapRule.defaultWords
        return raw.split(SEP).filter { it.isNotBlank() }.toSet()
    }

    private companion object {
        const val SEP = "\u0001"
    }
}
