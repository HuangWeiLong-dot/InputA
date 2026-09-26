package com.inputa.reader.domain.backup

import com.inputa.reader.domain.model.AnnotationsData
import com.inputa.reader.domain.model.SavedSentence
import com.inputa.reader.domain.model.WordStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 移植自 Web 版的 src/__tests__/backup.test.ts，逐条对应。
 *
 * 端点全部用**真实的 v2 JSON 文本**做夹具，而不是 Kotlin 对象对 Kotlin 对象 ——
 * 因为这条路径的真实输入是用户在 Web 版里导出、再拷到手机上的那个文件。
 * 只测 Kotlin↔Kotlin 的往返会让「字段名对不上」这类问题整个溜过去。
 */
class BackupTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    private val sampleJson = """
        {
          "version": 2,
          "exportedAt": "2026-09-24T00:00:00.000Z",
          "words": { "ubiquitous": 3, "run": "mastered", "curiosity": 5 },
          "notes": { "ubiquitous": ["到处都是", "无处不在"] },
          "sentences": {
            "ubiquitous": [
              { "sentence": "Screens are ubiquitous.", "translation": "屏幕**无处不在**。", "createdAt": 1 }
            ]
          }
        }
    """.trimIndent()

    // ---------- 主路径：Web 版导出的 v2 文件 ----------

    @Test
    fun `imports a v2 export with all three data sets intact`() {
        val data = Backup.parse(json(sampleJson))
        assertNotNull(data)

        assertEquals(3, data!!.words.size)
        assertEquals(WordStatus.L3, data.words["ubiquitous"])
        assertEquals(WordStatus.MASTERED, data.words["run"])
        assertEquals(WordStatus.L5, data.words["curiosity"])

        assertEquals(listOf("到处都是", "无处不在"), data.notes["ubiquitous"])
        assertEquals("屏幕**无处不在**。", data.sentences["ubiquitous"]?.first()?.translation)
        assertEquals(1L, data.sentences["ubiquitous"]?.first()?.createdAt)
    }

    // ---------- 兼容：两种 v1 形状 ----------

    /**
     * 旧版「导出」写的是裸映射，而 localStorage 里是同一个映射外面包了一层 `{words: {...}}`。
     * 两种历史上都真实存在过，所以两种都得能导入。
     */
    @Test
    fun `reads a v1 bare map and migrates its old status values`() {
        val data = Backup.parse(json("""{"ubiquitous":3,"alice":"known"}"""))
        assertNotNull(data)
        assertEquals(mapOf("ubiquitous" to WordStatus.L3, "alice" to WordStatus.MASTERED), data!!.words)
        assertEquals(emptyMap<String, List<String>>(), data.notes)
        assertEquals(emptyMap<String, List<SavedSentence>>(), data.sentences)
    }

    @Test
    fun `reads a v1 wrapped payload as found in localStorage`() {
        val data = Backup.parse(json("""{"words":{"curiosity":"learning","wonderland":2}}"""))
        assertNotNull(data)
        assertEquals(mapOf("curiosity" to WordStatus.L5, "wonderland" to WordStatus.L2), data!!.words)
        assertEquals(emptyMap<String, List<String>>(), data.notes)
    }

    @Test
    fun `keeps importing a v2 file that lost its annotations`() {
        val data = Backup.parse(json("""{"version":2,"words":{"run":1}}"""))
        assertNotNull(data)
        assertEquals(mapOf("run" to WordStatus.L1), data!!.words)
        assertEquals(emptyMap<String, List<SavedSentence>>(), data.sentences)
    }

    @Test
    fun `drops unreadable entries and keeps the rest`() {
        val data = Backup.parse(
            json(
                """
                {
                  "version": 2,
                  "words": { "good": 2, "junk": "nonsense", "worse": { "level": 3 } },
                  "notes": { "run": ["跑", 42, null], "broken": "not an array" },
                  "sentences": { "run": [{ "sentence": "He runs." }, { "nothing": true }, "nope"] }
                }
                """.trimIndent(),
            ),
        )
        assertNotNull(data)
        assertEquals(mapOf("good" to WordStatus.L2), data!!.words)
        assertEquals(mapOf("run" to listOf("跑")), data.notes)
        assertEquals(mapOf("run" to listOf(SavedSentence("He runs."))), data.sentences)
    }

    // ---------- 拒绝「不是本应用的备份」 ----------

    @Test
    fun `rejects things that are not backups at all`() {
        assertNull(Backup.parse(null))
        assertNull(Backup.parse(json("\"a string\"")))
        assertNull(Backup.parse(json("[1,2,3]")))
        assertNull(Backup.parse(json("42")))
    }

    /**
     * 没有这道闸，这个文件会被当成裸映射读成 `{version: 2}` —— 一个名叫 "version"、
     * 熟练度 2 的假词，导入时用它覆盖掉读者的整本词库。
     */
    @Test
    fun `rejects a versioned file with no word list`() {
        assertNull(Backup.parse(json("""{"version":2}""")))
        assertNull(Backup.parse(json("""{"version":2,"words":"not an object"}""")))
    }

    // ---------- 空备份 ----------

    @Test
    fun `flags a backup with nothing in it`() {
        // 导入它就是静默清空读者的词库。
        assertEquals(true, Backup.isEmpty(BackupData(emptyMap(), AnnotationsData())))

        val parsed = Backup.parse(json("""{"words":{}}"""))
        assertNotNull(parsed)
        assertEquals(true, Backup.isEmpty(parsed!!))
    }

    @Test
    fun `does not flag a backup that carries any of the three sets`() {
        val full = Backup.parse(json(sampleJson))!!
        assertEquals(false, Backup.isEmpty(full))

        assertEquals(
            false,
            Backup.isEmpty(BackupData(emptyMap(), AnnotationsData(notes = mapOf("run" to listOf("跑"))))),
        )
        assertEquals(
            false,
            Backup.isEmpty(BackupData(mapOf("run" to WordStatus.L5), AnnotationsData())),
        )
    }

    // ---------- 导出（第二阶段才接 UI，形状现在就钉住） ----------

    @Test
    fun `build stamps the version and the caller supplied timestamp`() {
        val file = Backup.build(BackupData(emptyMap(), AnnotationsData()), "2026-09-24T00:00:00.000Z")
        assertEquals(2, file.version)
        assertEquals("2026-09-24T00:00:00.000Z", file.exportedAt)
        assertEquals(Backup.EXPORT_VERSION, file.version)
    }
}
