package cn.neeto.stockpot.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerMath
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.DayHeader
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.EntryRow
import cn.neeto.stockpot.ui.components.MonthStepper
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

@Composable
fun EntriesScreen(
    ledger: Ledger,
    month: String,
    dayFilter: String?,
    onMonth: (String) -> Unit,
    onDayFilter: (String?) -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onOpenImport: () -> Unit,
) {
    val colors = LocalPotColors.current
    var kindFilter by rememberSaveable { mutableStateOf("all") }
    var accountFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var sortBy by rememberSaveable { mutableStateOf("time") }
    var query by rememberSaveable { mutableStateOf("") }

    // 当前月出现过的账户，用来做「只看某张卡」的筛选
    val accountOptions = remember(ledger.entries, month) {
        ledger.entries
            .filter { !it.archived && Fmt.monthOf(it.date) == month }
            .map { it.account.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    val entries = remember(ledger.entries, month, kindFilter, accountFilter, sortBy, dayFilter, query) {
        val needle = query.trim()
        val filtered = ledger.entries
            .filter { !it.archived }
            .filter { Fmt.monthOf(it.date) == month }
            .filter { dayFilter == null || it.date == dayFilter }
            .filter { kindFilter == "all" || it.kind == kindFilter }
            .filter { accountFilter == null || it.account.trim() == accountFilter }
            .filter { entry ->
                needle.isEmpty() ||
                    entry.counterparty.contains(needle, ignoreCase = true) ||
                    entry.note.contains(needle, ignoreCase = true) ||
                    entry.category.contains(needle, ignoreCase = true) ||
                    entry.account.contains(needle, ignoreCase = true)
            }
        when (sortBy) {
            "amountDesc" -> filtered.sortedWith(
                compareByDescending<Entry> { it.effectiveAmount }.thenByDescending { it.date },
            )
            "amountAsc" -> filtered.sortedWith(
                compareBy<Entry> { it.effectiveAmount }.thenByDescending { it.date },
            )
            else -> filtered.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.time })
        }
    }

    val grouped = remember(entries) { entries.groupBy { it.date } }
    val summary = remember(ledger.entries, month) { LedgerMath.summarize(ledger.entries, month) }

    Column(Modifier.fillMaxWidth()) {
        MonthStepper(
            month = month,
            onShift = {
                onMonth(Fmt.shiftMonth(month, it))
                onDayFilter(null)
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
            trailing = {
                Text(
                    text = Fmt.yuan(summary.expense),
                    style = MoneyStyle.copy(fontSize = 14.sp),
                    color = colors.expense,
                )
            },
        )

        SearchField(query = query, onQuery = { query = it }, modifier = Modifier.padding(horizontal = 16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(
                    listOf(
                        "all" to "全部",
                        Kind.EXPENSE to "支出",
                        Kind.INCOME to "收入",
                        Kind.TRANSFER to "转账",
                    ),
                    key = { it.first },
                ) { (value, label) ->
                    Chip(label = label, active = kindFilter == value, onClick = { kindFilter = value })
                }
                val day = dayFilter
                if (day != null) {
                    item(key = "day-chip") {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.ink)
                                .clickable { onDayFilter(null) }
                                .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(Fmt.shortDate(day), fontSize = 12.sp, color = colors.panel)
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = null,
                                tint = colors.panel,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            SortButton(sortBy = sortBy, onChange = { sortBy = it })
        }

        if (accountOptions.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "all-accounts") {
                    Chip(
                        label = "全部账户",
                        active = accountFilter == null,
                        onClick = { accountFilter = null },
                    )
                }
                items(accountOptions, key = { it }) { name ->
                    Chip(
                        label = name,
                        active = accountFilter == name,
                        onClick = { accountFilter = if (accountFilter == name) null else name },
                    )
                }
            }
        }

        if (entries.isEmpty()) {
            EmptyState(
                title = when {
                    query.isNotBlank() -> "没有匹配的记录"
                    accountFilter != null -> "这个账户本月没有记录"
                    else -> "这个月还没有流水"
                },
                body = when {
                    query.isNotBlank() -> "换个关键词试试。"
                    accountFilter != null -> "换个账户，或者点上面的「全部账户」。"
                    else -> "导入账单，或者点右下角记一笔。"
                },
            ) {
                GhostButton("导入账单", onOpenImport)
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
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
                        EntryRow(entry = entry, onClick = { onOpenEntry(entry) })
                    }
                }
                item(key = "gap-$date") { Spacer(Modifier.height(6.dp)) }
            }
        }
    }
}

private val sortOptions = listOf(
    "time" to "按时间",
    "amountDesc" to "金额从高到低",
    "amountAsc" to "金额从低到高",
)

@Composable
private fun SortButton(sortBy: String, onChange: (String) -> Unit) {
    val colors = LocalPotColors.current
    var open by remember { mutableStateOf(false) }
    val label = sortOptions.firstOrNull { it.first == sortBy }?.second ?: "按时间"
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable { open = true }
                .padding(horizontal = 7.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.SwapVert,
                contentDescription = "排序",
                tint = colors.ink3,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(2.dp))
            Text(label, fontSize = 12.sp, color = colors.ink3, maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sortOptions.forEach { (value, text) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontSize = 14.sp,
                            color = if (value == sortBy) colors.brandInk else colors.ink,
                        )
                    },
                    onClick = {
                        onChange(value)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(colors.panel)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
            )
            if (query.isEmpty()) {
                Text("搜商家、备注、分类", fontSize = 14.sp, color = colors.ink3)
            }
        }
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
