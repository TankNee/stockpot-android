package cn.neeto.stockpot.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * 按钮震动。
 *
 * 只分两档，够用也不会吵：
 * - [Tap] 选择、切换、开弹层这类高频动作，用键盘敲击那种很轻的一下；
 * - [Confirm] 保存、删除、清空这类「落定」的动作，给一下沉的、有结果的。
 *
 * 刻意不加震动的：滚动、输入框、返回、列表里的普通行——高频动作一直震只会让人烦。
 */
enum class Haptic { Tap, Confirm }

@Composable
fun rememberHaptics(): (Haptic) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { kind ->
            view.performHapticFeedback(
                when (kind) {
                    Haptic.Tap -> HapticFeedbackConstants.KEYBOARD_TAP
                    Haptic.Confirm -> HapticFeedbackConstants.LONG_PRESS
                },
            )
        }
    }
}
