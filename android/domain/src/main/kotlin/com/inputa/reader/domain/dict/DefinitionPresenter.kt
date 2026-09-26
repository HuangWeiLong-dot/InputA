package com.inputa.reader.domain.dict

import com.inputa.reader.domain.model.DictionaryEntry
import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.vocab.WordLevels

/**
 * 释义面板要显示的一切，已经从词条算好了。
 *
 * 这批逻辑原本长在 Web 版的 `DefinitionDrawer.tsx` 里（453 行组件），而那边的测试
 * 跑在无 DOM 的环境、没有任何组件渲染覆盖，所以**它从未被测过**。搬到领域层之后
 * 就有了单测 —— 这是移植带来的净增覆盖，也是这个文件存在的理由。
 */
data class DefinitionPresentation(
    val word: String,
    /** 点击的那个词的原始形式，用于判断「词库词条」与它是否不同。 */
    val phonetic: String?,
    /** 词库里的词条形式，**只在它和点击形式不同时**才有值（如 `a couchpotato` → `a couch potato`）。 */
    val headword: String?,
    /** 原形（如 running → run）。 */
    val lemma: String?,
    /** 本词条是原形的哪种变形（如「现在分词」）。 */
    val inflection: String?,
    val translationLines: List<String>,
    val examTags: List<String>,
    /** 「柯林斯 ★×3」「牛津核心词」「BNC #3269」「COCA #3252」。 */
    val metaBits: List<String>,
    /** 「副词 92% · 形容词 8%」。 */
    val posLine: String?,
    /** 「过去式 ran / 复数 runnings」。 */
    val formLine: String?,
    val meanings: List<MeaningPresentation>,
    /** 状态标签：级别名 / 「已掌握」/ 「未标记」。 */
    val statusLabel: String,
    val status: WordStatus?,
    /** 「本地词库」/「Wiktionary」…，面板状态栏的来源标注。 */
    val sourceLabel: String,
) {
    /** 有没有中文释义（离线词库才有）——「英文释义」的分隔标题据此决定要不要显示。 */
    val hasTranslation: Boolean get() = translationLines.isNotEmpty()
}

data class MeaningPresentation(
    /** 徽章文字；**为 null 表示这组不该显示徽章**（来源没给词性）。 */
    val partOfSpeechBadge: String?,
    val definitions: List<DefinitionItemPresentation>,
)

data class DefinitionItemPresentation(
    val definition: String,
    val example: String?,
)

/**
 * 把词条渲染成面板要用的形状。纯函数。
 */
object DefinitionPresenter {

    /** 面板里每组分几个释义 —— 再多就把面板撑爆了，读者要看全部可以去查词典。 */
    const val MAX_DEFINITIONS_PER_MEANING = 4

    /** 词形变化最多显示几组。 */
    const val MAX_FORM_GROUPS = 4

    /**
     * @param clickedWord 读者点开的那个词的原始形式。
     *
     * `headword` 只在**它和词库里的词条不是同一个字符串**时才有值。典型情形是 ECDICT
     * 的 `sw` 兜底命中：查 `a couchpotato` 得到 `a couch potato`。显示这一行是要告诉
     * 读者「你查的那个写法，词库里是这个」；两者相同时显示它只是一行重复的标题。
     */
    fun present(
        entry: DictionaryEntry,
        source: DictionarySource,
        status: WordStatus?,
        clickedWord: String,
    ): DefinitionPresentation {
        val extra = entry.extra

        return DefinitionPresentation(
            word = entry.word,
            phonetic = entry.phonetic,
            headword = entry.word.takeIf { !it.equals(clickedWord, ignoreCase = true) },
            lemma = extra?.lemma,
            inflection = extra?.inflection,
            translationLines = extra?.translation
                ?.split('\n')
                ?.map(String::trim)
                ?.filter(String::isNotEmpty)
                .orEmpty(),
            examTags = extra?.examTags.orEmpty(),
            metaBits = metaBits(extra?.frequency),
            // percent 为 0 的档位不显示 —— 那是 ECDICT 的解析残留，不是真的占比。
            posLine = extra?.partsOfSpeech
                ?.filter { it.percent > 0 }
                ?.joinToString(" · ") { "${it.label} ${it.percent}%" }
                ?.takeIf { it.isNotEmpty() },
            // 有原形时变形信息已经在「原形」那一行里了，再列一遍是重复。
            formLine = if (extra?.lemma != null) {
                null
            } else {
                extra?.forms
                    ?.take(MAX_FORM_GROUPS)
                    ?.joinToString(" / ") { form -> "${form.label} ${form.words.joinToString(" ")}" }
                    ?.takeIf { it.isNotEmpty() }
            },
            meanings = entry.meanings.map { meaning ->
                MeaningPresentation(
                    partOfSpeechBadge = meaning.partOfSpeech.takeIf { it != UNKNOWN_PART_OF_SPEECH },
                    definitions = meaning.definitions
                        .take(MAX_DEFINITIONS_PER_MEANING)
                        .map { DefinitionItemPresentation(it.definition, it.example) },
                )
            },
            statusLabel = statusLabel(status),
            status = status,
            sourceLabel = source.label,
        )
    }

    /**
     * 状态标签。**未收录（null）显示「未标记」而不是「生词」** ——
     * 虽然正文里它被涂成生词色，但词库里确实没有记录，用词要说准。
     */
    fun statusLabel(status: WordStatus?): String = when (status) {
        null -> "未标记"
        WordStatus.MASTERED -> "已掌握"
        else -> WordLevels.label(status) ?: "未标记"
    }

    /** 词频元信息。顺序固定：柯林斯 → 牛津 → BNC → COCA。 */
    private fun metaBits(frequency: com.inputa.reader.domain.model.WordFrequency?): List<String> {
        if (frequency == null) return emptyList()
        val bits = mutableListOf<String>()
        frequency.collins?.let { bits += "柯林斯 ${"★".repeat(it.coerceIn(1, 5))}" }
        if (frequency.oxford == true) bits += "牛津核心词"
        frequency.bnc?.let { bits += "BNC #$it" }
        frequency.frq?.let { bits += "COCA #$it" }
        return bits
    }
}
