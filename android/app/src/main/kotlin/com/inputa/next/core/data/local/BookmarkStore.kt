package com.inputa.next.core.data.local

import android.content.Context
import com.inputa.next.core.data.LessonSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

/** 本机书签；按学习语言分开保存，最近添加的排在最前。 */
@Singleton
class BookmarkStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("inputa_next_bookmarks", Context.MODE_PRIVATE)

    fun list(language: String): List<LessonSummary> {
        val raw = prefs.getString(language, null) ?: return emptyList()
        return runCatching { JSONArray(raw).toLessons() }.getOrDefault(emptyList())
    }

    fun isBookmarked(language: String, lessonId: Int): Boolean =
        list(language).any { it.id == lessonId }

    /** 返回操作后的书签状态。 */
    fun set(language: String, lesson: LessonSummary, bookmarked: Boolean): Boolean {
        val current = list(language).filterNot { it.id == lesson.id }
        val next = if (bookmarked) listOf(lesson) + current else current
        write(language, next)
        return bookmarked
    }

    fun remove(language: String, lessonId: Int) {
        write(language, list(language).filterNot { it.id == lessonId })
    }

    private fun write(language: String, lessons: List<LessonSummary>) {
        val array = JSONArray()
        lessons.forEach { lesson ->
            runCatching { JSONObject(lesson.toJsonString()) }.getOrNull()?.let { json -> array.put(json) }
        }
        prefs.edit().putString(language, array.toString()).apply()
    }
}
