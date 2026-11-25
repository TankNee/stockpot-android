package cn.neeto.stockpot.core.stock

import cn.neeto.stockpot.core.data.StockCode
import cn.neeto.stockpot.core.data.StockMath
import cn.neeto.stockpot.core.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 场外基金这条支线：搜索、净值解析、代码归口、小数份额的账。
 *
 * 用的是东财两个接口的真实返回形状（字段大小写别改，解析是照这个写的）。
 */
class FundQuoteTest {

    /**
     * fundsuggest 的返回：真基金和基金公司（代码不是 6 位）混在一起。
     * 这个接口本身不返股票，所以只靠「6 位数字」过滤就够。
     */
    private val suggest = """
        {"Datas":[
          {"CODE":"005827","NAME":"易方达蓝筹精选混合","FundBaseInfo":{"FTYPE":"混合型-偏股"},"MktNum":"150"},
          {"CODE":"000001","NAME":"华夏成长混合","FundBaseInfo":{"FTYPE":"混合型-灵活"},"MktNum":"150"},
          {"CODE":"320007","NAME":"诺安成长混合","FundBaseInfo":{"FTYPE":"混合型-偏股"},"MktNum":"150"},
          {"CODE":"88","NAME":"招商基金","FundBaseInfo":{},"MktNum":"150"}
        ]}
    """.trimIndent()

    /** 盘中有估值：NAV 是昨净值，GSZ 是当前估算净值。 */
    private val withEstimate = """
        {"Datas":[
          {"FCODE":"005827","SHORTNAME":"易方达蓝筹精选混合","PDATE":"2026-11-24",
           "NAV":"1.3289","NAVCHGRT":"-0.51","GSZ":"1.3412","GSZZL":"0.93","GZTIME":"2026-11-25 14:32"},
          {"FCODE":"000001","SHORTNAME":"华夏成长混合","PDATE":"2026-11-24",
           "NAV":"0.9120","NAVCHGRT":"1.22","GSZ":"","GSZZL":"","GZTIME":""}
        ]}
    """.trimIndent()

    @Test
    fun `场外基金搜索单独归到 fund 市场`() {
        val hits = QuoteClient.parseFundSearch(suggest)

        // 88 是基金公司，不是 6 位数字，得滤掉
        assertEquals(listOf("fund005827", "fund000001", "fund320007"), hits.map { it.id })
        assertTrue(hits.all { it.market == Market.FUND })
        assertEquals("混合型-偏股", hits.first().kind)
        assertEquals("易方达蓝筹精选混合", hits.first().name)
    }

    @Test
    fun `000001 这种同号代码靠 id 前缀区分股票和基金`() {
        assertEquals("fund000001", StockCode.stockIdFor("000001", Market.FUND))
        assertEquals("sz000001", StockCode.stockIdFor("000001", Market.A))
        assertTrue(StockCode.isFundId("fund000001"))
        assertFalse(StockCode.isFundId("sz000001"))
        assertEquals(Market.FUND, StockCode.guessMarket("fund005827"))
        // 六位纯数字的基金代码猜不出来，必须有前缀
        assertEquals(Market.A, StockCode.guessMarket("005827"))
    }

    @Test
    fun `盘中有估值就用估值，昨净值仍是 NAV`() {
        val quotes = QuoteClient.parseFund(withEstimate)

        val blue = quotes.getValue("fund005827")
        assertEquals(13412L, blue.priceE4)   // GSZ 1.3412
        assertEquals(13289L, blue.prevCloseE4) // NAV 1.3289
        assertEquals("eastmoney-fund", blue.source)
        assertTrue(blue.changeE4 > 0)
    }

    @Test
    fun `收盘后 GSZ 为空，用净值涨跌反推昨净值`() {
        val quotes = QuoteClient.parseFund(withEstimate)

        // 华夏成长：NAV 0.9120、当日 +1.22% → 昨净值 0.9120/1.0122 ≈ 0.9010
        val hua = quotes.getValue("fund000001")
        assertEquals(9120L, hua.priceE4)
        assertEquals(9010L, hua.prevCloseE4)
        assertEquals(1.22, hua.changePercent, 0.02)
    }

    @Test
    fun `净值缺失的行直接跳过，不生成 0 价`() {
        val body = """{"Datas":[{"FCODE":"005827","SHORTNAME":"易方达蓝筹精选混合","NAV":""}]}"""
        assertTrue(QuoteClient.parseFund(body).isEmpty())
    }

    @Test
    fun `份额带小数也能算出市值`() {
        // 1234.56 份 × 1.3289 = 1640.61 元
        assertEquals(164_061L, StockMath.valueCents(1234.56, 13289L))
        // 成本 1.42：1234.56 × 1.42 = 1753.08 元
        assertEquals(175_308L, StockMath.valueCents(1234.56, 14200L))
        assertEquals(0L, StockMath.valueCents(0.0, 13289L))
    }
}
