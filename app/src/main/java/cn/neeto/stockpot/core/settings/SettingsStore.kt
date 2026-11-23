package cn.neeto.stockpot.core.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    /** system | light | dark */
    val theme: String = "system",
)

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("stockpot_settings", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(
        AppSettings(theme = prefs.getString("theme", "system") ?: "system"),
    )
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    fun setTheme(theme: String) {
        prefs.edit().putString("theme", theme).apply()
        _state.value = _state.value.copy(theme = theme)
    }
}
