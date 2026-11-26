package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

/**
 * 二级页面（叠加页）的列表底部留白。
 *
 * 这些页面没有底下那条 tab 胶囊——进去的时候它就滑走了，所以不用再给 108dp 那种
 * 「胶囊 + 手势条」的固定留白；只要避开系统导航条，再加一点呼吸就够了。
 * [extra] 是给页面自己浮在底部的操作条让位用的。
 */
@Composable
fun overlayBottomPadding(extra: Dp = 0.dp): Dp =
    WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp + extra

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
    val haptics = rememberHaptics()
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
                    .clickable {
                        // 已经选中的那格不再震，免得点了个寂寞
                        if (!active) haptics(Haptic.Tap)
                        onSelect(value)
                    }
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
    val haptics = rememberHaptics()
    // 用 Surface 的 onClick 重载，不要在外面套 Modifier.clickable：
    // 那样水波纹按矩形边界画，圆角胶囊上会露出一块方的点击底色。
    Surface(
        onClick = {
            haptics(Haptic.Tap)
            onClick()
        },
        modifier = modifier,
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
        // 分类配了图标就画图标，同色系、同底色；没配就退回首字
        val iconKey = LocalCategoryIcons.current[name].orEmpty()
        if (categoryIcon(iconKey) != null) {
            CategoryGlyph(iconKey, size = (size * 0.52f).dp, tint = color)
        } else {
            Text(
                text = key.take(1),
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
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
    /** 全额退款的原单：划掉，一眼看出这笔钱回来了 */
    strikethrough: Boolean = false,
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
        textDecoration = if (strikethrough) TextDecoration.LineThrough else null,
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
    /** 右上角的动作，比如「保存」。排在最右边，关闭按钮在它后面。 */
    trailing: (@Composable () -> Unit)? = null,
    /** 标题左边的返回。从一条账目跳到它关联的那条时，用它退回上一条。 */
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
            Spacer(Modifier.width(2.dp))
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
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 图标和文字得放在 Row 里排队；早先这里是个 Box，
        // 两个子元素都往正中摆，图标就压在「取消」两个字上
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.panel2)
                .clickable {
                    haptics(Haptic.Tap)
                    onCancel()
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("取消", fontSize = 15.sp, color = colors.ink2, maxLines = 1)
        }
        Row(
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
                .clickable(enabled = enabled) {
                    haptics(Haptic.Confirm)
                    onConfirm()
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                if (danger) Icons.Outlined.DeleteOutline else Icons.Outlined.Check,
                contentDescription = null,
                tint = if (enabled) colors.onBrand else colors.ink3,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = confirmText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) colors.onBrand else colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    password: Boolean = false,
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
            visualTransformation = if (password) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
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

/* --------------------------- 金额自动缩放 / 图标按钮 --------------------------- */

/**
 * 一行放得下的金额：先按 [maxSize] 量一次，放不下就按比例缩，缩到 [minSize] 为止。
 *
 * 首页「结余」那种三四位数带千分位加正负号的，17sp 在三分之一栏里必然溢出；
 * 用省略号截数字是最糟的处理方式（金额少一位没人看得出来），宁可字小一点。
 * 只量一次：宽度和字号近似线性，按比例缩就够准，不用反复试排。
 */
@Composable
fun MoneyText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    maxSize: TextUnit = 17.sp,
    minSize: TextUnit = 11.sp,
    weight: FontWeight = FontWeight.Medium,
) {
    val style = MoneyStyle.copy(fontSize = maxSize, fontWeight = weight)
    BoxWithConstraints(modifier) {
        val limit = constraints.maxWidth
        if (limit <= 0 || limit == Int.MAX_VALUE) {
            Text(text, style = style, color = color, maxLines = 1, softWrap = false)
            return@BoxWithConstraints
        }
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val measured = measurer.measure(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
        ).size.width
        val size = if (measured <= limit) {
            maxSize
        } else {
            val fitted = with(density) { (limit.toFloat() / measured * maxSize.toPx()).toSp() }
            // 兜底：真的长得离谱（十几位数）就到 minSize，再多也只能压到这儿
            if (fitted.value < minSize.value) minSize else fitted
        }
        Text(
            text = text,
            style = style.copy(fontSize = size),
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 胶囊按钮的语气。带图标、带底色，按下去能看出是一颗按钮而不是一行字。 */
enum class PillTone { Tinted, Ghost, Danger }

/**
 * 带图标的胶囊按钮。全站的小动作都用它：取消、返回、新建、查看全部……
 * 纯文字按钮在深色背景下几乎看不出可点，加个底色和图标才像按钮。
 */
@Composable
fun PotPillButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Tinted,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val bg = when (tone) {
        PillTone.Tinted -> colors.brandTint
        PillTone.Ghost -> colors.panel2
        PillTone.Danger -> colors.danger.copy(alpha = 0.12f)
    }
    val fg = when (tone) {
        PillTone.Tinted -> colors.brandInk
        PillTone.Ghost -> colors.ink2
        PillTone.Danger -> colors.danger
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(bg)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = fg.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(15.dp))
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = fg.copy(alpha = if (enabled) 1f else 0.4f),
            // 长一点的按钮文案（「把账目从这个项目里全部移出」）在窄屏上折成两行，
            // 而不是被裁掉一截
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 图标分段控件的一个选项。 */
data class IconOption(val id: String, val label: String, val icon: ImageVector)

/**
 * 分段控件，每一格都是「图标 + 文字」。
 *
 * 设置页里那两个纯文字的分段（主题、打开条目的动画）光看字分不出深浅和方向，
 * 选中态也只有底色差一点点。这里每格给一个图标，选中那格铺品牌淡色、字和图标一起变深。
 */
@Composable
fun IconSegmented(
    options: List<IconOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val active = option.id == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (active) colors.brandTint else Color.Transparent)
                    .clickable {
                        if (!active) haptics(Haptic.Tap)
                        onSelect(option.id)
                    }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    option.icon,
                    contentDescription = null,
                    tint = if (active) colors.brandInk else colors.ink3,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = option.label,
                    fontSize = 12.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    color = if (active) colors.brandInk else colors.ink3,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * 主按钮：实心品牌色 + 图标。全站就这一种「确定要做的事」。
 * 图标不是装饰：同一个按钮在深色背景下，光靠文字几乎看不出边界。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = Icons.Outlined.Check,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.brand)
            .clickable {
                haptics(Haptic.Confirm)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(17.dp))
        }
        Text(text, color = colors.onBrand, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 次要按钮：面板底色 + 一圈细边 + 图标，和主按钮并排时主次分明。 */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
            .clickable {
                haptics(Haptic.Tap)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(16.dp))
        }
        Text(text, color = colors.ink2, fontSize = 15.sp, maxLines = 1)
    }
}
