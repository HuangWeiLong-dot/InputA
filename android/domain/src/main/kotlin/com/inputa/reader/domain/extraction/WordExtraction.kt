package com.inputa.reader.domain.extraction

import com.inputa.reader.domain.text.TextNormalize
import com.inputa.reader.domain.text.Tokenizer

/**
 * 从一句话里挑出「值得查」的词，供单词爆炸（批量查词）面板使用。
 *
 * 移植自 LingKuma 的 `extractUnknownWords`（其 src/service/a7_words_boom.js，MIT），
 * 但**分词改用本仓库自己的 tokenizer，而不是 Intl.Segmenter** —— 这是与 LingKuma
 * 的一处有意分歧，理由是正确性：
 *
 *   Segmenter 会按 Unicode 词边界把 `well-known` 切成 `well` + `known`，
 *   而阅读器收录词时用的是 [Tokenizer.tokenize]，把 `well-known` 当成**一个**词、
 *   以它为键存进词库。两者不一致的直接后果是：面板会把这些词报成「未收录」
 *   （查 `well` 查不到 `well-known` 的状态），而用户点「已掌握」又会往词库里
 *   塞进 `well`、`known` 这些永远不会在正文里出现的键。
 *
 * 面板显示的词必须和阅读器收录的词是同一套单位，所以这里复用同一个分词器。
 * 代价是中日韩等无空格语言切不出来 —— 那本来就超出「只做检测与适配」的范围。
 */
object WordExtraction {

    /**
     * 单个字母算不上生词（冠词 a、代词 I），纯数字与纯符号同理。
     * 与翻页自动收录的规则保持一致（见 VocabularyRepository.applyPageTurn）。
     */
    private fun isNoise(clean: String): Boolean =
        clean.length < 2 || !TextNormalize.containsLetter(clean)

    fun extract(text: String): List<ExtractedWord> {
        if (text.trim().isEmpty()) return emptyList()

        val seen = HashSet<String>()
        val result = ArrayList<ExtractedWord>()

        for (token in Tokenizer.tokenize(text)) {
            if (!token.isWord) continue

            val clean = TextNormalize.cleanToken(token.raw)
            // seen.add 返回 false 表示已出现过 —— 去重与「首次拼写胜出」由它一次完成。
            if (clean.isEmpty() || isNoise(clean) || !seen.add(clean)) continue

            result += ExtractedWord(
                // raw 保留原大小写（给显示与朗读），只去掉首尾的撇号/连字符，
                // 这样 `'twas` 显示成 `twas` 但仍与词库的键对得上。
                raw = TextNormalize.stripEdgePunctuation(token.raw),
                clean = clean,
            )
        }

        return result
    }
}

data class ExtractedWord(
    /** 首次出现的原大小写形式，用于显示与朗读。 */
    val raw: String,
    /** 小写并去首尾标点的形式，用于查词库状态与查词典。 */
    val clean: String,
)
