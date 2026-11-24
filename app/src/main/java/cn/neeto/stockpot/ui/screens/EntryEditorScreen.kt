package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.EntryInput
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.LinkOff
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.Amount
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.EntryRow
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.SheetHeader
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

/** 记一笔 / 改一笔。同一个表单，`existing` 为空就是新建。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryEditorScreen(
    ledger: Ledger,
    existing: Entry?,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (EntryInput) -> Unit,
    onUpdate: (Entry) -> Unit,
    onDelete: (Entry) -> Unit,
    onAddCategory: ((String) -> Unit)? = null,
    /** 点退款关联跳到对面那条（支出 ⇄ 退款）。 */
    onOpenEntry: ((Entry) -> Unit)? = null,
    onLinkRefund: ((String, String) -> Unit)? = null,
    onUnlinkRefund: ((String) -> Unit)? = null,
    /** 从关联跳过来之后，左上角退回上一条。 */
    onBack: (() -> Unit)? = null,
) {
    val colors = LocalPotColors.current

    // 以前弹层自己接系统返回键；改成整页之后得自己接。
    // 从关联的那一条跳过来的，先退回到上一条，再退才是关掉。
    BackHandler {
        if (onBack != null) onBack() else onDismiss()
    }

    var kind by remember(existing?.id) { mutableStateOf(existing?.kind ?: Kind.EXPENSE) }
    var amountText by remember(existing?.id) { mutableStateOf(existing?.let { Fmt.editYuan(it.amount) } ?: "") }
    var date by remember(existing?.id) { mutableStateOf(existing?.date ?: initialDate) }
    // 手记通常不填时间；导入来的带着几个点，编辑时能改、能清掉
    var time by remember(existing?.id) { mutableStateOf(existing?.time ?: "") }
    var category by remember(existing?.id) { mutableStateOf(existing?.category ?: "") }
    var account by remember(existing?.id) { mutableStateOf(existing?.account ?: "") }
    var counterparty by remember(existing?.id) { mutableStateOf(existing?.counterparty ?: "") }
    var note by remember(existing?.id) { mutableStateOf(existing?.note ?: "") }
    var projectId by remember(existing?.id) { mutableStateOf(existing?.projectId ?: "") }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showProjectPicker by remember(existing?.id) { mutableStateOf(false) }
    var showRefundPicker by remember { mutableStateOf(false) }

    // 对面那条实时取：刚关联完 ledger 就变了，卡片要立刻显示成已关联
    val linkedEntry = remember(existing?.id, existing?.refundLinkId, ledger.entries) {
        val id = existing?.refundLinkId.orEmpty()
        if (existing == null || id.isEmpty()) null else ledger.entries.firstOrNull { it.id == id }
    }
    // 退款行：已经配上的、分类写着退款的，以及老版本导入留下的「不计收支」行。
    // 最后一种没有 link，正好给它一个补配的入口。
    val isRefundRow = existing != null && existing.kind != Kind.EXPENSE &&
        (existing.refundLinkId.isNotEmpty() || existing.category.startsWith("退款") || existing.excluded)

    // 账户候选：资产页里勾了「记账时可被选择」的账户优先，再补上历史导入留下的账户名
    val accountCandidates = remember(ledger.wallets, ledger.accounts) {
        (ledger.wallets.filter { it.selectable }.map { it.name } + ledger.accounts)
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
    }

    val amount = Fmt.parseYuan(amountText) ?: 0L
    val canSave = amount > 0

    // 标题下面写清「哪天、几点」：日期会跟着手动改，时间来自账单本身
    val whenLabel = if (existing == null) {
        null
    } else {
        Fmt.dayLabel(date) + " · " + if (time.isBlank()) "未记录时间" else time
    }

    fun submit() {
        if (!canSave) return
        val finalCategory = category.ifEmpty { if (kind == Kind.INCOME) "其他" else "其他" }
        if (existing == null) {
            onSave(
                EntryInput(
                    date = date,
                    time = time,
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
                    time = time,
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

    // 整页铺开，不再是底部弹层：点一行流水之后这一页从那一行的位置长出来（缩放由外面的
    // ZoomReveal 做，这里只管按最终尺寸布局），关掉时原样缩回那一行里去。
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.panel)
            .imePadding(),
    ) {
        // 头部钉在顶上：保存按钮得一直看得见，不能跟着表单一起滚下去
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp),
        ) {
            SheetHeader(
                title = if (existing == null) "记一笔" else "编辑账目",
                subtitle = whenLabel,
                onClose = onDismiss,
                trailing = { SaveChip(enabled = canSave) { submit() } },
                leading = if (onBack == null) {
                    null
                } else {
                    {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "返回上一条",
                                tint = colors.ink3,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                },
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(6.dp))
            IconSegmented(
                options = listOf(
                    IconOption(Kind.EXPENSE, "支出", Icons.Outlined.ArrowUpward),
                    IconOption(Kind.INCOME, "收入", Icons.Outlined.ArrowDownward),
                    IconOption(Kind.TRANSFER, "转账", Icons.Outlined.SwapHoriz),
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
                Spacer(Modifier.weight(1f))
                // 具体时间：点开是一个表盘，选「不记时间」可以清掉
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.panel2)
                        .clickable { showTimePicker = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (time.isBlank()) "加时间" else time,
                        fontSize = 12.5.sp,
                        color = if (time.isBlank()) colors.ink3 else colors.ink,
                        fontWeight = if (time.isBlank()) FontWeight.Normal else FontWeight.Medium,
                    )
                }
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
            PickerField(value = account, placeholder = "选择账户", onClick = { showAccountPicker = true })

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
                // 项目名长短差很多（「26年初·外伶仃岛」），chip 一排根本放不下；
                // 跟账户一样用一行下拉，选中的那个显示全名。
                PickerField(
                    value = ledger.projects.firstOrNull { it.id == projectId }?.name.orEmpty(),
                    placeholder = if (projectId.isEmpty()) "不归项目" else "选择项目",
                    onClick = { showProjectPicker = true },
                )
            }

            if (existing != null) {
                // 退款配对：支出看「退回来的那笔」，退款行看「原来是哪笔消费」
                if (existing.refundedAmount > 0 || isRefundRow) {
                    Spacer(Modifier.height(22.dp))
                    RefundLinkCard(
                        entry = existing,
                        linked = linkedEntry,
                        isRefundRow = isRefundRow,
                        onOpen = { linkedEntry?.let { onOpenEntry?.invoke(it) } },
                        onLink = { showRefundPicker = true },
                        onUnlink = if (linkedEntry != null && onUnlinkRefund != null) {
                            { onUnlinkRefund.invoke(existing.id) }
                        } else {
                            null
                        },
                        linkEnabled = onLinkRefund != null,
                    )
                }
                Spacer(Modifier.height(18.dp))
                MetaCard(existing)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PotPillButton(
                        text = "删除这笔",
                        icon = Icons.Outlined.DeleteOutline,
                        tone = PillTone.Danger,
                        onClick = {
                            onDelete(existing)
                            onDismiss()
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            }
        // 键盘收起来以后最后一条不会被系统导航条压住
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
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

    if (showAccountPicker) {
        AccountPickerDialog(
            options = accountCandidates,
            selected = account,
            onDismiss = { showAccountPicker = false },
            onPick = {
                account = it
                showAccountPicker = false
            },
        )
    }

    if (showRefundPicker && existing != null) {
        RefundPickerSheet(
            entry = existing,
            candidates = LedgerOps.refundCandidates(ledger, existing),
            onDismiss = { showRefundPicker = false },
            onPick = { other ->
                showRefundPicker = false
                val expenseId = if (existing.kind == Kind.EXPENSE) existing.id else other.id
                val refundId = if (existing.kind == Kind.EXPENSE) other.id else existing.id
                onLinkRefund?.invoke(expenseId, refundId)
            },
        )
    }

    if (showProjectPicker) {
        ProjectPickerSheet(
            projects = ledger.projects,
            selectedId = projectId,
            onDismiss = { showProjectPicker = false },
            onPick = { picked ->
                projectId = picked
                showProjectPicker = false
            },
        )
    }

    if (showTimePicker) {
        TimePickerSheet(
            initial = time,
            onDismiss = { showTimePicker = false },
            onPick = {
                time = it
                showTimePicker = false
            },
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = Fmt.epochMillis(date))
        val sheetColors = LocalPotColors.current
        PotSheet(onDismiss = { showDatePicker = false }) {
            Text("选择日期", style = MaterialTheme.typography.titleLarge, color = sheetColors.ink)
            Spacer(Modifier.height(6.dp))
            DatePicker(
                state = pickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = DatePickerDefaults.colors(containerColor = sheetColors.panel),
            )
            SheetActions(
                confirmText = "确定",
                danger = false,
                onConfirm = {
                    pickerState.selectedDateMillis?.let { date = Fmt.fromEpochMillis(it) }
                    showDatePicker = false
                },
                onCancel = { showDatePicker = false },
            )
        }
    }
}

/** 右上角的保存。金额没填时是灰的，按不动。 */
@Composable
private fun SaveChip(enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) colors.brand else colors.panel2)
            .clickable(enabled = enabled) {
                haptics(Haptic.Confirm)
                onClick()
            }
            .padding(horizontal = 15.dp, vertical = 7.dp),
    ) {
        Text(
            text = "保存",
            color = if (enabled) colors.onBrand else colors.ink3,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * 「这笔钱退回来了」/「这笔钱退的是哪一单」。
 *
 * 支出行看的是它对应的那条退款：标题、日期、金额都在，点一下跳到退款那条；
 * 还没配上的给一个入口，从候选里挑。退款行反过来指向原消费。
 * 关联错了能当场解开，不用去翻列表。
 */
@Composable
private fun RefundLinkCard(
    entry: Entry,
    linked: Entry?,
    isRefundRow: Boolean,
    onOpen: () -> Unit,
    onLink: () -> Unit,
    onUnlink: (() -> Unit)?,
    linkEnabled: Boolean,
) {
    val colors = LocalPotColors.current
    FieldLabel("退款关联")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2),
    ) {
        if (linked != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isRefundRow) "退款自" else "退款记录",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = entryTitle(linked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = Fmt.dayLabel(linked.date) + if (linked.time.isBlank()) "" else " · ${linked.time}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        maxLines = 1,
                    )
                    // 分几次退的时候，卡片上这条是「最近一笔」，总额另说一句
                    if (!isRefundRow && entry.refundedAmount > linked.amount) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "累计已退 ${Fmt.yuan(entry.refundedAmount)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Amount(linked.amount, linked.kind, fontSize = 15)
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = colors.ink3,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (onUnlink != null) {
                Hairline()
                PotPillButton(
                    text = "解除关联",
                    icon = Icons.Outlined.LinkOff,
                    tone = PillTone.Ghost,
                    modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp),
                    onClick = onUnlink,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = linkEnabled) { onLink() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isRefundRow) {
                            "这笔记账还没和对应那条配对"
                        } else {
                            val at = if (entry.refundedAt.isBlank()) "" else " · 退款于 ${Fmt.shortDate(entry.refundedAt)}"
                            "已退 ${Fmt.yuan(entry.refundedAmount)}$at"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = if (isRefundRow) {
                            "挑出它对应的是哪一条，两条就能互相跳"
                        } else {
                            "还没对上具体是哪一笔，挑一条退款记录来配对"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        maxLines = 2,
                    )
                }
                if (linkEnabled) {
                    Spacer(Modifier.width(10.dp))
                    Text("去关联", fontSize = 13.sp, color = colors.brand)
                }
            }
        }
    }
}

/** 一条账目的显示名，和列表行用的是同一套回退顺序。 */
private fun entryTitle(entry: Entry): String = entry.counterparty.ifEmpty {
    entry.note.ifEmpty { entry.category.ifEmpty { "未命名" } }
}

/** 手动配对的候选弹层：支出挑退款，退款挑支出。 */
@Composable
private fun RefundPickerSheet(
    entry: Entry,
    candidates: List<Entry>,
    onDismiss: () -> Unit,
    onPick: (Entry) -> Unit,
) {
    val colors = LocalPotColors.current
    val isExpense = entry.kind == Kind.EXPENSE
    PotSheet(onDismiss = onDismiss) {
        Text(
            text = if (isExpense) "挑一笔退款" else "挑原来的消费",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isExpense) {
                "同平台上金额对得上、还没配过对的退款。配好之后两条都能互相跳，钱只算一次。"
            } else {
                "还没退完、金额够这一笔的消费。配好之后这笔退款会从合计里让开，回到那笔消费的退款额里。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(12.dp))
        if (candidates.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.panel2)
                    .padding(horizontal = 14.dp, vertical = 18.dp),
            ) {
                Text(
                    text = if (isExpense) {
                        "没找到金额合适的退款记录。可能这笔是从桌面端折进来的，或者退款那一条还没导入。"
                    } else {
                        "没找到能配的消费。试试先把那笔支出记上，再来关联。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                candidates.forEach { candidate ->
                    EntryRow(entry = candidate, showDate = true, onClick = { onPick(candidate) })
                    Hairline()
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * 「这条的来历」：来源、具体时间、退款、交易单号、记录时间。
 * 这些都是表单改不了、但看账时想知道的信息，集中放在删除按钮上面。
 */
@Composable
private fun MetaCard(entry: Entry) {
    val colors = LocalPotColors.current
    val rows = buildList {
        add("时间" to if (entry.time.isBlank()) "未记录具体时间" else entry.time)
        add("来源" to sourceLabel(entry.source))
        // 退款金额和日期归上面那张「退款关联」卡管，这里不重复
        val status = when {
            entry.archived -> "已收起，列表和合计里都不算"
            entry.excluded && entry.kind == Kind.EXPENSE -> "退款记录，不计入合计"
            entry.excluded -> "不计入合计"
            else -> ""
        }
        if (status.isNotEmpty()) add("状态" to status)
        if (entry.externalId.isNotBlank()) add("单号" to entry.externalId)
        if (entry.createdAt.isNotBlank()) add("记录于" to Fmt.stampLabel(entry.createdAt))
    }

    FieldLabel("这条的来历")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2)
            .padding(horizontal = 14.dp, vertical = 2.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) Hairline()
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.width(52.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                )
            }
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

/** 点开列表挑一项（账户、项目共用）：一行里只显示当前值，不铺一排 chip。 */
@Composable
private fun PickerField(
    value: String,
    placeholder: String,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            style = MaterialTheme.typography.bodyMedium,
            color = if (value.isEmpty()) colors.ink3 else colors.ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(17.dp),
        )
    }
}

/** 账户列表：钱包里勾了「记账时可被选择」的在前，导入识别的账户名垫后。 */
@Composable
private fun AccountPickerDialog(
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        val needle = query.trim()
        if (needle.isEmpty()) options else options.filter { it.contains(needle, ignoreCase = true) }
    }
    val canCreate = query.isNotBlank() && options.none { it.equals(query.trim(), ignoreCase = true) }

    PotSheet(onDismiss = onDismiss) {
        Text("选择账户", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索，或输入新账户名", color = colors.ink3, fontSize = 14.sp) },
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
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (canCreate) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(colors.brandTint)
                                .clickable { onPick(query.trim()) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "使用「${query.trim()}」",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.brandInk,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                    filtered.forEach { name ->
                        val active = name == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) colors.brandTint else Color.Transparent)
                                .clickable { onPick(name) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) colors.brandInk else colors.ink2,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (active) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = colors.brandInk,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (filtered.isEmpty() && !canCreate) {
                        Text(
                            "没有匹配的账户，上面的输入框里可以直接新建一个。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
}

/** 项目选择：项目名可以很长，一行一个，选中的打勾。 */
@Composable
private fun ProjectPickerSheet(
    projects: List<Project>,
    selectedId: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    PotSheet(onDismiss = onDismiss) {
        Text("选择项目", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "项目只给账目分组，不影响合计。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(10.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ProjectPickRow(
                name = "不归项目",
                subtitle = "这笔不算在任何项目里",
                active = selectedId.isEmpty(),
                onClick = { onPick("") },
            )
            projects.filterNot { it.archived }.forEach { project ->
                Hairline()
                ProjectPickRow(
                    name = project.name,
                    subtitle = listOf(project.note, projectRange(project))
                        .filter { it.isNotBlank() }
                        .joinToString("  ·  "),
                    active = project.id == selectedId,
                    onClick = { onPick(project.id) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ProjectPickRow(
    name: String,
    subtitle: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) colors.brandTint else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) colors.brandInk else colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (active) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.brandInk,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** 项目的起止日期；只填了一头就写一头。 */
private fun projectRange(project: Project): String = when {
    project.startDate.isNotBlank() && project.endDate.isNotBlank() ->
        "${Fmt.shortDate(project.startDate)} – ${Fmt.shortDate(project.endDate)}"
    project.startDate.isNotBlank() -> "${Fmt.shortDate(project.startDate)} 起"
    project.endDate.isNotBlank() -> "到 ${Fmt.shortDate(project.endDate)}"
    else -> ""
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

    PotSheet(onDismiss = onDismiss) {
        Text("选择分类", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索分类", color = colors.ink3, fontSize = 14.sp) },
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
                                placeholder = { Text("新建分类", color = colors.ink3, fontSize = 14.sp) },
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
                            PotPillButton(
                                text = "添加",
                                icon = Icons.Outlined.Add,
                                enabled = newName.isNotBlank(),
                                onClick = {
                                    val name = newName.trim()
                                    onAddCategory(name)
                                    onPick(name)
                                },
                            )
                        }
                    }
                }
            }
        }
}

/**
 * 具体时间：24 小时表盘。
 *
 * 手工记账大多数时候只记到天，所以默认不填；从账单导进来的带着分钟，
 * 编辑时点开这个入口能改，也能「不记时间」清掉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    initial: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val parts = initial.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 12,
        initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0,
        is24Hour = true,
    )
    PotSheet(onDismiss = onDismiss) {
        Text("具体时间", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(2.dp))
        Text(
            "只影响显示顺序，不影响按天汇总。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.panel2,
                    selectorColor = colors.brand,
                    containerColor = colors.panel,
                ),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            PotPillButton(
                text = "不记时间",
                icon = Icons.Outlined.Schedule,
                tone = PillTone.Ghost,
                onClick = { onPick("") },
            )
            Spacer(Modifier.weight(1f))
        }
        SheetActions(
            confirmText = "确定",
            enabled = true,
            danger = false,
            onConfirm = { onPick(String.format("%02d:%02d", state.hour, state.minute)) },
            onCancel = onDismiss,
        )
    }
}
