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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Rule
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 关键词规则：导入账单时，「交易对方 / 备注里出现这个词」就归到某个分类。
 *
 * 原来只能在弹层里加和删，写错一个字只能删了重加。现在是独立页面：
 * 搜索（按关键词和分类名都能搜）、点一条改（关键词和分类都能改）、右侧删除。
 */
@Composable
fun RulesScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onAdd: (pattern: String, category: String) -> Unit,
    onUpdate: (Rule) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    var query by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Rule?>(null) }
    var deleting by remember { mutableStateOf<Rule?>(null) }

    val keyword = query.trim()
    val rules = remember(ledger.rules, keyword) {
        if (keyword.isEmpty()) {
            ledger.rules
        } else {
            ledger.rules.filter {
                it.pattern.contains(keyword, ignoreCase = true) ||
                    it.category.contains(keyword, ignoreCase = true)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "关键词规则", onBack = onBack, onConfirm = null)

        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchBar(
                value = query,
                placeholder = "搜索关键词或分类",
                onValueChange = { query = it },
            )
            Spacer(Modifier.height(10.dp))
        }

        LazyColumn(
            // 顶栏和搜索框占掉一部分，剩下的高度才是列表的
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = overlayBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "add") {
                PotCard {
                    SectionTitle("加一条")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "交易对方或备注里出现这个词就归到对应分类；越长越优先。",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.panel2)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        ) {
                            if (pattern.isEmpty()) {
                                Text("关键词，如 星巴克", style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                            }
                            BasicTextField(
                                value = pattern,
                                onValueChange = { pattern = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                                cursorBrush = SolidColor(colors.brand),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(colors.panel2)
                                .clickable { showPicker = true }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Label, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(
                                category.ifEmpty { "选分类" }.replace("/", " · "),
                                fontSize = 13.sp,
                                color = if (category.isEmpty()) colors.ink3 else colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        PotPillButton(
                            text = "添加规则",
                            icon = Icons.Outlined.Add,
                            enabled = pattern.isNotBlank() && category.isNotEmpty(),
                            onClick = {
                                if (pattern.isNotBlank() && category.isNotEmpty()) {
                                    onAdd(pattern.trim(), category)
                                    pattern = ""
                                }
                            },
                        )
                    }
                }
            }

            if (rules.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (keyword.isEmpty()) "还没有规则。加一条，导入时就能自动归类。" else "没有匹配「$keyword」的规则",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(top = 20.dp, start = 4.dp),
                    )
                }
            } else {
                item(key = "count") {
                    Text(
                        "${rules.size} 条规则 · 越长的关键词优先级越高",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
                items(rules, key = { it.id }) { rule ->
                    RuleRow(
                        rule = rule,
                        onEdit = { editing = rule },
                        onDelete = { deleting = rule },
                    )
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

    editing?.let { rule ->
        RuleEditorDialog(
            ledger = ledger,
            rule = rule,
            onDismiss = { editing = null },
            onSave = { updated ->
                onUpdate(updated)
                editing = null
            },
        )
    }

    deleting?.let { rule ->
        PotConfirmSheet(
            title = "删除「${rule.pattern}」？",
            body = "删掉之后，导入账单时这个关键词不再自动归类，已经归好的账目不受影响。",
            confirmText = "删除",
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(rule.id)
                deleting = null
            },
        )
    }
}

@Composable
private fun RuleRow(
    rule: Rule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard(padding = PaddingValues(0.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable { onEdit() }
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    rule.pattern,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = colors.ink3,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        rule.category.replace("/", " · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = colors.ink3, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = colors.ink3, modifier = Modifier.size(17.dp))
            }
        }
    }
}

/** 改一条规则：关键词和分类都能改。保存走的是同一个 onUpdate。 */
@Composable
private fun RuleEditorDialog(
    ledger: Ledger,
    rule: Rule,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit,
) {
    val colors = LocalPotColors.current
    var pattern by remember(rule.id) { mutableStateOf(rule.pattern) }
    var category by remember(rule.id) { mutableStateOf(rule.category) }
    var showPicker by remember { mutableStateOf(false) }

    PotSheet(onDismiss = onDismiss) {
        Text("编辑规则", style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(12.dp))
        SheetField(
            label = "关键词",
            value = pattern,
            placeholder = "出现在交易对方或备注里",
            onValueChange = { pattern = it },
        )
        Spacer(Modifier.height(12.dp))
        Text("归到", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(colors.panel2)
                .clickable { showPicker = true }
                .padding(horizontal = 12.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Label, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                category.ifEmpty { "选分类" }.replace("/", " · "),
                style = MaterialTheme.typography.bodyMedium,
                color = if (category.isEmpty()) colors.ink3 else colors.ink,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        }
        SheetActions(
            confirmText = "保存",
            enabled = pattern.isNotBlank() && category.isNotEmpty(),
            danger = false,
            onConfirm = { onSave(rule.copy(pattern = pattern.trim(), category = category)) },
            onCancel = onDismiss,
        )
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
