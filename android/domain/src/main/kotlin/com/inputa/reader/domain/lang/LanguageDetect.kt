package com.inputa.reader.domain.lang

import com.inputa.reader.domain.text.TextNormalize
import com.inputa.reader.domain.text.Tokenizer

/**
 * 轻量语言检测。用途只有三个：显示语言标签、挑 TTS 音色、选字体。
 *
 * 刻意不引入 LingKuma 用的那个 982KB 的 ELD 模型：为了一个标签付近 1MB 不值。
 *
 * 两部分：
 *   1. 中日韩等无空格文字按字符区间判别。区间与 tokenizer 的 CJK_PATTERN 同源，
 *      与分词、仿生阅读保持同一套定义。
 *   2. 拉丁语系用高频停用词打分。样本足够时这种方法相当稳（一两个常见词就能定）。
 *
 * 判不出来时返回 `en`：这是本应用默认的阅读语言，而且调用方拿到的永远是
 * 一个可用的语言标签，不需要再处理 null。
 */
object LanguageDetect {

    /** 各语言的判别用停用词。取的是高频且区分度大的虚词。 */
    private val STOPWORDS: Map<String, List<String>> = mapOf(
        "en" to listOf("the", "and", "of", "to", "in", "is", "that", "it", "was", "for", "with", "as", "his", "her", "not", "but", "you", "this", "have", "from"),
        "de" to listOf("der", "die", "das", "und", "ich", "nicht", "ist", "mit", "sich", "auf", "ein", "eine", "auch", "als", "aber", "wir", "sie", "dem", "den", "zu"),
        "fr" to listOf("le", "la", "les", "des", "et", "est", "que", "qui", "pour", "dans", "pas", "vous", "nous", "sur", "une", "avec", "plus", "mais", "son", "au"),
        "es" to listOf("el", "la", "los", "las", "de", "que", "y", "en", "un", "una", "por", "con", "para", "no", "se", "su", "como", "más", "pero", "sus"),
        "it" to listOf("il", "lo", "la", "gli", "le", "di", "che", "e", "per", "con", "una", "non", "sono", "come", "più", "ma", "suo", "sua", "nel", "del"),
        "pt" to listOf("o", "a", "os", "as", "de", "que", "e", "em", "um", "uma", "para", "com", "não", "se", "por", "mais", "como", "mas", "seu", "sua"),
        "nl" to listOf("de", "het", "een", "en", "van", "ik", "niet", "is", "dat", "op", "aan", "met", "zijn", "voor", "maar", "als", "ook", "die", "er", "was"),
    )

    // 判别用的字符区间。与 tokenizer 的 CJK_PATTERN 同源，但分得更细：
    // 只判「是不是中日韩」不足以选音色，还要把它们彼此分开。
    // 都写成码点转义以免依赖源文件编码。
    private val KANA = Regex("[\\u3040-\\u309F\\u30A0-\\u30FF]")
    private val HANGUL = Regex("[\\u1100-\\u11FF\\u3130-\\u318F\\uAC00-\\uD7AF]")
    private val HAN = Regex("[\\u4E00-\\u9FFF]")
    private val CYRILLIC = Regex("[\\u0400-\\u04FF]")
    private val GREEK = Regex("[\\u0370-\\u03FF]")
    private val ARABIC = Regex("[\\u0600-\\u06FF]")
    private val HEBREW = Regex("[\\u0590-\\u05FF]")
    private val THAI = Regex("[\\u0E00-\\u0E7F]")

    private val NON_LETTER = Regex("[^\\p{L}]+")

    /**
     * 日文优先于中文：日文正文里必然混有假名，而中文正文几乎不会有。
     * 所以先看假名，再退回汉字。
     */
    private fun detectNonSpaced(text: String): LanguageGuess? = when {
        KANA.containsMatchIn(text) -> LanguageGuess("ja", 0.95)
        HANGUL.containsMatchIn(text) -> LanguageGuess("ko", 0.95)
        HAN.containsMatchIn(text) -> LanguageGuess("zh", 0.9)
        else -> null
    }

    private fun detectByScript(text: String): LanguageGuess? = when {
        CYRILLIC.containsMatchIn(text) -> LanguageGuess("ru", 0.85)
        GREEK.containsMatchIn(text) -> LanguageGuess("el", 0.85)
        ARABIC.containsMatchIn(text) -> LanguageGuess("ar", 0.85)
        HEBREW.containsMatchIn(text) -> LanguageGuess("he", 0.85)
        THAI.containsMatchIn(text) -> LanguageGuess("th", 0.85)
        else -> null
    }

    /** 只留字母，其余当分隔符 —— 标点与数字对停用词命中没有帮助。 */
    private fun tokenizeForScoring(text: String): List<String> =
        text.lowercase().split(NON_LETTER).filter { it.isNotEmpty() }

    private fun detectSpaced(text: String): LanguageGuess {
        val tokens = tokenizeForScoring(text)
        if (tokens.isEmpty()) return LanguageGuess("en", 0.0)

        val counts = LinkedHashMap<String, Int>()
        for ((language, words) in STOPWORDS) {
            var hits = 0
            for (word in words) {
                // 逐个计数而不是用集合：一篇长文里 "the" 出现几十次，是有力的证据。
                for (token in tokens) {
                    if (token == word) hits++
                }
            }
            if (hits > 0) counts[language] = hits
        }

        if (counts.isEmpty()) return LanguageGuess("en", 0.0)

        var best = "en"
        var bestScore = 0
        var secondScore = 0
        for ((language, score) in counts) {
            if (score > bestScore) {
                secondScore = bestScore
                best = language
                bestScore = score
            } else if (score > secondScore) {
                secondScore = score
            }
        }

        // 命中数越多越可信；与第二名的差距越大越可信。两者取小。
        val volume = minOf(1.0, bestScore / 8.0)
        val margin = if (bestScore > 0) (bestScore - secondScore).toDouble() / bestScore else 0.0
        return LanguageGuess(best, minOf(volume, margin))
    }

    /**
     * 猜这段文字的语言。空白或判不出来时给 `en`、置信度 0 ——
     * 调用方永远拿到一个可用的标签，不需要再判 null。
     */
    fun detect(text: String): LanguageGuess {
        val sample = TextNormalize.trim(text)
        if (sample.isEmpty()) return LanguageGuess("en", 0.0)

        detectNonSpaced(sample)?.let { return it }

        // 中日韩之外的其它非拉丁文字：按字符区间就够，不用看停用词。
        if (Tokenizer.containsCjk(sample)) return LanguageGuess("zh", 0.5)

        detectByScript(sample)?.let { return it }

        return detectSpaced(sample)
    }
}

data class LanguageGuess(
    /** ISO 639-1（或带地区的标签）。 */
    val language: String,
    /** 检测依据的强度，0-1；低置信度时可以据此决定不显示标签。 */
    val confidence: Double,
)
