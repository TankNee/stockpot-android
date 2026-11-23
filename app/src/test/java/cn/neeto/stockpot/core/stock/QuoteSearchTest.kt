package cn.neeto.stockpot.core.stock

import cn.neeto.stockpot.core.model.Market
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 解析的是腾讯 smartbox 的真实返回：名字是 `\uXXXX` 转义，多条用 `^` 隔，
 * 指数/债券也会混进来，得挑出去。
 */
class QuoteSearchTest {

    private val maotai = "v_hint=\"sh~600519~\\u8d35\\u5dde\\u8305\\u53f0~gzmt~GP-A\""

    private val tencent = "v_hint=\"sh~000847~\\u817e\\u8baf\\u6d4e\\u5b89~txja~ZS" +
        "^hk~00700~\\u817e\\u8baf\\u63a7\\u80a1~txkg~GP" +
        "^hk~80700~\\u817e\\u8baf\\u63a7\\u80a1r~txkgr~GP" +
        "^us~tcehy.ps~\\u817e\\u8baf\\u63a7\\u80a1(adr)~txkgadr~GP\""

    @Test
    fun `A股一条：名字要还原成中文，id 带市场前缀`() {
        val hits = QuoteClient.parseSearch(maotai)

        assertEquals(1, hits.size)
        val hit = hits.first()
        assertEquals("sh600519", hit.id)
        assertEquals("600519", hit.code)
        assertEquals("贵州茅台", hit.name)
        assertEquals(Market.A, hit.market)
        assertEquals("GP-A", hit.kind)
    }

    @Test
    fun `指数被滤掉，港股美股各自成 id`() {
        val hits = QuoteClient.parseSearch(tencent)

        assertEquals(listOf("hk00700", "hk80700", "ustcehy"), hits.map { it.id })
        assertEquals(listOf(Market.HK, Market.HK, Market.US), hits.map { it.market })
        assertEquals("腾讯控股", hits.first().name)
    }

    @Test
    fun `美股代码去掉交易所后缀`() {
        val hit = QuoteClient.parseSearch(tencent).first { it.market == Market.US }
        assertEquals("TCEHY", hit.code)
        assertEquals("ustcehy", hit.id)
    }

    @Test
    fun `港股代码补足五位`() {
        val hits = QuoteClient.parseSearch("v_hint=\"hk~700~\\u817e\\u8baf~tx~GP\"")
        assertEquals("hk00700", hits.first().id)
        assertEquals("00700", hits.first().code)
    }

    @Test
    fun `搜不到时是空列表，不炸`() {
        assertTrue(QuoteClient.parseSearch("v_hint=\"\"").isEmpty())
        assertTrue(QuoteClient.parseSearch("").isEmpty())
        assertTrue(QuoteClient.parseSearch("v_hint=\"garbage\"").isEmpty())
    }

    @Test
    fun `ETF 保留，债券丢弃`() {
        val body = "v_hint=\"sh~512800~\\u94f6\\u884cETF\\u534e\\u5b9d~yhetfhb~ETF" +
            "^sh~019547~\\u56fd\\u503a~gz~QZR\""
        val hits = QuoteClient.parseSearch(body)

        assertEquals(1, hits.size)
        assertEquals("512800", hits.first().code)
        assertEquals("ETF", hits.first().kind)
    }
}
