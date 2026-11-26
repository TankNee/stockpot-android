package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Project
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistRemove
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Deselect
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.CategoryAvatar
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.EntryRow
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.SheetGrabber
import cn.neeto.stockpot.ui.components.SheetHeader
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import cn.neeto.stockpot.ui.theme.PotCardShape

private fun projectEntries(ledger: Ledger, projectId: String): List<Entry> =
    ledger.entries.filter { it.projectId == projectId && it.countsTowardTotals }
        .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })

@Composable
fun ProjectsScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    onCreate: (name: String, note: String, start: String, end: String) -> Unit,
) {
    val colors = LocalPotColors.current
    var showEditor by remember { mutableStateOf(false) }
    val projects = ledger.projects.sortedByDescending { it.createdAt }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = colors.ink2, modifier = Modifier.size(21.dp))
            }
            Text(
                text = "项目",
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton("新建项目", onClick = { showEditor = true })
        }
        Text(
            text = "把一次旅行、一次搬家、一次装修的账目收在一起。项目只分组，不改分类和金额。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
            lineHeight = 19.sp,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 12.dp),
        )

        if (projects.isEmpty()) {
            EmptyState(
                title = "还没有项目",
                body = "项目不改变账目的分类和金额，只是多一个「这件事花了多少」的视角。",
            ) { PrimaryButton("新建项目", onClick = { showEditor = true }) }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = overlayBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(projects, key = { it.id }) { project ->
                val rows = projectEntries(ledger, project.id)
                val expense = rows.filter { it.kind == Kind.EXPENSE }.sumOf { it.effectiveAmount }
                val income = rows.filter { it.kind == Kind.INCOME }.sumOf { it.amount }
                val range = when {
                    project.startDate.isNotEmpty() && project.endDate.isNotEmpty() ->
                        "${Fmt.shortDate(project.startDate)} – ${Fmt.shortDate(project.endDate)}"
                    rows.isNotEmpty() -> "${Fmt.shortDate(rows.last().date)} – ${Fmt.shortDate(rows.first().date)}"
                    else -> "没有日期范围"
                }
                PotCard(onClick = { onOpenProject(project.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.brandTint),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("📁", fontSize = 17.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                project.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "$range  ·  ${rows.size} 笔",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink3,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                Fmt.yuan(expense),
                                style = MoneyStyle.copy(fontSize = 16.sp),
                                color = colors.expense,
                            )
                            if (income > 0) {
                                Spacer(Modifier.height(2.dp))
                                Text("+${Fmt.yuan(income)}", fontSize = 13.sp, color = colors.income)
                            }
                        }
                    }
                    if (project.note.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            project.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ProjectEditorDialog(
            project = null,
            onDismiss = { showEditor = false },
            onSave = { name, note, start, end ->
                onCreate(name, note, start, end)
                showEditor = false
            },
        )
    }
}

@Composable
fun ProjectDetailScreen(
    ledger: Ledger,
    projectId: String,
    onBack: () -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onUpdate: (Project) -> Unit,
    onDelete: (String) -> Unit,
    onAttach: (String, Set<String>) -> Unit,
    onDetach: (Set<String>) -> Unit,
) {
    val colors = LocalPotColors.current
    val project = ledger.projects.firstOrNull { it.id == projectId }
    var showEditor by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (project == null) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.padding(16.dp)) {
                PotPillButton(text = "返回", icon = Icons.AutoMirrored.Outlined.ArrowBack, tone = PillTone.Ghost, onClick = onBack)
            }
            EmptyState("项目不存在", "它可能已经被删除了。")
        }
        return
    }

    val rows = projectEntries(ledger, project.id)
    val expense = rows.filter { it.kind == Kind.EXPENSE }.sumOf { it.effectiveAmount }
    val income = rows.filter { it.kind == Kind.INCOME }.sumOf { it.amount }
    val days = rows.map { it.date }.distinct().size.coerceAtLeast(1)
    val categories = rows.filter { it.kind == Kind.EXPENSE }
        .groupBy { it.category }
        .map { (category, list) -> category to list.sumOf { it.effectiveAmount } }
        .sortedByDescending { it.second }
        .take(3)

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "返回", tint = colors.ink2, modifier = Modifier.size(19.dp))
            }
            Text(
                project.name,
                style = MaterialTheme.typography.titleLarge,
                color = colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { showEditor = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = colors.ink3, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = colors.ink3, modifier = Modifier.size(18.dp))
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 4.dp,
                bottom = overlayBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                PotCard {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("支出", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(Fmt.yuan(expense), style = MoneyStyle.copy(fontSize = 22.sp), color = colors.expense)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("收入", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(Fmt.yuan(income), style = MoneyStyle.copy(fontSize = 22.sp), color = colors.income)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("日均", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(Fmt.yuan(expense / days), style = MoneyStyle.copy(fontSize = 22.sp), color = colors.ink)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                    val range = when {
                        project.startDate.isNotEmpty() || project.endDate.isNotEmpty() ->
                            "${project.startDate.ifEmpty { "…" }} → ${project.endDate.ifEmpty { "…" }}"
                        rows.isNotEmpty() -> "${Fmt.shortDate(rows.last().date)} → ${Fmt.shortDate(rows.first().date)}"
                        else -> "还没有日期"
                    }
                    Text(
                        "$range  ·  ${rows.size} 笔  ·  $days 天有账目",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                    if (project.note.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(project.note, style = MaterialTheme.typography.bodySmall, color = colors.ink2)
                    }
                }
            }

            if (categories.isNotEmpty()) {
                item {
                    PotCard {
                        SectionTitle("花在哪儿")
                        Spacer(Modifier.height(10.dp))
                        categories.forEach { (category, total) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                CategoryAvatar(category, size = 26)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    category.replace("/", " · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.ink2,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(Fmt.yuan(total), style = MoneyStyle.copy(fontSize = 14.sp), color = colors.ink)
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "账目",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    PotPillButton(
                        text = "添加账目",
                        icon = Icons.Outlined.PlaylistAdd,
                        onClick = { showAttach = true },
                    )
                }
            }

            if (rows.isEmpty()) {
                item {
                    PotCard {
                        Text(
                            "还没有账目。从「添加账目」里挑选，或者在某笔账的编辑页里选这个项目。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                        )
                    }
                }
            } else {
                items(rows, key = { it.id }) { entry ->
                    Box(
                        Modifier
                            .clip(PotCardShape)
                            .background(colors.panel),
                    ) {
                        EntryRow(entry = entry, onClick = { onOpenEntry(entry) })
                    }
                }
                item {
                    Box(Modifier.padding(top = 6.dp)) {
                        PotPillButton(
                            text = "把账目从这个项目里全部移出",
                            icon = Icons.Outlined.PlaylistRemove,
                            tone = PillTone.Ghost,
                            onClick = { onDetach(rows.map { it.id }.toSet()) },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        ProjectEditorDialog(
            project = project,
            onDismiss = { showEditor = false },
            onSave = { name, note, start, end ->
                onUpdate(project.copy(name = name, note = note, startDate = start, endDate = end))
                showEditor = false
            },
        )
    }

    if (showAttach) {
        AttachEntriesSheet(
            ledger = ledger,
            project = project,
            onDismiss = { showAttach = false },
            onAttach = { ids ->
                onAttach(project.id, ids)
                showAttach = false
            },
        )
    }

    if (confirmDelete) {
        PotConfirmSheet(
            title = "删除项目？",
            body = "只会解除分组，账目一条都不会删。",
            confirmText = "删除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                onDelete(project.id)
                confirmDelete = false
                onBack()
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectEditorDialog(
    project: Project?,
    onDismiss: () -> Unit,
    onSave: (name: String, note: String, start: String, end: String) -> Unit,
) {
    val colors = LocalPotColors.current
    var name by remember { mutableStateOf(project?.name ?: "") }
    var note by remember { mutableStateOf(project?.note ?: "") }
    var start by remember { mutableStateOf(project?.startDate ?: "") }
    var end by remember { mutableStateOf(project?.endDate ?: "") }
    var picking by remember { mutableStateOf<String?>(null) }

    PotSheet(onDismiss = onDismiss) {
        Text(
            if (project == null) "新建项目" else "编辑项目",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                DialogField("名称", name, "比如 杭州出差") { name = it }
                Spacer(Modifier.height(10.dp))
                DialogField("备注", note, "可留空") { note = it }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateBox("开始", start, Modifier.weight(1f)) { picking = "start" }
                    DateBox("结束", end, Modifier.weight(1f)) { picking = "end" }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "日期只用来筛选候选账目，加入项目始终是手动选择。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }
        SheetActions(
            confirmText = "保存",
            enabled = name.isNotBlank(),
            danger = false,
            onConfirm = { onSave(name.trim(), note.trim(), start, end) },
            onCancel = onDismiss,
        )
    }

    val target = picking
    if (target != null) {
        val initial = if (target == "start") start else end
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (initial.isEmpty()) null else Fmt.epochMillis(initial),
        )
        PotSheet(onDismiss = { picking = null }) {
            Text("选择日期", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Spacer(Modifier.height(6.dp))
            DatePicker(
                state = pickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = colors.panel),
            )
            SheetActions(
                confirmText = "确定",
                danger = false,
                onConfirm = {
                    val value = pickerState.selectedDateMillis?.let { Fmt.fromEpochMillis(it) }
                    if (target == "start") start = value.orEmpty() else end = value.orEmpty()
                    picking = null
                },
                onCancel = { picking = null },
            )
        }
    }
}

@Composable
private fun DialogField(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    val colors = LocalPotColors.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(4.dp))
        TextField(
            value = value,
            onValueChange = onChange,
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
                cursorColor = colors.brand,
            ),
        )
    }
}

@Composable
private fun DateBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.panel2)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(2.dp))
        Text(
            value.ifEmpty { "选择日期" },
            fontSize = 14.sp,
            color = if (value.isEmpty()) colors.ink3 else colors.ink,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachEntriesSheet(
    ledger: Ledger,
    project: Project,
    onDismiss: () -> Unit,
    onAttach: (Set<String>) -> Unit,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember { mutableStateOf(setOf<String>()) }

    val candidates = remember(ledger.entries, project.id) {
        ledger.entries
            .filter { it.projectId.isEmpty() && !it.archived }
            .filter {
                (project.startDate.isEmpty() || it.date >= project.startDate) &&
                    (project.endDate.isEmpty() || it.date <= project.endDate)
            }
            .sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })
            .take(200)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
        ) {
            SheetHeader(
                title = "添加账目",
                subtitle = "${project.name}  ·  已选 ${selected.size} 笔",
                onClose = onDismiss,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PotPillButton(
                    text = if (selected.size == candidates.size) "取消全选" else "全选",
                    icon = if (selected.size == candidates.size) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                    tone = PillTone.Ghost,
                    onClick = {
                        selected = if (selected.size == candidates.size) emptySet() else candidates.map { it.id }.toSet()
                    },
                )
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = if (selected.isEmpty()) "选择账目" else "加入 ${selected.size} 笔",
                    onClick = { if (selected.isNotEmpty()) onAttach(selected) },
                )
            }
            Spacer(Modifier.height(4.dp))
            if (candidates.isEmpty()) {
                EmptyState("没有可添加的账目", "项目日期范围内没有未归类的账目。")
            } else {
                LazyColumn(Modifier.fillMaxWidth()) {
                    items(candidates, key = { it.id }) { entry ->
                        val checked = entry.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selected = if (checked) selected - entry.id else selected + entry.id
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(if (checked) colors.brand else colors.panel2),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (checked) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = colors.onBrand,
                                        modifier = Modifier.size(13.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            EntryRow(entry = entry, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
