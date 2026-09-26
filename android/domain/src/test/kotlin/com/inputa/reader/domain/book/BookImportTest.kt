package com.inputa.reader.domain.book

import com.inputa.reader.domain.model.BookSource
import com.inputa.reader.domain.util.Clock
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件导入的解析层。
 *
 * 覆盖的原则与 Web 版的 `src/__tests__/bookImport.test.ts` 一致 —— 那是同一个功能的
 * 另一份实现，两边的行为要对得上。EPUB 的夹具是**在代码里现造 zip**（见 [buildZip]），
 * 不使用 `src/test/resources`：`:domain` 至今没有二进制夹具，不新开这个惯例。
 */
class BookImportTest {

    /* ------------------------------ 实体解码 ------------------------------ */

    @Test
    fun `decodes named, decimal and hexadecimal entities`() {
        assertEquals(
            "a & b <c> ’quoted’ — dash",
            decodeEntities("a &amp; b &lt;c&gt; &#8217;quoted&#8217; &#x2014; dash"),
        )
    }

    @Test
    fun `keeps an entity it does not recognise, rather than eating it`() {
        assertEquals("&nosuchentity; stays", decodeEntities("&nosuchentity; stays"))
        // 越界与代理区：Web 那边是「原样保留」，这里必须一致 —— 不能抛，也不能产出乱码。
        assertEquals("&#x110000; out of range", decodeEntities("&#x110000; out of range"))
        assertEquals("&#xD800; surrogate", decodeEntities("&#xD800; surrogate"))
    }

    /* ---------------------------- HTML → 纯文本 ---------------------------- */

    @Test
    fun `drops script and style content entirely`() {
        val html = "<p>before</p><script>var x = \"secret\";</script><style>.a{color:red}</style><p>after</p>"
        val text = htmlToPlainText(html)

        assertFalse(text.contains("secret"))
        assertFalse(text.contains("color"))
        assertEquals("before\n\nafter", text)
    }

    @Test
    fun `separates block elements with a blank line, which is what pagination keys on`() {
        // 分页是按空行切段落的（Pagination.paginate 用 `\n\s*\n`），所以相邻块级元素之间
        // 必须是**空行**；换成单换行会把两段粘成一段、丢掉段落边界。
        assertEquals("one\n\ntwo", htmlToPlainText("<div>one</div><div>two</div>"))
        assertEquals("one\n\ntwo", htmlToPlainText("<p>one</p><p>two</p>"))
        // 段内的 <br> 是行内换行，只给一个换行 —— 不该被当成新段落。
        assertEquals("a\nb", htmlToPlainText("a<br/>b"))
    }

    @Test
    fun `leaves inline tags without adding spaces`() {
        // 若把标签换成空格，`<b>the</b> <i>cat</i>` 会变成 "the  cat"，词间距就被弄乱了。
        assertEquals("the cat", htmlToPlainText("<p><b>the</b> <i>cat</i></p>"))
    }

    @Test
    fun `collapses non-breaking spaces too, not just plain ones`() {
        // 这一条盯的是 WHITESPACE_RUN 里那个看不见的 U+00A0：漏掉它，`&nbsp;` 与 EPUB 里
        // 原生的 NBSP 就不会被折叠，两端的分页随之不同。
        assertEquals("a b", htmlToPlainText("<p>a&nbsp;&nbsp;&nbsp;b</p>"))
        assertEquals("a b", htmlToPlainText("<p>a b</p>"))
        assertEquals("fish & chips\n\nnext", htmlToPlainText("<p>fish &amp; chips</p>\n\n\n<p>next</p>"))
    }

    /* ------------------------------ Markdown ------------------------------ */

    @Test
    fun `strips leading hashes so the chapter splitter can see the heading`() {
        // 不剥 `#`，`## CHAPTER II` 就认不出来，整本 md 会退化成一个均分块。
        assertEquals("CHAPTER II\n\ntext", markdownToPlainText("## CHAPTER II\n\ntext"))
    }

    @Test
    fun `reduces a link to its label`() {
        assertEquals("see the docs now", markdownToPlainText("see [the docs](https://example.test) now"))
    }

    @Test
    fun `leaves emphasis markers alone rather than risking eaten text`() {
        assertEquals("**bold** and _it_", markdownToPlainText("**bold** and _it_"))
    }

    /* ------------------------------ 类型判断 ------------------------------ */

    @Test
    fun `recognises the supported extensions regardless of case`() {
        assertEquals(ImportKind.EPUB, parseKindOf("book.epub"))
        assertEquals(ImportKind.EPUB, parseKindOf("BOOK.EPUB"))
        assertEquals(ImportKind.TEXT, parseKindOf("a.txt"))
        assertEquals(ImportKind.MARKDOWN, parseKindOf("a.markdown"))
        assertEquals(ImportKind.HTML, parseKindOf("a.HTM"))
    }

    @Test
    fun `rejects what it does not support`() {
        assertEquals(null, parseKindOf("paper.pdf"))
        assertEquals(null, parseKindOf("no-extension"))
    }

    /* ---------------------------- TXT → 章节 ---------------------------- */

    @Test
    fun `splits a pasted or imported text through the shared splitter`() {
        // 每章正文都要超过 200 字符：切分器会把小于 200 字符的碎片并进前一章。
        val text = listOf(
            "CHAPTER I.",
            "It is a truth universally acknowledged, that a single man in possession of a good fortune, must be in want of a wife.",
            "",
            "However little known the feelings or views of such a man may be on his first entering a neighbourhood, this truth is so well fixed in the minds of the surrounding families.",
            "",
            "CHAPTER II.",
            "Mr Bennet was among the earliest of those who waited on Mr Bingley. He had always intended to visit him, though to the last always assuring his wife that he should not go.",
            "",
            "Till the evening after the visit was paid she had no knowledge of it. It was then disclosed in the following manner: he had been one of the first to wait on Mr Bingley.",
        ).joinToString("\n")

        val chapters = textToChapters(text, "Fallback")

        assertEquals(2, chapters.size)
        assertTrue(chapters[0].title.contains("CHAPTER I"))
        assertTrue(chapters[1].content.contains("Mr Bennet"))
    }

    @Test
    fun `falls back to a single chapter for a short text with no headings`() {
        val chapters = textToChapters("Just one short paragraph.", "My Article")

        assertEquals(1, chapters.size)
        assertEquals("My Article", chapters[0].title)
    }

    /* ----------------------------- EPUB ---------------------------------- */

    @Test
    fun `reads a minimal EPUB with deflated entries`() {
        val chapters = epubToChapters(buildEpub(ZipVariant.DEFLATED))

        // 封面（linear="no"）与目录页（properties="nav"）都不该出现，所以正好两章。
        assertEquals(2, chapters.size)
        assertEquals("Chapter One", chapters[0].title)
        assertTrue(chapters[0].content.contains("quick brown fox"))
        assertEquals("Chapter Two", chapters[1].title)
        assertTrue(chapters[1].content.contains("best of times"))
    }

    @Test
    fun `reads the same EPUB when its entries are stored or streamed`() {
        // stored 是小文件的合法写法；DESCRIPTOR 是「局部头里的尺寸写着 0」那一类，
        // 也就是 Web 版手写 zip 读取时最容易解出空内容的那种。这里由 JDK 处理，
        // 但值得钉住 —— 将来若换成手写实现，这两条会立刻变红。
        for (variant in listOf(ZipVariant.STORED, ZipVariant.DESCRIPTOR)) {
            val chapters = epubToChapters(buildEpub(variant))
            assertEquals("variant=$variant", 2, chapters.size)
            assertTrue("variant=$variant", chapters[0].content.contains("quick brown fox"))
        }
    }

    @Test
    fun `prefers the heading over the title element, which repeats the book name`() {
        // 夹具里每章的 <title> 都是「测试书」；若不优先看 <h1>，四十章会同名。
        val chapters = epubToChapters(buildEpub(ZipVariant.DEFLATED))

        assertEquals(listOf("Chapter One", "Chapter Two"), chapters.map { it.title })
    }

    @Test
    fun `skips a spine entry whose file is missing instead of failing the import`() {
        val chapters = epubToChapters(buildEpub(ZipVariant.DEFLATED, includeSecondChapter = false))

        assertEquals(1, chapters.size)
    }

    @Test
    fun `reports a clear error for each way an EPUB can be unusable`() {
        val notAZip = runCatching { epubToChapters("this is plainly not a zip".toByteArray()) }
            .exceptionOrNull()
        assertTrue(notAZip is BookImportException)
        assertTrue(notAZip?.message?.contains("zip") == true)

        val noContainer = runCatching {
            epubToChapters(buildEpub(ZipVariant.DEFLATED, includeContainer = false))
        }.exceptionOrNull()
        assertTrue(noContainer is BookImportException)
        assertTrue(noContainer?.message?.contains("container.xml") == true)

        // 图片版（扫描件）的典型形态：spine 里有正经内容，但每页抽出来都是空。
        val imageOnly = runCatching { epubToChapters(imageOnlyEpub()) }.exceptionOrNull()
        assertTrue(imageOnly is BookImportException)
        assertTrue(imageOnly?.message?.contains("抽不出文字") == true)
    }

    /* --------------------------- 入口：importBook -------------------------- */

    @Test
    fun `assembles a book from the file name, its bytes and the clock`() {
        val clock = Clock { 1_700_000_000_000 }

        val book = importBook("My Article.txt", "The quick brown fox jumps over the lazy dog. ".repeat(30).toByteArray(), clock)

        assertEquals("custom-1700000000000", book.id)
        assertEquals("My Article", book.title)
        assertEquals("User Imported", book.author)
        assertEquals(BookSource.CUSTOM, book.source)
        assertEquals("en", book.language)
        assertTrue(book.chapters.isNotEmpty())
        assertNotNull(book.chapters[0].content)
    }

    @Test
    fun `refuses an unsupported file type by name`() {
        val error = runCatching { importBook("paper.pdf", ByteArray(0), Clock { 0 }) }.exceptionOrNull()

        assertTrue(error is BookImportException)
        assertTrue(error?.message?.contains("paper.pdf") == true)
    }
}

/* ------------------------------- zip 夹具 ------------------------------- */

/** 条目怎么写进 zip。三种都要能读 —— 见 `reads the same EPUB when its entries are stored or streamed`。 */
private enum class ZipVariant { DEFLATED, STORED, DESCRIPTOR }

private fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

/**
 * 在代码里造一个最小但合法的 zip。
 *
 * 用 `ZipOutputStream` 而不是像 Web 版那样手写中央目录与 EOCD —— JDK 已经会写这些，
 * 而这里要验的是**读**的那一侧。
 */
private fun buildZip(files: List<Pair<String, ByteArray>>, variant: ZipVariant): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in files) {
            val entry = ZipEntry(name)
            when (variant) {
                ZipVariant.DEFLATED -> Unit // 默认就是 deflate
                ZipVariant.STORED -> {
                    // STORED 必须先把尺寸与校验和告诉它，否则 JDK 直接抛。
                    entry.method = ZipEntry.STORED
                    entry.size = bytes.size.toLong()
                    entry.crc = crc32(bytes)
                }
                ZipVariant.DESCRIPTOR -> Unit // 不预设 size/crc，JDK 就会走 data descriptor
            }
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

private const val CONTAINER_XML = """<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""

private const val OPF = """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata><dc:title>测试书</dc:title></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="c1" href="text/ch1.xhtml" media-type="application/xhtml+xml"/>
    <item id="c2" href="text/ch2.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover" href="cover.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine>
    <itemref idref="cover" linear="no"/>
    <itemref idref="c1"/>
    <itemref idref="c2"/>
  </spine>
</package>"""

private const val CH1 = """<html><head><title>测试书</title></head><body>
  <h1>Chapter One</h1><p>The quick brown fox jumps over the lazy dog.</p></body></html>"""

private const val CH2 = """<html><head><title>测试书</title></head><body>
  <h2>Chapter Two</h2><p>It was the best of times, it was the worst of times.</p></body></html>"""

private fun buildEpub(
    variant: ZipVariant,
    includeContainer: Boolean = true,
    includeSecondChapter: Boolean = true,
): ByteArray {
    val files = ArrayList<Pair<String, ByteArray>>()
    files += "mimetype" to "application/epub+zip".toByteArray()
    if (includeContainer) files += "META-INF/container.xml" to CONTAINER_XML.toByteArray()
    files += "OEBPS/content.opf" to OPF.toByteArray()
    files += "OEBPS/nav.xhtml" to "<html><body><nav>目录</nav></body></html>".toByteArray()
    files += "OEBPS/cover.xhtml" to """<html><body><img src="cover.png"/></body></html>""".toByteArray()
    files += "OEBPS/text/ch1.xhtml" to CH1.toByteArray()
    if (includeSecondChapter) files += "OEBPS/text/ch2.xhtml" to CH2.toByteArray()
    return buildZip(files, variant)
}

/** 只有图片的 EPUB：spine 里有正经内容，但抽出来全是空。 */
private fun imageOnlyEpub(): ByteArray {
    val opf = """<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0">
  <metadata><dc:title>扫描件</dc:title></metadata>
  <manifest><item id="p1" href="page1.xhtml" media-type="application/xhtml+xml"/></manifest>
  <spine><itemref idref="p1"/></spine>
</package>"""
    return buildZip(
        listOf(
            "META-INF/container.xml" to CONTAINER_XML.toByteArray(),
            "OEBPS/content.opf" to opf.toByteArray(),
            "OEBPS/page1.xhtml" to """<html><body><img src="page1.png" alt=""/></body></html>""".toByteArray(),
        ),
        ZipVariant.DEFLATED,
    )
}
