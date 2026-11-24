package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.EntryFilter
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import androidx.compose.material.icons.outlined.RestartAlt
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.DayHeader
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.EntryRow
import cn.neeto.stockpot.ui.components.GhostButton
import cn.neeto.stockpot.ui.components.MonthStepper
import cn.neeto.stockpot.ui.components.MonthSwipe
import cn.neeto.stockpot.ui.components.PotPullToRefresh
import cn.neeto.stockpot.ui.components.PrimaryButton
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.SheetGrabber
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import java.time.LocalDate

/**
 * 流水页。
 *
 * 顶上一行是月份、搜索和「筛选」；具体条件（类型、账户、金额区间、时间区间、排序）
 * 全在筛选弹层里，页面上只留一行已生效条件，点一下就能撤掉。
 */
@Composable
fun EntriesScreen(
    ledger: Ledger,
    month: String,
    dayFilter: String?,
    onMonth: (String) -> Unit,
    onDayFilter: (String?) -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onLongPressEntry: (Entry) -> Unit,
    onOpenImport: () -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val colors = LocalPotColors.current
    var filter by rememberSaveable(stateSaver = FilterSaver) { mutableStateOf(EntryFilter()) }
    var query by rememberSaveable { mutableStateOf("") }
    var filterOpen by remember { mutableStateOf(false) }

    // 左右滑月和点箭头走同一条路。翻月就回到按月浏览：区间筛选跟新月份对不上，顺手清掉
    fun shift(delta: Long) {
        onMonth(Fmt.shiftMonth(month, delta))
        onDayFilter(null)
        if (filter.hasRange) filter = filter.copy(from = null, to = null)
    }

    // 时间范围能跨月，候选账户就别只限当月了
    val accountOptions = remember(ledger.entries) {
        ledger.entries
            .filter { !it.archived }
            .map { it.account.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    // 类目候选按出现次数排：常用的排在前面，一屏就能点到；
    // 长长一条「餐饮/外卖/工作日午餐」也留着，筛选就是要有话直说
    val categoryOptions = remember(ledger.entries) {
        ledger.entries
            .asSequence()
            .filter { !it.archived && it.category.isNotEmpty() }
            .groupingBy { it.category }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
    }

    val entries = remember(ledger.entries, month, dayFilter, filter, query) {
        filter.apply(ledger.entries, month, dayFilter, query)
    }
    // 和列表保持一致：筛完之后这一屏到底花了多少
    val viewExpense = remember(entries) {
        entries.filter { it.kind == Kind.EXPENSE && it.countsTowardTotals }
            .sumOf { it.effectiveAmount }
    }
    val grouped = remember(entries) { entries.groupBy { it.date } }
    // 按金额排的时候不能按日期分块：分块会把「今天的小额」整批压到「昨天的大额」后面，
    // 全局顺序就废了。这时平铺，右侧的日期顶替时间，一眼知道是哪天。
    val byAmount = filter.sort == EntryFilter.AMOUNT_DESC || filter.sort == EntryFilter.AMOUNT_ASC

    Column(Modifier.fillMaxWidth()) {
        MonthStepper(
            month = month,
            label = if (filter.hasRange) rangeLabel(filter) else null,
            onShift = ::shift,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            trailing = {
                Text(
                    text = Fmt.yuan(viewExpense),
                    style = MoneyStyle.copy(fontSize = 14.sp),
                    color = colors.expense,
                )
            },
        )

        Row(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchField(query = query, onQuery = { query = it }, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FilterButton(
                count = filter.activeCount,
                onClick = { filterOpen = true },
            )
        }

        if (!filter.isEmpty || dayFilter != null) {
            Spacer(Modifier.height(8.dp))
            ActiveFilters(
                filter = filter,
                dayFilter = dayFilter,
                onFilter = { filter = it },
                onDay = onDayFilter,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            EmptyState(
                title = when {
                    query.isNotBlank() -> "没有匹配的记录"
                    !filter.isEmpty -> "这些条件下一笔都没有"
                    else -> "这个月还没有流水"
                },
                body = when {
                    query.isNotBlank() -> "换个关键词试试，金额也能搜，像 25 或 25.50 这样写。"
                    !filter.isEmpty -> "把条件放宽一点，或者点上面的标签撤掉一个。"
                    else -> "导入账单，或者点右下角记一笔。"
                },
            ) {
                GhostButton("导入账单", onOpenImport)
            }
            return@Column
        }

        PotPullToRefresh(
            isRefreshing = refreshing,
            onRefresh = onRefresh,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            // 搜索框、筛选按钮留在页头不动，滑的只是下面的列表：
            // 手指落在输入框上左右拖是要选字的，不能被拿去翻月
            MonthSwipe(
                month = month,
                onShift = { shift(it.toLong()) },
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 108.dp),
                ) {
            if (byAmount) {
                items(entries, key = { it.id }, contentType = { "entry" }) { entry ->
                    Box(Modifier.background(colors.panel)) {
                        EntryRow(
                            entry = entry,
                            showDate = true,
                            onClick = { onOpenEntry(entry) },
                            onLongClick = { onLongPressEntry(entry) },
                        )
                    }
                }
                item(key = "tail") { Spacer(Modifier.height(6.dp)) }
            } else {
                grouped.forEach { (date, dayEntries) ->
                    val expense = dayEntries.filter { it.kind == Kind.EXPENSE && it.countsTowardTotals }
                        .sumOf { it.effectiveAmount }
                    val income = dayEntries.filter { it.kind == Kind.INCOME && it.countsTowardTotals }
                        .sumOf { it.amount }
                    item(key = "day-$date") {
                        DayHeader(date = date, expense = expense, income = income)
                    }
                    items(dayEntries, key = { it.id }, contentType = { "entry" }) { entry ->
                        Box(Modifier.background(colors.panel)) {
                            EntryRow(
                                entry = entry,
                                onClick = { onOpenEntry(entry) },
                                onLongClick = { onLongPressEntry(entry) },
                            )
                        }
                    }
                    item(key = "gap-$date") { Spacer(Modifier.height(6.dp)) }
                }
            }
                }
            }
        }
    }

    if (filterOpen) {
        EntryFilterSheet(
            month = month,
            accounts = accountOptions,
            categories = categoryOptions,
            initial = filter,
            entries = ledger.entries,
            day = dayFilter,
            query = query,
            onDismiss = { filterOpen = false },
            onApply = {
                filter = it
                filterOpen = false
            },
        )
    }
}

/* ------------------------------ 页头上的筛选入口 ------------------------------ */

/** 漏斗按钮，右上角一个数字角标表示有几个条件在生效。 */
@Composable
private fun FilterButton(count: Int, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    val active = count > 0
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(if (active) colors.brandTint else colors.panel)
            .clickable {
                haptics(Haptic.Tap)
                onClick()
            }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.FilterList,
            contentDescription = "筛选",
            tint = if (active) colors.brandInk else colors.ink2,
            modifier = Modifier.size(17.dp),
        )
        if (active) {
            Spacer(Modifier.width(4.dp))
            Text("$count", fontSize = 13.sp, color = colors.brandInk, fontWeight = FontWeight.Medium)
        }
    }
}

/** 已生效的条件，一排小标签，点掉就撤。 */
@Composable
private fun ActiveFilters(
    filter: EntryFilter,
    dayFilter: String?,
    onFilter: (EntryFilter) -> Unit,
    onDay: (String?) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dayFilter != null) {
            item(key = "day") {
                ActiveChip(Fmt.shortDate(dayFilter)) { onDay(null) }
            }
        }
        if (filter.kind != EntryFilter.ALL) {
            item(key = "kind") {
                ActiveChip(kindLabel(filter.kind)) { onFilter(filter.copy(kind = EntryFilter.ALL)) }
            }
        }
        val category = filter.category
        if (category != null) {
            item(key = "category") {
                ActiveChip(category.replace("/", " · ")) { onFilter(filter.copy(category = null)) }
            }
        }
        val account = filter.account
        if (account != null) {
            item(key = "account") {
                ActiveChip(account) { onFilter(filter.copy(account = null)) }
            }
        }
        if (filter.minCents != null || filter.maxCents != null) {
            item(key = "amount") {
                ActiveChip(amountLabel(filter)) {
                    onFilter(filter.copy(minCents = null, maxCents = null))
                }
            }
        }
        if (filter.hasRange) {
            item(key = "range") {
                ActiveChip(rangeLabel(filter)) { onFilter(filter.copy(from = null, to = null)) }
            }
        }
        if (filter.sort != EntryFilter.TIME_DESC) {
            item(key = "sort") {
                ActiveChip(sortLabel(filter.sort)) { onFilter(filter.copy(sort = EntryFilter.TIME_DESC)) }
            }
        }
    }
}

@Composable
private fun ActiveChip(label: String, onClear: () -> Unit) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.brandTint)
            .clickable {
                haptics(Haptic.Tap)
                onClear()
            }
            .padding(start = 11.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = colors.brandInk, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.Outlined.Close,
            contentDescription = "移除",
            tint = colors.brandInk,
            modifier = Modifier.size(12.dp),
        )
    }
}

private fun kindLabel(kind: String): String = when (kind) {
    Kind.EXPENSE -> "支出"
    Kind.INCOME -> "收入"
    Kind.TRANSFER -> "转账"
    else -> "全部"
}

private fun sortLabel(sort: String): String = when (sort) {
    EntryFilter.TIME_ASC -> "时间正序"
    EntryFilter.AMOUNT_DESC -> "金额从高到低"
    EntryFilter.AMOUNT_ASC -> "金额从低到高"
    else -> "时间倒序"
}

/** 「¥50.00 ~ ¥800.00」「≥ ¥50.00」「≤ ¥800.00」。 */
private fun amountLabel(filter: EntryFilter): String {
    val min = filter.minCents
    val max = filter.maxCents
    return when {
        min != null && max != null -> {
            val low = minOf(min, max)
            val high = maxOf(min, max)
            Fmt.yuan(low) + " ~ " + Fmt.yuan(high)
        }

        min != null -> "≥ " + Fmt.yuan(min)
        max != null -> "≤ " + Fmt.yuan(max)
        else -> ""
    }
}

/** 顶部月份位置显示的区间：「11-01 ~ 11-20」「11-01 起」「至 11-20」。 */
private fun rangeLabel(filter: EntryFilter): String {
    val from = filter.from
    val to = filter.to
    return when {
        from != null && to != null && from > to -> Fmt.shortDate(to) + " ~ " + Fmt.shortDate(from)
        from != null && to != null -> Fmt.shortDate(from) + " ~ " + Fmt.shortDate(to)
        from != null -> Fmt.shortDate(from) + " 起"
        to != null -> "至 " + Fmt.shortDate(to)
        else -> ""
    }
}

/* ------------------------------ 筛选弹层 ------------------------------ */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun EntryFilterSheet(
    month: String,
    accounts: List<String>,
    categories: List<String>,
    initial: EntryFilter,
    entries: List<Entry>,
    day: String?,
    query: String,
    onDismiss: () -> Unit,
    onApply: (EntryFilter) -> Unit,
) {
    val colors = LocalPotColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initial) }
    var minText by remember { mutableStateOf(initial.minCents?.let { Fmt.editYuan(it) } ?: "") }
    var maxText by remember { mutableStateOf(initial.maxCents?.let { Fmt.editYuan(it) } ?: "") }
    // 非 null 时面板换成日期选择，避免再套一层 sheet
    var picking by remember { mutableStateOf<String?>(null) }
    var preset by remember { mutableStateOf(presetOf(initial)) }

    val matched = remember(draft, entries, month, day, query) {
        draft.apply(entries, month, day, query).size
    }

    fun syncAmount() {
        draft = draft.copy(
            minCents = EntryFilter.cents(minText),
            maxCents = EntryFilter.cents(maxText),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
        contentColor = colors.ink,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        dragHandle = { SheetGrabber() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // 键盘和手势条取最大值，别和 imePadding 叠两层
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (picking == null) "筛选" else if (picking == "from") "起始日期" else "结束日期",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.ink,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (picking == null) "$matched 条符合条件" else "选一天，点确定",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                        )
                    }
                    if (picking == null) {
                        if (!draft.isEmpty) {
                            PotPillButton(
                                text = "重置",
                                icon = Icons.Outlined.RestartAlt,
                                tone = PillTone.Ghost,
                                onClick = {
                                    draft = EntryFilter()
                                    minText = ""
                                    maxText = ""
                                    preset = "month"
                                },
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "关闭",
                                tint = colors.ink3,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))

                val target = picking
                if (target != null) {
                    val fallback = if (target == "from") draft.from else draft.to
                    key(target, fallback) {
                        val pickerState = rememberDatePickerState(
                            initialSelectedDateMillis = Fmt.epochMillis(
                                fallback ?: if (target == "from") month + "-01" else Fmt.today(),
                            ),
                        )
                        DatePicker(
                            state = pickerState,
                            title = null,
                            headline = null,
                            showModeToggle = false,
                            colors = DatePickerDefaults.colors(containerColor = colors.panel),
                        )
                        SheetActions(
                            confirmText = "确定",
                            danger = false,
                            onConfirm = {
                                val picked = pickerState.selectedDateMillis
                                    ?.let { Fmt.fromEpochMillis(it) }
                                if (picked != null) {
                                    draft = if (target == "from") {
                                        draft.copy(from = picked)
                                    } else {
                                        draft.copy(to = picked)
                                    }
                                    preset = "custom"
                                }
                                picking = null
                            },
                            onCancel = { picking = null },
                        )
                    }
                    return@Column
                }

                // 类型
                SectionLabel("类型")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        EntryFilter.ALL to "全部",
                        Kind.EXPENSE to "支出",
                        Kind.INCOME to "收入",
                        Kind.TRANSFER to "转账",
                    ).forEach { (value, label) ->
                        Chip(
                            label = label,
                            active = draft.kind == value,
                            onClick = { draft = draft.copy(kind = value) },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                // 类目
                SectionLabel("类目")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        label = "全部类目",
                        active = draft.category == null,
                        onClick = { draft = draft.copy(category = null) },
                    )
                    categories.forEach { name ->
                        Chip(
                            // 账单里的 `餐饮/外卖` 显示成 `餐饮 · 外卖`，跟条目行的写法一致
                            label = name.replace("/", " · "),
                            active = draft.category == name,
                            onClick = {
                                draft = draft.copy(category = if (draft.category == name) null else name)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                // 账户
                SectionLabel("账户")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Chip(
                        label = "全部账户",
                        active = draft.account == null,
                        onClick = { draft = draft.copy(account = null) },
                    )
                    accounts.forEach { name ->
                        Chip(
                            label = name,
                            active = draft.account == name,
                            onClick = {
                                draft = draft.copy(account = if (draft.account == name) null else name)
                            },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                // 金额
                SectionLabel("金额范围")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SheetField(
                        label = "最低",
                        value = minText,
                        placeholder = "不限",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = { input ->
                            minText = input.filter { it.isDigit() || it == '.' }
                            syncAmount()
                        },
                    )
                    Text(" 至 ", fontSize = 13.sp, color = colors.ink3, modifier = Modifier.padding(horizontal = 6.dp))
                    SheetField(
                        label = "最高",
                        value = maxText,
                        placeholder = "不限",
                        modifier = Modifier.weight(1f),
                        keyboardType = KeyboardType.Decimal,
                        onValueChange = { input ->
                            maxText = input.filter { it.isDigit() || it == '.' }
                            syncAmount()
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "按实付金额算，退款抵扣后的数",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )

                Spacer(Modifier.height(18.dp))
                // 时间
                SectionLabel("时间范围")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "month" to "跟随月份",
                        "7d" to "近 7 天",
                        "30d" to "近 30 天",
                        "thisMonth" to "本月",
                        "custom" to "自定义",
                    ).forEach { (value, label) ->
                        Chip(
                            label = label,
                            active = preset == value,
                            onClick = {
                                preset = value
                                val today = Fmt.today()
                                draft = when (value) {
                                    "7d" -> draft.copy(
                                        from = LocalDate.parse(today).minusDays(6).toString(),
                                        to = today,
                                    )

                                    "30d" -> draft.copy(
                                        from = LocalDate.parse(today).minusDays(29).toString(),
                                        to = today,
                                    )

                                    "thisMonth" -> draft.copy(from = today.take(8) + "01", to = today)
                                    "custom" -> draft
                                    else -> draft.copy(from = null, to = null)
                                }
                            },
                        )
                    }
                }
                if (preset == "custom") {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateButton(
                            text = draft.from?.let { Fmt.shortDate(it) } ?: "起始",
                            active = draft.from != null,
                            modifier = Modifier.weight(1f),
                            onClick = { picking = "from" },
                        )
                        DateButton(
                            text = draft.to?.let { Fmt.shortDate(it) } ?: "结束",
                            active = draft.to != null,
                            modifier = Modifier.weight(1f),
                            onClick = { picking = "to" },
                        )
                    }
                }
                if (draft.hasRange) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "选了日期区间就不再按月看，翻月份会清掉区间",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }

                Spacer(Modifier.height(18.dp))
                // 排序
                SectionLabel("排序")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        EntryFilter.TIME_DESC to "时间 · 最新在前",
                        EntryFilter.TIME_ASC to "时间 · 最早在前",
                        EntryFilter.AMOUNT_DESC to "金额 · 从高到低",
                        EntryFilter.AMOUNT_ASC to "金额 · 从低到高",
                    ).forEach { (value, label) ->
                        Chip(
                            label = label,
                            active = draft.sort == value,
                            onClick = { draft = draft.copy(sort = value) },
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
            }

            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 16.dp)) {
                if (picking == null) {
                    SheetActions(
                        confirmText = "查看 $matched 条",
                        danger = false,
                        onConfirm = { onApply(draft) },
                        onCancel = onDismiss,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalPotColors.current
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = colors.ink3,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun DateButton(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) colors.brandTint else colors.panel2)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            fontSize = 14.sp,
            color = if (active) colors.brandInk else colors.ink3,
            modifier = Modifier.weight(1f),
        )
        Text("改", fontSize = 12.sp, color = colors.ink3)
    }
}

/** 从现有条件反推它属于哪个快捷档，打开面板时高亮对应 chip。 */
private fun presetOf(filter: EntryFilter): String {
    val today = Fmt.today()
    val from = filter.from
    val to = filter.to
    return when {
        from == null && to == null -> "month"
        from == null || to == null -> "custom"
        from == LocalDate.parse(today).minusDays(6).toString() && to == today -> "7d"
        from == LocalDate.parse(today).minusDays(29).toString() && to == today -> "30d"
        from == today.take(8) + "01" && to == today -> "thisMonth"
        else -> "custom"
    }
}

/* ------------------------------ 搜索框 ------------------------------ */

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    // 整行都能点：之前只有占位文字那一小块能吃点击，点框里其他地方没反应
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.panel)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                // 已经把焦点留在框里、但键盘被收下去的情况，点一下也要能叫回来
                focus.requestFocus()
                keyboard?.show()
            }
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .focusRequester(focus),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.brand),
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) {
                        Text("搜商家、备注、分类或金额", fontSize = 14.sp, color = colors.ink3)
                    }
                    inner()
                }
            },
        )
        if (query.isNotEmpty()) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "清空",
                tint = colors.ink3,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onQuery("") },
            )
        }
    }
}

/** 筛选条件要跨进程存活，null 用空串/负数代替，省得 Bundle 里塞 null。 */
private val FilterSaver = mapSaver(
    save = { f ->
        mapOf(
            "kind" to f.kind,
            "category" to f.category.orEmpty(),
            "account" to f.account.orEmpty(),
            "min" to (f.minCents ?: -1L),
            "max" to (f.maxCents ?: -1L),
            "from" to f.from.orEmpty(),
            "to" to f.to.orEmpty(),
            "sort" to f.sort,
        )
    },
    restore = { m ->
        EntryFilter(
            kind = m["kind"] as String,
            category = (m["category"] as String).ifEmpty { null },
            account = (m["account"] as String).ifEmpty { null },
            minCents = (m["min"] as Long).takeIf { it >= 0 },
            maxCents = (m["max"] as Long).takeIf { it >= 0 },
            from = (m["from"] as String).ifEmpty { null },
            to = (m["to"] as String).ifEmpty { null },
            sort = m["sort"] as String,
        )
    },
)
