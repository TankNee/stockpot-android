package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Market
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.LineChart
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.PotSheet
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.SheetActions
import cn.neeto.stockpot.ui.components.SheetField
import cn.neeto.stockpot.ui.components.Sparkline
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import kotlin.math.abs
import kotlin.math.roundToLong

/* ------------------------------ 价格小工具 ------------------------------ */

@Composable
internal fun PnlText(cents: Long, fontSize: Int = 14, withSign: Boolean = true) {
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

/** 金额和百分比一起给：「+¥1,234.00 (+5.67%)」。 */
@Composable
internal fun PnlLine(cents: Long, percent: Double, fontSize: Int = 14) {
    val colors = LocalPotColors.current
    val tone = when {
        cents > 0 -> colors.expense
        cents < 0 -> colors.income
        else -> colors.ink3
    }
    val sign = if (cents > 0) "+" else if (cents < 0) "-" else ""
    val pctSign = if (percent > 0) "+" else if (percent < 0) "-" else ""
    Text(
        text = "$sign${Fmt.yuan(abs(cents))}（$pctSign${"%.2f".format(abs(percent))}%）",
        style = MoneyStyle.copy(fontSize = fontSize.sp),
        color = tone,
        maxLines = 1,
    )
}

/* ------------------------------ 股票总览 ------------------------------ */

@Composable
internal fun RefreshButton(busy: Boolean, onClick: () -> Unit) {
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
    candles: Map<String, List<Candle>>,
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
                    // 列表里也带一条近一个月的走势，点进去再看完整曲线
                    val spark = remember(account, ledger.stocks, candles, rates) {
                        StockMath.trend(
                            accounts = listOf(account),
                            stocks = ledger.stocks.associateBy { it.id },
                            candles = candles,
                            rates = rates,
                            limit = 30,
                        ).map { it.value }
                    }
                    val sparkChange = if (spark.size < 2) 0L else spark.last() - spark.first()
                    val sparkPercent = if (spark.size < 2 || spark.first() <= 0) {
                        0.0
                    } else {
                        sparkChange * 100.0 / spark.first()
                    }
                    val sparkTone = when {
                        sparkChange > 0 -> colors.expense
                        sparkChange < 0 -> colors.income
                        else -> colors.brand
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
                        if (spark.size >= 2) {
                            Spacer(Modifier.height(10.dp))
                            Sparkline(values = spark, color = sparkTone, height = 36.dp)
                            Spacer(Modifier.height(4.dp))
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "近 1 个月市值",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.ink3,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = (if (sparkChange >= 0) "+" else "-") +
                                        Fmt.yuan(abs(sparkChange)) + "（" +
                                        (if (sparkPercent >= 0) "+" else "-") +
                                        "%.2f".format(abs(sparkPercent)) + "%）",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = sparkTone,
                                )
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
        NameSheet(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StockAccountScreen(
    ledger: Ledger,
    accountId: String,
    quotes: Map<String, Quote>,
    rates: Rates,
    candles: Map<String, List<Candle>>,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSaveHolding: (Stock, Long, Long) -> Unit,
    onRemoveHolding: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onOpenStock: (String) -> Unit,
    onLoadDaily: () -> Unit,
) {
    val colors = LocalPotColors.current
    val account = ledger.stockAccounts.firstOrNull { it.id == accountId }
    var editingStockId by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var span by rememberSaveable { mutableStateOf("1m") }

    LaunchedEffect(accountId, account?.entries) {
        if (account != null) onLoadDaily()
    }

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
    val trend = remember(account, ledger.stocks, candles, rates) {
        StockMath.trend(
            accounts = listOf(account),
            stocks = ledger.stocks.associateBy { it.id },
            candles = candles,
            rates = rates,
            limit = 250,
        )
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
                    Text("市值", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        Fmt.yuan(totals.marketValue),
                        style = MoneyStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("今日", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(4.dp))
                            PnlText(totals.dayPnl, 15)
                        }
                        Column(Modifier.weight(1.4f)) {
                            Text("持仓盈亏", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(4.dp))
                            PnlLine(totals.pnl, totals.pnlPercent, 14)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "成本 ${Fmt.yuan(totals.cost)} · ${totals.holdingCount} 个持仓" +
                            if (totals.quotedCount < totals.holdingCount) " · 部分标的暂无行情" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
            }

            item(key = "trend") {
                AccountTrendCard(
                    trend = trend,
                    span = span,
                    onSpan = { span = it },
                    onRetry = onRefresh,
                )
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
                            .combinedClickable(
                                onClick = { onOpenStock(line.stock.id) },
                                onLongClick = { editingStockId = line.stock.id },
                            )
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
        HoldingEditorSheet(
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
        NameSheet(
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
        PotConfirmSheet(
            title = "删除「${account.name}」？",
            body = "这个账户下的持仓记录会一起删掉，账本里的流水不受影响。",
            confirmText = "删除",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
        )
    }
}

/* ------------------------------ 账户走势 ------------------------------ */

/**
 * 券商账户的曲线：市值 / 盈亏 / 收益率三条，按持仓 × 每日收盘价算出来。
 *
 * 区间盈亏给绝对值和百分比，手指按在图上能看那一天的具体数字，
 * 这是「收益随时间变化」最直接的看法。
 */
@Composable
private fun AccountTrendCard(
    trend: List<StockMath.TrendPoint>,
    span: String,
    onSpan: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val colors = LocalPotColors.current
    var metric by rememberSaveable { mutableStateOf("value") }
    var picked by remember(trend, span) { mutableStateOf<Int?>(null) }

    val taken = remember(trend, span) {
        trend.takeLast(
            when (span) {
                "3m" -> 66
                "1y" -> 250
                else -> 22
            },
        )
    }
    val first = taken.firstOrNull()
    val last = taken.lastOrNull()
    val focus = picked?.coerceIn(0, taken.lastIndex) ?: taken.lastIndex
    val point = taken.getOrNull(focus)

    // 曲线画的序列：市值是分，盈亏也是分，收益率换成万分位（显示时再除 100）
    val series = remember(taken, metric) {
        when (metric) {
            "pnl" -> taken.map { it.pnl }
            "pct" -> taken.map { (it.pnlPercent * 100).roundToLong() }
            else -> taken.map { it.value }
        }
    }
    val baseline = when (metric) {
        "value" -> first?.value
        else -> 0L
    }
    val change = when {
        first == null || last == null -> 0L
        metric == "pnl" -> last.pnl - first.pnl
        metric == "value" -> last.value - first.value
        else -> ((last.pnlPercent - first.pnlPercent) * 100).roundToLong()
    }
    val changePercent = if (first == null || first.value <= 0) 0.0 else change * 100.0 / first.value
    val changeTone = when {
        change > 0 -> colors.expense
        change < 0 -> colors.income
        else -> colors.ink3
    }
    val lineTone = when {
        metric == "value" -> changeTone
        (last?.pnl ?: 0L) > 0 -> colors.expense
        (last?.pnl ?: 0L) < 0 -> colors.income
        else -> colors.ink3
    }
    val subtitle = when (metric) {
        "pnl" -> "每天的浮动盈亏，虚线是 0 轴"
        "pct" -> "累计收益率，虚线是 0 轴"
        else -> "按每日收盘价算的市值"
    }

    PotCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("账户走势", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
            }
            Box(Modifier.width(150.dp)) {
                Segmented(
                    options = listOf("1m" to "1月", "3m" to "3月", "1y" to "1年"),
                    selected = span,
                    onSelect = onSpan,
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (taken.size < 2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (trend.isEmpty()) "还没拿到这只账户的日 K。" else "日 K 只有一天，画不出曲线。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.ink3,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text("重新拉", fontSize = 14.sp, color = colors.brandInk) }
            }
            return@PotCard
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                metricText(point, metric),
                style = MoneyStyle.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                color = if (metric == "value") colors.ink else lineTone,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            if (picked == null) {
                when (metric) {
                    "pct" -> Text(
                        text = (if (change >= 0) "+" else "-") + "%.2f".format(abs(change) / 100.0) + "%",
                        style = MoneyStyle.copy(fontSize = 13.sp),
                        color = changeTone,
                    )

                    else -> Text(
                        // PnlLine 会带上百分比，这里只想要区间金额的增量
                        text = (if (change >= 0) "+" else "-") + Fmt.yuan(abs(change)),
                        style = MoneyStyle.copy(fontSize = 13.sp),
                        color = changeTone,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (picked == null) "最近 ${taken.size} 个交易日" else point?.day.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = when {
                picked != null -> "松手回到最新"
                metric == "value" -> "区间 " + (if (change >= 0) "+" else "-") +
                    Fmt.yuan(abs(change)) + "（" + (if (changePercent >= 0) "+" else "-") +
                    "%.2f".format(abs(changePercent)) + "%）"

                else -> "期末累计盈亏 " + (if ((last?.pnl ?: 0L) >= 0) "+" else "-") +
                    Fmt.yuan(abs(last?.pnl ?: 0L)) + "（" + (if ((last?.pnl ?: 0L) >= 0) "+" else "-") +
                    "%.2f".format(abs(last?.pnlPercent ?: 0.0)) + "%）"
            },
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(8.dp))
        LineChart(
            values = series,
            color = lineTone,
            baseline = baseline,
            selected = picked,
            onPick = { picked = it },
            height = 148.dp,
            fill = metric == "value",
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(taken.first().day, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
            Spacer(Modifier.weight(1f))
            Text(
                if (picked == null) "按住看某一天" else "左右拖动看变化",
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
            )
            Spacer(Modifier.weight(1f))
            Text(taken.last().day, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        }
        Spacer(Modifier.height(10.dp))
        MiniTabs(
            options = listOf("value" to "市值", "pnl" to "盈亏", "pct" to "收益率"),
            selected = metric,
            onSelect = {
                metric = it
                picked = null
            },
        )
    }
}

/** 曲线当前指示的那个数：市值给金额，盈亏带正负号，收益率给百分比。 */
private fun metricText(point: StockMath.TrendPoint?, metric: String): String {
    if (point == null) return "—"
    return when (metric) {
        "pnl" -> (if (point.pnl >= 0) "+" else "-") + Fmt.yuan(abs(point.pnl))
        "pct" -> (if (point.pnlPercent >= 0) "+" else "-") + "%.2f".format(abs(point.pnlPercent)) + "%"
        else -> Fmt.yuan(point.value)
    }
}

/** 卡里用的小切换，比 Segmented 轻，不抢标题的视觉重量。 */
@Composable
private fun MiniTabs(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            val active = value == selected
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (active) colors.brandInk else colors.ink3,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) colors.brandTint else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
    }
}

/* ------------------------------ 添加 / 编辑持仓 ------------------------------ */

@Composable
internal fun HoldingEditorSheet(
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
    val canSave = code.isNotBlank() && (sharesText.toLongOrNull() ?: 0L) > 0

    PotSheet(onDismiss = onDismiss) {
        Text(
            if (existing == null) "添加持仓" else "编辑持仓",
            style = MaterialTheme.typography.titleLarge,
            color = colors.ink,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "$accountName · 成本价和股数自己维护，行情自动更新",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(14.dp))

        SheetField(
            label = "代码",
            value = code,
            placeholder = "600396 / 00700 / AAPL",
            onValueChange = { input -> code = input.uppercase().filter { it.isLetterOrDigit() } },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "市场：${Market.label(market)}" + (quote?.let { " · 现价 ${StockCode.priceText(it.priceE4)}" } ?: " · 暂未查到行情"),
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.height(12.dp))

        SheetField(
            label = "名称",
            value = name,
            placeholder = "留空则用行情里的名字",
            onValueChange = { name = it },
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SheetField(
                label = "成本价",
                value = costText,
                placeholder = "每股成本",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Decimal,
                onValueChange = { input -> costText = input.filter { it.isDigit() || it == '.' } },
            )
            SheetField(
                label = "股数",
                value = sharesText,
                placeholder = "持有股数",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                onValueChange = { input -> sharesText = input.filter { it.isDigit() } },
            )
        }

        Spacer(Modifier.height(10.dp))
        val cost = StockCode.parsePriceE4(costText) ?: existing?.second?.costE4 ?: 0L
        val shares = sharesText.toLongOrNull() ?: 0L
        val value = StockMath.valueCents(shares, quote?.priceE4?.takeIf { it > 0 } ?: cost)
        Text(
            "市值约 ${Fmt.yuan(value)}" + if (quote != null) " · 成本 ${Fmt.yuan(StockMath.valueCents(shares, cost))}" else " · 无行情时按成本算",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )

        if (existing != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = { onDelete(existing.first.id) },
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("删除这只持仓", fontSize = 14.sp, color = colors.danger)
            }
        }

        SheetActions(
            confirmText = "保存",
            enabled = canSave,
            danger = false,
            onConfirm = {
                val stock = Stock(
                    id = StockCode.stockIdFor(code, market),
                    code = code.trim(),
                    name = name.trim(),
                    market = market,
                    updatedAt = Fmt.stamp(),
                )
                // 改了代码就先把旧票从账户里摘掉，不然会变成两条持仓
                if (existing != null && existing.first.id != stock.id) {
                    onDelete(existing.first.id)
                }
                onSave(stock, cost, shares)
            },
            onCancel = onDismiss,
        )
    }
}

/** 新建 / 重命名账户这种只要一个名字的场合。 */
@Composable
internal fun NameSheet(
    title: String,
    placeholder: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    var name by remember { mutableStateOf(initial) }
    PotSheet(onDismiss = onDismiss) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = colors.ink)
        Spacer(Modifier.height(14.dp))
        SheetField(
            label = "名称",
            value = name,
            placeholder = placeholder,
            onValueChange = { name = it },
        )
        SheetActions(
            confirmText = "确定",
            enabled = name.isNotBlank(),
            danger = false,
            onConfirm = { onConfirm(name.trim()) },
            onCancel = onDismiss,
        )
    }
}
