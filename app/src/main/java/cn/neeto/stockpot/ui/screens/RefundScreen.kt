package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.ui.components.Amount
import cn.neeto.stockpot.ui.components.CategoryAvatar
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 退款配对页。
 *
 * 以前只能在条目详情里配，而且得那笔已经「退过一点」才看得见入口——
 * 一笔从没配过的新消费根本没法手动关联。这里把三堆东西摊开：
 * 没配对的退款、还能退的支出、已经配好的对，都能搜、能按时间和金额筛。
 *
 * 选中一条 → 底下出现「挑对应的那笔」→ 进 [RefundPickScreen]（整页，能搜能筛，
 * 点开一条看细节再定），挑回来两边互相跳，钱只算一次。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefundScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onOpenEntry: (Entry) -> Unit,
    onUnlink: (String) -> Unit,
    onAutoPair: () -> Int,
    onPickFor: (Entry) -> Unit,
    onToast: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val overview = remember(ledger.entries) { LedgerOps.refundOverview(ledger) }
    var tabId by rememberSaveable { mutableStateOf(RefundTab.Refunds.id) }
    var query by rememberSaveable { mutableStateOf("") }
    // 默认全部：待配对的退款本来就不多，藏起来反而让人以为配完了
    var rangeLabel by rememberSaveable { mutableStateOf(RefundRange.All.label) }
    var matchOnly by rememberSaveable { mutableStateOf(false) }
    var selectedId by rememberSaveable { mutableStateOf("") }
    var confirmAuto by remember { mutableStateOf(false) }

    val tab = RefundTab.of(tabId)
    val range = RefundRange.of(rangeLabel)
    val today = remember { LocalDate.parse(Fmt.today()) }
    val keyword = query.trim()

    fun recent(entry: Entry): Boolean {
        if (range.days <= 0L) return true
        val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return true
        return ChronoUnit.DAYS.between(date, today) <= range.days
    }

    fun matches(entry: Entry): Boolean =
        keyword.isEmpty() || refundHay(entry).contains(keyword, ignoreCase = true)

    /** 「金额能对上」：退款正好等于这笔还没退的余额，或者反过来。 */
    fun hasTwin(entry: Entry): Boolean = if (entry.kind == Kind.EXPENSE) {
        overview.pendingRefunds.any { it.amount == entry.amount - entry.refundedAmount }
    } else {
        overview.openExpenses.any { it.amount - it.refundedAmount == entry.amount }
    }

    val refunds = remember(overview, keyword, range, matchOnly) {
        overview.pendingRefunds.filter { recent(it) && matches(it) && (!matchOnly || hasTwin(it)) }
    }
    val expenses = remember(overview, keyword, range, matchOnly) {
        overview.openExpenses.filter { recent(it) && matches(it) && (!matchOnly || hasTwin(it)) }
    }
    val pairs = remember(overview, keyword, range) {
        overview.pairs.filter { pair ->
            recent(pair.refund) && (matches(pair.expense) || matches(pair.refund))
        }
    }

    val shown = when (tab) {
        RefundTab.Refunds -> refunds
        RefundTab.Expenses -> expenses
        RefundTab.Pairs -> emptyList()
    }
    val selected = shown.firstOrNull { it.id == selectedId }
    // 换了筛选条件或切了 tab，选中的那条可能已经不在列表里了
    LaunchedEffect(tabId, keyword, rangeLabel, matchOnly, overview) {
        if (selectedId.isNotEmpty() && shown.none { it.id == selectedId }) selectedId = ""
    }

    val autoPreview = remember(ledger.entries) { LedgerOps.autoPairRefunds(ledger).second }

    Column(Modifier.fillMaxSize()) {
        EditorBar(
            title = "退款配对",
            onBack = onBack,
            onConfirm = null,
            trailing = if (overview.pendingRefunds.isEmpty() || autoPreview == 0) {
                null
            } else {
                {
                    PotPillButton(
                        text = "自动配 $autoPreview",
                        icon = Icons.Outlined.AutoFixHigh,
                        modifier = Modifier.padding(end = 4.dp),
                        onClick = { confirmAuto = true },
                    )
                }
            },
        )

        Column(Modifier.padding(horizontal = 16.dp)) {
            SearchBar(
                value = query,
                placeholder = "搜平台、分类、账户或金额",
                onValueChange = { query = it },
            )
            Spacer(Modifier.height(10.dp))
            IconSegmented(
                options = listOf(
                    IconOption(RefundTab.Refunds.id, "待配退款", Icons.Outlined.Undo),
                    IconOption(RefundTab.Expenses.id, "可退支出", Icons.Outlined.ReceiptLong),
                    IconOption(RefundTab.Pairs.id, "已配对", Icons.Outlined.Link),
                ),
                selected = tab.id,
                onSelect = {
                    tabId = it
                    selectedId = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${overview.pendingRefunds.size} 笔待配 · ${overview.openExpenses.size} 笔可退 · ${overview.pairs.size} 对已配",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (tab == RefundTab.Refunds) {
                Text(
                    text = "只收收入类的退款；余额宝、零钱通这种理财进出的不在这儿。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RefundRange.entries.forEach { option ->
                    Chip(
                        label = option.label,
                        active = range == option,
                        onClick = { rangeLabel = option.label },
                    )
                }
                if (tab != RefundTab.Pairs) {
                    Chip(
                        label = "只看金额对得上",
                        active = matchOnly,
                        onClick = { matchOnly = !matchOnly },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        if (tab == RefundTab.Pairs) {
            if (pairs.isEmpty()) {
                EmptyState(
                    title = if (overview.pairs.isEmpty()) "还没有配好的退款" else "这个范围里没有",
                    body = if (overview.pairs.isEmpty()) {
                        "在「待配退款」里选一条，再挑出它退的是哪笔消费，钱就只算一次。"
                    } else {
                        "换个时间范围，或者清掉搜索词再看看。"
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = overlayBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(pairs, key = { it.refund.id }) { pair ->
                        PairCard(
                            pair = pair,
                            onOpenExpense = { onOpenEntry(pair.expense) },
                            onOpenRefund = { onOpenEntry(pair.refund) },
                            onUnlink = {
                                onUnlink(pair.expense.id)
                                onToast("已解除关联")
                            },
                        )
                    }
                }
            }
        } else {
            if (shown.isEmpty()) {
                EmptyState(
                    title = if (keyword.isNotEmpty()) "没搜到「$keyword」" else if (tab == RefundTab.Refunds) "没有待配对的退款" else "没有还能退的支出",
                    body = when {
                        keyword.isNotEmpty() -> "换个词试试，金额也能搜，比如 25.00。"
                        tab == RefundTab.Refunds -> "导入账单时对不上的退款会留在这里；配好之后钱只算一次，两条也能互相跳。"
                        else -> "已经退完的支出不会出现在这里。"
                    },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(shown, key = { it.id }) { entry ->
                        RefundEntryRow(
                            entry = entry,
                            hint = hintFor(entry, overview),
                            selected = entry.id == selectedId,
                            onClick = { selectedId = if (selectedId == entry.id) "" else entry.id },
                        )
                    }
                }
            }
        }

        // 选中之后的动作条：手机上比长按菜单好按，也不用记手势
        if (selected != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel)
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${if (selected.kind == Kind.EXPENSE) "这笔支出" else "这笔退款"} ${Fmt.yuan(selected.amount)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (selected.kind == Kind.EXPENSE) "挑出退回来的那一笔" else "挑出它退的是哪笔消费",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(10.dp))
                PotPillButton(
                    text = "挑对应的那笔",
                    icon = Icons.Outlined.Link,
                    onClick = { onPickFor(selected) },
                )
            }
        }
    }

    if (confirmAuto) {
        PotConfirmSheet(
            title = "自动配对 $autoPreview 对？",
            body = "只配「金额正好、日期最近且唯一」的那种，拿不准的留给你自己挑。配好之后退款行不再计入合计，钱只算一次。",
            confirmText = "自动配对",
            danger = false,
            onDismiss = { confirmAuto = false },
            onConfirm = {
                confirmAuto = false
                val linked = onAutoPair()
                onToast(if (linked > 0) "已配好 $linked 对" else "这次没有能确定的")
            },
        )
    }
}

/* ------------------------------ 列表 ------------------------------ */

private enum class RefundTab(val id: String) {
    Refunds("refunds"),
    Expenses("expenses"),
    Pairs("pairs");

    companion object {
        fun of(id: String): RefundTab = entries.firstOrNull { it.id == id } ?: Refunds
    }
}

private enum class RefundRange(val label: String, val days: Long) {
    All("全部", 0L),
    Recent("近 90 天", 90L),
    Month("近 30 天", 30L),
    ;

    companion object {
        fun of(label: String): RefundRange = entries.firstOrNull { it.label == label } ?: All
    }
}

/** 列表里那句提示：能不能直接配上、差多少。 */
private fun hintFor(entry: Entry, overview: LedgerOps.RefundOverview): String? {
    val remaining = entry.amount - entry.refundedAmount
    return if (entry.kind == Kind.EXPENSE) {
        val exact = overview.pendingRefunds.filter { it.amount == remaining }
        when {
            exact.isNotEmpty() -> "有一笔 ${Fmt.yuan(remaining)} 的退款正好对上"
            entry.refundedAmount > 0 -> "还要退 ${Fmt.yuan(remaining)}"
            else -> null
        }
    } else {
        val exact = overview.openExpenses.filter { it.amount - it.refundedAmount == entry.amount }
        if (exact.isNotEmpty()) "有一笔 ${Fmt.yuan(entry.amount)} 的支出正好对上" else null
    }
}

@Composable
private fun RefundEntryRow(
    entry: Entry,
    hint: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    val title = entry.counterparty.ifEmpty {
        entry.note.ifEmpty { entry.category.ifEmpty { "未命名" } }
    }
    val subtitle = buildList {
        add(Fmt.shortDate(entry.date))
        if (entry.time.isNotBlank()) add(entry.time)
        if (entry.account.isNotBlank()) add(entry.account)
        if (entry.category.isNotBlank()) add(entry.category.replace("/", " · "))
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.brandTint else Color.Transparent)
            .clickable {
                haptics(Haptic.Tap)
                onClick()
            }
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(entry.category.ifEmpty { "退" }, size = 36)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = hint,
                    fontSize = 11.sp,
                    color = if (hint.startsWith("有一笔")) colors.brand else colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Amount(cents = entry.amount, kind = entry.kind, fontSize = 15)
            if (entry.kind == Kind.EXPENSE && entry.refundedAmount > 0) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已退 ${Fmt.yuan(entry.refundedAmount)}",
                    fontSize = 10.sp,
                    color = colors.ink3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) colors.brand else colors.ink3.copy(alpha = 0.5f),
            modifier = Modifier.size(19.dp),
        )
    }
}

@Composable
private fun PairCard(
    pair: LedgerOps.RefundPair,
    onOpenExpense: () -> Unit,
    onOpenRefund: () -> Unit,
    onUnlink: () -> Unit,
) {
    val colors = LocalPotColors.current
    PotCard(padding = PaddingValues(0.dp)) {
        PairLine(
            entry = pair.expense,
            label = "消费",
            onClick = onOpenExpense,
        )
        Hairline(Modifier.padding(start = 52.dp))
        PairLine(
            entry = pair.refund,
            label = "退款",
            onClick = onOpenRefund,
        )
        Hairline()
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PotPillButton(
                text = "打开消费",
                icon = Icons.Outlined.ReceiptLong,
                tone = PillTone.Ghost,
                onClick = onOpenExpense,
            )
            PotPillButton(
                text = "解除关联",
                icon = Icons.Outlined.LinkOff,
                tone = PillTone.Danger,
                onClick = onUnlink,
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PairLine(entry: Entry, label: String, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "$label · ${entry.counterparty.ifEmpty { entry.note.ifEmpty { entry.category.ifEmpty { "未命名" } } }}",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = Fmt.dayLabel(entry.date) + if (entry.time.isBlank()) "" else " · ${entry.time}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Amount(cents = entry.amount, kind = entry.kind, fontSize = 14)
    }
}

/** 搜索用的那串字：平台、备注、分类、账户、金额都能搜到。金额按元写，25.00 这种。 */
private fun refundHay(entry: Entry): String = buildString {
    append(entry.counterparty).append(' ')
    append(entry.note).append(' ')
    append(entry.category).append(' ')
    append(entry.account).append(' ')
    append(Fmt.cents(entry.amount))
}
