package cn.neeto.stockpot.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Markdown 只做更新说明够用的那一小撮语法。
 * 这里守两件事：该分块的要分开，认不出来的字一个都不能丢。
 */
class MarkdownTest {

    @Test
    fun `headings bullets and paragraphs`() {
        val blocks = parseMarkdown(
            """
            ## 0.18.2

            挑一笔退款变成了整页。

            - 能搜
            - 能筛日期
            - 能看细节

            最后一段。
            """.trimIndent(),
        )
        assertEquals(4, blocks.size)
        assertEquals(MdBlock.Heading(2, "0.18.2"), blocks[0])
        assertEquals(MdBlock.Para("挑一笔退款变成了整页。"), blocks[1])
        assertEquals(MdBlock.Bullet(listOf("能搜", "能筛日期", "能看细节")), blocks[2])
        assertEquals(MdBlock.Para("最后一段。"), blocks[3])
    }

    @Test
    fun `ordered list keeps the number it was written with`() {
        val blocks = parseMarkdown("3. 第三\n4. 第四")
        val list = blocks.single() as MdBlock.Ordered
        assertEquals(3, list.start)
        assertEquals(listOf("第三", "第四"), list.items)
    }

    @Test
    fun `code fence keeps its lines and knows where it ends`() {
        val blocks = parseMarkdown("```\nval a = 1\nval b = 2\n```\n\n后面还有字")
        assertEquals(MdBlock.Code(listOf("val a = 1", "val b = 2")), blocks[0])
        assertEquals(MdBlock.Para("后面还有字"), blocks[1])
    }

    @Test
    fun `a broken block never swallows text`() {
        // 只有开头没有结尾的围栏：剩下的行当代码收着，也不丢
        val blocks = parseMarkdown("```\n孤零零一行")
        assertEquals(MdBlock.Code(listOf("孤零零一行")), blocks.single())
    }

    @Test
    fun `rule and quote`() {
        val blocks = parseMarkdown("---\n\n> 一句提醒")
        assertEquals(MdBlock.Rule, blocks[0])
        assertEquals(MdBlock.Quote("一句提醒"), blocks[1])
    }

    @Test
    fun `plain text with no markdown is one paragraph`() {
        val blocks = parseMarkdown("就一句话，没有别的。")
        assertTrue(blocks.single() is MdBlock.Para)
    }
}
