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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.SwapHit
import cn.neeto.stockpot.core.data.SwapRule
import cn.neeto.stockpot.core.data.TitleSwap
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.ui.components.Amount
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.Haptic
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.rememberHaptics
import cn.neeto.stockpot.ui.theme.LocalPotColors

/** 底部胶囊（56dp）+ 它的下内边距，操作条浮在它上面，留 8dp 呼吸。 */
private val ActionBarAbove = 76.dp

/** 列表底部留白：胶囊 108dp 那一档，再加上浮着的操作条。 */
private val ListBottomInset = 176.dp

/**
 * 条目标题整理：把「美团 / 淘宝闪购」这类把平台名当标题的条目扫出来，
 * 让用户看一眼换完之后长什么样，再一次换掉。
 *
 * 规则分两种：内置的六条平台（整组开关），以及用户自己加的词（加一条算一条）。
 * 全程只碰 [cn.neeto.stockpot.core.model.Entry.counterparty] 和 `note` 两个字段，
 * 金额、分类、时间、单号一个不动，所以换错了再换一次就回去了。
 */
@Composable
fun TitleSwapScreen(
    ledger: Ledger,
    words: Set<String>,
    onSetWords: (Set<String>) -> Unit,
    onBack: () -> Unit,
    onSwap: (Set<String>) -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    // 账本或开关一变就重扫，列表永远跟当前数据一致
    val hits = remember(ledger.entries, words) { TitleSwap.scan(ledger.entries, words) }
    // 新的一批结果默认全选；用户在列表里取消的那些，换完就自然从结果里消失了
    var picked by remember(hits) { mutableStateOf(hits.map { it.entry.id }.toSet()) }
    val customs = remember(words) { TitleSwap.customWords(words) }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        EditorBar(title = "条目标题整理", onBack = onBack, onConfirm = null)

        // 操作条浮在列表之上、胶囊之上；列表多留一段空白，滚到底时最后一条能整条露出来
        Box(Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = ListBottomInset),
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
                                onCheckedChange = {
                                    // 一条规则带好几个词，开关就整组一起加减
                                    onSetWords(
                                        if (on) words - rule.words.toSet() else words + rule.words,
                                    )
                                },
                            )
                        }

                        // 自己加的词：一条一行，右边 × 删掉
                        customs.forEach { word ->
                            Hairline(Modifier.padding(start = 16.dp))
                            CustomWordRow(
                                word = word,
                                count = hits.count { it.oldTitle.contains(word, ignoreCase = true) },
                                onRemove = { onSetWords(words - word) },
                            )
                        }

                        Hairline(Modifier.padding(start = 16.dp))
                        if (adding) {
                            WordAdder(
                                exists = { it in words },
                                onAdd = { word ->
                                    onSetWords(words + word)
                                    haptics(Haptic.Confirm)
                                },
                                onDismiss = { adding = false },
                            )
                        } else {
                            AddWordRow { adding = true }
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
                            body = "标题里认出了上面那些词、而且备注里确实写了内容，条目才会出现在这里。" +
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

            // 底部只放一件事：换。没选的时候按钮是灰的，不给按。
            // 位置抬到胶囊之上——这个页面 tab 栏是浮着的，贴底会被它整个盖住。
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = ActionBarAbove),
            ) {
                val ready = picked.isNotEmpty()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (ready) colors.brand else colors.panel2)
                        .border(
                            width = 1.dp,
                            color = if (ready) colors.brand else colors.line,
                            shape = RoundedCornerShape(14.dp),
                        )
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
            CountLabel(label, count)
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

/** 自己加的词：没有开关，加进来就是开的；右边一个 × 直接删。 */
@Composable
private fun CustomWordRow(
    word: String,
    count: Int,
    onRemove: () -> Unit,
) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountLabel(word, count)
        Spacer(Modifier.weight(1f))
        Text(
            "自定义",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .clickable {
                    haptics(Haptic.Tap)
                    onRemove()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "删除 $word",
                tint = colors.ink3,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun CountLabel(text: String, count: Int) {
    val colors = LocalPotColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.ink)
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
}

@Composable
private fun AddWordRow(onClick: () -> Unit) {
    val colors = LocalPotColors.current
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptics(Haptic.Tap)
                onClick()
            }
            .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("＋ 添加关键词", style = MaterialTheme.typography.bodyMedium, color = colors.brand)
        Spacer(Modifier.weight(1f))
        Text(
            "比如 山姆、盒马",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
    }
}

/**
 * 加词的小输入行。[exists] 用来挡住重复的词（内置的、已经加过的都算）。
 * 输入为空或重复时「添加」是灰的，不会误点。
 */
@Composable
private fun WordAdder(
    exists: (String) -> Boolean,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPotColors.current
    var draft by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val word = TitleSwap.normalizeWord(draft)
    val usable = word != null && !exists(word)

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    val submit = {
        if (usable && word != null) {
            onAdd(word)
            draft = ""
        }
    }

    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(14) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.panel2)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.brand),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                decorationBox = { inner ->
                    Box {
                        if (draft.isEmpty()) {
                            Text(
                                "标题里包含的字，比如 山姆",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.ink3,
                            )
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "添加",
                style = MaterialTheme.typography.bodyMedium,
                color = if (usable) colors.brand else colors.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = usable) { submit() }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                draft.isBlank() -> "只比对标题、不看备注；最多 12 个字"
                word == null && draft.isNotBlank() && draft.trim().length > 12 -> "太长了，压缩到 12 个字以内"
                word == null -> "这个词已经在内置规则里了"
                exists(word) -> "已经加过了"
                else -> "会一直记着，下次进来还在"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "收起",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onDismiss() }
                .padding(vertical = 4.dp, horizontal = 2.dp),
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
