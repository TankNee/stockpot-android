package cn.neeto.stockpot.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParserTest {

    private val alipayHeader = listOf(
        "交易时间", "交易分类", "交易对方", "对方账号", "商品说明", "收/支", "金额", "收/付款方式", "交易状态", "交易订单号", "备注",
    )

    @Test
    fun `parses alipay rows by header name`() {
        val rows = listOf(
            alipayHeader,
            listOf("2026-11-01 12:30:00", "餐饮美食", "瑞幸咖啡", "x", "生椰拿铁", "支出", "19.50", "余额宝", "交易成功", "AL001", ""),
            listOf("2026-11-02 09:00:00", "转账红包", "张三", "y", "转账", "收入", "200.00", "余额", "交易成功", "AL002", ""),
        )
        val bill = BillParser.parseRows(rows)
        assertEquals("alipay", bill.source)
        assertTrue(bill.detected)
        assertEquals(2, bill.rows.size)
        assertEquals(1950L, bill.rows[0].amount)
        assertEquals("2026-11-01", bill.rows[0].date)
        assertEquals("12:30", bill.rows[0].time)
        assertEquals("瑞幸咖啡", bill.rows[0].counterparty)
        assertEquals("expense", bill.rows[0].kind)
        assertEquals("income", bill.rows[1].kind)
    }

    @Test
    fun `wechat header does not mistake 交易类型 for direction`() {
        val rows = listOf(
            listOf("交易时间", "交易类型", "交易对方", "商品", "收/支", "金额(元)", "支付方式", "当前状态", "交易单号"),
            listOf("2026-11-03 08:05", "商户消费", "肠粉店", "早餐", "支出", "¥8.00", "零钱", "支付成功", "WX001"),
        )
        val bill = BillParser.parseRows(rows)
        assertEquals("wechat", bill.source)
        assertEquals(1, bill.rows.size)
        assertEquals(800L, bill.rows[0].amount)
        assertEquals("expense", bill.rows[0].kind)
    }

    @Test
    fun `excel serial dates are converted`() {
        val stamp = BillParser.normalizeDate("46348.45753472222")
        assertEquals("2026-11-22 10:58", stamp)
    }

    @Test
    fun `amounts with currency symbols and thousands separators`() {
        assertEquals(123456L, BillParser.parseAmountCents("\"¥1,234.56\""))
        assertEquals(1500L, BillParser.parseAmountCents("-15.00"))
        assertNull(BillParser.parseAmountCents(""))
        assertNull(BillParser.parseAmountCents("--"))
    }

    @Test
    fun `closed rows are ignored, refunds are kept`() {
        val rows = listOf(
            alipayHeader,
            listOf("2026-11-01 10:00", "购物", "淘宝", "", "耳机", "支出", "99.00", "余额", "交易关闭", "A1", ""),
            listOf("2026-11-02 10:00", "购物", "淘宝", "", "耳机", "支出", "99.00", "余额", "退款成功", "A2", ""),
        )
        val bill = BillParser.parseRows(rows)
        assertEquals(1, bill.ignored)
        assertEquals(1, bill.rows.size)
        assertTrue(bill.rows[0].selfRefunded)
    }

    @Test
    fun `generic bank csv with separate expense and income columns`() {
        val rows = listOf(
            listOf("交易日期", "摘要", "支出金额", "收入金额", "余额"),
            listOf("2026/11/05", "超市消费", "128.40", "", "1000.00"),
            listOf("2026/11/06", "工资", "", "8,000.00", "9000.00"),
        )
        val bill = BillParser.parseRows(rows)
        assertEquals("generic", bill.source)
        assertEquals(2, bill.rows.size)
        assertEquals("expense", bill.rows[0].kind)
        assertEquals(12840L, bill.rows[0].amount)
        assertEquals("income", bill.rows[1].kind)
        assertEquals(800000L, bill.rows[1].amount)
    }

    @Test
    fun `icost layout keeps its two level category`() {
        val rows = listOf(
            listOf("日期", "类型", "金额", "一级分类", "二级分类", "账户1", "备注"),
            listOf("2026-11-07", "支出", "45.00", "餐饮", "午餐", "微信", "公司楼下"),
        )
        val bill = BillParser.parseRows(rows)
        assertEquals("icost", bill.source)
        assertEquals("餐饮/午餐", bill.rows[0].category)
        assertEquals(4500L, bill.rows[0].amount)
    }

    @Test
    fun `csv reader survives quoted commas and newlines`() {
        val rows = Csv.rows("a,b,c\n\"x,y\",\"line1\nline2\",z\n")
        assertEquals(2, rows.size)
        assertEquals("x,y", rows[1][0])
        assertEquals("line1\nline2", rows[1][1])
    }

    @Test
    fun `gbk alipay export decodes`() {
        val text = "交易时间,交易对方,收/支,金额\n2026-11-01,早餐店,支出,5.00\n"
        val bytes = text.toByteArray(charset("GB18030"))
        val bill = BillParser.parseBytes(bytes)
        assertEquals(1, bill.rows.size)
        assertEquals("早餐店", bill.rows[0].counterparty)
    }
}
