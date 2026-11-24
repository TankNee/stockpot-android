package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 列表行在屏幕上的位置登记处：点开编辑页时，动画从那一行的位置长出来。
 *
 * 存的是 [LayoutCoordinates] 而不是现成的 [Rect]：列表一滚，每一行的位置都在变，
 * 每帧算一遍再存等于白扔对象；等真的要用的时候（手指按下的那一刻）再取一次，
 * 也不会拿到滚走之前的旧位置。
 */
class RowAnchors {
    private val coords = mutableMapOf<String, LayoutCoordinates>()

    fun put(key: String, value: LayoutCoordinates) {
        coords[key] = value
    }

    /** 取不到（还没布局、已经离开屏幕）就返回 null，调用方自己挑一个退路。 */
    fun rectOf(key: String): Rect? = coords[key]?.takeIf { it.isAttached }?.boundsInWindow()
}

/** 谁都能查这张表，但只有列表行会往里写。 */
val LocalRowAnchors = staticCompositionLocalOf<RowAnchors?> { null }

/** 让一行把自己在屏幕上的位置登记进去。 */
fun Modifier.rowAnchor(key: String, anchors: RowAnchors): Modifier =
    onGloballyPositioned { anchors.put(key, it) }

/**
 * iOS 那种「从一个小块长到整页」的展开。
 *
 * 内容按整页布局（字号、间距都是最终尺寸），展开只靠 [graphicsLayer] 的缩放来做——
 * 缩放原点钉在点中的那一行上，于是视觉上是那一行从中间往两边撑开，而不是整页从屏幕边缘滑进来。
 * 缩回去时同一个原点、同一段圆角，收完正好落回原来那一行里。
 *
 * 几个刻意的地方：
 * - `progress` 用 lambda 交进来。它每帧都在变，直接传值会让整个编辑页每帧重组一次；
 *   传 lambda 的话这个读取落在绘制阶段，只更新图层。
 * - 自己的位置 `bounds` 也在图层里读。放在外面 `remember(bounds)` 的话，第一帧拿到的是
 *   空矩形、按「不缩放」画一次，会闪一下全屏。
 * - 起点宽度夹在 0.35–0.72 之间：流水行横向铺满，纯按比例只能缩到 0.9 出头，看不出在动；
 *   0.72 是上限（再大就没动作了），0.35 是下限（加号那种小方块起步，再小整页的字就糊了）。
 */
@Composable
fun ZoomReveal(
    progress: () -> Float,
    origin: Rect?,
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val cornerPx = with(LocalDensity.current) { corner.toPx() }

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = progress()
                    val area = bounds
                    // 还没量到自己（或者没有来源行）就当成整页，不做缩放
                    val src = origin?.takeIf { area.width > 1f && area.height > 1f }

                    val startScale = if (src == null) 1f else (src.width / area.width).coerceIn(0.35f, 0.72f)
                    val scale = startScale + (1f - startScale) * t
                    scaleX = scale
                    scaleY = scale
                    // 头一小段跟着淡入淡出：从 0.72 倍「啪」地出现会像闪了一下，
                    // 淡一下就把这一下盖掉了；收尾同理，淡掉之后底下那一行正好露出来。
                    // 只占 1/10，30 毫秒左右，长了自己就成了一段空场。
                    alpha = (t / 0.1f).coerceIn(0f, 1f)

                    if (src != null) {
                        val x = ((src.center.x - area.left) / area.width).coerceIn(0f, 1f)
                        val y = ((src.center.y - area.top) / area.height).coerceIn(0f, 1f)
                        // 贴顶／贴底的条目如果严丝合缝钉在原点上，展开时会像从屏幕边缘挤出来；
                        // 往屏幕中线拉回三成，位置还在那一行上，展开的方向自然些。
                        transformOrigin = TransformOrigin(x, y + (0.5f - y) * 0.28f)
                    } else {
                        transformOrigin = TransformOrigin.Center
                    }

                    // 圆角跟着一起收：铺满时是直角，缩回去的那一瞬像一张卡片插回列表
                    val round = cornerPx * (1f - t)
                    clip = round > 0.5f
                    shape = if (round > 0.5f) {
                        RoundedCornerShape(CornerSize(round))
                    } else {
                        RectangleShape
                    }
                },
        ) {
            content()
        }
    }
}
