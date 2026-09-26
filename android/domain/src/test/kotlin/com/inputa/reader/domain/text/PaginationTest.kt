package com.inputa.reader.domain.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 移植自 Web 版的 src/__tests__/reader.test.ts（分页部分），另补了几条 Web 版没有的断言。
 *
 * 为什么这里值得多写：分页边界就是「翻页自动标掌握」写数据的边界。两端切得不一样，
 * 同一本书就会往词库里写进不同的词。
 *
 * 空白字符用码点构造，理由见 TextNormalizeTest。
 */
class PaginationTest {

    private fun cp(codePoint: Int): String = codePoint.toChar().toString()

    private val nbsp = cp(0x00A0)

    private fun wordCount(page: String): Int = page.trim().split(Regex("\\s+")).size

    @Test
    fun `paginates into chunks while keeping paragraphs intact`() {
        val paragraph1 = "A ".repeat(150)
        val paragraph2 = "B ".repeat(150)
        val pages = Pagination.paginate("$paragraph1\n\n$paragraph2", targetWordsPerPage = 100)

        assertTrue("expected at least 2 pages, got ${pages.size}", pages.size >= 2)
        assertTrue(pages[0].contains("A"))
        assertTrue(pages[1].contains("B"))
    }

    /** 翻页器依赖这一点：页数永远不为 0，否则页索引会越界。 */
    @Test
    fun `never returns an empty list`() {
        assertEquals(listOf(""), Pagination.paginate(""))
        assertEquals(listOf(""), Pagination.paginate("   "))
    }

    @Test
    fun `a short chapter stays one page`() {
        val text = "Short first paragraph.\n\nShort second paragraph."
        assertEquals(1, Pagination.paginate(text, targetWordsPerPage = 220).size)
    }

    /**
     * 单段超过目标的 1.5 倍时按句子切 —— 否则一个超长段落会独占一页，
     * 页边界落在毫无意义的位置。
     */
    @Test
    fun `splits an oversized paragraph by sentences`() {
        val sentence = "This is a fairly long sentence about reading. "
        val huge = sentence.repeat(40) // 约 320 词 > 100 * 1.5
        val pages = Pagination.paginate(huge, targetWordsPerPage = 100)

        assertTrue("expected the long paragraph to be split, got ${pages.size}", pages.size > 1)
        // 单句本身超长时允许溢出（不硬切句子），所以上界放到 2 倍。
        for (page in pages) {
            assertTrue("page of ${wordCount(page)} words is too large", wordCount(page) <= 200)
        }
    }

    /**
     * JS 的 `\s` 认 NBSP，Java 的不认。不做归一化，同一本书在两端就会切出不同的页 ——
     * 进而写下不同的词库。这条测试钉住这个等价性。
     */
    @Test
    fun `NBSP does not change page boundaries`() {
        val withSpace = "alpha beta gamma delta\n\nepsilon zeta eta theta"
        val withNbsp = withSpace.replace(" ", nbsp)

        assertEquals(
            Pagination.paginate(withSpace, targetWordsPerPage = 3),
            Pagination.paginate(withNbsp, targetWordsPerPage = 3),
        )
    }

    /** 归一化必须发生在判空**之前**：JS 的 trim 会把它清空，Kotlin 的不会。 */
    @Test
    fun `a chapter of only NBSP is treated as empty`() {
        assertEquals(listOf(""), Pagination.paginate(nbsp + nbsp))
        assertEquals(listOf(""), Pagination.paginate(cp(0x3000)))
        assertEquals(listOf(""), Pagination.paginate(cp(0xFEFF)))
    }

    @Test
    fun `paragraph breaks survive into the page text`() {
        // 页内段落之间仍以空行分隔 —— 阅读器按 \n\n 分段渲染。
        val pages = Pagination.paginate("One paragraph.\n\nTwo paragraph.", targetWordsPerPage = 220)
        assertEquals(1, pages.size)
        assertTrue(pages[0].contains("\n\n"))
    }
}
