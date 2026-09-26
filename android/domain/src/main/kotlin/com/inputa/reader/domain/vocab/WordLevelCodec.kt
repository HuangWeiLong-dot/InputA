package com.inputa.reader.domain.vocab

import com.inputa.reader.domain.json.intOrNull
import com.inputa.reader.domain.json.stringOrNull
import com.inputa.reader.domain.model.WordStatus
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 熟练度的编解码 —— **这是词库状态进出的唯一闸门**。
 *
 * 对应 Web 版的 `normalizeWordStatus` / `normalizeWordMap`。它同时是「加载」和
 * 「导入备份」两条路径的过滤器，所以任何无法识别的值都必须被**丢弃**而不是透传：
 * 脏数据一旦进了词库，下一次导出就把它传播给别的设备。
 *
 * 有一条容易写错、也容易被后代「顺手修掉」的规则：
 *
 *   **备份里的数字 6 是无效值，而数据库列里的 6 是 MASTERED。**
 *
 * 因为 Web 版的词库值域是 `1-5 | 'mastered'`（字符串），「已掌握」从来不用数字表示。
 * 所以 `decode`（读外部来的 JSON）必须拒绝 6，而 `fromCode`（读我们自己的列）必须
 * 接受 6。把它们合成一个函数，就会在某一端出错：要么导入时把 `6` 静默读成 masterd
 * 而丢掉一个本来无效的值，要么读库时把 `MASTERED` 读成 null 而清空所有已掌握的词。
 * Web 版有测试钉住 `normalizeWordStatus(6) === null`。
 */
object WordLevelCodec {

    // ---------- 外部 JSON（localStorage 形状的备份、旧导出文件）→ WordStatus ----------

    /**
     * 旧版本的**两态**词库用的字符串值，迁移时按语义对应到今天的结果：
     *   learning → 5「生词」   —— 用户明确点过的词，仍然是最不熟的一档
     *   known    → MASTERED    —— 翻页自动收录的词，今天翻页给的就是「掌握」
     */
    fun decodeLegacyString(raw: String): WordStatus? = when (raw) {
        "mastered" -> WordStatus.MASTERED
        "learning" -> WordStatus.L5
        "known" -> WordStatus.MASTERED
        else -> null
    }

    /** 1-5 之外的整数（含 6、0、-1）一律 null —— 见类注释。 */
    fun decodeLevel(level: Int): WordStatus? = WordLevels.fromLevel(level)

    /** 一个任意的 JSON 值 → WordStatus，无法识别返回 null。 */
    fun decode(value: JsonElement?): WordStatus? {
        value?.stringOrNull()?.let { return decodeLegacyString(it) }
        value?.intOrNull()?.let { return decodeLevel(it) }
        return null
    }

    /**
     * 逐条规范化一张 `{ 单词: 状态 }` 表，丢弃无法识别的条目。
     *
     * 键**不做**大小写规范化：Web 版的 `normalizeWordMap` 也没有做，因为写进去的
     * 键本来就来自 `cleanToken`。导入路径由 AnnotationNormalizer 那侧统一处理。
     */
    fun normalizeMap(input: JsonElement?): Map<String, WordStatus> {
        val obj = input as? JsonObject ?: return emptyMap()
        val result = LinkedHashMap<String, WordStatus>()
        for ((word, value) in obj) {
            val status = decode(value) ?: continue
            result[word] = status
        }
        return result
    }

    // ---------- Room 的 INTEGER 列 ----------

    /**
     * 列值 → WordStatus。1-5 是等级本身，6 是 MASTERED。
     *
     * 这是磁盘上的既有数据，遇到范围外的值只能丢弃（返回 null）——
     * 静默当成某一档会让用户看到自己没设过的熟练度。
     */
    fun fromCode(code: Int): WordStatus? = when (code) {
        1 -> WordStatus.L1
        2 -> WordStatus.L2
        3 -> WordStatus.L3
        4 -> WordStatus.L4
        5 -> WordStatus.L5
        6 -> WordStatus.MASTERED
        else -> null
    }

    fun toCode(status: WordStatus): Int = status.code
}
