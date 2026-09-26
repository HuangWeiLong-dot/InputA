package com.inputa.reader.domain.vocab

import com.inputa.reader.domain.model.WordStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 移植自 Web 版的 src/__tests__/reader.test.ts（migration 与 highlight 部分）。
 *
 * 这批测试保护的是一条**会静默清空用户词库**的路径：Web 版是「localStorage 里存着
 * 旧两态数据、新版本读它」；Android 端是「导入一份 Web 版导出的备份」。
 * 机制不同，被检验的函数是同一个，所以覆盖度原样保留。
 */
class WordStatusMigrationTest {

    private fun json(text: String): JsonElement = Json.parseToJsonElement(text)

    // ---------- 旧值迁移 ----------

    @Test
    fun `maps the old two-state values onto the levels they meant`() {
        assertEquals(WordStatus.L5, WordLevelCodec.decodeLegacyString("learning")) // 点过 → 生词
        assertEquals(WordStatus.MASTERED, WordLevelCodec.decodeLegacyString("known")) // 翻页 → 掌握
    }

    @Test
    fun `passes through current values and rejects anything else`() {
        assertEquals(WordStatus.L1, WordLevelCodec.decodeLevel(1))
        assertEquals(WordStatus.L5, WordLevelCodec.decodeLevel(5))
        assertEquals(WordStatus.MASTERED, WordLevelCodec.decodeLegacyString("mastered"))

        assertNull(WordLevelCodec.decodeLevel(0))
        assertNull(WordLevelCodec.decodeLevel(6)) // 见 WordLevelCodec 类注释：备份里的 6 无效
        assertNull(WordLevelCodec.decodeLevel(-1))
        assertNull(WordLevelCodec.decodeLegacyString("nonsense"))
    }

    @Test
    fun `decodes through the JSON layer the way an imported backup arrives`() {
        assertEquals(WordStatus.L3, WordLevelCodec.decode(json("3")))
        assertEquals(WordStatus.MASTERED, WordLevelCodec.decode(json("\"mastered\"")))
        assertEquals(WordStatus.L5, WordLevelCodec.decode(json("\"learning\"")))

        assertNull(WordLevelCodec.decode(json("2.5"))) // 非整数
        assertNull(WordLevelCodec.decode(json("null")))
        assertNull(WordLevelCodec.decode(json("{\"level\":3}"))) // 对象
        assertNull(WordLevelCodec.decode(json("\"3\""))) // 字符串数字也不行，与 JS 的 typeof 判据一致
        assertNull(WordLevelCodec.decode(json("true")))
    }

    /**
     * 端到端版本：一份真的像 localStorage 那样的词库，逐条过闸门。
     * 迁移一旦退化，这条就是「用户的词库被静默清空」的警报。
     */
    @Test
    fun `normalizes a whole word list without losing words`() {
        val normalized = WordLevelCodec.normalizeMap(
            json("""{"curiosity":"learning","alice":"known","wonderland":3}"""),
        )

        assertEquals(
            mapOf(
                "curiosity" to WordStatus.L5, // 点过 → 生词
                "alice" to WordStatus.MASTERED, // 翻页自动收录 → 掌握
                "wonderland" to WordStatus.L3, // 本来就是等级，原样保留
            ),
            normalized,
        )
    }

    @Test
    fun `skips unrecognizable entries and keeps the rest`() {
        assertEquals(
            mapOf("good" to WordStatus.L2),
            WordLevelCodec.normalizeMap(json("""{"junk":"nonsense","good":2}""")),
        )
    }

    // ---------- 数据库列编码 ----------

    /**
     * 与上面相反的方向：**列里的 6 是合法的 MASTERED**。
     * 这两条规则必须分开，合并成一个函数就会在某一端出错 —— 见 WordLevelCodec 的类注释。
     */
    @Test
    fun `the database column code accepts 6 as mastered`() {
        assertEquals(WordStatus.L1, WordLevelCodec.fromCode(1))
        assertEquals(WordStatus.L5, WordLevelCodec.fromCode(5))
        assertEquals(WordStatus.MASTERED, WordLevelCodec.fromCode(6))
        assertNull(WordLevelCodec.fromCode(0))
        assertNull(WordLevelCodec.fromCode(7))
    }

    @Test
    fun `encode and decode round trip`() {
        for (status in WordStatus.entries) {
            assertEquals(status, WordLevelCodec.fromCode(WordLevelCodec.toCode(status)))
        }
    }

    // ---------- 正文着色（只关于显示，不写词库） ----------

    @Test
    fun `paints a word that was never collected as the newest level`() {
        assertEquals(WordStatus.L5, WordLevels.highlightLevel(null))
    }

    @Test
    fun `paints a collected word with the level it was given`() {
        assertEquals(WordStatus.L1, WordLevels.highlightLevel(WordStatus.L1))
        assertEquals(WordStatus.L3, WordLevels.highlightLevel(WordStatus.L3))
        assertEquals(WordStatus.L5, WordLevels.highlightLevel(WordStatus.L5))
    }

    @Test
    fun `leaves a mastered word unhighlighted`() {
        assertNull(WordLevels.highlightLevel(WordStatus.MASTERED))
    }

    @Test
    fun `level labels are the ones the reader sees`() {
        assertEquals("熟知", WordLevels.label(WordStatus.L1))
        assertEquals("生词", WordLevels.label(WordStatus.L5))
        assertEquals("3 一般", WordLevels.levelLabel(WordStatus.L3))
        assertNull(WordLevels.label(WordStatus.MASTERED))
    }

    @Test
    fun `isLevel answers about status, not about colour`() {
        assertEquals(true, WordLevels.isLevel(WordStatus.L5))
        assertEquals(false, WordLevels.isLevel(WordStatus.MASTERED))
        assertEquals(false, WordLevels.isLevel(null)) // 未收录：不是等级，但会按 5 级着色
    }
}
