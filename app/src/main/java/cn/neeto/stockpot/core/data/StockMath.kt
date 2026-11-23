package cn.neeto.stockpot.core.data

import cn.neeto.stockpot.core.model.Candle
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Rates
import cn.neeto.stockpot.core.model.Market
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import kotlin.math.roundToLong

/**
 * 持仓的账算在这里：价格统一是万分之一元，股数是股，结果统一折成分（Long），
 * 跟账本里的金额一个口径，摆到资产页也不用换单位。
 */
object StockMath {

    data class Totals(
        /** 市值，分 */
        val marketValue: Long = 0,
        /** 成本，分 */
        val cost: Long = 0,
        /** 今日盈亏，分 */
        val dayPnl: Long = 0,
        /** 持仓盈亏，分 */
        val pnl: Long = 0,
        val holdingCount: Int = 0,
        /** 有几只票真正拿到了行情 */
        val quotedCount: Int = 0,
    ) {
        val pnlPercent: Double get() = if (cost <= 0) 0.0 else pnl * 100.0 / cost
    }

    fun priceE4(stock: Stock?, holding: Holding, quotes: Map<String, Quote>): Long {
        val quoted = stock?.let { quotes[it.id] }?.priceE4 ?: 0L
        return if (quoted > 0) quoted else holding.costE4
    }

    /** 市值（分）= 股数 × 价格(万分之一元) ÷ 100，港股美股再乘汇率。 */
    fun valueCents(shares: Long, priceE4: Long, rate: Double = 1.0): Long =
        if (shares <= 0 || priceE4 <= 0) 0 else (shares * priceE4 / 100.0 * rate).roundToLong()

    fun forAccount(
        account: StockAccount,
        stocks: Map<String, Stock>,
        quotes: Map<String, Quote>,
        rates: Rates = Rates(),
    ): Totals {
        var value = 0L
        var cost = 0L
        var dayPnl = 0L
        var count = 0
        var quoted = 0
        account.entries.forEach { (stockId, holding) ->
            if (holding.shares <= 0) return@forEach
            val stock = stocks[stockId]
            val price = priceE4(stock, holding, quotes)
            val quote = stock?.let { quotes[it.id] }
            val rate = rates.toCny(stock?.market ?: Market.A)
            value += valueCents(holding.shares, price, rate)
            cost += valueCents(holding.shares, holding.costE4, rate)
            if (quote != null && quote.prevCloseE4 > 0) {
                dayPnl += (holding.shares * (quote.priceE4 - quote.prevCloseE4) / 100.0 * rate).roundToLong()
                quoted++
            }
            count++
        }
        return Totals(
            marketValue = value,
            cost = cost,
            dayPnl = dayPnl,
            pnl = value - cost,
            holdingCount = count,
            quotedCount = quoted,
        )
    }

    fun totals(
        accounts: List<StockAccount>,
        stocks: List<Stock>,
        quotes: Map<String, Quote>,
        rates: Rates = Rates(),
    ): Totals {
        val byId = stocks.associateBy { it.id }
        var result = Totals()
        accounts.forEach { account ->
            val one = forAccount(account, byId, quotes, rates)
            result = Totals(
                marketValue = result.marketValue + one.marketValue,
                cost = result.cost + one.cost,
                dayPnl = result.dayPnl + one.dayPnl,
                pnl = result.pnl + one.pnl,
                holdingCount = result.holdingCount + one.holdingCount,
                quotedCount = result.quotedCount + one.quotedCount,
            )
        }
        return result
    }

    /** 列表里一只票一行的数据。 */
    data class Line(
        val stock: Stock,
        val holding: Holding,
        val priceE4: Long,
        val value: Long,
        val pnl: Long,
        val dayPnl: Long,
        val changePercent: Double,
        val quoted: Boolean,
    )

    fun lines(
        account: StockAccount,
        stocks: List<Stock>,
        quotes: Map<String, Quote>,
        rates: Rates = Rates(),
    ): List<Line> {
        val byId = stocks.associateBy { it.id }
        return account.entries
            .filterValues { it.shares > 0 }
            .mapNotNull { (stockId, holding) ->
                val stock = byId[stockId] ?: return@mapNotNull null
                val quote = quotes[stock.id]
                val price = priceE4(stock, holding, quotes)
                val rate = rates.toCny(stock.market)
                val value = valueCents(holding.shares, price, rate)
                val costValue = valueCents(holding.shares, holding.costE4, rate)
                Line(
                    stock = stock,
                    holding = holding,
                    priceE4 = price,
                    value = value,
                    pnl = value - costValue,
                    dayPnl = if (quote != null && quote.prevCloseE4 > 0) {
                        (holding.shares * (quote.priceE4 - quote.prevCloseE4) / 100.0 * rate).roundToLong()
                    } else {
                        0
                    },
                    changePercent = quote?.changePercent ?: 0.0,
                    quoted = quote != null && quote.priceE4 > 0,
                )
            }
            .sortedByDescending { it.value }
    }

    /**
     * 账户总资产走势：按持仓股数 × 当日收盘价（拿不到就退回成本价）逐日累加。
     * 只画到有 K 线的那些天，`limit` 是保留最后多少根。
     */
    data class TrendPoint(val day: String, val value: Long, val cost: Long) {
        val pnl: Long get() = value - cost
        val pnlPercent: Double get() = if (cost <= 0) 0.0 else pnl * 100.0 / cost
    }

    fun trend(
        accounts: List<StockAccount>,
        stocks: Map<String, Stock>,
        candles: Map<String, List<Candle>>,
        rates: Rates = Rates(),
        limit: Int = 60,
    ): List<TrendPoint> {
        // 先把持仓摊平成「股数 × 成本 × 汇率 × 这条票的日线」，后面按天走一遍游标
        val positions = accounts.flatMap { account ->
            account.entries.mapNotNull { (stockId, holding) ->
                val stock = stocks[stockId] ?: return@mapNotNull null
                if (holding.shares <= 0) return@mapNotNull null
                val series = candles[stockId].orEmpty().sortedBy { it.day }
                if (series.isEmpty()) return@mapNotNull null
                Position(
                    shares = holding.shares,
                    costE4 = holding.costE4,
                    rate = rates.toCny(stock.market),
                    series = series.map { it.day to it.closeE4 },
                )
            }
        }
        if (positions.isEmpty()) return emptyList()

        val days = positions
            .flatMap { p -> p.series.map { it.first } }
            .distinct()
            .sorted()
            .takeLast(limit)
        if (days.isEmpty()) return emptyList()

        val cursors = IntArray(positions.size) { -1 }
        return days.map { day ->
            var value = 0L
            var cost = 0L
            positions.forEachIndexed { index, position ->
                val series = position.series
                while (cursors[index] + 1 < series.size && series[cursors[index] + 1].first <= day) {
                    cursors[index]++
                }
                val price = if (cursors[index] >= 0) series[cursors[index]].second else position.costE4
                value += valueCents(position.shares, price, position.rate)
                cost += valueCents(position.shares, position.costE4, position.rate)
            }
            TrendPoint(day = day, value = value, cost = cost)
        }
    }

    private data class Position(
        val shares: Long,
        val costE4: Long,
        val rate: Double,
        val series: List<Pair<String, Long>>,
    )
}
