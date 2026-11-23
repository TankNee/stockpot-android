package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

@Composable
fun PotCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val colors = LocalPotColors.current
    val shape = RoundedCornerShape(18.dp)
    // 不点的时候只画圆角背景，不做 clip：clip 会为整张卡建一个图层，列表滑动时每帧都要还这笔账
    val base = modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clip(shape) else Modifier)
        .background(colors.panel, shape)
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
        .padding(padding)
    Column(modifier = base, content = content)
}

/**
 * 底部弹层的抓手。
 *
 * 弹层内容长到顶满屏幕时，标题会顶到状态栏上，所以这里自己把状态栏高度让出来，
 * 同时让面板的底色继续铺到屏幕最上沿（配合 contentWindowInsets = 0）。
 */
@Composable
fun SheetGrabber() {
    val colors = LocalPotColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(colors.lineStrong),
        )
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.ink3,
            letterSpacing = 0.6.sp,
        )
        trailing?.invoke()
    }
}

/** 只有两个到四个选项的胶囊切换器。 */
@Composable
fun Segmented(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val active = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) colors.panel else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) colors.ink else colors.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun Chip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
) {
    val colors = LocalPotColors.current
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = CircleShape,
        color = if (active) colors.brandTint else colors.panel,
        border = BorderStroke(1.dp, if (active) Color.Transparent else colors.line),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (dotColor != null) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (active) colors.brandInk else colors.ink2,
                fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

/** 分类头像：取名字最后一个字，颜色由名字决定，稳定且不用维护图标表。 */
@Composable
fun CategoryAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Int = 38,
) {
    val colors = LocalPotColors.current
    val palette = colors.chartPalette
    val key = name.substringAfterLast('/').ifEmpty { name }
    val color = palette[(key.hashCode().let { if (it < 0) -it else it }) % palette.size]
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 2.6).dp))
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = key.take(1),
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/** 金额。支出红、收入绿、转账灰，符号永远在。 */
@Composable
fun Amount(
    cents: Long,
    kind: String,
    modifier: Modifier = Modifier,
    signed: Boolean = true,
    fontSize: Int = 16,
) {
    val colors = LocalPotColors.current
    val color = when (kind) {
        Kind.EXPENSE -> colors.expense
        Kind.INCOME -> colors.income
        else -> colors.ink3
    }
    val sign = when {
        kind == Kind.EXPENSE -> "-"
        kind == Kind.INCOME -> if (signed) "+" else ""
        else -> ""
    }
    Text(
        text = "$sign${Fmt.yuan(kotlin.math.abs(cents))}",
        modifier = modifier,
        color = color,
        style = MoneyStyle.copy(fontSize = fontSize.sp),
        maxLines = 1,
    )
}

@Composable
fun MonthStepper(
    month: String,
    onShift: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onShift(-1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上个月", tint = colors.ink2, modifier = Modifier.size(19.dp))
        }
        Text(
            text = Fmt.monthLabel(month),
            style = MaterialTheme.typography.titleMedium,
            color = colors.ink,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(onClick = { onShift(1) }, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Outlined.ChevronRight, contentDescription = "下个月", tint = colors.ink2, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(colors.panel2),
            contentAlignment = Alignment.Center,
        ) {
            Text("🍲", fontSize = 22.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun SheetHeader(
    title: String,
    subtitle: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
            }
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "关闭", tint = colors.ink3, modifier = Modifier.size(19.dp))
            }
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    val colors = LocalPotColors.current
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(colors.line),
    )
}

@Composable
fun StatBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: Color? = null,
    hint: String? = null,
    valueSize: Int = 21,
) {
    val colors = LocalPotColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            color = tone ?: colors.ink,
            style = MoneyStyle.copy(fontSize = valueSize.sp, fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (hint != null) {
            Spacer(Modifier.height(3.dp))
            Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.ink3, maxLines = 1)
        }
    }
}
