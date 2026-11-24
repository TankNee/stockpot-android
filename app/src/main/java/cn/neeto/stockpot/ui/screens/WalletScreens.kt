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
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors

/** 内置类型：顺序决定分组与选择页的顺序，`credit` 单独当负债看。 */
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

/** 自定义类型的 kind 直接存中文名，所以未知 kind 原样显示。 */
fun walletKindLabel(kind: String): String =
    walletKinds.firstOrNull { it.first == kind }?.second ?: kind.ifEmpty { "其他" }

/** 内置类型 + 账本里已经用过的自定义类型，资产页分组按这个顺序走。 */
fun walletKindOptions(wallets: List<Wallet>): List<Pair<String, String>> {
    val builtin = walletKinds.map { it.first }.toSet()
    val custom = wallets.map { it.kind }
        .filter { it.isNotEmpty() && it !in builtin }
        .distinct()
    return walletKinds + custom.map { it to it }
}

/** 从导入账单里的账户名猜一个类型，猜错了在表单里还能改。 */
fun inferWalletKind(name: String): String {
    val n = name.lowercase()
    return when {
        listOf("信用卡", "花呗", "白条", "借呗").any { n.contains(it) } -> "credit"
        listOf("公积金").any { n.contains(it) } -> "provident"
        listOf("医保", "社保").any { n.contains(it) } -> "medical"
        listOf("养老", "年金").any { n.contains(it) } -> "pension"
        listOf("证券", "股票", "基金", "理财", "投资", "余额宝", "零钱通", "余利宝").any { n.contains(it) } -> "investment"
        listOf("支付宝", "微信", "零钱", "现金", "钱包", "qq").any { n.contains(it) } -> "cash"
        else -> "debit"
    }
}

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
    val index = walletKinds.indexOfFirst { it.first == kind }
    val tint = colors.chartPalette[(if (index >= 0) index else walletKinds.size) % colors.chartPalette.size]
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
    ledger: Ledger,
    selected: String,
    onPick: (kind: String, presetName: String) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPotColors.current
    var customOpen by remember { mutableStateOf(false) }

    // 导入账单里出现过、但还没有建成账户的名字
    val imported = remember(ledger.accounts, ledger.wallets) {
        val existing = ledger.wallets.map { it.name.trim().lowercase() }.toSet()
        ledger.accounts
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in existing }
            .distinct()
            .take(12)
    }

    Column(Modifier.fillMaxWidth()) {
        EditorBar(title = "选择类型", onBack = onBack, onConfirm = null)
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 2.dp,
                bottom = overlayBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (imported.isNotEmpty()) {
                item {
                    Text(
                        "从导入账单里认出来的账户",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink3,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
                item {
                    PotCard(padding = PaddingValues(0.dp)) {
                        imported.forEachIndexed { index, name ->
                            ImportedAccountRow(
                                name = name,
                                kind = inferWalletKind(name),
                                onClick = { onPick(inferWalletKind(name), name) },
                            )
                            if (index < imported.lastIndex) {
                                Box(Modifier.padding(start = 64.dp)) { Hairline() }
                            }
                        }
                    }
                }
                item {
                    Text(
                        "点一下就用这个账户名建好，类型是猜的，进表单还能改。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            item {
                Text(
                    "资金账户",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.ink3,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            item {
                PotCard(padding = PaddingValues(0.dp)) {
                    walletKinds.forEachIndexed { index, (id, label) ->
                        KindPickerRow(
                            kind = id,
                            label = label,
                            active = id == selected,
                            onClick = { onPick(id, "") },
                        )
                        if (index < walletKinds.lastIndex) {
                            Box(Modifier.padding(start = 64.dp)) { Hairline() }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.panel)
                        .clickable { customOpen = true }
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("＋", fontSize = 16.sp, color = colors.brandInk)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "自定义类型",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.brandInk,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "比如 数字钱包",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
            }

            item {
                Text(
                    "信用卡填待还金额，账上记成负数，净资产自动扣掉。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                )
            }
        }
    }

    if (customOpen) {
        CustomKindDialog(
            onDismiss = { customOpen = false },
            onConfirm = { name ->
                customOpen = false
                onPick(name, "")
            },
        )
    }
}

@Composable
private fun ImportedAccountRow(name: String, kind: String, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.panel2),
            contentAlignment = Alignment.Center,
        ) {
            Text(name.take(1), fontSize = 16.sp, color = colors.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "识别为 ${walletKindLabel(kind)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        }
        Text(
            "添加",
            fontSize = 13.sp,
            color = colors.brandInk,
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.brandTint)
                .padding(horizontal = 11.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun CustomKindDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = LocalPotColors.current
    var name by remember { mutableStateOf("") }
    PotSheet(onDismiss = onDismiss) {
        Text("自定义类型", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(4.dp))
        Text(
            "自定义类型只存在这台手机的账本里，和内置类型一样参与分组和统计。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            lineHeight = 19.sp,
        )
        Spacer(Modifier.height(14.dp))
        SheetField(
            label = "类型名称",
            value = name,
            placeholder = "比如 数字钱包、黄金",
            onValueChange = { name = it.take(8) },
        )
        SheetActions(
            confirmText = "创建",
            enabled = name.isNotBlank(),
            onConfirm = { onConfirm(name.trim()) },
            onCancel = onDismiss,
            danger = false,
        )
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
    // 信用卡填的是欠款：表单里是正数，落库记成负数，资产页就自动扣掉
    val isCredit = wallet.kind == "credit"
    // 名称一起当 key：从「识别出的账户」点进来时草稿已经被填了名字，
    // 这里要跟着重置，否则 rememberSaveable 会把上一次空表单的 "" 还原回来。
    var name by rememberSaveable(wallet.id, isNew, wallet.name) {
        mutableStateOf(wallet.name)
    }
    var note by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.note) }
    var balanceText by rememberSaveable(wallet.id, isNew) {
        mutableStateOf(
            when {
                wallet.balance == 0L && isNew -> ""
                isCredit -> Fmt.editYuan(kotlin.math.abs(wallet.balance))
                else -> Fmt.editYuan(wallet.balance)
            },
        )
    }
    var countInAssets by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.countInAssets) }
    var selectable by rememberSaveable(wallet.id, isNew) { mutableStateOf(wallet.selectable) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun submit() {
        if (name.isBlank()) return
        val typed = Fmt.parseYuan(balanceText) ?: 0L
        onSave(
            wallet.copy(
                name = name.trim(),
                note = note.trim(),
                // 信用卡：不管有没有手输负号，都按欠款存成负数
                balance = if (isCredit) -kotlin.math.abs(typed) else typed,
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
                .padding(start = 16.dp, end = 16.dp, bottom = overlayBottomPadding()),
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
                    label = if (isCredit) "下月待还" else "账户余额",
                    value = balanceText,
                    placeholder = if (isCredit) "输入待还金额，不用填负号" else "请输入账户余额",
                    onValueChange = { input ->
                        balanceText = input
                            .filter { it.isDigit() || it == '.' || (it == '-' && !isCredit) }
                    },
                    keyboardType = KeyboardType.Decimal,
                )
                RowDivider()
                ValueRow(label = "账户币种", value = "人民币 (CNY)")
                RowDivider()
                SwitchRow(
                    label = "计入总资产",
                    hint = if (isCredit) "关掉后只在账户列表里显示待还" else "关掉后只在账户列表里显示余额",
                    checked = countInAssets,
                    onCheckedChange = { countInAssets = it },
                )
            }

            if (isCredit) {
                Text(
                    "信用卡填的是待还金额，这里按正数记，资产页和净资产里算作负债（显示成负数）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    lineHeight = 17.sp,
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
                PotPillButton(
                    text = "删除这个账户",
                    icon = Icons.Outlined.DeleteOutline,
                    tone = PillTone.Danger,
                    onClick = { confirmDelete = true },
                )

            }
        }
    }

    if (confirmDelete) {
        PotConfirmSheet(
            title = "删除「${wallet.name}」？",
            body = "只是不再统计这个账户的余额，账本里的账目不受影响。",
            confirmText = "删除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete(wallet)
            },
        )
    }
}

/** 子页面顶栏：返回箭头 + 标题，右侧是可选的圆形确认。股票页也用它。 */
@Composable
internal fun EditorBar(
    title: String,
    onBack: () -> Unit,
    onConfirm: (() -> Unit)?,
    confirmEnabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
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
        trailing?.invoke()
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
    val haptics = rememberHaptics()
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
            onCheckedChange = {
                haptics(Haptic.Tap)
                onCheckedChange(it)
            },
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
