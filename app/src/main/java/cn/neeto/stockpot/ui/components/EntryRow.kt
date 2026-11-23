package cn.neeto.stockpot.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.model.Entry
import cn.neeto.stockpot.core.model.Kind
import cn.neeto.stockpot.ui.theme.LocalPotColors

/** 一行流水。所有列表共用，保证同一个账目在哪儿看都长一样。 */
@Composable
fun EntryRow(
    entry: Entry,
    modifier: Modifier = Modifier,
    showDate: Boolean = false,
    projectName: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalPotColors.current
    val title = entry.counterparty.ifEmpty {
        entry.note.ifEmpty { entry.category.ifEmpty { "未命名" } }
    }
    // 全额退款：原金额划掉，别让人以为这月真的花了这么多
    val fullyRefunded = entry.kind == Kind.EXPENSE && entry.refundedAmount > 0 &&
        entry.refundedAmount >= entry.amount
    val subtitle = buildList {
        if (entry.category.isNotEmpty()) add(entry.category.replace("/", " · "))
        if (entry.account.isNotEmpty()) add(entry.account)
        if (projectName != null) add(projectName)
        if (fullyRefunded) add("已全额退款")
        else if (entry.refundedAmount > 0) add("已退 ${Fmt.yuan(entry.refundedAmount)}")
        // 退款行配上原消费后标一下：列表里一眼能看出这条点进去有得跳
        if (entry.kind != Kind.EXPENSE && entry.refundLinkId.isNotEmpty()) add("已关联")
        if (entry.excluded) add("不计收支")
    }.joinToString("  ·  ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryAvatar(entry.category.ifEmpty { "其" }, size = 38)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (entry.kind == Kind.TRANSFER) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(colors.ink3),
                    )
                }
            }
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Amount(
                cents = if (fullyRefunded) entry.amount else entry.effectiveAmount,
                kind = entry.kind,
                fontSize = 15,
                strikethrough = fullyRefunded,
            )
            val meta = when {
                showDate -> Fmt.shortDate(entry.date)
                entry.time.isNotEmpty() -> entry.time
                entry.archived -> "已收起"
                else -> ""
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(meta, fontSize = 12.sp, color = colors.ink3)
            }
        }
    }
}

/** 日期小标题：`11月22日 周六   -¥128.00` */
@Composable
fun DayHeader(
    date: String,
    expense: Long,
    income: Long,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = Fmt.dayLabel(date),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = colors.ink3,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (income > 0) {
                Text("+${Fmt.yuan(income)}", fontSize = 13.sp, color = colors.income)
                Spacer(Modifier.width(8.dp))
            }
            if (expense > 0) {
                Text("-${Fmt.yuan(expense)}", fontSize = 13.sp, color = colors.expense)
            }
        }
    }
}
