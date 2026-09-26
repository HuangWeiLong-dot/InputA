package com.inputa.reader.domain.model

/**
 * 词典查询结果。字段与 Web 版的 src/types/reader.ts 一致。
 *
 * 四个来源的产物统一成这一个形状（顺序即降级顺序，见 DictionaryRepository）：
 *   local-ecdict（后端 /api/dict，ECDICT，最丰富）→ dictionaryapi.dev → wiktionary → datamuse
 */

/** 词典来源。`UNKNOWN_PART_OF_SPEECH` 之类的哨兵另见 DictionaryParsers。 */
enum class DictionarySource(val key: String, val label: String) {
    LOCAL_ECDICT("local-ecdict", "本地词库"),
    DICTIONARY_API("dictionaryapi.dev", "dictionaryapi.dev"),
    WIKTIONARY("wiktionary", "Wiktionary"),
    DATAMUSE("datamuse", "Datamuse");

    companion object {
        fun fromKey(raw: String?): DictionarySource? = entries.firstOrNull { it.key == raw }
    }
}

data class Phonetic(
    val text: String? = null,
    val audio: String? = null,
)

data class DefinitionItem(
    val definition: String,
    val example: String? = null,
    /** 声明了但四个解析器都没填过 —— 保留以对齐 Web 版的类型。 */
    val synonyms: List<String>? = null,
)

data class Meaning(
    val partOfSpeech: String,
    val definitions: List<DefinitionItem> = emptyList(),
)

/**
 * 只有本地词库（GET /api/dict，ECDICT）才会带上的补充信息：中文释义、考试标签、
 * 词性分布、词形变化与词频。在线来源没有这些字段，因此整体可选。
 */
data class DictionaryExtra(
    /** 中文释义原文，多行（每行一段，含词性前缀）。 */
    val translation: String? = null,
    /** 考试标签，如 ['中考', '高考', '四级']。 */
    val examTags: List<String> = emptyList(),
    /** 词性分布（中文标签 + 英文缩写 + 占比），按占比降序。 */
    val partsOfSpeech: List<PartOfSpeechShare> = emptyList(),
    /** 原形：本词是某个变形时指向它的原形，如 running → run。 */
    val lemma: String? = null,
    /** 本词条是原形的哪种变形（中文），如「现在分词」。 */
    val inflection: String? = null,
    /** 其他词形变化，如 [WordForm("过去式", listOf("ran"))]。 */
    val forms: List<WordForm> = emptyList(),
    /** 柯林斯星级 1-5、牛津核心词标记、BNC 与 COCA 词频排名（越小越常用）。 */
    val frequency: WordFrequency? = null,
)

data class PartOfSpeechShare(
    val label: String,
    val abbr: String,
    val percent: Int,
)

data class WordForm(
    val label: String,
    val words: List<String>,
)

data class WordFrequency(
    val collins: Int? = null,
    val oxford: Boolean? = null,
    val bnc: Int? = null,
    /** COCA / 当代语料库排名。ECDICT 里叫 frq。 */
    val frq: Int? = null,
)

data class DictionaryEntry(
    val word: String,
    val phonetic: String? = null,
    val phonetics: List<Phonetic> = emptyList(),
    val meanings: List<Meaning> = emptyList(),
    val audioUrl: String? = null,
    /** 离线词库附带的补充信息，见 DictionaryExtra。 */
    val extra: DictionaryExtra? = null,
)

/**
 * 一次查词的结果。**这是一个三态，不是一个可空的词条** —— Web 版的
 * `{entry, source, unavailable}` 三元组里，`entry == null` 同时表示
 * 「查了但没收录」和「服务不可用」，靠 `unavailable` 区分。
 *
 * 这里用密封类型让第三种情况无法被忽略：调用点必须显式处理，而
 * `Found(entry, Unavailable)` 这种矛盾组合根本构造不出来。
 *
 * 之所以重要：404（词典里没有这个词）是**确定结论**，会被缓存；
 * 传输失败是**临时故障**，绝不能缓存，否则「重新查询」永远打不通。
 */
sealed interface LookupResult {
    data class Found(
        val entry: DictionaryEntry,
        val source: DictionarySource,
    ) : LookupResult

    /** 所有 provider 都答了，但都不认识这个词。UI 显示「词典均未收录该词」。 */
    data object NotFound : LookupResult

    /** 非 optional 的 provider 出现传输失败。UI 显示「词典服务暂时无法访问」+ 重新查询。 */
    data object Unavailable : LookupResult
}
