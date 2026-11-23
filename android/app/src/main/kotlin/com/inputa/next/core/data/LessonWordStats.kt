package com.inputa.next.core.data

/**
 * 一课的词量统计。**全部由本机词库算出来**，服务端返回的 new words 只作缺省值 ——
 * 本应用的词状态只写本地、不回写 LingQ，服务端数字必然与阅读器里看到的颜色不一致。
 */
data class LessonWordStats(
    val total: Int,
    val unique: Int,
    val newWords: Int,
)

/**
 * 按本机词库算一课的 Total / Unique / New。
 *
 * 词切分与阅读器同一套规则（[LingqApi.tokenizeWords]），「New」= 本机**没有任何记录**
 * 的词，也就是阅读器里显示为蓝块的那些词；已掌握、已忽略、LingQ 1-4 都不算新词。
 */
fun computeLessonWordStats(
    api: LingqApi,
    text: String,
    language: String,
    records: Map<String, Int>,
): LessonWordStats {
    val tokens = api.tokenizeWords(text, language)
    val keys = ArrayList<String>(tokens.size)
    tokens.forEach { keys += it.text.trim().lowercase() }
    val uniqueKeys = keys.toHashSet()
    return LessonWordStats(
        total = keys.size,
        unique = uniqueKeys.size,
        newWords = uniqueKeys.count { records[it] == null },
    )
}
