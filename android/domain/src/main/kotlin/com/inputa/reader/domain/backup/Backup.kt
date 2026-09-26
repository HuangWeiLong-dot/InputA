package com.inputa.reader.domain.backup

import com.inputa.reader.domain.annotation.AnnotationNormalizer
import com.inputa.reader.domain.json.asObjectOrNull
import com.inputa.reader.domain.json.intOrNull
import com.inputa.reader.domain.model.AnnotationsData
import com.inputa.reader.domain.model.SavedSentence
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.vocab.WordLevelCodec
import kotlinx.serialization.json.JsonElement

/**
 * 词汇备份文件的读写。
 *
 * v1（旧版「导出」）写的是**裸的** `{ 单词: 状态 }` 映射 —— 注意它和 Web 版
 * localStorage 里存的形状并不一致（那里是 `{words: {...}}`），所以历史上「导出」
 * 出来的文件直接拿去当 localStorage 的值是用不了的。
 *
 * v2 把三份数据装进一个带版本号的对象，并记下导出时间。
 *
 * Android 端目前的定位是**只导入不导出**：用户手里已经有一份 Web 版导出的备份，
 * 需要能搬到手机上。导出排在第二阶段，但表结构从现在起就保证无损。
 */
object Backup {

    const val EXPORT_VERSION: Int = 2

    /**
     * 解析一份备份。认得三种形状：
     *   - v2：`{version, exportedAt, words, notes, sentences}`
     *   - v1 包裹形：`{words: {...}}`（localStorage 里就是长这样，用户可能直接拷出来）
     *   - v1 裸映射：`{ 单词: 状态 }`（旧版「导出」真正写出的形状）
     *
     * 判别包裹形的依据是 `words` 的值是不是对象：裸映射里任何键的值都只能是标量
     * （1-5 或 'mastered'），所以那个位置出现对象只可能是包裹形。
     *
     * @return null 表示「这不是本应用的备份」；数据本身不合法（脏条目）不算 null，
     *   交给 normalize* 逐条丢弃。
     */
    fun parse(raw: JsonElement?): BackupData? {
        val record = raw.asObjectOrNull() ?: return null
        val wrapper = record["words"]

        if (wrapper.asObjectOrNull() != null) {
            return BackupData(
                words = WordLevelCodec.normalizeMap(wrapper),
                // v1 没有这两项；normalize* 对 null 返回空集，正是替换式导入要的语义。
                annotations = AnnotationNormalizer.normalize(record),
            )
        }

        // 声称自己是 v2 却没有 words 对象 —— 文件是坏的。没有这道闸，下面的裸映射
        // 分支会把 `version: 2` 读成一个名为 "version"、熟练度 2 的单词，于是导入
        // 就用这一个假词覆盖掉用户的整个词库。
        if (record["version"].intOrNull() != null) return null

        return BackupData(
            words = WordLevelCodec.normalizeMap(record),
            // 裸映射形状里本来就没有标注，但仍扫一遍 record：万一用户在裸映射基础上
            // 手加了 notes 字段，丢掉不如留着。
            annotations = AnnotationNormalizer.normalize(record),
        )
    }

    /** 导出时间由调用方传入，好让测试拿到确定的输出。 */
    fun build(data: BackupData, exportedAt: String): BackupFile = BackupFile(
        version = EXPORT_VERSION,
        exportedAt = exportedAt,
        words = data.words,
        annotations = data.annotations,
    )

    /**
     * 这份备份是不是空的。空备份等同于「清空词库」，而那更可能是选错了文件 ——
     * 调用方据此拒绝导入，比默默抹掉用户的数据安全。
     *
     * 注意三份数据是**独立**判断的：只有 notes 的备份是非空备份，必须能导入。
     * 这也是数据库里 `vocabulary` 与 `notes` 之间**不建外键**的原因 ——
     * 建了的话这份合法备份会因为找不到对应的词条而插入失败。
     */
    fun isEmpty(data: BackupData): Boolean =
        data.words.isEmpty() && data.annotations.isEmpty
}

data class BackupData(
    val words: Map<String, WordStatus>,
    val annotations: AnnotationsData,
) {
    val notes: Map<String, List<String>> get() = annotations.notes
    val sentences: Map<String, List<SavedSentence>> get() = annotations.sentences
}

/** 导出文件的形状。字段名就是 JSON 里的键名，与 Web 版 v2 完全一致。 */
data class BackupFile(
    val version: Int,
    val exportedAt: String,
    val words: Map<String, WordStatus>,
    val annotations: AnnotationsData,
)
