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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.StockCode
import cn.neeto.stockpot.core.data.StockMath
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Market
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import kotlin.math.abs
import kotlin.math.roundToLong

/* ------------------------------ 价格小工具 ------------------------------ */

@Composable
private fun PnlText(cents: Long, fontSize: Int = 14, withSign: Boolean = true) {
    val colors = LocalPotColors.current
    val tone = when {
        cents > 0 -> colors.expense
        cents < 0 -> colors.income
        else -> colors.ink3
    }
    val sign = if (withSign && cents > 0) "+" else if (cents < 0) "-" else ""
    Text(
        text = sign + Fmt.yuan(abs(cents)),
        style = MoneyStyle.copy(fontSize = fontSize.sp),
        color = tone,
        maxLines = 1,
    )
}

/* ------------------------------ 股票总览 ------------------------------ */

@Composable
private fun RefreshButton(busy: Boolean, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp), enabled = !busy) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = colors.brand,
            )
        } else {
            Icon(
                Icons.Outlined.Refresh,
                contentDescription = "刷新行情",
                tint = colors.ink3,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun StocksScreen(
    ledger: Ledger,
    quotes: Map<String, Quote>,
    rates: Rates,
    busy: Boolean,
    note: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAccount: (String) -> Unit,
    onAddAccount: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    var adding by remember { mutableStateOf(false) }

    val totals = remember(ledger.stockAccounts, ledger.stocks, quotes, rates) {
        StockMath.totals(ledger.stockAccounts, ledger.stocks, quotes, rates)
    }

    Column(Modifier.fillMaxWidth()) {
        EditorBar(
            title = "股票",
            onBack = onBack,
            onConfirm = null,
            trailing = { RefreshButton(busy = busy, onClick = onRefresh) },
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                PotCard(padding = PaddingValues(18.dp)) {
                    Text("股票市值", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Fmt.yuan(totals.marketValue),
                        style = MoneyStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("今日", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            PnlText(totals.dayPnl, 15)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("持仓盈亏", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            PnlText(totals.pnl, 15)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("成本", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                Fmt.yuan(totals.cost),
                                style = MoneyStyle.copy(fontSize = 15.sp),
                                color = colors.ink2,
                                maxLines = 1,
                            )
                        }
                    }
                    if (totals.holdingCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${ledger.stockAccounts.size} 个账户 · ${totals.holdingCount} 个持仓" +
                                if (totals.quotedCount < totals.holdingCount) " · 部分标的暂无行情" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                        )
                    }
                }
            }

            if (note != null) {
                item(key = "note") {
                    Text(
                        note,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "券商账户",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { adding = true }) {
                        Text("新建账户", fontSize = 14.sp, color = colors.brandInk)
                    }
                }
            }

            if (ledger.stockAccounts.isEmpty()) {
                item(key = "empty") {
                    PotCard {
                        Text(
                            "还没有股票账户。建一个「华泰」「IBKR」，再往里加票，成本价和股数自己填，行情自动更新。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                            lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                items(ledger.stockAccounts, key = { it.id }) { account ->
                    val one = remember(account, ledger.stocks, quotes, rates) {
                        StockMath.forAccount(account, ledger.stocks.associateBy { it.id }, quotes, rates)
                    }
                    PotCard(onClick = { onOpenAccount(account.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.brandTint),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("📈", fontSize = 18.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    account.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.ink,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "${one.holdingCount} 个持仓",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.ink3,
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    Fmt.yuan(one.marketValue),
                                    style = MoneyStyle.copy(fontSize = 16.sp),
                                    color = colors.ink,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(2.dp))
                                PnlText(one.pnl, 13)
                            }
                        }
                    }
                }
            }

            item(key = "foot") {
                Text(
                    "行情来自腾讯，A 股 / 港股实时，休市时停在最后成交价。港币、美元按 1 美元 = " +
                        "%.2f 人民币、1 美元 = %.2f 港币折成人民币。持仓成本与股数需要自己维护。"
                            .format(rates.usdCny, rates.usdHkd),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                )
            }
        }
    }

    if (adding) {
        NameDialog(
            title = "新建股票账户",
            placeholder = "比如 华泰证券",
            onDismiss = { adding = false },
            onConfirm = { name ->
                adding = false
                onAddAccount(name)
            },
        )
    }
}

/* ------------------------------ 单个账户 ------------------------------ */

@Composable
fun StockAccountScreen(
    ledger: Ledger,
    accountId: String,
    quotes: Map<String, Quote>,
    rates: Rates,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveHolding: (Stock, Long, Long) -> Unit,
    onRemoveHolding: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalPotColors.current
    val account = ledger.stockAccounts.firstOrNull { it.id == accountId }
    var editingStockId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (account == null) {
        Column(Modifier.fillMaxWidth()) {
            EditorBar(title = "股票账户", onBack = onBack, onConfirm = null)
            EmptyState("账户不见了", "它可能已经被删除了。")
        }
        return
    }

    val totals = remember(account, ledger.stocks, quotes, rates) {
        StockMath.forAccount(account, ledger.stocks.associateBy { it.id }, quotes, rates)
    }
    val lines = remember(account, ledger.stocks, quotes, rates) {
        StockMath.lines(account, ledger.stocks, quotes, rates)
    }
    val editingHolding: Pair<Stock, Holding>? = editingStockId?.let { id ->
        val stock = ledger.stocks.firstOrNull { it.id == id }
        val holding = account.entries[id]
        if (stock != null && holding != null) stock to holding else null
    }

    Column(Modifier.fillMaxWidth()) {
        EditorBar(
            title = account.name,
            onBack = onBack,
            onConfirm = null,
            trailing = { RefreshButton(busy = busy, onClick = onRefresh) },
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") {
                PotCard {
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("市值", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(Fmt.yuan(totals.marketValue), style = MoneyStyle.copy(fontSize = 20.sp), color = colors.ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("今日", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            PnlText(totals.dayPnl, 16)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("持仓盈亏", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            PnlText(totals.pnl, 16)
                        }
                    }
                }
            }

            item(key = "head") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "持仓",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { adding = true }) {
                        Text("添加持仓", fontSize = 14.sp, color = colors.brandInk)
                    }
                }
            }

            if (lines.isEmpty()) {
                item(key = "empty") {
                    PotCard {
                        Text(
                            "这个账户还没有票。点「添加持仓」，输代码、成本价、股数就行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.ink3,
                            lineHeight = 20.sp,
                        )
                    }
                }
            } else {
                items(lines, key = { it.stock.id }) { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.panel)
                            .clickable { editingStockId = line.stock.id }
                            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                line.stock.name.ifEmpty { line.stock.code },
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "${line.stock.code} · ${Market.label(line.stock.market)} · ${line.holding.shares} 股",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink3,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    StockCode.priceText(line.priceE4),
                                    style = MoneyStyle.copy(fontSize = 16.sp),
                                    color = colors.ink,
                                )
                                if (line.quoted) {
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = (if (line.changePercent >= 0) "+" else "") +
                                            "%.2f%%".format(line.changePercent),
                                        fontSize = 11.sp,
                                        color = if (line.changePercent >= 0) colors.expense else colors.income,
                                    )
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Fmt.yuan(line.value),
                                    fontSize = 12.sp,
                                    color = colors.ink3,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = (if (line.pnl >= 0) "+" else "-") + Fmt.yuan(abs(line.pnl)),
                                    fontSize = 12.sp,
                                    color = if (line.pnl >= 0) colors.expense else colors.income,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "actions") {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    TextButton(onClick = { renaming = true }) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.ink3, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重命名账户", fontSize = 14.sp, color = colors.ink2)
                    }
                    TextButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = colors.danger,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("删除这个账户", fontSize = 14.sp, color = colors.danger)
                    }
                }
            }
        }
    }

    if (adding || editingHolding != null) {
        HoldingEditorDialog(
            accountName = account.name,
            existing = editingHolding,
            quotes = quotes,
            onDismiss = {
                adding = false
                editingStockId = null
            },
            onSave = { stock, costE4, shares ->
                onSaveHolding(stock, costE4, shares)
                adding = false
                editingStockId = null
            },
            onDelete = { stockId ->
                onRemoveHolding(stockId)
                editingStockId = null
            },
        )
    }

    if (renaming) {
        NameDialog(
            title = "重命名账户",
            placeholder = account.name,
            initial = account.name,
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                onRename(it)
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = colors.panel,
            title = { Text("删除「${account.name}」？", style = MaterialTheme.typography.titleMedium, color = colors.ink) },
            text = {
                Text(
                    "这个账户下的持仓记录会一起删掉，账本里的流水不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink2,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("删除", fontSize = 15.sp, color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("取消", fontSize = 15.sp, color = colors.ink2) }
            },
        )
    }
}

/* ------------------------------ 添加 / 编辑持仓 ------------------------------ */

@Composable
private fun HoldingEditorDialog(
    accountName: String,
    existing: Pair<Stock, Holding>?,
    quotes: Map<String, Quote>,
    onDismiss: () -> Unit,
    onSave: (Stock, Long, Long) -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    var code by remember { mutableStateOf(existing?.first?.code ?: "") }
    var name by remember { mutableStateOf(existing?.first?.name ?: "") }
    var costText by remember { mutableStateOf(existing?.second?.let { StockCode.priceText(it.costE4) } ?: "") }
    var sharesText by remember { mutableStateOf(existing?.second?.shares?.toString() ?: "") }

    val market = remember(code) { StockCode.guessMarket(code) }
    val quote = quotes[StockCode.stockIdFor(code, market)] ?: existing?.first?.let { quotes[it.id] }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        title = {
            Text(
                if (existing == null) "添加持仓" else "编辑持仓",
                style = MaterialTheme.typography.titleMedium,
                color = colors.ink,
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                LabeledInput(label = "代码", value = code, placeholder = "600396 / 00700 / AAPL") {
                    code = it.uppercase().filter { ch -> ch.isLetterOrDigit() }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "市场：${Market.label(market)}" + (quote?.let { " · 现价 ${StockCode.priceText(it.priceE4)}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.height(10.dp))
                LabeledInput(label = "名称", value = name, placeholder = "留空则用行情里的名字") { name = it }
                Spacer(Modifier.height(10.dp))
                LabeledInput(
                    label = "成本价",
                    value = costText,
                    placeholder = "每股成本，元",
                    keyboardType = KeyboardType.Decimal,
                ) { input -> costText = input.filter { it.isDigit() || it == '.' } }
                Spacer(Modifier.height(10.dp))
                LabeledInput(
                    label = "股数",
                    value = sharesText,
                    placeholder = "持有多少股",
                    keyboardType = KeyboardType.Number,
                ) { input -> sharesText = input.filter { it.isDigit() } }
                Spacer(Modifier.height(10.dp))
                val cost = StockCode.parsePriceE4(costText) ?: existing?.second?.costE4 ?: 0L
                val shares = sharesText.toLongOrNull() ?: 0L
                val value = StockMath.valueCents(shares, quote?.priceE4?.takeIf { it > 0 } ?: cost)
                Text(
                    "市值约 ${Fmt.yuan(value)}" + if (quote != null) " · 现价 ${StockCode.priceText(quote.priceE4)}" else " · 暂无行情，按成本算",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = code.isNotBlank() && sharesText.toLongOrNull() != null,
                onClick = {
                    val cost = StockCode.parsePriceE4(costText) ?: 0L
                    val shares = sharesText.toLongOrNull() ?: 0L
                    val id = StockCode.stockIdFor(code, market)
                    val stock = Stock(
                        id = id,
                        code = code.trim(),
                        name = name.trim(),
                        market = market,
                        updatedAt = Fmt.stamp(),
                    )
                    // 改了代码就先把旧票从账户里摘掉，不然会变成两条持仓
                    if (existing != null && existing.first.id != id) {
                        onDelete(existing.first.id)
                    }
                    onSave(stock, cost, shares)
                },
            ) {
                Text(
                    "保存",
                    fontSize = 15.sp,
                    color = if (code.isNotBlank() && sharesText.toLongOrNull() != null) colors.brand else colors.ink3,
                )
            }
        },
        dismissButton = {
            Row {
                if (existing != null) {
                    TextButton(onClick = { onDelete(existing.first.id) }) {
                        Text("删除", fontSize = 15.sp, color = colors.danger)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消", fontSize = 15.sp, color = colors.ink2) }
            }
        },
    )
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.panel2)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.ink),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodySmall, color = colors.ink3)
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    placeholder: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        title = { Text(title, style = MaterialTheme.typography.titleMedium, color = colors.ink) },
        text = {
            LabeledInput(label = "名称", value = name, placeholder = placeholder) { name = it }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name.trim()) }) {
                Text("确定", fontSize = 15.sp, color = if (name.isNotBlank()) colors.brand else colors.ink3)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", fontSize = 15.sp, color = colors.ink2) }
        },
    )
}
