package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.theme.LocalPotColors

/** 账户类型：顺序决定分组与选择页的顺序，`credit` 单独当负债看。 */
val walletKinds: List<Pair<String, String>> = listOf(
    "debit" to "储蓄卡",
    "cash" to "现金 / 零钱",
    "credit" to "信用卡",
    "investment" to "投资",
    "pension" to "养老",
    "provident" to "公积金",
    "medical" to "医保",
    "other" to "其他",
)

fun walletKindLabel(kind: String): String =
    walletKinds.firstOrNull { it.first == kind }?.second ?: "其他"

private fun walletKindEmoji(kind: String): String = when (kind) {
    "debit" -> "🏦"
    "cash" -> "👛"
    "credit" -> "💳"
    "investment" -> "📈"
    "pension" -> "🧾"
    "provident" -> "🏠"
    "medical" -> "🏥"
    else -> "📦"
}

/** 类型图标：同一套 emoji 配主题里的低饱和色，深色主题下也压得住。 */
@Composable
fun WalletKindBadge(kind: String, size: Int = 38) {
    val colors = LocalPotColors.current
    val index = walletKinds.indexOfFirst { it.first == kind }.coerceAtLeast(0)
    val tint = colors.chartPalette[index % colors.chartPalette.size]
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 3).dp))
            .background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(walletKindEmoji(kind), fontSize = (size * 0.5f).sp)
    }
}

/* ------------------------------ 选择类型 ------------------------------ */

@Composable
fun WalletKindScreen(
    selected: String,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPotColors.current
    Column(Modifier.fillMaxWidth()) {
        EditorBar(title = "选择类型", onBack = onBack, onConfirm = null)
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "资金账户",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink3,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
            item {
                PotCard(padding = PaddingValues(0.dp)) {
                    walletKinds.forEachIndexed { index, (id, label) ->
                        KindPickerRow(
                            kind = id,
                            label = label,
                            active = id == selected,
                            onClick = { onPick(id) },
                        )
                        if (index < walletKinds.lastIndex) {
                            Box(Modifier.padding(start = 64.dp)) { Hairline() }
                        }
                    }
                }
            }
            item {
                Text(
                    "信用卡把欠款记成负数，净资产会自动扣掉。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun KindPickerRow(kind: String, label: String, active: Boolean, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletKindBadge(kind)
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (active) {
            Icon(Icons.Outlined.Check, contentDescription = null, tint = colors.brandInk, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(17.dp))
    }
}

/* ------------------------------ 添加 / 编辑 ------------------------------ */

@Composable
fun WalletEditorScreen(
    wallet: Wallet,
    isNew: Boolean,
    onBack: () -> Unit,
    onPickKind: () -> Unit,
    onSave: (Wallet) -> Unit,
    onDelete: (Wallet) -> Unit,
) {
    val colors = LocalPotColors.current
    var name by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.name) }
    var note by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.note) }
    var balanceText by rememberSaveable(wallet.id, isNew) {
        mutableStateOf(if (isNew && wallet.balance == 0L) "" else Fmt.editYuan(wallet.balance))
    }
    var countInAssets by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.countInAssets) }
    var selectable by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.selectable) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun submit() {
        if (name.isBlank()) return
        onSave(
            wallet.copy(
                name = name.trim(),
                note = note.trim(),
                balance = Fmt.parseYuan(balanceText) ?: 0L,
                countInAssets = countInAssets,
                selectable = selectable,
            ),
        )
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        EditorBar(
            title = if (isNew) "添加账户" else "编辑账户",
            onBack = onBack,
            onConfirm = { submit() },
            confirmEnabled = name.isNotBlank(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 类型：点进去换一种，回来还是这张表，已经填的内容不会丢
            PotCard(padding = PaddingValues(0.dp), onClick = onPickKind) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalletKindBadge(wallet.kind, size = 34)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        walletKindLabel(wallet.kind),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(17.dp))
                }
            }

            PotCard(padding = PaddingValues(0.dp)) {
                TextFieldRow(
                    label = "账户名称",
                    value = name,
                    placeholder = "请输入账户名称",
                    onValueChange = { name = it },
                )
                RowDivider()
                TextFieldRow(
                    label = "账户备注",
                    value = note,
                    placeholder = "点击填写备注（可不填）",
                    onValueChange = { note = it },
                )
            }

            PotCard(padding = PaddingValues(0.dp)) {
                TextFieldRow(
                    label = "账户余额",
                    value = balanceText,
                    placeholder = "请输入账户余额",
                    onValueChange = { input ->
                        balanceText = input.filter { it.isDigit() || it == '.' || it == '-' }
                    },
                    keyboardType = KeyboardType.Decimal,
                )
                RowDivider()
                ValueRow(label = "账户币种", value = "人民币 (CNY)")
                RowDivider()
                SwitchRow(
                    label = "计入总资产",
                    hint = "关掉后只在账户列表里显示余额",
                    checked = countInAssets,
                    onCheckedChange = { countInAssets = it },
                )
            }

            PotCard(padding = PaddingValues(0.dp)) {
                SwitchRow(
                    label = "记账时可被选择",
                    hint = "记一笔时的账户候选里出现",
                    checked = selectable,
                    onCheckedChange = { selectable = it },
                )
            }

            if (!isNew) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.padding(start = 2.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                ) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = null,
                        tint = colors.danger,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("删除这个账户", fontSize = 14.sp, color = colors.danger)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.panel,
            title = { Text("删除「${wallet.name}」？", style = MaterialTheme.typography.titleMedium, color = colors.ink) },
            text = {
                Text(
                    "只是不再统计这个账户的余额，账本里的账目不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(wallet)
                }) { Text("删除", fontSize = 15.sp, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", fontSize = 15.sp, color = colors.ink2) }
            },
        )
    }
}

/** 子页面顶栏：返回箭头 + 标题，右侧是可选的圆形确认。 */
@Composable
private fun EditorBar(
    title: String,
    onBack: () -> Unit,
    onConfirm: (() -> Unit)?,
    confirmEnabled: Boolean = true,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = colors.ink2, modifier = Modifier.size(21.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (onConfirm != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (confirmEnabled) colors.brand else colors.panel2)
                    .clickable(enabled = confirmEnabled) { onConfirm() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = "保存",
                    tint = if (confirmEnabled) colors.onBrand else colors.ink3,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(Modifier.padding(start = 14.dp, end = 14.dp)) { Hairline() }
}

/** 一行「标签 + 右对齐输入」，跟参考稿的表单一个节奏。 */
@Composable
private fun TextFieldRow(
    label: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val colors = LocalPotColors.current
    val focus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { focus.requestFocus() }
            .padding(horizontal = 14.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink, textAlign = TextAlign.End),
                cursorBrush = SolidColor(colors.brand),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink3,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink, modifier = Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    hint: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 12.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(hint, style = MaterialTheme.typography.labelSmall, color = colors.ink3, maxLines = 1)
            }
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onBrand,
                checkedTrackColor = colors.brand,
                checkedBorderColor = colors.brand,
                uncheckedThumbColor = colors.panel,
                uncheckedTrackColor = colors.panel2,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}
