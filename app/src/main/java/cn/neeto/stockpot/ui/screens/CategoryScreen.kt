package cn.neeto.stockpot.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.model.CategoryDef
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.builtinIcon
import cn.neeto.stockpot.core.util.Diag
import cn.neeto.stockpot.ui.components.CategoryGlyph
import cn.neeto.stockpot.ui.components.CategoryIconGrid
import cn.neeto.stockpot.ui.components.categoryIcon
import cn.neeto.stockpot.ui.components.effectiveIcon
import cn.neeto.stockpot.core.model.Ledger
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.SwapHoriz
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 分类管理：和账本里那棵两层的树一一对应，搬成独立页面。
 *
 * 原来是一个底部弹层，四百多行挤在里面、新建完就看不见列表了。这里铺成整页：
 * 顶上是搜索（分类一多，翻列表不如打两个字），下面是可展开的树。
 * - 点分组名展开/收起子分类，箭头跟着转；
 * - 点右边的笔进编辑，垃圾桶删除（删之前照例问一句）；
 * - 搜索时按名字过滤，命中的分组自动展开，子分类也跟着过滤。
 */
@Composable
fun CategoriesScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onAdd: (name: String, parent: String, kind: String, icon: String) -> Unit,
    onUpdate: (CategoryDef, String, String, String, String) -> Unit,
    onDelete: (CategoryDef) -> Unit,
) {
    val colors = LocalPotColors.current
    LaunchedEffect(Unit) { Diag.step("categories.open", "count=${ledger.categories.size}") }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CategoryDef?>(null) }
    var adding by remember { mutableStateOf(false) }
    // 新建子分类时预填的分组名
    var addingParent by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<CategoryDef?>(null) }
    // 默认全展开：分类树一共就两层，收起来反而要多点一次
    var collapsed by remember { mutableStateOf(setOf<String>()) }

    val keyword = query.trim()
    val tree = remember(ledger.categories, keyword) {
        ledger.categoryTree().map { (group, children) ->
            if (keyword.isEmpty()) {
                group to children
            } else {
                val hitChildren = children.filter { it.name.contains(keyword, ignoreCase = true) }
                val hitGroup = group.name.contains(keyword, ignoreCase = true)
                when {
                    hitGroup -> group to children
                    hitChildren.isNotEmpty() -> group to hitChildren
                    else -> null
                }
            }
        }.filterNotNull()
    }
    val searching = keyword.isNotEmpty()

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "分类管理", onBack = onBack, onConfirm = null)

        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchBar(
                value = query,
                placeholder = "搜索分类",
                onValueChange = { query = it },
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${ledger.categories.count { it.parent.isEmpty() }} 个分组 · ${ledger.categories.size} 个分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.weight(1f),
                )
                PotPillButton(
                    text = "新建",
                    icon = Icons.Outlined.Add,
                    onClick = {
                        addingParent = ""
                        adding = true
                    },
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (tree.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    if (searching) "没有叫「$keyword」的分类" else "还没有分类",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink3,
                    modifier = Modifier.padding(top = 40.dp),
                )
            }
            return@Column
        }

        LazyColumn(
            // 上面还有顶栏和搜索框，用 weight 才拿得到「剩下的」高度；
            // fillMaxSize 会按整屏算，列表底部被顶出可见区域
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = overlayBottomPadding() + 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tree.forEach { (group, children) ->
                val open = searching || group.name !in collapsed
                item(key = "g-${group.path}") {
                    PotCard(padding = PaddingValues(0.dp)) {
                        GroupRow(
                            group = group,
                            childCount = children.size,
                            open = open,
                            onToggle = {
                                collapsed = if (group.name in collapsed) {
                                    collapsed - group.name
                                } else {
                                    collapsed + group.name
                                }
                            },
                            onEdit = { editing = group },
                            onAddChild = {
                                addingParent = group.name
                                adding = true
                            },
                            onDelete = { deleting = group },
                        )
                        if (open) {
                            children.forEach { child ->
                                ChildRow(
                                    child = child,
                                    onEdit = { editing = child },
                                    onDelete = { deleting = child },
                                )
                            }
                            if (children.isEmpty()) {
                                Text(
                                    "这个分组下面还没有分类",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.ink3,
                                    modifier = Modifier.padding(start = 42.dp, bottom = 12.dp),
                                )
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
            initialParent = if (editing == null) addingParent else (editing?.parent ?: ""),
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { name, parent, kind, icon ->
                val target = editing
                if (target == null) onAdd(name, parent, kind, icon) else onUpdate(target, name, parent, kind, icon)
                adding = false
                editing = null
            },
        )
    }

    val removing = deleting
    if (removing != null) {
        PotConfirmSheet(
            title = "删除「${removing.name}」？",
            body = if (removing.parent.isEmpty()) {
                "分组下的子分类会一起删除，相关账目会归到「其他」，账目本身不会丢。"
            } else {
                "相关账目会归到「${removing.parent}」，账目本身不会丢。"
            },
            confirmText = "删除",
            onDismiss = { deleting = null },
            onConfirm = {
                onDelete(removing)
                deleting = null
            },
        )
    }
}

@Composable
private fun GroupRow(
    group: CategoryDef,
    childCount: Int,
    open: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onAddChild: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPotColors.current
    val angle by animateFloatAsState(if (open) 90f else 0f, tween(180), label = "chevron")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
            .padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = if (open) "收起" else "展开",
            tint = colors.ink3,
            modifier = Modifier
                .size(18.dp)
                .rotate(angle),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
            if (categoryIcon(group.effectiveIcon()) != null) {
                CategoryGlyph(group.effectiveIcon(), size = 17.dp, tint = colors.brand)
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.brand),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            group.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            when (group.kind) {
                Kind.EXPENSE -> "支出"
                Kind.INCOME -> "收入"
                else -> "通用"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when (group.kind) {
                Kind.EXPENSE -> colors.expense
                Kind.INCOME -> colors.income
                else -> colors.ink3
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "$childCount",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            modifier = Modifier.width(18.dp),
        )
        IconButton(onClick = onAddChild, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Add, contentDescription = "在这个分组下新建", tint = colors.ink3, modifier = Modifier.size(17.dp))
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = colors.ink3, modifier = Modifier.size(16.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = colors.ink3, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ChildRow(
    child: CategoryDef,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onEdit() }
            .padding(start = 32.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (categoryIcon(child.effectiveIcon()) != null) {
                CategoryGlyph(child.effectiveIcon(), size = 15.dp, tint = colors.ink3)
            } else {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.lineStrong),
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            child.name,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.ink2,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (child.kind.isNotEmpty()) {
            Text(
                if (child.kind == Kind.INCOME) "收入" else "支出",
                style = MaterialTheme.typography.labelSmall,
                color = if (child.kind == Kind.INCOME) colors.income else colors.expense,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.Edit, contentDescription = "编辑", tint = colors.ink3, modifier = Modifier.size(15.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Outlined.DeleteOutline, contentDescription = "删除", tint = colors.ink3, modifier = Modifier.size(16.dp))
        }
    }
}

/** 搜索框：带放大镜、右侧一键清空。分类页和规则页共用。 */
@Composable
internal fun SearchBar(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.panel2)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "清空",
                tint = colors.ink3,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onValueChange("") },
            )
        }
    }
}

/**
 * 新建 / 编辑一个分类。
 *
 * 「归属」那一排原来是 take(3)，分组一多就选不到下面几个；改成自动换行的流式布局，
 * 全部分组都在。从某个分组里点「+」新建时，[initialParent] 会预填好。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryEditorDialog(
    ledger: Ledger,
    existing: CategoryDef?,
    onDismiss: () -> Unit,
    onSave: (name: String, parent: String, kind: String, icon: String) -> Unit,
    initialParent: String = "",
) {
    val colors = LocalPotColors.current
    var name by remember(existing?.path) { mutableStateOf(existing?.name ?: "") }
    var parent by remember(existing?.path) { mutableStateOf(existing?.parent ?: initialParent) }
    var kind by remember(existing?.path) {
        mutableStateOf(existing?.kind ?: Kind.EXPENSE)
    }
    // 已有的分类拿它自己的图标；没有就跟着默认映射走，网格里能看出「不选会是什么」
    var icon by remember(existing?.path) {
        mutableStateOf(existing?.icon?.ifEmpty { builtinIcon(existing.name) } ?: "")
    }
    val groups = ledger.categories.filter { it.parent.isEmpty() && it.name != existing?.name }

    PotSheet(onDismiss = onDismiss) {
      // 多了图标网格，小屏上一屏放不下，弹层内容自己滚
      Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text(
            if (existing == null) "新建分类" else "编辑分类",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (parent.isEmpty()) "顶级分类是一个分组，下面还能挂子分类" else "挂在「$parent」下面",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(12.dp))
        SheetField(
            label = "名称",
            value = name,
            placeholder = "分类名称",
            onValueChange = { name = it },
        )
        Spacer(Modifier.height(12.dp))
        Text("归属", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Chip(label = "顶级", active = parent.isEmpty(), onClick = { parent = "" })
            groups.forEach { group ->
                Chip(label = group.name, active = parent == group.name, onClick = { parent = group.name })
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("方向", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(6.dp))
        IconSegmented(
            options = listOf(
                IconOption(
                    Kind.EXPENSE, "支出", Icons.Outlined.ArrowUpward,
                    tint = colors.expense,
                    tintSoft = colors.expense.copy(alpha = 0.13f),
                ),
                IconOption(
                    Kind.INCOME, "收入", Icons.Outlined.ArrowDownward,
                    tint = colors.income,
                    tintSoft = colors.income.copy(alpha = 0.13f),
                ),
                IconOption("", "通用", Icons.Outlined.SwapHoriz),
            ),
            selected = kind,
            onSelect = { kind = it },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("图标", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(6.dp))
        CategoryIconGrid(selected = icon, onSelect = { icon = it })
        Spacer(Modifier.height(4.dp))
        Text(
            "不选也行，会按分类名自动配一个。",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        SheetActions(
            confirmText = "保存",
            enabled = name.isNotBlank(),
            danger = false,
            onConfirm = { onSave(name.trim(), parent, kind, icon) },
            onCancel = onDismiss,
        )
      }
    }
}
