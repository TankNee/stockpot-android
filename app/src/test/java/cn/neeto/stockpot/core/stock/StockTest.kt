package cn.neeto.stockpot.core.stock

import cn.neeto.stockpot.core.data.StockCode
import cn.neeto.stockpot.core.data.StockMath
import cn.neeto.stockpot.core.model.Holding
import cn.neeto.stockpot.core.model.Quote
import cn.neeto.stockpot.core.model.Stock
import cn.neeto.stockpot.core.model.StockAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StockTest {

    @Test
    fun `腾讯行情按波浪号切开`() {
        val body = "v_sh600396=\"1~华电辽能~600396~3.42~3.40~3.41~12345~6789~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~20261123113000~0.02~0.59\";" +
            "v_r_hk00700=\"100~腾讯控股~00700~418.000~420.000~419.000~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~0~2026/11/23 11:30:00\";"
        val quotes = QuoteClient.parse(body)

        val a = quotes["sh600396"]
        assertEquals("华电辽能", a?.name)
        assertEquals(34_200L, a?.priceE4)
        assertEquals(34_000L, a?.prevCloseE4)

        val hk = quotes["hk00700"]
        assertEquals(4_180_000L, hk?.priceE4)
        assertEquals(-0.47619, hk!!.changePercent, 0.0001)
    }

    @Test
    fun `价格按四位小数解析，ETF 不丢精度`() {
        assertEquals(4_720L, StockCode.parsePriceE4("0.472"))
        assertEquals(151_240L, StockCode.parsePriceE4("15.124"))
        assertEquals(342L, StockCode.parsePriceE4("0.0342"))
        assertEquals(1_000_000L, StockCode.parsePriceE4("100"))
        assertEquals(0L, StockCode.parsePriceE4("0"))
        assertEquals(null, StockCode.parsePriceE4(""))
        assertEquals("0.472", StockCode.priceText(4_720))
        assertEquals("3.42", StockCode.priceText(34_200))
        assertEquals("100.00", StockCode.priceText(1_000_000))
    }

    @Test
    fun `代码推断市场与行情 id`() {
        assertEquals("a", StockCode.guessMarket("600396"))
        assertEquals("a", StockCode.guessMarket("sh600396"))
        assertEquals("hk", StockCode.guessMarket("00700"))
        assertEquals("us", StockCode.guessMarket("AAPL"))

        assertEquals("sh600396", StockCode.stockIdFor("600396", "a"))
        assertEquals("sz000001", StockCode.stockIdFor("000001", "a"))
        assertEquals("hk00700", StockCode.stockIdFor("00700", "hk"))
        assertEquals("usaapl", StockCode.stockIdFor("aapl", "us"))
    }

    @Test
    fun `市值与盈亏按分计算`() {
        val stock = Stock(id = "sh600396", code = "600396", name = "华电辽能")
        val account = StockAccount(
            id = "a1",
            name = "华泰",
            entries = mapOf("sh600396" to Holding(costE4 = 30_000, shares = 1_000)),
        )
        val quotes = mapOf(
            "sh600396" to Quote(
                id = "sh600396",
                name = "华电辽能",
                priceE4 = 34_200,
                prevCloseE4 = 34_000,
            ),
        )
        val totals = StockMath.forAccount(account, mapOf(stock.id to stock), quotes)

        // 1000 股 × 3.42 元 = 3420 元 = 342000 分
        assertEquals(342_000L, totals.marketValue)
        assertEquals(300_000L, totals.cost)
        assertEquals(42_000L, totals.pnl)
        // (3.42 - 3.40) × 1000 股 = 20 元
        assertEquals(2_000L, totals.dayPnl)
        assertEquals(1, totals.holdingCount)
        assertEquals(1, totals.quotedCount)
    }

    @Test
    fun `没有行情时按成本算市值，盈亏为零`() {
        val stock = Stock(id = "sh600396", code = "600396", name = "华电辽能")
        val account = StockAccount(
            id = "a1",
            name = "华泰",
            entries = mapOf("sh600396" to Holding(costE4 = 30_000, shares = 1_000)),
        )
        val totals = StockMath.forAccount(account, mapOf(stock.id to stock), emptyMap())

        assertEquals(300_000L, totals.marketValue)
        assertEquals(0L, totals.pnl)
        assertEquals(0, totals.quotedCount)
        assertTrue(totals.marketValue == totals.cost)
    }

    @Test
    fun `多账户同一只票的市值相加`() {
        val stock = Stock(id = "sh600396", code = "600396", name = "华电辽能")
        val quotes = mapOf("sh600396" to Quote(id = "sh600396", priceE4 = 34_200, prevCloseE4 = 34_000))
        val accounts = listOf(
            StockAccount("a1", "华泰", mapOf("sh600396" to Holding(30_000, 1_000))),
            StockAccount("a2", "中信", mapOf("sh600396" to Holding(32_000, 500))),
        )
        val totals = StockMath.totals(accounts, listOf(stock), quotes)

        assertEquals(2, totals.holdingCount)
        assertEquals(513_000L, totals.marketValue)
    }
}
