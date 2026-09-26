package com.inputa.reader.domain.annotation

import com.inputa.reader.domain.json.asArrayOrNull
import com.inputa.reader.domain.json.asObjectOrNull
import com.inputa.reader.domain.json.intOrNull
import com.inputa.reader.domain.json.longOrNull
import com.inputa.reader.domain.json.stringOrNull
import com.inputa.reader.domain.model.AnnotationsData
import com.inputa.reader.domain.model.SavedSentence
import com.inputa.reader.domain.text.TextNormalize
import kotlinx.serialization.json.JsonElement

/**
 * 笔记与例句的规范化。
 *
 * **为什么单独一套、跟熟练度分开**：熟练度存在 `vocabulary` 表里，值是标量
 * （L1-L5 或 MASTERED），而 [com.inputa.reader.domain.vocab.WordLevelCodec.normalizeMap]
 * 会把任何非标量的值判为无效丢弃 —— 它同时是加载和导入的唯一闸门。把笔记塞进同一张表
 * （比如把值改成 `{level, note}`）会让所有现存用户的词库在下一次加载时被**静默清空**，
 * 而且 Web 版有测试钉住了这个行为。
 *
 * 这里的函数一律「丢弃脏数据、保留能救的」，与 WordLevelCodec 同风格：
 * 导入一份旧备份或手改过的 JSON 不能抛异常。
 */
object AnnotationNormalizer {

    /**
     * 词的键一律去空白 + 小写，与 `WordLevels.normalizeKey` 一致。
     *
     * 读取时永远用规范化后的词查表，所以导入时若不规范化，带大写或前后空白的键
     * 会**永远查不到** —— 那是静默丢数据，比报错更糟。
     */
    fun normalizeKey(raw: String): String = raw.trim().lowercase()

    private fun optionalString(value: JsonElement?): String? {
        val text = value.stringOrNull() ?: return null
        val trimmed = TextNormalize.trim(text)
        return trimmed.ifEmpty { null }
    }

    /**
     * 章节号 / 页码只取整数：`3.5` 页或 `'yesterday'` 这种值说明数据已经被改坏或
     * 来自别的格式，丢掉比原样带着走进渲染更安全。
     */
    private fun optionalInteger(value: JsonElement?): Int? = value.intOrNull()

    /**
     * 规范化一个词的笔记数组：丢掉非字符串与空白项，按**精确匹配**去重。
     *
     * 精确匹配（而不是大小写无关）是有意的：LingKuma 的 addTranslationToDB 就是
     * `includes()` 后 push，用户可能刻意保留两条只差大小写的笔记。
     */
    fun normalizeNoteList(input: JsonElement?): List<String> {
        val array = input.asArrayOrNull() ?: return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (item in array) {
            val text = TextNormalize.trim(item.stringOrNull() ?: continue)
            if (text.isEmpty() || !seen.add(text)) continue
            result += text
        }
        return result
    }

    /** 规范化一条例句。没有 `sentence` 就丢弃 —— 它是去重键，缺了没有意义。 */
    fun normalizeSavedSentence(input: JsonElement?): SavedSentence? {
        val raw = input.asObjectOrNull() ?: return null
        val sentence = optionalString(raw["sentence"]) ?: return null

        return SavedSentence(
            sentence = sentence,
            translation = optionalString(raw["translation"]),
            bookId = optionalString(raw["bookId"]),
            bookTitle = optionalString(raw["bookTitle"]),
            chapterIndex = optionalInteger(raw["chapterIndex"]),
            pageIndex = optionalInteger(raw["pageIndex"]),
            createdAt = raw["createdAt"].longOrNull(),
        )
    }

    /** 规范化一个词的例句数组，按 `sentence` 去重（先出现的那条胜出）。 */
    fun normalizeSentenceList(input: JsonElement?): List<SavedSentence> {
        val array = input.asArrayOrNull() ?: return emptyList()
        val seen = HashSet<String>()
        val result = ArrayList<SavedSentence>()
        for (item in array) {
            val sentence = normalizeSavedSentence(item) ?: continue
            if (!seen.add(sentence.sentence)) continue
            result += sentence
        }
        return result
    }

    /** 规范化一张 `{ 单词 → 笔记[] }` 表，丢掉空表，避免导出文件里满是空壳。 */
    fun normalizeNotesMap(input: JsonElement?): Map<String, List<String>> {
        val obj = input.asObjectOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, List<String>>()
        for ((rawKey, value) in obj) {
            val key = normalizeKey(rawKey)
            if (key.isEmpty()) continue
            val notes = normalizeNoteList(value)
            if (notes.isEmpty()) continue
            result[key] = notes
        }
        return result
    }

    /** 规范化一张 `{ 单词 → 例句[] }` 表。 */
    fun normalizeSentencesMap(input: JsonElement?): Map<String, List<SavedSentence>> {
        val obj = input.asObjectOrNull() ?: return emptyMap()
        val result = LinkedHashMap<String, List<SavedSentence>>()
        for ((rawKey, value) in obj) {
            val key = normalizeKey(rawKey)
            if (key.isEmpty()) continue
            val sentences = normalizeSentenceList(value)
            if (sentences.isEmpty()) continue
            result[key] = sentences
        }
        return result
    }

    /**
     * 把任意来源的数据规范化成 [AnnotationsData]。
     *
     * 接受的形状：`{notes, sentences}`、或者任何缺字段/类型不对的输入（按空处理）。
     * 导入 v1 备份时没有标注数据，传 null 即得到空集 —— 这正是「替换式导入」想要的语义。
     */
    fun normalize(input: JsonElement?): AnnotationsData {
        val obj = input.asObjectOrNull() ?: return AnnotationsData()
        return AnnotationsData(
            notes = normalizeNotesMap(obj["notes"]),
            sentences = normalizeSentencesMap(obj["sentences"]),
        )
    }
}
