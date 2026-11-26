package cn.neeto.stockpot.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Link
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerOps
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Source
import cn.neeto.stockpot.ui.components.Amount
import cn.neeto.stockpot.ui.components.CategoryAvatar
import cn.neeto.stockpot.ui.components.Chip
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.PotCardShape
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 挑对面那一笔。
 *
 * 以前这是个底部弹层：条数一多就得在里面滚，搜也没法搜，更看不着那条到底是谁——
 * 金额一样的两笔退款只差日期，凭一行「¥1.12 · 10-25」根本不敢下手。
 * 现在是一整页：搜索、按时间范围收窄、点开每一条看细节（备注、账户、单号、
 * 与当前这笔相隔几天、差多少钱），看完再决定。
 *
 * 默认只列金额分毫不差的；部分退款和「全都看看」各是一档，切过去才知道放宽了什么，
 * 不会像以前那样「只看金额对得上」里面还混着一堆十几块的零星退款。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RefundPickScreen(
    ledger: Ledger,
    entry: Entry,
    onBack: () -> Unit,
    onPick: (Entry) -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    val isExpense = entry.kind == Kind.EXPENSE
    val remaining = entry.amount - entry.refundedAmount
    // 退款这一侧没有「余额」的概念，比的是它自己的金额
    val targetCents = if (isExpense) remaining else entry.amount

    var query by rememberSaveable { mutableStateOf("") }
    var matchId by rememberSaveable { mutableStateOf(LedgerOps.RefundMatch.Exact.id) }
    var windowId by rememberSaveable { mutableStateOf(PickWindow.All.id) }
    // 点开看细节的那一条：选中和展开是同一件事，省得再点一次「选它」
    var openedId by rememberSaveable { mutableStateOf("") }

    val match = LedgerOps.RefundMatch.of(matchId)
    val window = PickWindow.of(windowId)
    val keyword = query.trim()

    // 池子给大一点：页面自己会滚，别在外面先截断了再筛
    val pool = remember(ledger.entries, entry.id, entry.refundedAmount, match) {
        LedgerOps.refundCandidates(ledger, entry, limit = 400, match = match)
    }
    val shown = remember(pool, keyword, window, entry.date) {
        pool.filter { candidate ->
            window.near(entry.date, candidate.date) &&
                (keyword.isEmpty() || pickHay(candidate).contains(keyword, ignoreCase = true))
        }
    }
    val opened = shown.firstOrNull { it.id == openedId }

    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding(),
    ) {
        Column(Modifier.statusBarsPadding()) {
            EditorBar(
                title = if (isExpense) "挑一笔退款" else "挑原来的消费",
                onBack = onBack,
                onConfirm = null,
            )
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            CurrentCard(entry = entry, isExpense = isExpense)
            Spacer(Modifier.height(10.dp))
            SearchBar(
                value = query,
                placeholder = "搜平台、备注、分类、账户或金额",
                onValueChange = { query = it },
            )
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickWindow.entries.forEach { option ->
                    Chip(
                        label = option.label,
                        active = window == option,
                        onClick = { windowId = option.id },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LedgerOps.RefundMatch.entries.forEach { option ->
                    Chip(
                        label = option.label,
                        active = match == option,
                        onClick = { matchId = option.id },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append("${shown.size} 条 · ${match.hint}")
                    if (window != PickWindow.All) append(" · ${window.label}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
        }

        if (shown.isEmpty()) {
            EmptyState(
                title = if (keyword.isNotEmpty()) "没搜到「$keyword」" else "这一档没有能配的",
                body = when {
                    keyword.isNotEmpty() -> "换个词试试；金额也能搜，像 1.12 这样写。"
                    match == LedgerOps.RefundMatch.Exact && isExpense ->
                        "没有金额正好是 ${Fmt.yuan(remaining)} 的退款。切到「允许少退」看看部分退的，或者「全部」自己认。"
                    match == LedgerOps.RefundMatch.Exact ->
                        "没有剩下 ${Fmt.yuan(entry.amount)} 的消费。切到「允许少退」或者「全部」再看看。"
                    window != PickWindow.All -> "把时间范围放回「全部」，或者换个金额档。"
                    isExpense -> "可能那一条还没导入，或者已经配给别的消费了（先解开那边）。"
                    else -> "没有花得比这笔退款还多的消费。先把那笔支出记上，或者切到「全部」看看。"
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 10.dp,
                    end = 10.dp,
                    top = 2.dp,
                    bottom = if (opened != null) 8.dp else overlayBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(shown, key = { it.id }) { candidate ->
                    PickRow(
                        candidate = candidate,
                        targetCents = targetCents,
                        targetDate = entry.date,
                        opened = candidate.id == openedId,
                        onClick = {
                            haptics(Haptic.Tap)
                            openedId = if (openedId == candidate.id) "" else candidate.id
                        },
                    )
                    AnimatedVisibility(
                        visible = candidate.id == openedId,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        PickDetail(
                            candidate = candidate,
                            targetCents = targetCents,
                            targetDate = entry.date,
                            isExpense = isExpense,
                            onPick = { onPick(candidate) },
                        )
                    }
                }
            }
        }

        // 展开的那条下面已经有「就是这笔」；这条是给滚到别处时准备的，
        // 手指在长列表里翻着翻着也能一眼看到当前选的是谁
        if (opened != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pickTitle(opened),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${Fmt.yuan(opened.amount)} · ${pickWhen(opened)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                PotPillButton(
                    text = "就是这笔",
                    icon = Icons.Outlined.Link,
                    onClick = { onPick(opened) },
                )
            }
        }
    }
}

/* ------------------------------ 当前这笔 ------------------------------ */

/** 顶上一张条：挑的是谁，差多少还没退。不然翻两条就忘了自己在配什么。 */
@Composable
private fun CurrentCard(entry: Entry, isExpense: Boolean) {
    val colors = LocalPotColors.current
    val remaining = entry.amount - entry.refundedAmount
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PotCardShape)
            .background(colors.panel)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pickTitle(entry),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Amount(cents = entry.amount, kind = entry.kind, fontSize = 15)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildString {
                append(pickWhen(entry))
                if (entry.account.isNotBlank()) append(" · ").append(entry.account)
                if (entry.category.isNotBlank()) append(" · ").append(entry.category.replace("/", " · "))
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = when {
                isExpense && remaining != entry.amount -> "还要退 ${Fmt.yuan(remaining)}"
                isExpense -> "找一笔金额 ${Fmt.yuan(entry.amount)} 的退款"
                // 说清底线：比这笔退款还少的消费根本不在列表里
                else -> "只列支出不少于 ${Fmt.yuan(entry.amount)} 的消费"
            },
            fontSize = 11.sp,
            color = colors.brandInk,
            maxLines = 1,
        )
    }
}

/* ------------------------------ 一行 ------------------------------ */

@Composable
private fun PickRow(
    candidate: Entry,
    targetCents: Long,
    targetDate: String,
    opened: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val gap = candidate.amount - targetCents
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (opened) colors.brandTint else colors.panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(candidate.category.ifEmpty { "其" }, size = 36)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = pickTitle(candidate),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            // 日期 + 具体时间：同一天的两笔只有时分能分开，缺了就写「未记录时间」
            Text(
                text = buildString {
                    append(Fmt.shortDate(candidate.date))
                    append(' ')
                    append(candidate.time.ifBlank { "未记录时间" })
                    if (candidate.account.isNotBlank()) append(" · ").append(candidate.account)
                },
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Amount(cents = candidate.amount, kind = candidate.kind, fontSize = 15)
            if (gap != 0L) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (gap > 0) "多 ${Fmt.yuan(gap)}" else "少 ${Fmt.yuan(-gap)}",
                    fontSize = 10.sp,
                    color = colors.ink3,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = if (opened) colors.brand else colors.ink3.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/* ------------------------------ 细节 ------------------------------ */

/**
 * 点开一条之后摊开的东西：看账得知道的都在，改不了的（单号、来源）也摆出来。
 * 末尾那颗按钮是「就是这笔」，看完直接定。
 */
@Composable
private fun PickDetail(
    candidate: Entry,
    targetCents: Long,
    targetDate: String,
    isExpense: Boolean,
    onPick: () -> Unit,
) {
    val colors = LocalPotColors.current
    val gap = candidate.amount - targetCents
    val days = dayGap(targetDate, candidate.date)
    val rows = buildList {
        add("时间" to pickWhen(candidate))
        add("相隔" to when {
            days == null -> "算不出来"
            days == 0L -> "同一天"
            days > 0 -> "$days 天前"
            else -> "${-days} 天后"
        })
        add("差多少钱" to when {
            gap == 0L -> if (isExpense) "金额正好，一分不差" else "金额正好"
            gap > 0L -> "比这笔多 ${Fmt.yuan(gap)}"
            else -> "比这笔少 ${Fmt.yuan(-gap)}"
        })
        if (candidate.category.isNotBlank()) add("分类" to candidate.category.replace("/", " · "))
        if (candidate.account.isNotBlank()) add("账户" to candidate.account)
        if (candidate.counterparty.isNotBlank()) add("对方" to candidate.counterparty)
        if (candidate.note.isNotBlank()) add("备注" to candidate.note)
        add("来源" to pickSourceLabel(candidate.source))
        if (candidate.externalId.isNotBlank()) add("单号" to candidate.externalId)
        val status = when {
            candidate.refundedAmount > 0 -> "已退 ${Fmt.yuan(candidate.refundedAmount)}"
            candidate.excluded -> "不计入合计"
            else -> ""
        }
        if (status.isNotEmpty()) add("状态" to status)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.panel2)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        rows.forEachIndexed { index, (label, value) ->
            if (index > 0) {
                Spacer(Modifier.height(7.dp))
            }
            Row {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = colors.ink3,
                    modifier = Modifier.width(64.dp),
                )
                Text(
                    text = value,
                    fontSize = 12.sp,
                    color = colors.ink2,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isExpense) "就是这笔退回来的" else "这笔退的就是它",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                modifier = Modifier.weight(1f),
            )
            PotPillButton(
                text = "就是这笔",
                icon = Icons.Outlined.Link,
                onClick = onPick,
            )
        }
    }
}

/* ------------------------------ 小工具 ------------------------------ */

/** 时间范围：以当前这笔的日期为准，前后多少天。退款一般就发生在消费之后不久。 */
private enum class PickWindow(val id: String, val label: String, val days: Long) {
    All("all", "全部时间", 0L),
    Month("month", "前后 30 天", 30L),
    Quarter("quarter", "前后 90 天", 90L),
    Half("half", "前后 180 天", 180L),
    ;

    fun near(base: String, other: String): Boolean {
        if (days <= 0L) return true
        val gap = dayGap(base, other) ?: return true
        return gap <= days
    }

    companion object {
        fun of(id: String): PickWindow = entries.firstOrNull { it.id == id } ?: All
    }
}

/** 两个 `YYYY-MM-DD` 差几天（正数 = `other` 在 `base` 之前）。 */
private fun dayGap(base: String, other: String): Long? = runCatching {
    ChronoUnit.DAYS.between(LocalDate.parse(other), LocalDate.parse(base))
}.getOrNull()

private fun pickTitle(entry: Entry): String = entry.counterparty.ifEmpty {
    entry.note.ifEmpty { entry.category.ifEmpty { "未命名" } }
}

/** `10-25 17:32`；没有时分就只写日期，别硬编一个 00:00。 */
private fun pickWhen(entry: Entry): String =
    Fmt.shortDate(entry.date) + if (entry.time.isBlank()) "" else " ${entry.time}"

/** 搜索串：平台、备注、分类、账户、金额、日期都能搜到。 */
private fun pickHay(entry: Entry): String = buildString {
    append(entry.counterparty).append(' ')
    append(entry.note).append(' ')
    append(entry.category).append(' ')
    append(entry.account).append(' ')
    append(Fmt.cents(entry.amount)).append(' ')
    append(entry.date).append(' ')
    append(entry.time)
}

private fun pickSourceLabel(source: String): String = when (source) {
    Source.WECHAT -> "微信账单"
    Source.ALIPAY -> "支付宝账单"
    Source.ICOST -> "iCost 导出"
    Source.GENERIC -> "通用账单"
    Source.IMPORTED -> "导入"
    Source.MANUAL -> "手工记账"
    else -> source.ifBlank { "未知" }
}
