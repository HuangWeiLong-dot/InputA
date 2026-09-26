package com.inputa.reader.domain.book

import com.inputa.reader.domain.model.BookChapter

/**
 * 把下载到的 Gutenberg 正文切成章节。逐条移植自 Web 版的 `processGutenbergText`，
 * **外加一道 Android 特有的长度上限**（见 [MAX_CHAPTER_CHARS]）。
 */
object GutenbergTextProcessor {

    /**
     * 单章正文的字符上限。
     *
     * **这是 Android 特有的约束，Web 版不可能有。** Room 读取单行时受
     * `CursorWindow` 的 2MB 限制，超出会抛 `Row too big to fit into CursorWindow`。
     * 而下面的切章逻辑有两条路径能产出**整本书作一章**：
     *   - 「只有一个标题且正文 > 3000 字符就信它」这条分支
     *   - 词数不足 1800 时的兜底：整段正文就是一章
     * 一本 12MB 的书走这两条路，章节就装不进一个 SQLite 行。
     *
     * 20 万字符约合 3.3 万词，离 2MB 还差一个数量级，同时远超任何一章的正常长度 ——
     * 所以正常书永远不会被切，只有病态输入才会。
     *
     * **刻意不改成「正文存文件」**：那会引入两个真相源（数据库与文件系统可能不同步）
     * 外加一个 GC 问题，而这个上限本身完全可以待在下限以内。
     */
    const val MAX_CHAPTER_CHARS = 200_000

    private val BOILERPLATE_START =
        Regex("\\*{3}\\s*START OF (?:THE|THIS) PROJECT GUTENBERG EBOOK[^*]*\\*{3}", RegexOption.IGNORE_CASE)

    private val BOILERPLATE_END =
        Regex("\\*{3}\\s*END OF (?:THE|THIS) PROJECT GUTENBERG EBOOK[^*]*\\*{3}", RegexOption.IGNORE_CASE)

    /**
     * 章节标题，如 "CHAPTER I."、"Chapter 12"、"LETTER 2"、"BOOK III"。
     *
     * `MULTILINE` 对应 JS 正则的 `m` 标志 —— 标题必须**独占一行**，
     * 否则正文里一句以 "Chapter 3 ..." 开头的叙述会被误判成标题。
     */
    private val CHAPTER_HEADING = Regex(
        "^[ \\t]{0,3}(?:CHAPTER|Chapter|LETTER|Letter|STAVE|Stave|BOOK|Book|PART|Part|SECTION|Section)" +
            "[ \\t]+(?:[0-9]{1,3}|[IVXLCDMivxlcdm]{1,7})(?:[^\\n]{0,70})?$",
        RegexOption.MULTILINE,
    )

    private val WHITESPACE_RUN = Regex("\\s+")
    private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

    /** 去掉 Project Gutenberg 的法律声明头尾。 */
    fun stripBoilerplate(rawText: String): String {
        var text = rawText.replace("\r\n", "\n").replace('\r', '\n')

        BOILERPLATE_START.find(text)?.let { match ->
            text = text.substring(match.range.last + 1)
        }
        // 注意顺序：在**已经切掉开头**的文本上再找结尾，与 Web 版一致。
        BOILERPLATE_END.find(text)?.let { match ->
            text = text.substring(0, match.range.first)
        }

        return text.trim()
    }

    /**
     * 把一本书的原文切成章节：有章节标题就按标题切，否则退回按固定大小分节。
     *
     * @return 永远非空 —— 阅读器依赖这一点。
     */
    fun process(rawText: String, defaultTitle: String): List<BookChapter> {
        val cleanText = stripBoilerplate(rawText)
        if (cleanText.isEmpty()) {
            return listOf(BookChapter(fallbackTitle(defaultTitle), ""))
        }

        val headings = ArrayList<Heading>()
        for (match in CHAPTER_HEADING.findAll(cleanText)) {
            val index = match.range.first
            val previous = headings.lastOrNull()
            // 与上一个「标题」贴得太近的是目录，不是标题。
            if (previous != null && index - previous.index < 40) continue
            headings += Heading(index, match.value.trim())
        }

        // 只有一个标题时只在很长的文本里才信它 —— 免得叙述句里的 "Chapter 3 ..."
        // 把一整篇故事劈开。
        val canSplitByHeading = headings.size >= 2 || (headings.size == 1 && cleanText.length > 3000)

        if (canSplitByHeading) {
            val chapters = ArrayList<BookChapter>()

            val frontMatter = cleanText.substring(0, headings.first().index).trim()
            if (frontMatter.length > 600) {
                chapters += BookChapter("前言 / Front Matter", frontMatter)
            }

            headings.forEachIndexed { position, heading ->
                val end = if (position + 1 < headings.size) headings[position + 1].index else cleanText.length
                val content = cleanText.substring(heading.index, end).trim()
                if (content.isEmpty()) return@forEachIndexed

                val previous = chapters.lastOrNull()
                if (previous != null && content.length < 200) {
                    // 太短、单独读不成一章的碎片并回上一章。
                    chapters[chapters.lastIndex] = previous.copy(
                        content = "${previous.content}\n\n$content",
                    )
                    return@forEachIndexed
                }

                chapters += BookChapter(heading.title.take(80), content)
            }

            if (chapters.isNotEmpty()) return enforceSizeLimit(chapters)
        }

        val words = cleanText.split(WHITESPACE_RUN).filter(String::isNotEmpty)
        if (words.size > 1800) {
            val wordsPerSection = 1500
            val chapters = ArrayList<BookChapter>()
            var start = 0
            while (start < words.size) {
                chapters += BookChapter(
                    title = "Section ${chapters.size + 1}",
                    content = words.subList(start, minOf(start + wordsPerSection, words.size))
                        .joinToString(" "),
                )
                start += wordsPerSection
            }
            return chapters
        }

        return enforceSizeLimit(listOf(BookChapter(fallbackTitle(defaultTitle), cleanText)))
    }

    private fun fallbackTitle(defaultTitle: String): String =
        defaultTitle.ifEmpty { "Chapter 1" }

    private data class Heading(val index: Int, val title: String)

    /**
     * 把超长章节切开，见 [MAX_CHAPTER_CHARS]。
     *
     * 在**所有返回路径的末尾**统一施加，而不是只补在某一条分支上 —— 三条路径
     * （按标题切、按词数分节、整篇一章）里只有前两条和最后一条有风险，但统一处理
     * 意味着将来新增分支也自动受保护。
     *
     * `internal` 而不是 `private`：文件导入那条路（[BookImport]）**自己构章**、
     * 不经过 [process]，所以它得能调到这里 —— 否则那个上限就只保护了一半的入库路径，
     * 而这条不变量（「所有路径都受保护」）正是上面那段注释所承诺的。
     * 对 `:app` 仍然不可见。
     */
    internal fun enforceSizeLimit(chapters: List<BookChapter>): List<BookChapter> {
        if (chapters.all { it.content.length <= MAX_CHAPTER_CHARS }) return chapters
        return chapters.flatMap { chapter ->
            if (chapter.content.length <= MAX_CHAPTER_CHARS) listOf(chapter) else splitOversized(chapter)
        }
    }

    private fun splitOversized(chapter: BookChapter): List<BookChapter> {
        val parts = ArrayList<String>()
        val current = StringBuilder()

        for (paragraph in chapter.content.split(PARAGRAPH_BREAK)) {
            // 段落本身超限（整本书没有空行的情形）时先把它切碎，
            // 否则它永远塞不进任何一块，循环就会产出单块超限的结果。
            for (piece in chunkParagraph(paragraph)) {
                if (current.isNotEmpty() && current.length + piece.length + 2 > MAX_CHAPTER_CHARS) {
                    parts += current.toString()
                    current.clear()
                }
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        if (parts.isEmpty()) parts += chapter.content

        // 第一块保留原标题，后续块加序号 —— 序号是语言中立的，书名本身不该被翻译。
        return parts.mapIndexed { index, content ->
            BookChapter(
                title = if (index == 0) chapter.title else "${chapter.title} (${index + 1})",
                content = content,
            )
        }
    }

    /** 把一个超长段落切成不超过上限的片段，尽量切在空白处以免把词劈成两半。 */
    private fun chunkParagraph(paragraph: String): List<String> {
        if (paragraph.length <= MAX_CHAPTER_CHARS) return listOf(paragraph)

        val pieces = ArrayList<String>()
        var start = 0
        while (start < paragraph.length) {
            val hardEnd = minOf(start + MAX_CHAPTER_CHARS, paragraph.length)
            var end = hardEnd
            if (hardEnd < paragraph.length) {
                val boundary = paragraph.lastIndexOf(' ', hardEnd)
                // `boundary > start` 的保证很关键：否则 end 可能等于 start，
                // start 就不再前进，循环永远不结束。
                if (boundary > start) end = boundary
            }
            pieces += paragraph.substring(start, end)
            start = end
            while (start < paragraph.length && paragraph[start] == ' ') start++
        }
        return pieces
    }
}
