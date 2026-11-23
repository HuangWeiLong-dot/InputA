package com.inputa.next.core.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/**
 * 词熟练度/学习记录只存本机，LingQ 接口只负责课程内容与查词。
 *
 * 状态编码：
 *  -1 忽略
 *   0 已知
 * 1–4 LingQ 熟练度
 */
@Singleton
class LocalWordStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("inputa_next_words", Context.MODE_PRIVATE)

    /** key 统一为小写词形；只返回当前语言的记录。 */
    fun load(language: String): Map<String, Int> {
        val raw = prefs.getString(language, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key -> put(key, json.optInt(key)) }
            }
        }.getOrDefault(emptyMap())
    }

    fun put(language: String, term: String, status: Int) {
        val key = term.trim().lowercase()
        if (key.isEmpty()) return
        val next = load(language).toMutableMap()
        val previous = next[key]
        next[key] = status
        write(language, next)
        // 只有状态真正变化才算今天学到的词，重复点同一个词不重复计数。
        if (previous != status) recordToday(language, key)
    }

    /** 今天在这个语言下产生过状态变化的去重词数，用于每日目标进度。 */
    fun todayLearned(language: String): Int {
        val raw = prefs.getString(dailyKey(language), null) ?: return 0
        return runCatching {
            val json = JSONObject(raw)
            if (json.optString("day") != LocalDate.now().toString()) return 0
            json.optJSONArray("words")?.length() ?: 0
        }.getOrDefault(0)
    }

    private fun recordToday(language: String, key: String) {
        val today = LocalDate.now().toString()
        val current = prefs.getString(dailyKey(language), null)
        val json = runCatching { current?.let { JSONObject(it) } }.getOrNull()
        val sameDay = json?.optString("day") == today
        val words = if (sameDay) json?.optJSONArray("words") ?: JSONArray() else JSONArray()
        val existing = buildSet {
            for (index in 0 until words.length()) {
                words.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        if (key in existing) return
        val next = JSONArray().apply {
            existing.sorted().forEach { word -> put(word) }
            put(key)
        }
        prefs.edit()
            .putString(dailyKey(language), JSONObject().put("day", today).put("words", next).toString())
            .apply()
    }

    fun remove(language: String, term: String) {
        val key = term.trim().lowercase()
        if (key.isEmpty()) return
        val next = load(language).toMutableMap()
        next.remove(key)
        write(language, next)
    }

    private fun write(language: String, values: Map<String, Int>) {
        val json = JSONObject()
        values.forEach { (word, value) -> json.put(word, value) }
        prefs.edit().putString(language, json.toString()).apply()
    }

    private fun dailyKey(language: String): String = "daily_$language"
}
