package com.inputa.reader.domain.text

/**
 * 一个词元。[raw] 保留原文（含大小写与标点），[cleanWord] 是查词库/查词典用的键。
 *
 * `id` 形如 `p3-t17`，按遇到顺序编号且**词元与非词元共用同一个计数器**，所以
 * 把一段文本的所有 `raw` 按顺序拼回去能精确还原原文。Compose 侧靠它做稳定 key。
 */
data class Token(
    val id: String,
    val raw: String,
    val isWord: Boolean,
    val cleanWord: String,
)

/**
 * 分词。逐字移植自 Web 版的 src/utils/tokenizer.ts —— 这是整个移植里最需要
 * 对照源码 review 的文件，因为它的输出直接决定翻页时写入词库的那些键。
 */
object Tokenizer {

    /**
     * 用 Unicode 字母/数字类，而不是 `[a-zA-Z0-9]`：后者会把 café 切成 `caf` + `é`、
     * 把 über 切成 `ber`，于是非英文书里几乎没有能整词点击的词，点击还会把碎片
     * 当成生词收录。
     *
     * 内部允许撇号（don't、it's）与连字符（well-known），保持整词。
     * Kotlin 的 Regex 默认就是全局语义（`findAll`），不需要 JS 的 `g` 标志；
     * `\p{...}` 在 JVM 上也不需要 `u` 标志。
     */
    private val WORD_REGEX =
        Regex("([\\p{L}\\p{N}]+(?:['’][\\p{L}\\p{N}]+)*(?:-[\\p{L}\\p{N}]+)*)")

    /**
     * 中日韩等无空格文字。
     *
     * 区间来自 Web 版的字面量 `[ᄀ-ᇿ぀-ヿ㄰-㆏一-鿿가-힯]`，这里写成码点转义以免
     * 依赖源文件编码：
     *   U+1100–U+11FF 谚文字母   U+3040–U+30FF 平假名+片假名   U+3130–U+318F 谚文兼容字母
     *   U+4E00–U+9FFF 中日韩统一表意文字                        U+AC00–U+D7AF 谚文音节
     *
     * 为什么整类排除：它们没有「点一下这个词」的天然边界，真要分词得引入
     * kuromoji / jieba 这类专门的库；更糟的是翻页自动收录规则会把整段中文当成
     * 一个「词」塞进词库。所以这里不把它们算作可点击的词 —— 与 LingKuma 的
     * shouldHighlightText 是同一个取舍。
     *
     * **只留这一份定义**：分词器、仿生阅读、语言检测三处都引用它，改了这里三处一起变。
     */
    private val CJK_PATTERN =
        Regex("[\\u1100-\\u11FF\\u3040-\\u30FF\\u3130-\\u318F\\u4E00-\\u9FFF\\uAC00-\\uD7AF]")

    private val HAS_LETTER = Regex("\\p{L}")

    /** 这段文字里有没有中日韩文字。 */
    fun containsCjk(text: String): Boolean = CJK_PATTERN.containsMatchIn(text)

    /**
     * 把一段文本切成词元与非词元。
     *
     * @param paragraphIndex 只影响生成的 id，用于在 Compose 里给词元一个稳定 key。
     */
    fun tokenize(text: String, paragraphIndex: Int = 0): List<Token> {
        if (text.isEmpty()) return emptyList()

        val tokens = ArrayList<Token>()
        var lastIndex = 0
        var counter = 0

        for (match in WORD_REGEX.findAll(text)) {
            val matchIndex = match.range.first
            val word = match.value

            // 这个词之前若有非词文本（空白、标点），先单独成一个词元。
            if (matchIndex > lastIndex) {
                tokens += Token(
                    id = "p$paragraphIndex-t${counter++}",
                    raw = text.substring(lastIndex, matchIndex),
                    isWord = false,
                    cleanWord = "",
                )
            }

            val clean = TextNormalize.cleanToken(word)
            tokens += Token(
                id = "p$paragraphIndex-t${counter++}",
                raw = word,
                // 必须含字母（纯数字不算词），且不能是中日韩 —— 见 CJK_PATTERN。
                isWord = clean.isNotEmpty() &&
                    HAS_LETTER.containsMatchIn(clean) &&
                    !CJK_PATTERN.containsMatchIn(clean),
                cleanWord = clean,
            )

            lastIndex = matchIndex + word.length
        }

        // 结尾剩下的非词文本。
        if (lastIndex < text.length) {
            tokens += Token(
                id = "p$paragraphIndex-t${counter++}",
                raw = text.substring(lastIndex),
                isWord = false,
                cleanWord = "",
            )
        }

        return tokens
    }

    /** 取出所有不重复的 cleanWord（保持首次出现的顺序）。 */
    fun extractWords(tokens: List<Token>): List<String> {
        val seen = LinkedHashSet<String>()
        for (token in tokens) {
            if (token.isWord && token.cleanWord.isNotEmpty()) seen += token.cleanWord
        }
        return seen.toList()
    }
}

/** 默认的每页词数。阅读器、分页、设置项共用这一个默认值。 */
const val WORDS_PER_PAGE: Int = 220
