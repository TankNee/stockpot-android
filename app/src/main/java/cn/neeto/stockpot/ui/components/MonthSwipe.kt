package cn.neeto.stockpot.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 左右滑动换月份。
 *
 * 只认横向手势：竖向的让给里面的列表滚动，横向的让给里面的横向 chip 行——
 * `detectHorizontalDragGestures` 要先过了横向的触摸斜率才会接管，子组件先吃到事件，
 * 所以筛选标签那种横向滚动不会被这层抢走。
 *
 * 手感上分两段：跟手时拖动量打个折（越拖越沉），松手后
 * 过了阈值就「先滑出去、换月份、再从另一边滑进来」，没过就弹回原位。
 * 换月份那一刻调 [onShift]，内容由外面按新的 month 重新算，这一层不碰数据。
 */
@Composable
fun MonthSwipe(
    month: String,
    onShift: (Int) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()

    var offset by remember { mutableFloatStateOf(0f) }
    var job by remember { mutableStateOf<Job?>(null) }

    suspend fun glideTo(target: Float, duration: Int) {
        animate(
            initialValue = offset,
            targetValue = target,
            animationSpec = tween(duration, easing = FastOutSlowInEasing),
        ) { value, _ -> offset = value }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = with(density) { maxWidth.toPx() }
        // 阈值跟着屏宽走：窄屏滑一点点就算数，宽屏得多滑一些才不至于误触
        val minSlop = with(density) { 48.dp.toPx() }
        val threshold = (width * 0.16f).coerceIn(minSlop, width * 0.3f)

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(month, width) {
                    if (width <= 0f) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            job?.cancel()
                        },
                        onHorizontalDrag = { change, drag ->
                            change.consume()
                            offset = (offset + drag * 0.82f)
                                .coerceIn(-width * 0.55f, width * 0.55f)
                        },
                        onDragCancel = {
                            job = scope.launch { glideTo(0f, 160) }
                        },
                        onDragEnd = {
                            val dragged = offset
                            if (abs(dragged) < threshold) {
                                job = scope.launch { glideTo(0f, 180) }
                                return@detectHorizontalDragGestures
                            }
                            // 往左滑 = 看下个月；滑出方向跟手指一致，新内容从反方向进来
                            val dir = if (dragged < 0) 1 else -1
                            job = scope.launch {
                                glideTo(if (dir > 0) -width else width, 140)
                                offset = if (dir > 0) width * 0.32f else -width * 0.32f
                                haptics(Haptic.Tap)
                                onShift(dir)
                                glideTo(0f, 220)
                            }
                        },
                    )
                }
                .graphicsLayer { translationX = offset },
        ) {
            content()
        }
    }
}
