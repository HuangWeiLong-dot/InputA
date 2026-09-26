package com.inputa.reader.domain.dict

import com.inputa.reader.domain.json.asArrayOrNull
import com.inputa.reader.domain.json.asObjectOrNull
import com.inputa.reader.domain.json.booleanOrNull
import com.inputa.reader.domain.json.intOrNull
import com.inputa.reader.domain.json.stringOrNull
import com.inputa.reader.domain.model.DefinitionItem
import com.inputa.reader.domain.model.DictionaryEntry
import com.inputa.reader.domain.model.DictionaryExtra
import com.inputa.reader.domain.model.Meaning
import com.inputa.reader.domain.model.PartOfSpeechShare
import com.inputa.reader.domain.model.Phonetic
import com.inputa.reader.domain.model.WordForm
import com.inputa.reader.domain.model.WordFrequency
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 四个词典来源的响应解析。逐字移植自 Web 版的 `src/services/dictionaryApi.ts`。
 *
 * 全部是纯函数：输入是已经解析好的 [JsonElement]，输出是领域模型。所以它们能在
 * 纯 JVM 上被测，不需要网络、不需要 Android —— 而这里的每一条规则都对应一个
 * 曾经出过错的地方（尤其是 ECDICT 那个必须带点的词性前缀）。
 */

/**
 * 来源没有给出词性时的哨兵。**不要把它换成 `null` 或字符串 "unknown"**：
 * UI 拿到它时不渲染词性徽章 —— 把字面的 "unknown" 印在界面上，读者什么也没得到，
 * 看起来还像个 bug。
 */
const val UNKNOWN_PART_OF_SPEECH = "unknown"

/** 词典响应里满是 HTML 标记与实体；UI 只渲染纯文本。 */
private val HTML_TAG = Regex("<[^>]*>")
private val WHITESPACE_RUN = Regex("\\s+")

fun stripMarkup(input: String): String = WHITESPACE_RUN.replace(
    HTML_TAG.replace(input, "")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&"),
    " ",
).trim()

/** 非空字符串才返回值 —— 对应 JS 里 `a || b` 对空串同样回退的语义。 */
private fun JsonElement?.nonEmptyString(): String? =
    stringOrNull()?.takeIf { it.isNotEmpty() }

private fun JsonObject.nonEmpty(key: String): String? = this[key].nonEmptyString()

/* ------------------------------ Provider: dictionaryapi.dev ---------------- */

fun parseDictionaryApi(payload: JsonElement?, fallbackWord: String): DictionaryEntry? {
    val array = payload.asArrayOrNull() ?: return null
    val first = array.firstOrNull()?.asObjectOrNull() ?: return null

    val meanings = ArrayList<Meaning>()
    for (rawMeaning in first["meanings"].asArrayOrNull().orEmpty()) {
        val obj = rawMeaning.asObjectOrNull() ?: continue
        val definitions = ArrayList<DefinitionItem>()
        for (rawDefinition in obj["definitions"].asArrayOrNull().orEmpty()) {
            val def = rawDefinition.asObjectOrNull() ?: continue
            val text = def.nonEmpty("definition") ?: continue
            definitions += DefinitionItem(
                definition = stripMarkup(text),
                example = def.nonEmpty("example")?.let(::stripMarkup),
            )
        }
        if (definitions.isNotEmpty()) {
            meanings += Meaning(
                partOfSpeech = obj.nonEmpty("partOfSpeech") ?: UNKNOWN_PART_OF_SPEECH,
                definitions = definitions,
            )
        }
    }
    if (meanings.isEmpty()) return null

    val phonetics = first["phonetics"].asArrayOrNull().orEmpty().mapNotNull { it.asObjectOrNull() }
        .map { Phonetic(text = it.nonEmpty("text"), audio = it.nonEmpty("audio")) }

    // 优先挑美音/英音，与 Web 版一致。
    var audioUrl: String? = null
    for (phonetic in phonetics) {
        val audio = phonetic.audio ?: continue
        audioUrl = audio
        if (US_OR_UK_AUDIO.containsMatchIn(audio)) break
    }

    return DictionaryEntry(
        word = first.nonEmpty("word") ?: fallbackWord,
        phonetic = first.nonEmpty("phonetic") ?: phonetics.firstOrNull { it.text != null }?.text,
        phonetics = phonetics,
        meanings = meanings,
        audioUrl = audioUrl,
    )
}

private val US_OR_UK_AUDIO = Regex("-us\\.mp3|-uk\\.mp3")

/* ------------------------------ Provider: Wiktionary REST ------------------ */

fun parseWiktionary(payload: JsonElement?, fallbackWord: String): DictionaryEntry? {
    val sections = payload.asObjectOrNull()?.get("en").asArrayOrNull() ?: return null

    val meanings = ArrayList<Meaning>()
    for (rawSection in sections) {
        val section = rawSection.asObjectOrNull() ?: continue
        val definitions = ArrayList<DefinitionItem>()
        for (rawDefinition in section["definitions"].asArrayOrNull().orEmpty()) {
            val def = rawDefinition.asObjectOrNull() ?: continue
            val definition = stripMarkup(def.nonEmpty("definition") ?: "")
            if (definition.isEmpty()) continue

            val rawExample = def["parsedExamples"].asArrayOrNull()?.firstOrNull()
                ?.asObjectOrNull()?.nonEmpty("example")
                ?: def["examples"].asArrayOrNull()?.firstOrNull()?.stringOrNull()
            val example = rawExample?.let(::stripMarkup).orEmpty()

            definitions += DefinitionItem(
                definition = definition,
                example = example.ifEmpty { null },
            )
        }
        if (definitions.isNotEmpty()) {
            meanings += Meaning(
                partOfSpeech = section.nonEmpty("partOfSpeech") ?: UNKNOWN_PART_OF_SPEECH,
                definitions = definitions,
            )
        }
    }
    if (meanings.isEmpty()) return null

    return DictionaryEntry(word = fallbackWord, meanings = meanings)
}

/* ------------------------------ Provider: Datamuse ------------------------- */

private val DATAMUSE_POS_LABELS: Map<String, String> = mapOf(
    "n" to "noun",
    "v" to "verb",
    "adj" to "adjective",
    "adv" to "adverb",
    "u" to UNKNOWN_PART_OF_SPEECH,
)

fun parseDatamuse(payload: JsonElement?, fallbackWord: String): DictionaryEntry? {
    val array = payload.asArrayOrNull() ?: return null
    val first = array.firstOrNull()?.asObjectOrNull() ?: return null
    val defs = first["defs"].asArrayOrNull() ?: return null
    if (defs.isEmpty()) return null

    // Datamuse 把每条释义打包成 "词性<TAB>释义"。
    val grouped = LinkedHashMap<String, MutableList<DefinitionItem>>()
    for (rawDef in defs) {
        val fields = (rawDef.stringOrNull() ?: continue).split('\t')
        val abbreviation = fields.firstOrNull().orEmpty()
        val definition = stripMarkup(fields.lastOrNull().orEmpty())
        if (definition.isEmpty()) continue
        val partOfSpeech = DATAMUSE_POS_LABELS[abbreviation]
            ?: abbreviation.ifEmpty { UNKNOWN_PART_OF_SPEECH }
        grouped.getOrPut(partOfSpeech) { ArrayList() } += DefinitionItem(definition = definition)
    }
    if (grouped.isEmpty()) return null

    return DictionaryEntry(
        word = first.nonEmpty("word") ?: fallbackWord,
        meanings = grouped.map { (partOfSpeech, definitions) ->
            Meaning(partOfSpeech = partOfSpeech, definitions = definitions)
        },
    )
}

/* --------------------- Provider: local ECDICT (/api/dict) ------------------ */

/**
 * WordNet 词性代码（ECDICT 英文释义行的前缀，如 `"n. a score…"` / `"s. precisely…"`）
 * → 与其它来源一致的英文词性名，这样 UI 不必区分来源。
 */
private val ECDICT_DEFINITION_POS: Map<String, String> = mapOf(
    "n" to "noun",
    "v" to "verb",
    "a" to "adjective",
    "s" to "adjective",
    "r" to "adverb",
)

/**
 * 释义行的词性代码。**点号是必需的。**
 *
 * WordNet 的代码一律写作 `"n. "` / `"r. "`，而释义正文以单个字母单词开头非常常见
 * （`"A sack or matters inflated with air…"`、`"s indicating the beginning unit…"`）。
 * 把点号写成可选时，正文开头那个 "A" 会被当成形容词代码 `a` 吃掉 —— 于是词性标错，
 * 且释义的首词凭空消失（`"A sack or…"` 变成 `"sack or…"`）。抽样 ECDICT 约 30% 的
 * 释义行属于这种情况，所以这里严格要求点号：匹配不上只会让该行归入无词性分组，
 * 正文一个字都不会少。
 *
 * 用 IGNORE_CASE 对应 JS 的 `/i` 标志。
 */
private val ECDICT_DEFINITION_LINE = Regex("^([a-z])\\.\\s+(.+)$", RegexOption.IGNORE_CASE)

/**
 * GET /api/dict 的响应（见 server/dictLookup.ts）：一条本地 ECDICT 记录，
 * 带中文释义、考试标签、词形变化与词频。ECDICT 没有例句，所以 meanings 只有释义。
 */
fun parseLocalEcdict(payload: JsonElement?, fallbackWord: String): DictionaryEntry? {
    val raw = payload.asObjectOrNull() ?: return null

    // 英文释义一行一条，前缀是 WordNet 词性代码；没有代码的行保留全文并归入
    // 无词性分组，而不是被猜成某个词性。点号为什么必需见 ECDICT_DEFINITION_LINE。
    val grouped = LinkedHashMap<String, MutableList<DefinitionItem>>()
    val definitionColumn = raw.nonEmpty("definition").orEmpty()
    for (line in definitionColumn.split('\n')) {
        val text = stripMarkup(line)
        if (text.isEmpty()) continue

        val (partOfSpeech, definition) = splitEcdictDefinitionLine(text)
        if (definition.isEmpty()) continue

        val key = partOfSpeech ?: UNKNOWN_PART_OF_SPEECH
        grouped.getOrPut(key) { ArrayList() } += DefinitionItem(definition = definition)
    }

    val translation = raw.nonEmpty("translation")
    if (grouped.isEmpty() && translation == null) return null

    val collins = raw["collins"].intOrNull()?.takeIf { it > 0 }
    val bnc = raw["bnc"].intOrNull()?.takeIf { it > 0 }
    val frq = raw["frq"].intOrNull()?.takeIf { it > 0 }
    val oxford = raw["oxford"].booleanOrNull()?.takeIf { it }
    val frequency = if (collins != null || oxford != null || bnc != null || frq != null) {
        WordFrequency(collins = collins, oxford = oxford, bnc = bnc, frq = frq)
    } else {
        null
    }

    val examTags = raw["tags"].asArrayOrNull().orEmpty()
        .mapNotNull { it.asObjectOrNull()?.nonEmpty("label") }

    val partsOfSpeech = raw["partsOfSpeech"].asArrayOrNull().orEmpty()
        .mapNotNull { it.asObjectOrNull() }
        .mapNotNull { part ->
            val label = part.nonEmpty("label") ?: return@mapNotNull null
            PartOfSpeechShare(
                label = label,
                // 缩写缺失时退回标签本身，与 Web 版的 `abbr || label` 一致。
                abbr = part.nonEmpty("abbr") ?: label,
                percent = part["percent"].intOrNull() ?: 0,
            )
        }

    val forms = raw["forms"].asArrayOrNull().orEmpty()
        .mapNotNull { it.asObjectOrNull() }
        .mapNotNull { form ->
            val label = form.nonEmpty("label") ?: return@mapNotNull null
            val words = form["words"].asArrayOrNull().orEmpty()
                .mapNotNull { it.stringOrNull() }
                .filter { it.isNotEmpty() }
            if (words.isEmpty()) return@mapNotNull null
            WordForm(label = label, words = words)
        }

    val phonetic = raw.nonEmpty("phonetic")
    val audio = raw.nonEmpty("audio")
    val phonetics = if (phonetic != null || audio != null) {
        listOf(Phonetic(text = phonetic, audio = audio))
    } else {
        emptyList()
    }

    return DictionaryEntry(
        word = raw.nonEmpty("word") ?: fallbackWord,
        phonetic = phonetic,
        phonetics = phonetics,
        meanings = grouped.map { (partOfSpeech, definitions) ->
            Meaning(partOfSpeech = partOfSpeech, definitions = definitions)
        },
        audioUrl = audio,
        extra = DictionaryExtra(
            translation = translation,
            examTags = examTags,
            partsOfSpeech = partsOfSpeech,
            lemma = raw.nonEmpty("lemma"),
            inflection = raw.nonEmpty("inflection"),
            forms = forms,
            frequency = frequency,
        ),
    )
}

/**
 * ECDICT 释义行的前缀解析。
 *
 * @return 词性对应的英文名与去掉前缀后的释义。前缀不成立（没有点号、或点号前的
 *   字母不在词性表里）时词性为 null、释义为**整行原文** —— 调用方据此把它归入
 *   无词性分组。这正是「点号必需」那条规则的落点。
 */
internal fun splitEcdictDefinitionLine(line: String): Pair<String?, String> {
    val matched = ECDICT_DEFINITION_LINE.matchEntire(line) ?: return null to line
    val code = matched.groupValues[1].lowercase()
    val partOfSpeech = ECDICT_DEFINITION_POS[code] ?: return null to line
    return partOfSpeech to matched.groupValues[2]
}
