package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.EntryInput
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.SheetGrabber
import cn.neeto.stockpot.ui.components.SheetHeader
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

/** 记一笔 / 改一笔。同一个表单，`existing` 为空就是新建。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorSheet(
    ledger: Ledger,
    existing: Entry?,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (EntryInput) -> Unit,
    onUpdate: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onAddCategory: ((String) -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var kind by remember { mutableStateOf(existing?.kind ?: Kind.EXPENSE) }
    var amountText by remember { mutableStateOf(existing?.let { Fmt.editYuan(it.amount) } ?: "") }
    var date by remember { mutableStateOf(existing?.date ?: initialDate) }
    var category by remember { mutableStateOf(existing?.category ?: "") }
    var account by remember { mutableStateOf(existing?.account ?: "") }
    var counterparty by remember { mutableStateOf(existing?.counterparty ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var projectId by remember { mutableStateOf(existing?.projectId ?: "") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val amount = Fmt.parseYuan(amountText) ?: 0L
    val canSave = amount > 0

    fun submit() {
        if (!canSave) return
        val finalCategory = category.ifEmpty { if (kind == Kind.INCOME) "其他" else "其他" }
        if (existing == null) {
            onSave(
                EntryInput(
                    date = date,
                    kind = kind,
                    amount = amount,
                    category = finalCategory,
                    account = account,
                    counterparty = counterparty,
                    note = note,
                    projectId = projectId,
                ),
            )
        } else {
            onUpdate(
                existing.copy(
                    date = date,
                    kind = kind,
                    amount = amount,
                    category = finalCategory,
                    account = account.trim(),
                    counterparty = counterparty.trim(),
                    note = note.trim(),
                    projectId = projectId,
                    categoryLocked = category != existing.category || existing.categoryLocked,
                ),
            )
        }
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        // 自己管 inset：抓手要把状态栏让出来，底部留白交给下面的 Spacer
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { SheetGrabber() },
    ) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Column(
                modifier = Modifier
                    .weight(1f, false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
            ) {
                SheetHeader(
                    title = if (existing == null) "记一笔" else "编辑账目",
                    subtitle = if (existing == null) null else Fmt.dayLabel(existing.date),
                    onClose = onDismiss,
                )

            Spacer(Modifier.height(6.dp))
            Segmented(
                options = listOf(
                    Kind.EXPENSE to "支出",
                    Kind.INCOME to "收入",
                    Kind.TRANSFER to "转账",
                ),
                selected = kind,
                onSelect = { kind = it },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "¥",
                    fontSize = 26.sp,
                    color = if (amount > 0) colors.ink else colors.ink3,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(end = 8.dp, top = 4.dp),
                )
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = amountText,
                        onValueChange = { input ->
                            // 只留数字和一个小数点，顺手把全角符号挡掉
                            amountText = input.filter { it.isDigit() || it == '.' }
                        },
                        singleLine = true,
                        textStyle = MoneyStyle.copy(fontSize = 34.sp, color = colors.ink),
                        cursorBrush = SolidColor(colors.brand),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    if (amountText.isEmpty()) {
                        Text(
                            text = "0.00",
                            fontSize = 34.sp,
                            color = colors.lineStrong,
                            style = MoneyStyle.copy(fontSize = 34.sp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line),
            )

            Spacer(Modifier.height(14.dp))
            // 快速填金额：整十/整百，少按几下键盘
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1000L, 2000L, 5000L, 10000L).forEach { cents ->
                    Chip(
                        label = Fmt.compact(cents),
                        active = amount == cents,
                        onClick = { amountText = Fmt.editYuan(cents) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            FieldLabel("分类")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.panel2)
                    .clickable { showCategoryPicker = true }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = category.ifEmpty { "选择分类" },
                    color = if (category.isEmpty()) colors.ink3 else colors.ink,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(18.dp))
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("日期")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = Fmt.today()
                listOf(
                    today to "今天",
                    Fmt.fromEpochMillis(Fmt.epochMillis(today) - 86_400_000L) to "昨天",
                    Fmt.fromEpochMillis(Fmt.epochMillis(today) - 2 * 86_400_000L) to "前天",
                ).forEach { (value, label) ->
                    Chip(label = label, active = date == value, onClick = { date = value })
                }
                Chip(
                    label = if (date != today) Fmt.shortDate(date) else "选择",
                    active = false,
                    onClick = { showDatePicker = true },
                    dotColor = null,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(Fmt.dayLabel(date), style = MaterialTheme.typography.labelSmall, color = colors.ink3)
            }

            Spacer(Modifier.height(18.dp))
            FieldLabel("对方 / 商家")
            PlainField(
                value = counterparty,
                placeholder = "比如 瑞幸咖啡",
                onValueChange = { counterparty = it },
            )

            Spacer(Modifier.height(14.dp))
            FieldLabel("账户")
            PlainField(
                value = account,
                placeholder = "比如 微信零钱",
                onValueChange = { account = it },
            )
            if (ledger.accounts.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ledger.accounts.take(3).forEach { name ->
                        Chip(label = name, active = account == name, onClick = { account = name })
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("备注")
            PlainField(
                value = note,
                placeholder = "写点什么",
                onValueChange = { note = it },
            )

            if (ledger.projects.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                FieldLabel("项目")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(label = "无", active = projectId.isEmpty(), onClick = { projectId = "" })
                    ledger.projects.filterNot { it.archived }.take(3).forEach { project ->
                        Chip(
                            label = project.name,
                            active = projectId == project.id,
                            onClick = { projectId = project.id },
                        )
                    }
                }
            }

            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (existing != null) {
                    TextButton(onClick = {
                        onDelete(existing)
                        onDismiss()
                    }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                        Text("删除", color = colors.danger, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("取消", color = colors.ink2, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canSave) colors.brand else colors.lineStrong)
                        .clickable(enabled = canSave) { submit() }
                        .padding(horizontal = 26.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = "保存",
                        color = if (canSave) colors.onBrand else colors.panel,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showCategoryPicker) {
        CategoryPickerDialog(
            ledger = ledger,
            kind = kind,
            selected = category,
            onDismiss = { showCategoryPicker = false },
            onPick = {
                category = it
                showCategoryPicker = false
            },
            onAddCategory = onAddCategory,
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = Fmt.epochMillis(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = Fmt.fromEpochMillis(it) }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState, title = null, headline = null, showModeToggle = false)
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    val colors = LocalPotColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colors.ink3,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

@Composable
private fun PlainField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = colors.ink3, fontSize = 14.sp) },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.panel2,
            unfocusedContainerColor = colors.panel2,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = colors.ink,
            unfocusedTextColor = colors.ink,
            cursorColor = colors.brand,
        ),
    )
}

/** 分类选择：按树展示，也能现场新建一个。 */
@Composable
fun CategoryPickerDialog(
    ledger: Ledger,
    kind: String,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onAddCategory: ((String) -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    var query by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }

    val tree = remember(ledger.categories, kind, query) {
        ledger.categoryTree()
            .map { (group, children) ->
                val groupOk = kind == Kind.TRANSFER || group.kind.isEmpty() || group.kind == kind
                val visibleChildren = children.filter { child ->
                    (kind == Kind.TRANSFER || child.kind.isEmpty() || child.kind == kind) &&
                        (query.isEmpty() || child.name.contains(query) || group.name.contains(query))
                }
                Triple(group, if (groupOk) group else null, visibleChildren)
            }
            .filter { (group, g, children) ->
                query.isEmpty() || (g != null && group.name.contains(query)) || children.isNotEmpty()
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        title = { Text("选择分类", style = MaterialTheme.typography.titleMedium, color = colors.ink) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索分类", color = colors.ink3, fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.panel2,
                        unfocusedContainerColor = colors.panel2,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = colors.brand,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .height(340.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    tree.forEach { (group, groupDef, children) ->
                        val groupSelected = selected == group.path
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (groupSelected) colors.brandTint else Color.Transparent)
                                .clickable(enabled = groupDef != null) { onPick(group.path) }
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (groupDef != null) colors.ink else colors.ink3,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        children.forEach { child ->
                            val active = selected == child.path
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(if (active) colors.brandTint else Color.Transparent)
                                    .clickable { onPick(child.path) }
                                    .padding(start = 26.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = child.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (active) colors.brandInk else colors.ink2,
                                )
                            }
                        }
                    }
                    if (onAddCategory != null) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextField(
                                value = newName,
                                onValueChange = { newName = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("新建分类", color = colors.ink3, fontSize = 13.sp) },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                textStyle = MaterialTheme.typography.bodySmall,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = colors.panel2,
                                    unfocusedContainerColor = colors.panel2,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = colors.brand,
                                ),
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                enabled = newName.isNotBlank(),
                                onClick = {
                                    val name = newName.trim()
                                    onAddCategory(name)
                                    onPick(name)
                                },
                            ) { Text("添加", color = if (newName.isNotBlank()) colors.brand else colors.ink3) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = colors.ink2) }
        },
    )
}
