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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.neeto.stockpot.core.data.Fmt
import cn.neeto.stockpot.core.data.LedgerMath
import cn.neeto.stockpot.core.model.Ledger
import cn.neeto.stockpot.core.model.Wallet
import cn.neeto.stockpot.core.model.signedBalance
import cn.neeto.stockpot.ui.components.EmptyState
import cn.neeto.stockpot.ui.components.Hairline
import cn.neeto.stockpot.ui.components.PotCard
import cn.neeto.stockpot.ui.components.SectionTitle
import cn.neeto.stockpot.ui.theme.LocalPotColors
import cn.neeto.stockpot.ui.theme.MoneyStyle

/** 负债：信用卡或余额为负。 */
private fun isLiability(wallet: Wallet): Boolean = wallet.kind == "credit" || wallet.balance < 0

private data class KindTotal(val kind: String, val label: String, val total: Long, val share: Float)

@Composable
fun AssetsScreen(
    ledger: Ledger,
    stockValue: Long,
    stockPnl: Long,
    stockAccountCount: Int,
    onOpenStocks: () -> Unit,
    onAdd: () -> Unit,
    onOpenWallet: (Wallet?) -> Unit,
) {
    val colors = LocalPotColors.current

    val wallets = ledger.wallets
    // 关掉「计入总资产」的账户仍留在列表里，但不参与净资产和构成，只当备注看
    val counted = wallets.filter { it.countInAssets }
    val walletNet = remember(counted) { counted.sumOf { it.signedBalance() } }
    val net = walletNet + stockValue
    val assets = remember(counted) { counted.filter { !isLiability(it) }.sumOf { it.signedBalance() } } + stockValue
    val debts = remember(counted) { counted.filter { isLiability(it) }.sumOf { it.signedBalance() } }
    val entryNet = remember(ledger.entries) { LedgerMath.allTimeNet(ledger.entries) }

    // 一张环形图那种「按类型分」的口径，只统计资产侧，负债不进占比
    val kindOptions = remember(wallets) { walletKindOptions(wallets) }
    val kindTotals = remember(counted, kindOptions, stockValue) {
        val positive = counted.filter { !isLiability(it) }.groupBy { it.kind }
        val base = (positive.values.sumOf { list -> list.sumOf { it.balance } } + stockValue)
            .coerceAtLeast(1L)
        val walletRows = kindOptions.mapNotNull { (kind, label) ->
            val total = positive[kind]?.sumOf { it.balance } ?: 0L
            if (total <= 0L) null else KindTotal(kind, label, total, total.toFloat() / base)
        }
        val withStock = if (stockValue > 0) {
            walletRows + KindTotal("stock", "股票", stockValue, stockValue.toFloat() / base)
        } else {
            walletRows
        }
        withStock.sortedByDescending { it.total }
    }

    val groups = remember(wallets, kindOptions) {
        kindOptions.map { (kind, label) ->
            label to wallets.filter { it.kind == kind }.sortedByDescending { it.signedBalance() }
        }.filter { it.second.isNotEmpty() }
    }

    if (wallets.isEmpty() && stockAccountCount == 0) {
        EmptyState(
            title = "还没有账户",
            body = "添加银行卡、微信零钱或信用卡，这里就能看到总资产和净资产。余额自己维护，账本只记录钱的流动。",
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("添加账户", onClick = onAdd)
                GhostButton("股票账户", onOpenStocks)
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero", contentType = "card") {
                PotCard(padding = PaddingValues(18.dp)) {
                    Text("净资产", style = MaterialTheme.typography.labelSmall, color = colors.ink3)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = Fmt.yuan(net),
                        style = MoneyStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.SemiBold),
                        color = if (net < 0) colors.expense else colors.ink,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = if (stockAccountCount > 0) {
                            "${wallets.size} 个账户 + ${stockAccountCount} 个股票账户 · 余额与持仓自己维护"
                        } else {
                            "${wallets.size} 个账户 · 余额手动维护，不随账目自动变"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                    )
                    Spacer(Modifier.height(14.dp))
                    Hairline()
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        StatColumn("总资产", Fmt.yuan(assets), colors.ink, Modifier.weight(1f))
                        // 负债在账上存的是负数，给人看的时候翻成正的「欠多少」
                        StatColumn(
                            "总负债 · 待还",
                            Fmt.yuan(if (debts < 0) -debts else debts),
                            if (debts < 0) colors.expense else colors.ink,
                            Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "账本累计净 ${Fmt.yuan(entryNet, signed = true)} · 只算账目，不含上面这些余额",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.ink3,
                        lineHeight = 19.sp,
                    )
                }
            }

            if (kindTotals.isNotEmpty()) {
                item(key = "mix", contentType = "card") {
                    CompositionCard(kindTotals = kindTotals, total = assets)
                }
            }

            if (stockAccountCount > 0) {
                item(key = "stock-card", contentType = "card") {
                    PotCard(onClick = onOpenStocks) {
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
                                Text("股票账户", style = MaterialTheme.typography.bodyLarge, color = colors.ink)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "$stockAccountCount 个账户 · 市值 ${Fmt.yuan(stockValue)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.ink3,
                                )
                            }
                            Text(
                                text = (if (stockPnl > 0) "+" else if (stockPnl < 0) "-" else "") +
                                    Fmt.yuan(if (stockPnl < 0) -stockPnl else stockPnl),
                                style = MoneyStyle.copy(fontSize = 15.sp),
                                color = when {
                                    stockPnl > 0 -> colors.expense
                                    stockPnl < 0 -> colors.income
                                    else -> colors.ink3
                                },
                            )
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = colors.ink3,
                                modifier = Modifier.padding(start = 4.dp).size(17.dp),
                            )
                        }
                    }
                }
            }

            item(key = "add", contentType = "row") {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "账户",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.ink3,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onAdd) {
                        Text("添加账户", fontSize = 14.sp, color = colors.brandInk)
                    }
                }
            }

            groups.forEach { (label, rows) ->
                item(key = "g-$label", contentType = "header") {
                    val subtotal = remember(rows) { rows.sumOf { it.signedBalance() } }
                    Row(
                        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.ink3,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Fmt.yuan(subtotal),
                            style = MoneyStyle.copy(fontSize = 13.sp),
                            color = colors.ink3,
                        )
                    }
                }
                item(key = "c-$label", contentType = "card") {
                    PotCard(padding = PaddingValues(0.dp)) {
                        rows.forEachIndexed { index, wallet ->
                            WalletRow(wallet = wallet, onClick = { onOpenWallet(wallet) })
                            if (index < rows.lastIndex) {
                                Box(Modifier.padding(start = 60.dp)) { Hairline() }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String, tone: Color, modifier: Modifier = Modifier) {
    val colors = LocalPotColors.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.ink3)
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MoneyStyle.copy(fontSize = 16.sp),
            color = tone,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 一条按类型的堆叠色带 + 图例。比环形图省事，一屏就能读完。 */
@Composable
private fun CompositionCard(kindTotals: List<KindTotal>, total: Long) {
    val colors = LocalPotColors.current
    val palette = colors.chartPalette
    PotCard {
        SectionTitle(
            "资产构成",
            trailing = {
                Text(
                    text = "${kindTotals.size} 类",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            kindTotals.forEachIndexed { index, item ->
                Box(
                    Modifier
                        .weight(item.share.coerceAtLeast(0.02f))
                        .height(10.dp)
                        .background(palette[index % palette.size]),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        kindTotals.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette[index % palette.size]),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.ink2,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${(item.share * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.ink3,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    Fmt.yuan(item.total),
                    style = MoneyStyle.copy(fontSize = 14.sp),
                    color = colors.ink,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "合计 ${Fmt.yuan(total)}",
            style = MaterialTheme.typography.labelSmall,
            color = colors.ink3,
        )
    }
}

@Composable
private fun WalletRow(wallet: Wallet, onClick: () -> Unit) {
    val colors = LocalPotColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(start = 14.dp, end = 10.dp, top = 13.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.panel2),
            contentAlignment = Alignment.Center,
        ) {
            Text(wallet.name.take(1), fontSize = 16.sp, color = colors.ink2)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                wallet.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(
                    if (wallet.kind == "credit") "信用卡 · 待还" else walletKindLabel(wallet.kind),
                    wallet.institution.ifEmpty { null },
                    if (wallet.countInAssets) null else "不计入总资产",
                ).joinToString("  ·  "),
                style = MaterialTheme.typography.labelSmall,
                color = colors.ink3,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            Fmt.yuan(wallet.signedBalance()),
            style = MoneyStyle.copy(fontSize = 15.sp),
            color = when {
                wallet.signedBalance() < 0 -> colors.expense
                !wallet.countInAssets -> colors.ink3
                else -> colors.ink
            },
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = colors.ink3,
            modifier = Modifier.size(17.dp),
        )
    }
}
