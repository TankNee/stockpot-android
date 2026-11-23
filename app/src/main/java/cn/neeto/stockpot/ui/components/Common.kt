package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
    /** 换掉中间的月份文案（流水页筛了自定义时间区间时显示区间），翻月会回到按月浏览。 */
    label: String? = null,
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
            text = label ?: Fmt.monthLabel(month),
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

/* ------------------------------ 底部弹层 ------------------------------ */

/**
 * 全应用统一的底部弹层。原来这些小表单用的是 AlertDialog，键盘弹起来会把内容顶掉一半，
 * 也不符合手机上「从下面推上来」的习惯。现在统一走这里，边框、留白、键盘都一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PotSheet(
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        contentColor = colors.ink,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 键盘和手势条取最大值，别叠两层留白
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 18.dp)
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

/** 弹层底部的一对按钮，比对话框里的文字按钮好点得多。 */
@Composable
fun SheetActions(
    confirmText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panel2)
                .clickable { onCancel() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("取消", fontSize = 15.sp, color = colors.ink2)
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    when {
                        !enabled -> colors.panel2
                        danger -> colors.danger
                        else -> colors.brand
                    },
                )
                .clickable(enabled = enabled) { onConfirm() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = confirmText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.onBrand else colors.ink3,
            )
        }
    }
}

/** 确认类弹层：删除、清空、重置都走这一个，不用每个页面再写一遍对话框。 */
@Composable
fun PotConfirmSheet(
    title: String,
    body: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    danger: Boolean = true,
) {
    val colors = LocalPotColors.current
    PotSheet(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = colors.ink3, lineHeight = 20.sp)
        SheetActions(
            confirmText = confirmText,
            onConfirm = onConfirm,
            onCancel = onDismiss,
            danger = danger,
        )
    }
}

/** 弹层里的一行标签 + 输入框，替换掉对话框里那种紧凑的 TextField。 */
@Composable
fun SheetField(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: androidx.compose.ui.text.input.KeyboardType =
        androidx.compose.ui.text.input.KeyboardType.Text,
) {
    val colors = LocalPotColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(5.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.panel2)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(colors.brand),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                    }
                    inner()
                }
            },
        )
    }
}
