package cn.neeto.stockpot.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SwipeLeft
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlin.math.min

/**
 * 打开条目编辑页的四种过场。用户可以在「设置 → 显示」里换，存在 AppSettings.editorAnim。
 *
 * 四种都不动编辑页自己的布局：内容永远按整页尺寸排版，动的只是外层怎么把它端上来。
 */
enum class EditorAnim(
    val id: String,
    val label: String,
    val hint: String,
    val icon: ImageVector,
) {
    /** 从那一行上下摊开，同时整体微微放大：纸从一行那么窄长到满屏。 */
    Fold("fold", "折叠", "从这一行上下摊开，边摊边长大", Icons.Outlined.UnfoldMore),

    /** 整页按比例放大，起点钉在那一行上。iOS 应用打开那种。 */
    Zoom("zoom", "缩放", "整页从这一行放大到满屏", Icons.Outlined.ZoomOutMap),

    /** 从右边推进来，底下的页面退到后面。 */
    Slide("slide", "侧滑", "整页从右侧滑进来", Icons.Outlined.SwipeLeft),

    /** 从屏幕底下整页推上来，顶上带一圈圆角，原来那个底部弹层的手感。 */
    Lift("lift", "上推", "整页从底下一路推上来", Icons.Outlined.ArrowUpward),
    ;

    companion object {
        fun of(id: String?): EditorAnim = entries.firstOrNull { it.id == id } ?: Fold
    }
}

@Composable
fun EditorReveal(
    anim: EditorAnim,
    progress: () -> Float,
    origin: Rect?,
    modifier: Modifier = Modifier,
    corner: Dp = 26.dp,
    content: @Composable () -> Unit,
) {
    when (anim) {
        EditorAnim.Fold -> FoldReveal(progress, origin, modifier, corner, content)
        EditorAnim.Zoom -> ZoomReveal(progress, origin, modifier, corner, content)
        EditorAnim.Slide -> SlideReveal(progress, modifier, content)
        EditorAnim.Lift -> LiftReveal(progress, modifier, corner, content)
    }
}

/**
 * 折叠 + 缩放：纸从那一行那么窄、那么薄的一条长到满屏，边摊开边长大。
 *
 * 两件事叠在一起：
 * - 裁剪区域（clip）负责「从这一行上下打开」：上下两条边从行中间那条缝往两头走，
 *   这个比缩放快得多，所以前半段能明确看出是从这一行里拉出来的；
 * - 内容再乘一个 0.74 → 1 的缩放，负责「纸本身在长大」：不做这一步，一开始就是
 *   一条横贯全屏的白条，看着像扫描线，不像一张卡片。
 *
 * 左右两边特意跟着缩放走（左 = (1-s)×宽/2），不写死成全宽：
 * 纸的宽度永远等于内容缩放后的宽度，所以既不会露出两条白边，也不会把字裁掉。
 * 左右不参与「比缩放快」的那套插值 —— 横向的展开感交给缩放本身。
 *
 * 几个刻意的处理：
 * - 起点的那条缝取半行高而不是整行：整行会一瞬间把条目盖住，像"换"不像"开"。
 * - 纵向的摊开和缩放同时跑（外层是 spring，前段本来就快，不用再叠缓动）。
 * - 内容的上边始终贴在纸的上沿（`push`）：缩放把内容顶边顶到 rowCenter×(1-s)，
 *   比纸的开场位置高一大截，不按下去就是「半截标题」。
 * - 内容比纸晚一点出现（0.55 之后淡入）：纸先亮出来、字再浮上来。
 * - 纸的边缘画一道 1dp 细线，只在还没铺满时出现：没有它，浅色纸面在浅色背景上看不出边界。
 * - 圆角随进度收到 0，且夹在「当前纸高/纸宽的一半」以内，否则纸很扁时圆角会啃穿。
 * - `progress` 用 lambda 交进来，读状态发生在绘制阶段，摊开的每一帧不会重组整页。
 */
@Composable
private fun FoldReveal(
    progress: () -> Float,
    origin: Rect?,
    modifier: Modifier,
    corner: Dp,
    content: @Composable () -> Unit,
) {
    val colors = LocalPotColors.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current
    val cornerPx = with(density) { corner.toPx() }
    val hairlinePx = with(density) { 1.dp.toPx() }
    val paper = colors.panel
    val edge = colors.line

    Box(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { bounds = it.boundsInWindow() },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    val t = progress()
                    val area = bounds
                    // 还没量到自己：按整页画，别在测量完成前闪一帧
                    if (area.width <= 1f || area.height <= 1f) {
                        drawContent()
                        return@drawWithContent
                    }

                    // 摊开到底就按普通整页画：省掉每帧建 path 和裁一次的开销，
                    // 编辑页开着的时候是一直挂着的，这点成本值得省。
                    if (t >= 0.999f) {
                        drawContent()
                        return@drawWithContent
                    }

                    // 起点 = 那一行中间一条半行高的缝（上下还露着条目本身，看着像从这一行里张开），
                    // 终点 = 整页。
                    val rowTop = origin?.let { it.top - area.top }
                    val rowBottom = origin?.let { it.bottom - area.top }
                    val rowCenter: Float
                    var startTop: Float
                    var startBottom: Float
                    if (rowTop != null && rowBottom != null) {
                        val mid = (rowTop + rowBottom) / 2f
                        val half = (rowBottom - rowTop) * 0.25f
                        rowCenter = mid
                        startTop = mid - half
                        startBottom = mid + half
                    } else {
                        rowCenter = area.height / 2f
                        startTop = area.height * 0.24f
                        startBottom = area.height * 0.32f
                    }
                    if (startBottom - startTop < 8f) {
                        // 行矮到只剩几像素（比如被系统字体缩得特别小），保底留一条能看见的缝
                        val mid = (startTop + startBottom) / 2f
                        startTop = mid - 4f
                        startBottom = mid + 4f
                    }
                    // 纵向摊开就是线性的 t：外层本来就是 spring（前段自己就快），
                    // 这里再套一层缓动会冲过头，四十个点就铺满了。
                    val top = startTop + (0f - startTop) * t
                    val bottom = startBottom + (area.height - startBottom) * t
                    val height = bottom - top
                    if (height <= 0.5f) return@drawWithContent

                    // 缩放：起点跟行宽走（行本来就接近满宽，所以再乘 0.82 才看得出来窄了一截），
                    // 屏幕宽窄不同也能落在合适的区间里。
                    val rowWidth = origin?.width ?: 0f
                    val s0 = if (rowWidth > 1f) {
                        (rowWidth / area.width * 0.82f).coerceIn(0.58f, 0.74f)
                    } else {
                        0.7f
                    }
                    val s = s0 + (1f - s0) * t
                    // 左右边界跟着缩放走：纸的宽度 = 内容缩放后的宽度，不用写死全宽
                    val sideGap = (1f - s) * size.width / 2f
                    val width = size.width - sideGap * 2f

                    // 圆角要撑到很后面：只要纸还没铺满屏幕，它就是个圆角矩形。
                    // 跟着 t 线性收到 0 的话，八成进度时只剩两三 dp，看着就是尖角了。
                    // 最后 25% 才收干净 —— 铺满那一刻圆角本来也该交给屏幕自己处理。
                    val round = ((1f - t) / 0.25f).coerceIn(0f, 1f)
                    val radius = min(
                        min(cornerPx * round, height / 2f),
                        width / 2f,
                    ).coerceAtLeast(0f)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = sideGap,
                                top = top,
                                right = size.width - sideGap,
                                bottom = bottom,
                                radiusX = radius,
                                radiusY = radius,
                            ),
                        )
                    }
                    clipPath(path) {
                        drawRect(paper)
                        // 内容的上边不许跑到纸边外头：缩放到 s 时内容的上边在 rowCenter×(1-s)，
                        // 比纸的开场位置高得多，直接画就是「半截标题」。这里把内容整体按下、
                        // 顶边正好落在纸的上沿，纸往上让多少它就跟多少——看着像从纸口里被拉出来，
                        // 也不会出现字压在纸外的错觉。
                        val contentTop = rowCenter * (1f - s)
                        val push = (top - contentTop).coerceAtLeast(0f)
                        translate(top = push) {
                            scale(scaleX = s, scaleY = s, pivot = Offset(size.width / 2f, rowCenter)) {
                                this@drawWithContent.drawContent()
                            }
                        }
                    }

                    // 轮廓线只在还没铺满时画：全屏之后它就是屏幕边上一条多余的线
                    if (radius > 0.5f) {
                        drawRoundRect(
                            color = edge,
                            topLeft = Offset(sideGap, top),
                            size = Size(width, height),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(hairlinePx),
                        )
                    }
                },
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = progress()
                        // 纸先摊开、字后落定：纸开到一半多才开始浮上来，
                        // 之前看到的是一张空白卡片在长，不会有半截标题被裁在纸边上的错觉。
                        // 收起时反过来，字先退干净，纸再缩回去。
                        alpha = ((t - 0.55f) / 0.3f).coerceIn(0f, 1f)
                    },
            ) {
                content()
            }
        }
    }
}

/**
 * 推入：整页从右边滑进来。位移走一半屏宽就够读成「从右边来」，
 * 再配上开头 30% 的淡入，收尾时不会有一整块硬边扫过屏幕。
 */
@Composable
private fun SlideReveal(
    progress: () -> Float,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = progress()
                translationX = (1f - t) * size.width * 0.5f
                alpha = (t / 0.3f).coerceIn(0f, 1f)
            },
    ) {
        content()
    }
}

/**
 * 上推：整页从屏幕底下推上来，顶上带一圈圆角 —— 就是之前那个底部弹层的手感。
 *
 * 位移用整页高度：起点完全在屏幕外，退场也一路退到看不见为止。
 * 圆角只在最后 10% 收掉：推到底之后编辑页是整页的，顶上留一圈圆角会在状态栏边上割出个缺口；
 * 反过来，收起时它先圆起来，看着像弹层在往下缩。
 */
@Composable
private fun LiftReveal(
    progress: () -> Float,
    modifier: Modifier,
    corner: Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val t = progress()
                translationY = (1f - t) * size.height
                val r = corner * ((1f - t) / 0.1f).coerceIn(0f, 1f)
                shape = RoundedCornerShape(topStart = r, topEnd = r)
                clip = r > 0.5.dp
            },
    ) {
        content()
    }
}
