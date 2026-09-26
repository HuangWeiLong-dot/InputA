package com.inputa.reader.domain.book

import com.inputa.reader.domain.model.BookChapter
import com.inputa.reader.domain.model.BookSource
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gutendex 导入。移植自 Web 版的 `src/__tests__/gutendex.test.ts`，另加了
 * **长度上限**那一批 —— 那些是 Android 特有的，Web 版不可能有。
 *
 * 导入是两步：先拿书目信息与下载链接表，再单独下载正文。Web 上第二步必须走后端代理
 * （Gutenberg 不返回 CORS 头，且会 302 到纯 HTTP 缓存地址）；Android 没有 CORS 限制，
 * 但仍然走同一条后端路由，因为 SSRF 白名单与 302 跟随都在服务端做了。
 */
class GutendexTest {

    private val frankenstein = GutendexBook(
        id = 84,
        title = "Frankenstein; Or, The Modern Prometheus",
        authors = listOf("Shelley, Mary Wollstonecraft"),
        formats = mapOf(
            "text/html; charset=utf-8" to "https://www.gutenberg.org/ebooks/84.html.utf-8",
            "application/epub+zip" to "https://www.gutenberg.org/ebooks/84.epub.images",
            "text/plain; charset=us-ascii" to "https://www.gutenberg.org/files/84/84-0.txt",
            "text/plain; charset=utf-8" to "https://www.gutenberg.org/ebooks/84.txt.utf-8",
            "image/jpeg" to "https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg",
        ),
        downloadCount = 4321,
        languages = listOf("en"),
    )

    private val gutenbergText = listOf(
        "The Project Gutenberg eBook of Frankenstein",
        "",
        "This eBook is for the use of anyone anywhere in the United States.",
        "",
        "*** START OF THE PROJECT GUTENBERG EBOOK FRANKENSTEIN ***",
        "",
        "CHAPTER I.",
        "",
        "Letter one text. ".repeat(30).trim(),
        "",
        "CHAPTER II.",
        "",
        "Letter two text. ".repeat(30).trim(),
        "",
        "*** END OF THE PROJECT GUTENBERG EBOOK FRANKENSTEIN ***",
        "",
        "Updated editions will replace the previous one.",
    ).joinToString("\n")

    // ---------------------------------------------------------- 第 1 步：链接

    @Test
    fun `collects only the plain-text links, UTF-8 first`() {
        assertEquals(
            listOf(
                "https://www.gutenberg.org/ebooks/84.txt.utf-8",
                "https://www.gutenberg.org/files/84/84-0.txt",
            ),
            PlainTextUrls.collect(frankenstein),
        )
    }

    @Test
    fun `reports nothing when a book has no plain-text format`() {
        assertEquals(
            emptyList<String>(),
            PlainTextUrls.collect(
                frankenstein.copy(
                    formats = mapOf(
                        "application/epub+zip" to "https://www.gutenberg.org/ebooks/84.epub.images",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `ignores non-http links and duplicates`() {
        val item = frankenstein.copy(
            formats = mapOf(
                "text/plain; charset=utf-8" to "https://example.com/a.txt",
                "text/plain" to "https://example.com/a.txt", // 重复
                "text/plain; charset=iso-8859-1" to "file:///etc/passwd", // 非 http
            ),
        )
        assertEquals(listOf("https://example.com/a.txt"), PlainTextUrls.collect(item))
    }

    // ---------------------------------------------------------- 切章

    @Test
    fun `strips the Gutenberg header and footer, then splits on chapter headings`() {
        val chapters = GutenbergTextProcessor.process(gutenbergText, "Frankenstein")

        assertEquals(listOf("CHAPTER I.", "CHAPTER II."), chapters.map { it.title })
        assertTrue(chapters[0].content.contains("Letter one text."))
        assertTrue(!chapters[0].content.contains("START OF THE PROJECT GUTENBERG"))
        assertTrue(chapters[1].content.contains("Letter two text."))
        assertTrue(!chapters[1].content.contains("END OF THE PROJECT GUTENBERG"))
        assertTrue(!chapters[1].content.contains("Updated editions will replace"))
    }

    @Test
    fun `keeps a long title page as a front-matter chapter`() {
        val titlePage = "A very long dedication. ".repeat(40).trim()
        val body = "Body text. ".repeat(40).trim()
        val more = "More text. ".repeat(40).trim()

        val chapters = GutenbergTextProcessor.process(
            "$titlePage\n\nCHAPTER I.\n\n$body\n\nCHAPTER II.\n\n$more",
            "Book",
        )

        assertEquals("前言 / Front Matter", chapters[0].title)
        assertTrue(chapters[0].content.contains("A very long dedication."))
        assertTrue(chapters.map { it.title }.contains("CHAPTER II."))
    }

    @Test
    fun `falls back to size based sections for books without headings`() {
        val chapters = GutenbergTextProcessor.process("word ".repeat(2200), "Plain Book")

        assertTrue("expected more than one section, got ${chapters.size}", chapters.size > 1)
        assertEquals("Section 1", chapters[0].title)
    }

    @Test
    fun `keeps a short text as a single chapter named after the book`() {
        assertEquals(
            listOf(BookChapter("My Book", "A short public domain poem.")),
            GutenbergTextProcessor.process("A short public domain poem.", "My Book"),
        )
    }

    @Test
    fun `an empty body is still one chapter, never an empty list`() {
        assertEquals(listOf(BookChapter("Chapter 1", "")), GutenbergTextProcessor.process("", ""))
    }

    // ------------------------------------------- 长度上限（Android 特有）

    /**
     * 一条超长的章节必须被切开。不切的话 Room 会在读取时抛
     * `Row too big to fit into CursorWindow` —— 而这是**只有真机才会暴露**的失败，
     * 模拟器上多半看不出来。
     */
    @Test
    fun `splits a chapter longer than the size limit`() {
        val paragraph = "word ".repeat(100).trim()
        val huge = (paragraph + "\n\n").repeat(500) // 约 25 万字符
        val chapters = GutenbergTextProcessor.process(
            "CHAPTER I.\n\n$huge\n\nCHAPTER II.\n\nshort ending",
            "Huge",
        )

        val chapterOneParts = chapters.filter { it.title.startsWith("CHAPTER I.") }
        assertTrue("expected the long chapter to be split, got ${chapters.size} chapters", chapterOneParts.size > 1)
        for (chapter in chapters) {
            assertTrue(
                "chapter '${chapter.title}' is ${chapter.content.length} chars, over the limit",
                chapter.content.length <= GutenbergTextProcessor.MAX_CHAPTER_CHARS,
            )
        }
        // 第一块保留原标题，后续块带序号。
        assertEquals("CHAPTER I.", chapterOneParts[0].title)
        assertEquals("CHAPTER I. (2)", chapterOneParts[1].title)
    }

    /**
     * 整本书没有空行时，正文就是**一个**超长段落 —— 按段落切分块会永远进不去，
     * 于是产出单块超限的结果。这条钉住那种输入也被切碎。
     */
    @Test
    fun `splits an oversized single paragraph with no blank lines`() {
        val oneGiantParagraph = "word ".repeat(60_000).trim() // 约 30 万字符，无空行
        val chapters = GutenbergTextProcessor.process(
            "CHAPTER I.\n\n$oneGiantParagraph\n\nCHAPTER II.\n\nend",
            "Huge",
        )

        assertTrue(chapters.size > 1)
        for (chapter in chapters) {
            assertTrue(chapter.content.length <= GutenbergTextProcessor.MAX_CHAPTER_CHARS)
        }
    }

    /**
     * 极端情形：一个超长且**没有任何空格**的「词」。按空白切边界的逻辑会找不到边界，
     * 若不加保护就会原地打转。这条同时钉住「能切」与「能终止」。
     */
    @Test
    fun `splits a giant token with no whitespace at all and terminates`() {
        val noSpaces = "x".repeat(GutenbergTextProcessor.MAX_CHAPTER_CHARS * 2 + 5000)
        val chapters = GutenbergTextProcessor.process(
            "CHAPTER I.\n\n$noSpaces\n\nCHAPTER II.\n\nend",
            "Huge",
        )

        assertTrue(chapters.size > 1)
        for (chapter in chapters) {
            assertTrue(chapter.content.length <= GutenbergTextProcessor.MAX_CHAPTER_CHARS)
        }
        // 内容没丢：所有块的字符数之和至少覆盖原文。
        val total = chapters.sumOf { it.content.length }
        assertTrue(total >= noSpaces.length)
    }

    /**
     * 边界：恰好等于上限就不切，多一个字符就切。
     *
     * 两处容易写错的地方：**章节正文包含标题那一行**，所以填充长度要把前缀补回来；
     * 尾部也得够长（≥200 字符），否则会被「太短的碎片并回上一章」那条规则吞掉，
     * 于是断言看起来像切章逻辑出了问题。
     */
    @Test
    fun `a chapter exactly at the limit is left alone, one character over is split`() {
        val prefix = "CHAPTER I.\n\n"
        val tail = "CHAPTER II.\n\n" + "closing paragraph text. ".repeat(20).trim()
        val filler = GutenbergTextProcessor.MAX_CHAPTER_CHARS - prefix.length

        val atLimit = prefix + "y".repeat(filler)
        assertEquals(GutenbergTextProcessor.MAX_CHAPTER_CHARS, atLimit.length)
        assertEquals(
            listOf("CHAPTER I.", "CHAPTER II."),
            GutenbergTextProcessor.process("$atLimit\n\n$tail", "Huge").map { it.title },
        )

        val oneOver = atLimit + "y"
        val split = GutenbergTextProcessor.process("$oneOver\n\n$tail", "Huge")
        assertTrue("one character over the limit must split, got ${split.map { it.title }}", split.size > 2)
    }

    // ---------------------------------------------------------- 第 2 步 + 组装

    private fun loaderReturning(text: String) = GutendexBookLoader { text }

    @Test
    fun `assembles a readable book from the downloaded text`() = runTest {
        val book = loaderReturning(gutenbergText).load(frankenstein)

        assertEquals("gutendex-84", book.id)
        assertEquals(BookSource.GUTENBERG, book.source)
        assertEquals("Shelley, Mary Wollstonecraft", book.author)
        assertEquals("https://www.gutenberg.org/cache/epub/84/pg84.cover.medium.jpg", book.coverUrl)
        assertEquals(2, book.chapters.size)
        assertTrue(book.chapters[0].content.contains("Letter one text."))
        // 书源声明的语言优先，不做本地检测。
        assertEquals("en", book.language)
    }

    @Test
    fun `fails with a readable message when the book has no plain text`() = runTest {
        val noText = frankenstein.copy(
            formats = mapOf("text/html" to "https://www.gutenberg.org/ebooks/84.html.utf-8"),
        )

        val error = runCatching { loaderReturning(gutenbergText).load(noText) }.exceptionOrNull()

        assertTrue(error is BookDownloadException)
        assertTrue("message was: ${error?.message}", error?.message?.contains("纯文本") == true)
    }

    @Test
    fun `falls back to the next link when the first download fails`() = runTest {
        val attempted = mutableListOf<String>()
        val loader = GutendexBookLoader { url ->
            attempted += url
            if (url.endsWith(".utf-8")) throw BookDownloadException("上游返回 HTTP 502")
            gutenbergText
        }

        val book = loader.load(frankenstein)

        assertEquals(2, book.chapters.size)
        assertEquals(
            listOf(
                "https://www.gutenberg.org/ebooks/84.txt.utf-8",
                "https://www.gutenberg.org/files/84/84-0.txt",
            ),
            attempted,
        )
    }

    @Test
    fun `falls back to the next link when the first one answers with an empty body`() = runTest {
        val attempted = mutableListOf<String>()
        val loader = GutendexBookLoader { url ->
            attempted += url
            if (url.endsWith(".utf-8")) "   " else gutenbergText
        }

        val book = loader.load(frankenstein)

        assertEquals(2, book.chapters.size)
        assertEquals(2, attempted.size)
    }

    @Test
    fun `reports every failure when no link works`() = runTest {
        val loader = GutendexBookLoader { url -> throw BookDownloadException("$url 返回的是 HTML 页面") }

        val error = runCatching { loader.load(frankenstein) }.exceptionOrNull()

        assertTrue(error is BookDownloadException)
        val message = error?.message.orEmpty()
        assertTrue(message.contains("加载读物失败"))
        assertTrue("two attempts should both be reported", message.count { it == '；' } == 1)
    }

    @Test
    fun `a book with no authors falls back to Unknown Author`() = runTest {
        val book = loaderReturning("A short poem.").load(frankenstein.copy(authors = emptyList()))
        assertEquals("Unknown Author", book.author)
    }

    // ---------------------------------------------------------- 搜索结果映射

    @Test
    fun `maps a Gutendex search response`() {
        val payload = Json.parseToJsonElement(
            """
            {"count":1,"results":[
              {"id":84,"title":"Frankenstein","authors":[{"name":"Shelley, Mary Wollstonecraft"}],
               "formats":{"image/jpeg":"https://example.com/c.jpg"},
               "download_count":4321,"languages":["en"]}
            ]}
            """.trimIndent(),
        )

        val books = GutendexMapper.parseSearchResults(payload)

        assertEquals(1, books.size)
        assertEquals(84, books[0].id)
        assertEquals("Frankenstein", books[0].title)
        assertEquals(listOf("Shelley, Mary Wollstonecraft"), books[0].authors)
        assertEquals(4321, books[0].downloadCount)
        assertEquals(listOf("en"), books[0].languages)
        assertEquals("https://example.com/c.jpg", books[0].formats["image/jpeg"])
    }

    /** 没有 `languages` 字段的老响应不该让整个映射失败 —— 语言留给本地检测。 */
    @Test
    fun `tolerates a result with missing optional fields`() {
        val payload = Json.parseToJsonElement("""{"count":1,"results":[{"id":7,"title":"Thin"}]}""")

        val books = GutendexMapper.parseSearchResults(payload)

        assertEquals(1, books.size)
        assertEquals(emptyList<String>(), books[0].authors)
        assertEquals(emptyMap<String, String>(), books[0].formats)
        assertEquals(emptyList<String>(), books[0].languages)
        assertEquals(0, books[0].downloadCount)
    }

    @Test
    fun `returns nothing for a malformed or empty search response`() {
        assertEquals(emptyList<GutendexBook>(), GutendexMapper.parseSearchResults(null))
        assertEquals(emptyList<GutendexBook>(), GutendexMapper.parseSearchResults(Json.parseToJsonElement("{}")))
        assertEquals(
            emptyList<GutendexBook>(),
            GutendexMapper.parseSearchResults(Json.parseToJsonElement("""{"count":0,"results":[]}""")),
        )
        // 缺 id 的条目直接丢掉，而不是产出一个 id=0 的书。
        assertEquals(
            emptyList<GutendexBook>(),
            GutendexMapper.parseSearchResults(Json.parseToJsonElement("""{"results":[{"title":"x"}]}""")),
        )
    }
}
