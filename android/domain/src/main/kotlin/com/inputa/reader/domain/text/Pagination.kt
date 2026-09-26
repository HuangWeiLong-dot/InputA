package com.inputa.reader.domain.text

/**
 * 把整章正文切成适合阅读的块（约 220-260 词一页）。
 *
 * 移植自 Web 版的 `paginateText`，**逐分支对齐**。这一点比看起来重要：既然
 * 「翻页时把本页没点过的词标为掌握」是核心规则，那么分页边界就是我写数据的边界。
 * 两端的页切得不一样，同一本书在两个平台上就会往词库里写进不同的词。
 *
 * 之所以给 `targetWordsPerPage` 开放参数：手机一屏显示的词远少于桌面 46rem 栏宽，
 * 220 词在手上大约占 2.5 屏。默认值保持 220 是为了与 Web 版一致（见设置项 wordsPerPage）。
 */
object Pagination {

    /** 段落之间：空行（可含空白）。对应 JS `/\n\s*\n/`。 */
    private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

    /** 切句：非终止符 + 终止符 + 后随空白或行尾，或者结尾剩下的无终止符片段。 */
    private val SENTENCE_BREAK = Regex("[^.!?]+[.!?]+(\\s+|$)|[^.!?]+$")

    private val WHITESPACE_RUN = Regex("\\s+")

    /**
     * 数一段有多少「词」。JS 是 `para.trim().split(/\s+/).length`。
     *
     * 注意 Kotlin 的 `split` 会丢掉结尾的空串而 JS 不会 —— 但两边都先 `trim()` 了，
     * 所以这个差异够不到。**别删那个 trim。** 另外空白集合的差异由
     * [TextNormalize.normalizeContent] 在入口处抹平，见那个文件的说明。
     */
    private fun countWords(paragraph: String): Int =
        paragraph.trim().split(WHITESPACE_RUN).size

    /**
     * @return 永远非空；空输入返回 `[""]`。调用方（翻页器）依赖这一点 —— 页数不会是 0。
     */
    fun paginate(chapterContent: String, targetWordsPerPage: Int = WORDS_PER_PAGE): List<String> {
        // 先归一化再判空：JS 的 trim 认 NBSP，Kotlin 的不认，所以在归一化之前判空
        // 会让「仅由一个 NBSP 组成的一章」在两端走上不同分支。
        val normalized = TextNormalize.normalizeContent(chapterContent)
        if (normalized.trim().isEmpty()) return listOf("")

        val paragraphs = normalized.split(PARAGRAPH_BREAK).filter { it.trim().isNotEmpty() }
        if (paragraphs.isEmpty()) return listOf(normalized)

        val pages = ArrayList<String>()
        var currentParagraphs = ArrayList<String>()
        var currentWordCount = 0

        for (paragraph in paragraphs) {
            val paragraphWords = countWords(paragraph)

            // 单段就超过目标 1.5 倍：先把攒着的页收掉，然后按句子切这一段 ——
            // 否则一个超长段落会独占一整页，读起来断在莫名其妙的地方。
            if (paragraphWords > targetWordsPerPage * 1.5) {
                if (currentParagraphs.isNotEmpty()) {
                    pages += currentParagraphs.joinToString("\n\n")
                    currentParagraphs = ArrayList()
                    currentWordCount = 0
                }
                pages += splitLongParagraph(paragraph, targetWordsPerPage)
                continue
            }

            if (currentWordCount + paragraphWords > targetWordsPerPage && currentParagraphs.isNotEmpty()) {
                pages += currentParagraphs.joinToString("\n\n")
                currentParagraphs = arrayListOf(paragraph)
                currentWordCount = paragraphWords
            } else {
                currentParagraphs += paragraph
                currentWordCount += paragraphWords
            }
        }

        if (currentParagraphs.isNotEmpty()) {
            pages += currentParagraphs.joinToString("\n\n")
        }

        return if (pages.isNotEmpty()) pages else listOf("")
    }

    /** 把一个超长段落按句子聚成若干页。句子本身仍长于目标时也会单独成页（不硬切句子）。 */
    private fun splitLongParagraph(paragraph: String, targetWordsPerPage: Int): List<String> {
        val matches = SENTENCE_BREAK.findAll(paragraph).map { it.value }.toList()
        val sentences = matches.ifEmpty { listOf(paragraph) }

        val result = ArrayList<String>()
        var chunk = ArrayList<String>()
        var chunkWords = 0

        for (sentence in sentences) {
            val sentenceWords = countWords(sentence)
            if (chunkWords + sentenceWords > targetWordsPerPage && chunk.isNotEmpty()) {
                result += chunk.joinToString(" ").trim()
                chunk = arrayListOf(sentence)
                chunkWords = sentenceWords
            } else {
                chunk += sentence
                chunkWords += sentenceWords
            }
        }
        if (chunk.isNotEmpty()) {
            result += chunk.joinToString(" ").trim()
        }
        return result
    }
}
