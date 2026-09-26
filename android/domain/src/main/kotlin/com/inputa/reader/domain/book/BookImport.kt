package com.inputa.reader.domain.book

import com.inputa.reader.domain.lang.LanguageDetect
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.BookChapter
import com.inputa.reader.domain.model.BookSource
import com.inputa.reader.domain.text.TextNormalize
import com.inputa.reader.domain.util.Clock
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/**
 * 把用户选的文件变成一本可读的书：TXT / Markdown / HTML / EPUB。
 *
 * 与 Web 版 `src/services/bookImport.ts` **逐条对照**移植：同一本书在两端应该切出同样的
 * 章节、得到同样的标题。**刻意不支持 PDF** —— 安卓上要引入 PDFBox 之类的库（数 MB），
 * 而抽出的文本质量通常很差（分栏错位、页眉页脚混进正文），扫描件更是完全抽不出文字。
 *
 * 为什么放在 `:domain`：除 [importBook] 之外全是纯函数，而 `:domain` 是纯 Kotlin/JVM
 * 模块 —— `./gradlew :domain:test` 就能覆盖它们，不需要模拟器、不需要 Robolectric。
 * 选文件那一半（SAF / `ContentResolver`）留在 `:app`，把字节喂进来即可。
 *
 * 用到 `java.io` / `java.util.zip` 不违反模块边界：它们是 JDK，不是 android/androidx。
 */

/** 文件读不出来（类型不支持、不是有效的 EPUB、抽不出文字）。消息是给用户看的中文。 */
class BookImportException(message: String) : Exception(message)

/** 认出来的文件类型。 */
enum class ImportKind { EPUB, HTML, MARKDOWN, TEXT }

/** 按扩展名判断类型。返回 null 表示不支持。 */
fun parseKindOf(fileName: String): ImportKind? =
    when (fileName.substringAfterLast('.', "").lowercase()) {
        "epub" -> ImportKind.EPUB
        "html", "htm", "xhtml" -> ImportKind.HTML
        "md", "markdown" -> ImportKind.MARKDOWN
        "txt", "text" -> ImportKind.TEXT
        else -> null
    }

/**
 * 具名实体表。**与 Web 版同一份、17 项** —— 多一项少一项都会让两端把同一段文本解成
 * 不同的样子，而正文差异会一路传到分页。
 *
 * `nbsp` 解成**普通空格**（不是 U+00A0）：与 Web 一致，而且后续 `htmlToPlainText` 会把
 * 空白折叠掉。
 */
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "mdash" to "—", "ndash" to "–", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’",
    "ldquo" to "“", "rdquo" to "”", "times" to "×", "copy" to "©", "reg" to "®", "deg" to "°",
)

private val ENTITY = Regex("""&(#[xX]?[0-9a-fA-F]+|[a-zA-Z]+);""")

/**
 * 解 HTML 实体：具名、十进制 `&#8217;`、十六进制 `&#x2019;` 三种。
 * 认不出来的**原样保留** —— 宁可留下 `&foo;` 也不要吃掉内容。
 */
fun decodeEntities(text: String): String = ENTITY.replace(text) { match ->
    val body = match.groupValues[1]
    if (body.startsWith("#")) {
        val hex = body.length > 1 && (body[1] == 'x' || body[1] == 'X')
        val code = body.substring(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10)
        // 越界与代理区**必须显式挡掉**：这里不像 Web 那样有 `fromCodePoint` 的异常可依赖，
        // 而 `Character.toChars` 遇到非法码点会抛异常、代理区码点会产出乱码 ——
        // Web 那两种情况的处理都是「原样保留」。
        if (code == null || code <= 0 || code > 0x10FFFF || code in 0xD800..0xDFFF) {
            match.value
        } else {
            String(Character.toChars(code))
        }
    } else {
        NAMED_ENTITIES[body.lowercase()] ?: match.value
    }
}

private val COMMENT = Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL)

private val HIDDEN_ELEMENT = Regex(
    """<(script|style|head)\b[^>]*>.*?</\1\s*>""",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
)

private val BLOCK_TAG = Regex(
    """</?(p|div|br|li|tr|td|h[1-6]|section|article|blockquote|pre|figure|hr)\b[^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val ANY_TAG = Regex("<[^>]*>")

/** 折叠空白。**中间那个字符是不换行空格 U+00A0** —— 见 [htmlToPlainText] 里的说明。 */
private val WHITESPACE_RUN = Regex("[ \t\u00A0]+")

/**
 * HTML → 纯文本。
 *
 * 刻意不用 HTML 解析器：EPUB 里的 XHTML 是机器生成的、结构规整，字符串处理足够，
 * 而引入解析器既不必要、也会让这段逻辑没法在纯 JVM 上测。代价是不处理畸形 HTML。
 *
 * 块级标签换成换行、行内标签**直接去掉** —— 后者很重要：`<b>the</b> <i>cat</i>` 若把
 * 标签换成空格会得到 "the  cat"，段落里的词间距就被标签弄乱了。
 */
fun htmlToPlainText(html: String): String {
    val withoutHidden = html
        .replace(COMMENT, " ")
        .replace(HIDDEN_ELEMENT, " ")

    // 相邻块级元素之间留**空行**（`\n\n`）而不是单换行：分页是按空行切段落的
    // （`Pagination.paginate` 用 `\n\s*\n`），单换行会把两段粘成一段、丢掉段落边界。
    val withBreaks = withoutHidden.replace(BLOCK_TAG, "\n")

    return decodeEntities(withBreaks.replace(ANY_TAG, ""))
        // ⚠️ `WHITESPACE_RUN` 的字符类里含 U+00A0，而它在等宽字体下看不出来。Web 版那一行
        // 就是字面量 NBSP（我核对过字节）；漏掉它，`&nbsp;` 与 EPUB 里原生的 U+00A0 就不会
        // 被折叠，两端的分页随之不同。
        .replace(WHITESPACE_RUN, " ")
        .replace(Regex(" *\n *"), "\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
}

private val MD_HEADING = Regex("""^[ \t]*#{1,6}[ \t]+""", RegexOption.MULTILINE)
private val MD_LINK = Regex("""\[([^\]]*)\]\([^)]*\)""")

/**
 * Markdown → 纯文本。**刻意只做两件事**，理由与 Web 版一致：宁可少处理，也不要让不认识
 * 的语法把正文吃掉。
 *
 * 1. 剥掉行首的 `#`（`## Chapter 2` → `Chapter 2`）。这不只是好看：章节切分器认的是
 *    「Chapter/Letter/Section + 编号」，不剥这一行就认不出来，整本 md 会退化成一个
 *    按词数均分的块。
 * 2. `[label](url)` → `label`。URL 在正文里就是点击噪音，而 label 才是读者要的词。
 *
 * 其余（`**粗体**`、列表符号、代码围栏）一律原样留着：去掉它们有吃掉正文的风险，
 * 而词本身仍然可点可查。
 */
fun markdownToPlainText(markdown: String): String = markdown
    .replace(MD_HEADING, "")
    .replace(MD_LINK) { match -> match.groupValues[1] }

/* --------------------------------- EPUB --------------------------------- */

/**
 * 把 zip 里所有条目读进内存（名 → 字节）。
 *
 * 用 JDK 的 [ZipInputStream] 而不是像 Web 版那样手写中央目录解析：data descriptor
 * （局部头里尺寸写着 0）与中央目录那些坑由 JDK 处理，代码短得多也稳得多。
 *
 * 一次全读进来是为了换取「按名字随便取」—— SAF 给的流不保证可重复读取，重开一次要
 * 重新解析 Uri。代价是大 EPUB 会多占内存，几 MB 级可接受。
 */
private fun readZipEntries(bytes: ByteArray): Map<String, ByteArray> {
    val entries = LinkedHashMap<String, ByteArray>()
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            zip.closeEntry()
        }
    }
    return entries
}

/**
 * `decodeURIComponent` 的等价物。OPF 里的 href 是 URI，中文文件名会被百分号编码。
 *
 * **不能直接用 `URLDecoder`**：它按表单规则把 `+` 解成空格，而路径里的 `+` 是字面量。
 * 先把 `+` 转义掉再解，就等于 `decodeURIComponent`。
 */
private fun decodeUriComponent(value: String): String =
    runCatching { URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8) }.getOrDefault(value)

/** 从一个标签的属性里取值，单双引号都认。 */
private fun attrOf(tag: String, name: String): String? =
    Regex("\\b$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)
        ?: Regex("\\b$name\\s*=\\s*'([^']*)'", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

/** 取第一个匹配标签的某个属性（用于 `container.xml` 里的 `<rootfile full-path>`）。 */
private fun firstAttrOf(xml: String, tagName: String, attrName: String): String? =
    Regex("<(?:\\w+:)?$tagName\\b([^>]*)>", RegexOption.IGNORE_CASE)
        .find(xml)?.groupValues?.get(1)?.let { attrOf(it, attrName) }

private val HEADING_TAG = Regex("""<h[1-6]\b[^>]*>(.*?)</h[1-6]\s*>""", RegexOption.DOT_MATCHES_ALL)
private val TITLE_TAG = Regex("""<title\b[^>]*>(.*?)</title\s*>""", RegexOption.DOT_MATCHES_ALL)

/**
 * 取一章标题。**先看标题标签，再看 `<title>`** —— 顺序不能反：EPUB 的 `<title>` 常被
 * 每一章都写成书名，于是四十章全叫同一个名字；`<h1>` 才是这一章自己的标题。
 */
private fun chapterTitleOf(xhtml: String): String? {
    HEADING_TAG.find(xhtml)?.groupValues?.get(1)?.let { heading ->
        htmlToPlainText(heading).take(80).takeIf { it.isNotEmpty() }?.let { return it }
    }
    TITLE_TAG.find(xhtml)?.groupValues?.get(1)?.let { title ->
        htmlToPlainText(title).take(80).takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

/**
 * EPUB → 章节。
 *
 * 走的是规范里那条链：`META-INF/container.xml` → 它指向的 OPF → OPF 里的 manifest
 * （id → href）与 spine（阅读顺序）。按 spine 成章比按标题切分更忠实 —— 电子书自己就
 * 声明了章节边界，没必要再猜。
 */
fun epubToChapters(bytes: ByteArray): List<BookChapter> {
    val entries = readZipEntries(bytes)
    if (entries.isEmpty()) throw BookImportException("这个文件不是有效的 zip（EPUB 应当是 zip 包）")

    fun readText(name: String): String? = entries[name]?.decodeToString()

    val container = readText("META-INF/container.xml")
        ?: throw BookImportException("不是有效的 EPUB（缺少 META-INF/container.xml）")
    val rootPath = firstAttrOf(container, "rootfile", "full-path")
        ?: throw BookImportException("不是有效的 EPUB（container.xml 里没有 rootfile）")

    val opfPath = decodeUriComponent(rootPath)
    val opf = readText(opfPath) ?: throw BookImportException("EPUB 里找不到 OPF 文件：$opfPath")
    val baseDir = if (opfPath.contains('/')) opfPath.substringBeforeLast('/') + "/" else ""

    // manifest：id → href。带 properties="nav" 的是目录页，不是正文，跳过。
    val manifest = LinkedHashMap<String, String>()
    for (match in Regex("<(?:\\w+:)?item\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(opf)) {
        val tag = match.groupValues[1]
        val id = attrOf(tag, "id")
        val href = attrOf(tag, "href")
        val properties = attrOf(tag, "properties").orEmpty()
        if (id != null && href != null && "nav" !in properties.split(Regex("\\s+"))) {
            manifest[id] = href
        }
    }

    val ordered = ArrayList<String>()
    for (match in Regex("<(?:\\w+:)?itemref\\b([^>]*)>", RegexOption.IGNORE_CASE).findAll(opf)) {
        val tag = match.groupValues[1]
        // linear="no" 是封面、版权页这类不参与阅读顺序的内容（EPUB 2 的写法）。
        if (Regex("\\blinear\\s*=\\s*\"(no|false)\"", RegexOption.IGNORE_CASE).containsMatchIn(tag)) continue
        manifest[attrOf(tag, "idref").orEmpty()]?.let { ordered += it }
    }
    if (ordered.isEmpty()) throw BookImportException("不是有效的 EPUB（spine 里没有任何正文）")

    val chapters = ArrayList<BookChapter>()
    for (href in ordered) {
        // OPF 里的 href 相对 OPF 所在目录，且可能被百分号编码。
        val path = decodeUriComponent(baseDir + href).removePrefix("./")
        val xhtml = readText(path) ?: continue
        val content = htmlToPlainText(xhtml)
        // 纯图片页会抽成空，跳过而不是留一个空章节。
        if (content.isEmpty()) continue
        chapters += BookChapter(chapterTitleOf(xhtml) ?: "第 ${chapters.size + 1} 章", content)
    }

    if (chapters.isEmpty()) {
        throw BookImportException("这本 EPUB 里抽不出文字 —— 很可能是图片版（扫描件），或它只含封面与目录。")
    }
    // 自己构章就必须自己走上限：那个上限是防 Room 读行时撞 CursorWindow，
    // 而它只在 GutenbergTextProcessor 的返回路径上施加，这里绕过了那些路径。
    return GutenbergTextProcessor.enforceSizeLimit(chapters)
}

/* ------------------------------ 面向调用方的入口 ----------------------------- */

/** 做语言检测用的样本：第一个像样的章节。与 Web 一致（它取正文超过 400 字符的第一个）。 */
private fun languageProbe(chapters: List<BookChapter>): String =
    (chapters.firstOrNull { it.content.length > 400 } ?: chapters.firstOrNull())?.content.orEmpty()

/**
 * TXT / Markdown / HTML → 章节：一律喂给 Gutenberg 那条切分器（见它的注释，它与书源无关）。
 *
 * 先过一遍 [normalizeContent] 再去切：Kotlin 的 `\s` 不含 NBSP / U+FEFF 等而 JS 的含，
 * 而 `&nbsp;` 与 EPUB 里的原生 U+00A0 都会走到这里 —— 不过这一层，同一本书在两端会因为
 * 词数统计不同而切出不同的章节。`TextNormalize.kt` 的注释就是为这类分歧写的。
 */
fun textToChapters(rawText: String, fallbackTitle: String): List<BookChapter> =
    GutenbergTextProcessor.process(TextNormalize.normalizeContent(rawText), fallbackTitle)

/**
 * 文件名 + 字节 → [Book]。`:app` 只需要调这一个入口（对应 Web 的 `readBookFile`）。
 *
 * `clock` 注入是为了让 id 可测 —— 与粘贴路径同一个惯例（`custom-${clock.nowMillis()}`）。
 * `source` 用既有的 [BookSource.CUSTOM] 而**不新增枚举值**：Room 里按字符串存，
 * 旧版本遇到不认识的 key 会落到 null，属于前向不兼容。
 */
fun importBook(fileName: String, bytes: ByteArray, clock: Clock): Book {
    val title = fileName.substringBeforeLast('.', fileName).trim().ifEmpty { "导入的书" }

    val chapters = when (val kind = parseKindOf(fileName)) {
        ImportKind.EPUB -> epubToChapters(bytes)
        ImportKind.HTML -> textToChapters(htmlToPlainText(bytes.decodeToString()), title)
        ImportKind.MARKDOWN -> textToChapters(markdownToPlainText(bytes.decodeToString()), title)
        ImportKind.TEXT -> textToChapters(bytes.decodeToString(), title)
        null -> throw BookImportException("不支持的文件类型：$fileName")
    }

    return Book(
        id = "custom-${clock.nowMillis()}",
        title = title,
        author = "User Imported",
        chapters = chapters,
        source = BookSource.CUSTOM,
        language = LanguageDetect.detect(languageProbe(chapters)).language,
    )
}
