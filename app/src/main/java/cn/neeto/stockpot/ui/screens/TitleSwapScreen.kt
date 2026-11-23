package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.SwapHit
import cn.neeto.stockpot.core.data.SwapRule
import cn.neeto.stockpot.core.data.TitleSwap
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.Amount
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors

/**
 * 条目标题整理：把「美团 / 淘宝闪购」这类把平台名当标题的条目扫出来，
 * 让用户看一眼换完之后长什么样，再一次换掉。
 *
 * 全程只碰 [cn.neeto.stockpot.core.model.Entry.counterparty] 和 `note` 两个字段，
 * 金额、分类、时间、单号一个不动，所以换错了再换一次就回去了。
 */
@Composable
fun TitleSwapScreen(
    ledger: Ledger,
    words: Set<String>,
    onToggleRule: (SwapRule) -> Unit,
    onBack: () -> Unit,
    onSwap: (Set<String>) -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    // 账本或开关一变就重扫，列表永远跟当前数据一致
    val hits = remember(ledger.entries, words) { TitleSwap.scan(ledger.entries, words) }
    // 新的一批结果默认全选；用户在列表里取消的那些，换完就自然从结果里消失了
    var picked by remember(hits) { mutableStateOf(hits.map { it.entry.id }.toSet()) }

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "条目标题整理", onBack = onBack, onConfirm = null)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "intro") {
                Text(
                    text = "微信、支付宝的账单里，交易对方常常只写「美团」「淘宝闪购」，" +
                        "买了什么反而在备注里。列表只看标题，一屏下来等于什么都没说。\n\n" +
                        "打开下面的规则，它会去认这类条目，把标题和备注对调——" +
                        "金额、分类、时间都不动，换错了再换一次就回来了。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }

            item(key = "rules") {
                PotCard(padding = PaddingValues(0.dp)) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)) {
                        SectionTitle("认哪些")
                    }
                    SwapRule.builtIn.forEachIndexed { index, rule ->
                        if (index > 0) Hairline(Modifier.padding(start = 16.dp))
                        val on = rule.words.any { it in words }
                        RuleRow(
                            label = rule.label,
                            hint = "${rule.hint} · ${rule.words.joinToString("、")}",
                            count = if (on) hits.count { it.ruleLabel == rule.label } else 0,
                            checked = on,
                            onCheckedChange = { onToggleRule(rule) },
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }

            item(key = "hits-head") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (hits.isEmpty()) "没有命中的条目" else "命中 ${hits.size} 条",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    if (hits.isNotEmpty()) {
                        val all = picked.size == hits.size
                        Text(
                            text = if (all) "全不选" else "全选",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.brand,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    haptics(Haptic.Tap)
                                    picked = if (all) emptySet() else hits.map { it.entry.id }.toSet()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (hits.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "这会儿没有要换的",
                        body = "标题里认出了上面那些平台名、而且备注里确实写了内容，条目才会出现在这里。" +
                            "没打开任何规则也是一样。",
                    )
                }
            } else {
                items(hits, key = { it.entry.id }) { hit ->
                    SwapRow(
                        hit = hit,
                        picked = hit.entry.id in picked,
                        onToggle = {
                            haptics(Haptic.Tap)
                            picked = if (hit.entry.id in picked) picked - hit.entry.id else picked + hit.entry.id
                        },
                    )
                }
            }
        }

        // 底部只放一件事：换。没选的时候按钮是灰的，不给按
        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.canvas)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        ) {
            val ready = picked.isNotEmpty()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (ready) colors.brand else colors.panel2)
                    .clickable(enabled = ready) {
                        haptics(Haptic.Confirm)
                        onSwap(picked)
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = when {
                        hits.isEmpty() -> "没有可换的条目"
                        !ready -> "选几条再换"
                        else -> "交换 ${picked.size} 条"
                    },
                    color = if (ready) colors.onBrand else colors.ink3,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun RuleRow(
    label: String,
    hint: String,
    count: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(colors.brandTint)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Text("$count", fontSize = 11.sp, color = colors.brandInk)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics(Haptic.Tap)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onBrand,
                checkedTrackColor = colors.brand,
                uncheckedThumbColor = colors.ink3,
                uncheckedTrackColor = colors.panel2,
                uncheckedBorderColor = colors.line,
            ),
        )
    }
}

/**
 * 一条命中项。主行显示**换完之后**的标题（也就是现在的备注），
 * 下面一行写 `原「美团」`，让人一眼看出是从什么换过来的。
 */
@Composable
private fun SwapRow(
    hit: SwapHit,
    picked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panel)
            .clickable { onToggle() }
            .padding(start = 12.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(21.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(if (picked) colors.brand else colors.panel)
                .border(
                    width = 1.5.dp,
                    color = if (picked) colors.brand else colors.lineStrong,
                    shape = RoundedCornerShape(7.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (picked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = colors.onBrand,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = hit.newTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "原「${hit.oldTitle}」 · ${Fmt.shortDate(hit.entry.date)}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Amount(hit.entry.effectiveAmount, hit.entry.kind, fontSize = 15)
    }
}
