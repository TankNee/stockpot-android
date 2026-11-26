package cn.neeto.stockpot.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.StockCode
import cn.neeto.stockpot.core.data.StockMath
import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.core.model.CurvePoint
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Market
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.ui.components.IconSegmented
import cn.neeto.stockpot.ui.components.IconOption
import cn.neeto.stockpot.ui.components.PotPillButton
import cn.neeto.stockpot.ui.components.PillTone
import cn.neeto.stockpot.ui.components.CandleChart
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.LineChart
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.PotConfirmSheet
import cn.neeto.stockpot.ui.components.Segmented
import cn.neeto.stockpot.ui.components.overlayBottomPadding
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle
import kotlin.math.abs

/**
 * 单只票的详情：当日分时、日 K、这笔持仓的盈亏，还有编辑入口。
 * 从券商账户的持仓列表点进来。
 */
@Composable
fun StockDetailScreen(
    ledger: Ledger,
    accountId: String,
    stockId: String,
    quotes: Map<String, Quote>,
    rates: Rates,
    candles: Map<String, List<Candle>>,
    minutes: Map<String, List<CurvePoint>>,
    busy: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMinute: () -> Unit,
    onEditHolding: () -> Unit,
    onRemoveHolding: (String) -> Unit,
) {
    val colors = LocalPotColors.current
    val account = ledger.stockAccounts.firstOrNull { it.id == accountId }
    val stock = ledger.stocks.firstOrNull { it.id == stockId }
    val holding = account?.entries?.get(stockId)
    var tab by rememberSaveable { mutableStateOf("minute") }
    var picked by remember(stockId, tab) { mutableStateOf<Int?>(null) }
    var confirmRemove by remember { mutableStateOf(false) }

    LaunchedEffect(stockId) { onLoadMinute() }

    if (account == null || stock == null || holding == null) {
        Column(Modifier.fillMaxWidth()) {
            EditorBar(title = "持仓", onBack = onBack, onConfirm = null)
            EmptyState("这只票不在账户里", "它可能已经被删掉了，回上一页看看。")
        }
        return
    }

    val quote = quotes[stock.id]
    val isFund = stock.market == Market.FUND
    val priceE4 = quote?.priceE4?.takeIf { it > 0 } ?: holding.costE4
    val rate = rates.toCny(stock.market)
    val value = StockMath.valueCents(holding.shares, priceE4, rate)
    val costValue = StockMath.valueCents(holding.shares, holding.costE4, rate)
    val pnl = value - costValue
    val pnlPercent = if (costValue <= 0) 0.0 else pnl * 100.0 / costValue
    val changeE4 = if (quote != null) quote.priceE4 - quote.prevCloseE4 else 0L
    val changePercent = quote?.changePercent ?: 0.0

    val minutePoints = minutes[stock.id].orEmpty()
    val dayCandles = remember(candles, stock.id) { candles[stock.id].orEmpty().takeLast(90) }
    val tone = when {
        changeE4 > 0 -> colors.expense
        changeE4 < 0 -> colors.income
        else -> colors.ink3
    }

    Column(Modifier.fillMaxWidth()) {
        EditorBar(
            title = stock.name.ifEmpty { stock.code },
            onBack = onBack,
            onConfirm = null,
            trailing = { RefreshButton(busy = busy, onClick = onRefresh) },
        )

        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 0.dp,
                bottom = overlayBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "price") {
                PotCard(padding = PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stock.code + " · " + Market.label(stock.market),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                            modifier = Modifier.weight(1f),
                        )
                        if (quote != null && quote.time.isNotEmpty()) {
                            Text(quote.time, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            StockCode.priceText(priceE4),
                            style = MoneyStyle.copy(fontSize = 30.sp, fontWeight = FontWeight.SemiBold),
                            color = tone,
                        )
                        Spacer(Modifier.width(10.dp))
                        if (quote != null) {
                            Text(
                                text = (if (changeE4 >= 0) "+" else "-") +
                                    StockCode.priceText(abs(changeE4)) +
                                    "（" + (if (changePercent >= 0) "+" else "-") +
                                    "%.2f".format(abs(changePercent)) + "%）",
                                fontSize = 14.sp,
                                color = tone,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    if (quote == null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "还没拉到行情，先按成本价显示。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    if (isFund) {
                        // 场外基金只有每日净值，没有分时也没有 K 线
                        Text(
                            "场外基金没有分时和 K 线，净值每个交易日收盘后更新一次。",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                            lineHeight = 18.sp,
                        )
                    } else {
                        Box(Modifier.width(176.dp)) {
                            IconSegmented(
                                options = listOf(
                                    IconOption("minute", "分时", Icons.Outlined.ShowChart),
                                    IconOption("day", "日 K", Icons.Outlined.CandlestickChart),
                                ),
                                selected = tab,
                                onSelect = {
                                    tab = it
                                    picked = null
                                },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        when (tab) {
                            "minute" -> MinuteBlock(
                                points = minutePoints,
                                baseline = quote?.prevCloseE4,
                                picked = picked,
                                onPick = { picked = it },
                                tone = tone,
                            )

                            else -> DayCandleBlock(
                                candles = dayCandles,
                                picked = picked,
                                onPick = { picked = it },
                            )
                        }
                    }
                }
            }

            item(key = "position") {
                PotCard {
                    Text("这笔持仓", style = MaterialTheme.typography.titleMedium, color = colors.ink)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isFund) "份额" else "股数",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.ink3,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                holding.sharesText,
                                style = MoneyStyle.copy(fontSize = 16.sp),
                                color = colors.ink,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("成本价", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                StockCode.priceText(holding.costE4),
                                style = MoneyStyle.copy(fontSize = 16.sp),
                                color = colors.ink,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text("市值", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                            Spacer(Modifier.height(3.dp))
                            Text(Fmt.yuan(value), style = MoneyStyle.copy(fontSize = 16.sp), color = colors.ink)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("持仓盈亏", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                        Spacer(Modifier.width(8.dp))
                        PnlLine(pnl, pnlPercent, 15)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "成本 ${Fmt.yuan(costValue)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.ink3,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "账户：${account.name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.ink3,
                    )
                }
            }

            item(key = "actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PotCard(
                        modifier = Modifier.weight(1f),
                        padding = PaddingValues(vertical = 14.dp),
                        onClick = onEditHolding,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Edit, contentDescription = null, tint = colors.ink2, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isFund) "改成本 / 份额" else "改成本 / 股数",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.ink2,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                PotPillButton(
                    text = "从这个账户里删掉",
                    icon = Icons.Outlined.DeleteOutline,
                    tone = PillTone.Danger,
                    onClick = { confirmRemove = true },
                )
            }
        }
    }


    if (confirmRemove) {
        PotConfirmSheet(
            title = "删掉「${stock.name.ifEmpty { stock.code }}」？",
            body = "只会把这个账户里的这笔持仓去掉，账本流水和其他账户不受影响。",
            confirmText = "删除",
            onDismiss = { confirmRemove = false },
            onConfirm = {
                confirmRemove = false
                onRemoveHolding(stock.id)
                onBack()
            },
        )
    }
}

/** 分时：横轴是 09:30 到收盘，虚线是昨收，手指按着能看某一分钟的价格。 */
@Composable
private fun MinuteBlock(
    points: List<CurvePoint>,
    baseline: Long?,
    picked: Int?,
    onPick: (Int?) -> Unit,
    tone: androidx.compose.ui.graphics.Color,
) {
    val colors = LocalPotColors.current
    if (points.isEmpty()) {
        Text(
            "分时还没拉到（可能是休市或网络不通），点右上角刷新试试。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        return
    }
    val focus = picked?.coerceIn(0, points.lastIndex) ?: points.lastIndex
    val point = points[focus]
    val diff = baseline?.let { point.value - it }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = intToTime(point.label),
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = StockCode.priceText(point.value),
            style = MoneyStyle.copy(fontSize = 14.sp),
            color = colors.ink,
        )
        if (diff != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = (if (diff >= 0) "+" else "-") + StockCode.priceText(abs(diff)),
                fontSize = 12.sp,
                color = if (diff > 0) colors.expense else if (diff < 0) colors.income else colors.ink3,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (picked == null) "按住看价格" else "松手回到最新",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
    }
    Spacer(Modifier.height(8.dp))
    LineChart(
        values = points.map { it.value },
        color = tone,
        baseline = baseline,
        selected = picked,
        onPick = { onPick(it) },
        height = 172.dp,
    )
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        Text(intToTime(points.first().label), style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.weight(1f))
        Text(intToTime(points.last().label), style = MaterialTheme.typography.labelSmall, color = colors.ink3)
    }
}

/** 日 K 那块：拖动看某一根的开高低收，默认停在最后一根。 */
@Composable
private fun DayCandleBlock(
    candles: List<Candle>,
    picked: Int?,
    onPick: (Int?) -> Unit,
) {
    val colors = LocalPotColors.current
    if (candles.isEmpty()) {
        Text(
            "日 K 还没下载下来，点右上角刷新试试。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.ink3,
        )
        return
    }
    val focus = picked?.coerceIn(0, candles.lastIndex) ?: candles.lastIndex
    val candle = candles[focus]
    val prevClose = candles.getOrNull(focus - 1)?.closeE4 ?: candle.openE4
    val dayChange = if (prevClose > 0) (candle.closeE4 - prevClose) * 100.0 / prevClose else 0.0
    val tone = when {
        candle.closeE4 > prevClose -> colors.expense
        candle.closeE4 < prevClose -> colors.income
        else -> colors.ink3
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(candle.day, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.width(8.dp))
        Text(
            text = (if (dayChange >= 0) "+" else "-") + "%.2f".format(abs(dayChange)) + "%",
            fontSize = 12.sp,
            color = tone,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (picked == null) "按住看某一根" else "松手回到最新",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth()) {
        CandleCell("开", candle.openE4, colors.ink2)
        CandleCell("高", candle.highE4, colors.expense)
        CandleCell("低", candle.lowE4, colors.income)
        CandleCell("收", candle.closeE4, tone)
    }
    Spacer(Modifier.height(8.dp))
    CandleChart(
        candles = candles,
        height = 176.dp,
        selected = picked,
        onPick = onPick,
    )
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(candles.first().day, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.weight(1f))
        Text(
            "MA5 · MA20 · 前复权 ${candles.size} 根",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
        Spacer(Modifier.weight(1f))
        Text(candles.last().day, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
    }
}

@Composable
private fun RowScope.CandleCell(
    label: String,
    priceE4: Long,
    tone: androidx.compose.ui.graphics.Color,
) {
    val colors = LocalPotColors.current
    Column(Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(2.dp))
        Text(StockCode.priceText(priceE4), style = MoneyStyle.copy(fontSize = 13.sp), color = tone)
    }
}

/** "0930" → "09:30"，不是四位就原样返回，别把异常数据弄崩。 */
private fun intToTime(raw: String): String =
    if (raw.length == 4 && raw.all { it.isDigit() }) raw.take(2) + ":" + raw.takeLast(2) else raw
