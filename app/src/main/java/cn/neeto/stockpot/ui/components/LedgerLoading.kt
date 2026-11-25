package cn.neeto.stockpot.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 账本还没读出来时的占位。
 *
 * 冷启动读库要几十毫秒、登录过的设备等第一轮同步要一两秒，这段时间里
 * 「一笔都没有」和「还不知道」看起来一模一样。以前直接摆空状态，用户第一眼
 * 看到的是「账本还是空的，记一笔试试」——以为账丢了。这里用几张呼吸的卡片
 * 顶住版式，不主张任何事。
 *
 * 不写字：写「正在加载」反而像出了什么问题，安静地呼吸一下就够了。
 */
@Composable
fun LedgerLoading(
    modifier: Modifier = Modifier,
    blocks: Int = 3,
) {
    val colors = LocalPotColors.current
    val pulse by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0.42f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loadingPulse",
    )
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "正在读取本机账本" },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(blocks) { index ->
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(if (index == 0) 96.dp else 72.dp)
                    .alpha(pulse)
                    .background(colors.panel, shape),
            )
        }
    }
}
