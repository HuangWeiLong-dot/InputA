package com.inputa.reader.domain.text

/**
 * 文本规范化 —— 这个文件存在的唯一理由是**空白语义在 JS 与 JVM 上不一致**。
 *
 * JavaScript 的 `\s` / `String.prototype.trim` 把下面这些都算空白：
 *   U+00A0 NBSP、U+1680、U+2000–U+200A、U+2028 行分隔符、U+2029 段分隔符、
 *   U+202F、U+205F、U+3000 全角空格、U+FEFF BOM
 * 而 Java/Kotlin 的 `\s` 只有 `[ \t\n\x0B\f\r]`，`Char.isWhitespace(U+00A0)` 甚至是 false。
 *
 * 为什么这不是小事：分页按「词数」切块（[Pagination.paginate]），而词数是
 * `split(/\s+/)` 数出来的。空白集合不同 → 词数不同 → **分页边界不同 → 翻页时被
 * 自动标为「掌握」的词不同**。也就是说，一个 NBSP 就能让 Android 端和 Web 端对同一本书
 * 得出不同的页、进而写下不同的词库。全项目后果最隐蔽的一处分歧。
 *
 * 解法：正文入库前统一跑一遍 [normalizeContent]，把上述字符统统换成普通空格。
 * 之后 Kotlin 的 `\s` / `trim()` 与 JS 的行为就一致了。
 */
object TextNormalize {

    /** JS 认作空白、而 Java 不认的那些字符。 */
    private val JS_ONLY_WHITESPACE =
        Regex("[\\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF]")

    /** 换行统一成 `\n`，再把 JS 的空白集合收敛成普通空格。 */
    fun normalizeContent(text: String): String =
        JS_ONLY_WHITESPACE.replace(text.replace("\r\n", "\n").replace('\r', '\n'), " ")

    /**
     * 等价于 JS 的 `String.prototype.trim()`。
     *
     * 先归一化再 `trim()`：Js-only 的空白变成普通空格后就能被 Kotlin 的 trim 去掉了。
     * 用在**外部输入**上（导入的备份、笔记正文、用户粘贴的文本），
     * 不要用在已经很热的循环里 —— 它对每条字符串都会分配一次。
     */
    fun trim(text: String): String = JS_ONLY_WHITESPACE.replace(text, " ").trim()

    /** 词元首尾要去掉的字符：撇号与连字符。对应 Web 版的 `/^['’-]+|['’-]+$/g`。 */
    private val EDGE_PUNCTUATION = Regex("^['\\u2019-]+|['\\u2019-]+$")

    /**
     * 规范化一个词元：去掉首尾的撇号与连字符，然后小写。
     *
     * 词库的状态键、字典查询、以及单词爆炸面板挑出来的词都用它，所以这个词元清洗
     * 只有一份实现 —— 两处不一致会让词查不到自己的状态。
     */
    fun cleanToken(raw: String): String = EDGE_PUNCTUATION.replace(raw, "").lowercase()

    /** 去掉首尾撇号/连字符但**保留原大小写**，用于显示与朗读（Web 版 wordExtraction 的 `raw`）。 */
    fun stripEdgePunctuation(raw: String): String = EDGE_PUNCTUATION.replace(raw, "")

    /** 有没有字母。用 `\p{L}` 而不是 `Char.isLetter()` —— 后者按 UTF-16 码元判断，星平面字母会漏。 */
    private val HAS_LETTER = Regex("\\p{L}")

    fun containsLetter(text: String): Boolean = HAS_LETTER.containsMatchIn(text)
}
