package cn.neeto.stockpot.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.neeto.stockpot.BuildConfig
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.core.util.Documents
import cn.neeto.stockpot.ui.PotViewModel
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.theme.LocalPotColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: PotViewModel, onToast: (String) -> Unit) {
    val colors = LocalPotColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ledger by vm.ledger.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()

    var showCategories by remember { mutableStateOf(false) }
    var showRules by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = Documents.writeText(context, uri, vm.exportJson())
        onToast(if (ok) "账本已导出" else "导出失败")
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) { Documents.read(context, uri) }
            if (picked == null) {
                onToast("读不到这个文件")
                return@launch
            }
            val outcome = vm.importLedgerJson(String(picked.bytes, Charsets.UTF_8))
            onToast(
                when {
                    outcome == null -> "这不是一份账本文件"
                    outcome.added == 0 -> "没有新账目，全部已存在"
                    else -> "已合并 ${outcome.added} 笔账目"
                },
            )
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            PotCard {
                SectionTitle("外观")
                Spacer(Modifier.height(10.dp))
                Segmented(
                    options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                    selected = prefs.theme,
                    onSelect = vm::setTheme,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.brandTint),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("支", fontSize = 11.sp, color = colors.expense)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("支出用红", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.income.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("收", fontSize = 11.sp, color = colors.income)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("收入用绿", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                }
            }
        }

        item {
            PotCard(padding = PaddingValues(0.dp)) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle("账本")
                }
                SettingsRow(
                    title = "分类管理",
                    subtitle = "${ledger.categories.count { it.parent.isEmpty() }} 个分组 · ${ledger.categories.size} 个分类",
                    onClick = { showCategories = true },
                )
                Hairline(Modifier.padding(start = 16.dp))
                SettingsRow(
                    title = "关键词规则",
                    subtitle = "${ledger.rules.size} 条 · 导入时最长命中的规则胜出",
                    onClick = { showRules = true },
                )
            }
        }

        item {
            PotCard(padding = PaddingValues(0.dp)) {
                Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 4.dp)) {
                    SectionTitle("数据")
                }
                SettingsRow(
                    title = "导出账本",
                    subtitle = "一份可读的 ledger.json，桌面端也能打开",
                    onClick = { exportLauncher.launch("stockpot-ledger.json") },
                )
                Hairline(Modifier.padding(start = 16.dp))
                SettingsRow(
                    title = "导入账本文件",
                    subtitle = "合并另一份 ledger.json，重复账目自动跳过",
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
                Hairline(Modifier.padding(start = 16.dp))
                SettingsRow(
                    title = "载入示例数据",
                    subtitle = "替换当前账本，用来看看图表长什么样",
                    onClick = { confirmDemo = true },
                )
                Hairline(Modifier.padding(start = 16.dp))
                SettingsRow(
                    title = "清空流水",
                    subtitle = "删除所有账目和导入记录，保留分类与规则",
                    danger = true,
                    onClick = { confirmClear = true },
                )
                Hairline(Modifier.padding(start = 16.dp))
                SettingsRow(
                    title = "重置分类与规则",
                    subtitle = "恢复出厂的内置分类树",
                    danger = true,
                    onClick = { confirmReset = true },
                )
            }
        }

        item {
            PotCard {
                SectionTitle("关于")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stockpot ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "本地优先：没有账号、没有同步、没有网络请求。账本以一份 JSON 存在应用的私有目录里，写入时先写临时文件再原子替换，并保留一份滚动备份。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text("数据文件", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(2.dp))
                Text(
                    vm.dataPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink2,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "界面参考桌面端 Stockpot，技术栈参考 Guodelist：Kotlin + Compose，数据全部落在本机。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }
        }
    }

    if (showCategories) {
        CategoryManagerSheet(
            ledger = ledger,
            onDismiss = { showCategories = false },
            onAdd = { name, parent, kind -> vm.addCategory(name, parent, kind) },
            onUpdate = { def, name, parent, kind -> vm.updateCategory(def, name, parent, kind) },
            onDelete = { def -> vm.deleteCategory(def) },
        )
    }

    if (showRules) {
        RuleManagerSheet(
            ledger = ledger,
            onDismiss = { showRules = false },
            onAdd = { pattern, category -> vm.addRule(pattern, category) },
            onDelete = { id -> vm.deleteRule(id) },
        )
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空流水？",
            body = "所有账目和导入历史会被删除，分类、规则和钱包保留。这一步不能撤销。",
            confirmText = "清空",
            onDismiss = { confirmClear = false },
            onConfirm = {
                vm.clearEntries()
                confirmClear = false
                onToast("流水已清空")
            },
        )
    }

    if (confirmDemo) {
        ConfirmDialog(
            title = "载入示例数据？",
            body = "当前账本会被示例账本替换。建议先导出备份。",
            confirmText = "载入",
            onDismiss = { confirmDemo = false },
            onConfirm = {
                vm.loadDemo()
                confirmDemo = false
                onToast("示例账本已载入")
            },
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "重置分类与规则？",
            body = "账目会保留，但它们的分类会变成内置分类树里的名字；不认识的分类会原样留着。",
            confirmText = "重置",
            onDismiss = { confirmReset = false },
            onConfirm = {
                vm.resetAll()
                confirmReset = false
                onToast("已恢复内置分类")
            },
        )
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (danger) colors.danger else colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalPotColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink) },
        text = { Text(body, style = MaterialTheme.typography.bodySmall, color = colors.ink2, lineHeight = 19.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText, color = colors.danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.ink2) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManagerSheet(
    ledger: Ledger,
    onDismiss: () -> Unit,
    onAdd: (name: String, parent: String, kind: String) -> Unit,
    onUpdate: (CategoryDef, String, String, String) -> Unit,
    onDelete: (CategoryDef) -> Unit,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<CategoryDef?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.panel) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("分类管理", style = MaterialTheme.typography.titleLarge, color = colors.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "两层的树：分组下面挂子分类。改名会同步更新已有账目。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
                PrimaryButton("新建", onClick = { adding = true })
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.fillMaxWidth().height(420.dp)) {
                ledger.categoryTree().forEach { (group, children) ->
                    item(key = "g-${group.path}") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { editing = group }
                                .padding(vertical = 9.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(colors.brand),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                group.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.ink,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                when (group.kind) {
                                    Kind.EXPENSE -> "支出"
                                    Kind.INCOME -> "收入"
                                    else -> "通用"
                                },
                                fontSize = 10.sp,
                                color = colors.ink3,
                            )
                            IconButton(
                                onClick = { deleting = group },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = "删除分类",
                                    tint = colors.ink3,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                    children.forEach { child ->
                        item(key = "c-${child.path}") {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { editing = child }
                                    .padding(start = 24.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    child.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.ink2,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { deleting = child }, modifier = Modifier.size(30.dp)) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "删除分类",
                                        tint = colors.ink3,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        CategoryEditorDialog(
            ledger = ledger,
            existing = editing,
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { name, parent, kind ->
                val target = editing
                if (target == null) onAdd(name, parent, kind) else onUpdate(target, name, parent, kind)
                adding = false
                editing = null
            },
        )
    }

    val removing = deleting
    if (removing != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = colors.panel,
            title = { Text("删除「${removing.name}」？", style = MaterialTheme.typography.titleMedium, color = colors.ink) },
            text = {
                Text(
                    if (removing.parent.isEmpty()) {
                        "分组下的子分类会一起删除，相关账目会归到「其他」，账目本身不会丢。"
                    } else {
                        "相关账目会归到「${removing.parent}」，账目本身不会丢。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(removing)
                    deleting = null
                }) { Text("删除", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消", color = colors.ink2) }
            },
        )
    }
}

@Composable
fun CategoryEditorDialog(
    ledger: Ledger,
    existing: CategoryDef?,
    onDismiss: () -> Unit,
    onSave: (name: String, parent: String, kind: String) -> Unit,
) {
    val colors = LocalPotColors.current
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var parent by remember { mutableStateOf(existing?.parent ?: "") }
    var kind by remember {
        mutableStateOf(
            when {
                existing != null -> existing.kind
                else -> Kind.EXPENSE
            },
        )
    }
    val groups = ledger.categories.filter { it.parent.isEmpty() && it.name != existing?.name }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        title = {
            Text(
                if (existing == null) "新建分类" else "编辑分类",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                TextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("分类名称", color = colors.ink3, fontSize = 13.sp) },
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
                Spacer(Modifier.height(12.dp))
                Text("归属", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip(label = "顶级", active = parent.isEmpty(), onClick = { parent = "" })
                    groups.take(3).forEach { group ->
                        Chip(label = group.name, active = parent == group.name, onClick = { parent = group.name })
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("方向", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                Spacer(Modifier.height(6.dp))
                Segmented(
                    options = listOf(
                        Kind.EXPENSE to "支出",
                        Kind.INCOME to "收入",
                        "" to "通用",
                    ),
                    selected = kind,
                    onSelect = { kind = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), parent, kind) },
            ) {
                Text("保存", color = if (name.isNotBlank()) colors.brand else colors.ink3)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = colors.ink2) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleManagerSheet(
    ledger: Ledger,
    onDismiss: () -> Unit,
    onAdd: (pattern: String, category: String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pattern by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = colors.panel) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text("关键词规则", style = MaterialTheme.typography.titleLarge, color = colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(
                "导入账单时，交易对方和备注里出现关键词就归到对应分类；越长的关键词优先级越高。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("关键词，如 星巴克", color = colors.ink3, fontSize = 13.sp) },
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
                Box(
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.panel2)
                        .clickable { showPicker = true }
                        .padding(horizontal = 10.dp, vertical = 12.dp),
                ) {
                    Text(
                        category.ifEmpty { "选分类" },
                        fontSize = 12.sp,
                        color = if (category.isEmpty()) colors.ink3 else colors.ink,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Spacer(Modifier.weight(1f))
                PrimaryButton(
                    text = "添加规则",
                    onClick = {
                        if (pattern.isNotBlank() && category.isNotEmpty()) {
                            onAdd(pattern.trim(), category)
                            pattern = ""
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().height(360.dp)) {
                items(ledger.rules.size) { index ->
                    val rule = ledger.rules[index]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(rule.pattern, style = MaterialTheme.typography.bodySmall, color = colors.ink)
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.ink3,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            rule.category.replace("/", " · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        IconButton(onClick = { onDelete(rule.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除规则",
                                tint = colors.ink3,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPicker) {
        CategoryPickerDialog(
            ledger = ledger,
            kind = Kind.TRANSFER,
            selected = category,
            onDismiss = { showPicker = false },
            onPick = {
                category = it
                showPicker = false
            },
        )
    }
}
