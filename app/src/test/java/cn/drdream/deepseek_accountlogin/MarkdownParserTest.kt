package cn.drdream.deepseek_accountlogin

import cn.drdream.deepseek_accountlogin.ui.markdown.MarkdownParser
import cn.drdream.deepseek_accountlogin.ui.markdown.MdBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun parsesHeadingsParagraphsAndCode() {
        val blocks = MarkdownParser.parse(
            """
            # Title

            Hello **world**.

            ```kotlin
            fun main() = println("hi")
            ```
            """.trimIndent(),
        )
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(1, (blocks[0] as MdBlock.Heading).level)
        assertTrue(blocks[1] is MdBlock.Paragraph)
        val code = blocks[2] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("println"))
    }

    @Test
    fun parsesListsQuotesAndTables() {
        val blocks = MarkdownParser.parse(
            """
            - one
            - two

            > quoted text

            | Name | Value |
            | --- | --- |
            | A | 1 |
            | B | 2 |
            """.trimIndent(),
        )
        assertTrue(blocks[0] is MdBlock.BulletList)
        assertEquals(2, (blocks[0] as MdBlock.BulletList).items.size)
        assertTrue(blocks[1] is MdBlock.Quote)
        val table = blocks[2] as MdBlock.Table
        assertEquals(listOf("Name", "Value"), table.header)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("B", "2"), table.rows[1])
    }

    @Test
    fun parsesDisplayMath() {
        val blocks = MarkdownParser.parse(
            """
            $$
            \int_0^\infty e^{-x} dx = 1
            $$
            """.trimIndent(),
        )
        assertTrue(blocks.single() is MdBlock.Math)
        assertTrue((blocks.single() as MdBlock.Math).latex.contains("int"))
    }

    @Test
    fun keepsChineseTextIntact() {
        val blocks = MarkdownParser.parse("你好，**世界**！")
        assertTrue(blocks.single() is MdBlock.Paragraph)
        assertEquals("你好，**世界**！", (blocks.single() as MdBlock.Paragraph).text)
    }
}
