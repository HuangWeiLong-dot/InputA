package com.inputa.reader.domain.annotation

import com.inputa.reader.domain.model.SavedSentence
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 移植自 Web 版的 src/__tests__/annotations.test.ts（规范化部分）。
 * 该文件里另一半测的是 store 的行为（去重、删空键、合并迟到的翻译），
 * 那些属于数据层，测试在 :app 的 DAO 测试里。
 */
class AnnotationNormalizerTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    // ---------- 笔记 ----------

    @Test
    fun `trims notes and drops blanks`() {
        assertEquals(listOf("跑", "奔跑"), AnnotationNormalizer.normalizeNoteList(json("""["  跑  ","","   ","奔跑"]""")))
    }

    @Test
    fun `rejects exact duplicates but keeps case variants`() {
        // 精确匹配而不是大小写无关：用户可能刻意保留两条只差大小写的笔记。
        assertEquals(listOf("Note", "note"), AnnotationNormalizer.normalizeNoteList(json("""["Note","Note","note"]""")))
    }

    @Test
    fun `rejects non-arrays and non-strings`() {
        assertEquals(emptyList<String>(), AnnotationNormalizer.normalizeNoteList(json("42")))
        assertEquals(emptyList<String>(), AnnotationNormalizer.normalizeNoteList(json("null")))
        assertEquals(emptyList<String>(), AnnotationNormalizer.normalizeNoteList(null))
        assertEquals(listOf("real"), AnnotationNormalizer.normalizeNoteList(json("""["real", 42, null, true]""")))
    }

    // ---------- 例句 ----------

    @Test
    fun `requires a non-blank sentence and drops the rest`() {
        assertNull(AnnotationNormalizer.normalizeSavedSentence(json("""{"translation":"x"}""")))
        assertNull(AnnotationNormalizer.normalizeSavedSentence(json("""{"sentence":"   "}""")))
        assertNull(AnnotationNormalizer.normalizeSavedSentence(json("42")))
        assertNull(AnnotationNormalizer.normalizeSavedSentence(null))
    }

    @Test
    fun `keeps provenance and drops empty or malformed fields`() {
        val sentence = AnnotationNormalizer.normalizeSavedSentence(
            json(
                """{"sentence":"  He runs.  ","bookId":"","bookTitle":"Frankenstein","pageIndex":3.5,"createdAt":"yesterday"}""",
            ),
        )

        assertEquals(
            // bookId 是空串 → 丢掉；pageIndex 3.5 与 createdAt 'yesterday' 都不是整数 → 丢掉。
            SavedSentence(sentence = "He runs.", bookTitle = "Frankenstein"),
            sentence,
        )
    }

    @Test
    fun `de-duplicates sentences by their text, first one wins`() {
        val list = AnnotationNormalizer.normalizeSentenceList(
            json("""[{"sentence":"A."},{"sentence":"A.","translation":"甲"},{"sentence":"B."}]"""),
        )
        assertEquals(listOf("A.", "B."), list.map { it.sentence })
        // 先出现的那条胜出，所以迟到的翻译不会被采用（合并逻辑在 store 里）。
        assertNull(list[0].translation)
    }

    // ---------- 两张映射 ----------

    @Test
    fun `normalizes word keys and drops empty lists`() {
        val notes = AnnotationNormalizer.normalizeNotesMap(json("""{"  Ubiquitous ":[ "到处都是" ],"RUN":[],"":["x"]}"""))
        assertEquals(mapOf("ubiquitous" to listOf("到处都是")), notes)
    }

    @Test
    fun `returns empty annotations for a words-only payload`() {
        // 导入 v1 备份时传进来的就是这种形状 —— 替换式导入要的正是空集，不是「保留原值」。
        val annotations = AnnotationNormalizer.normalize(json("""{"ubiquitous":3,"run":"mastered"}"""))
        assertEquals(emptyMap<String, List<String>>(), annotations.notes)
        assertEquals(emptyMap<String, List<SavedSentence>>(), annotations.sentences)
        assertEquals(true, annotations.isEmpty)
    }

    @Test
    fun `survives corrupt input instead of throwing`() {
        // 手改过的 JSON 不能把导入流程炸掉。
        for (bad in listOf("null", "42", "\"a string\"", "[1,2,3]", "{}")) {
            val annotations = AnnotationNormalizer.normalize(json(bad))
            assertEquals(emptyMap<String, List<String>>(), annotations.notes)
        }
        assertEquals(true, AnnotationNormalizer.normalize(null).isEmpty)
    }

    @Test
    fun `a backup carrying only notes is not empty`() {
        // 这条与数据库里「notes 表不建到 vocabulary 的外键」直接相关：
        // 建了外键，这份合法备份就会插入失败。
        val annotations = AnnotationNormalizer.normalize(json("""{"notes":{"run":["跑"]}}"""))
        assertEquals(false, annotations.isEmpty)
        assertEquals(mapOf("run" to listOf("跑")), annotations.notes)
    }
}
