package cn.neeto.stockpot.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshNoteTest {

    @Test
    fun `没登录、没有股票就是已经是最新的`() {
        assertEquals("已经是最新的", RefreshNote.of(synced = null, syncError = null, stocks = 0))
    }

    @Test
    fun `同步成功不带错误`() {
        assertEquals(
            "已和云端对齐",
            RefreshNote.of(synced = true, syncError = "不该出现", stocks = 0),
        )
    }

    @Test
    fun `同步失败要说清原因`() {
        assertEquals(
            "云端没对上：连接超时",
            RefreshNote.of(synced = false, syncError = "连接超时", stocks = 0),
        )
    }

    @Test
    fun `没给原因时兜底网络不通`() {
        assertEquals("云端没对上：网络不通", RefreshNote.of(synced = false, syncError = "  ", stocks = 0))
    }

    @Test
    fun `股票和有云端一起报`() {
        assertEquals(
            "已和云端对齐 · 行情已刷新",
            RefreshNote.of(synced = true, syncError = null, stocks = 12),
        )
    }

    @Test
    fun `只刷新行情也说得过去`() {
        assertEquals("行情已刷新", RefreshNote.of(synced = null, syncError = null, stocks = 3))
    }

    @Test
    fun `超长报错截断，别把吐司顶穿`() {
        val note = RefreshNote.of(synced = false, syncError = "x".repeat(200), stocks = 0)
        assertTrue("整句不该超过 60 个字", note.length <= 60)
        assertTrue(note.endsWith("…"))
    }
}
