package com.inputa.next.core.data.local

import android.content.Context
import com.inputa.next.core.data.LessonSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/** 上次真正进入阅读的课程，用于首页「继续上一课程」。 */
@Singleton
class LastLessonStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("inputa_next_last_lesson", Context.MODE_PRIVATE)

    fun save(language: String, lesson: LessonSummary) {
        prefs.edit().putString(language, lesson.toJsonString()).apply()
    }

    fun load(language: String): LessonSummary? {
        val raw = prefs.getString(language, null) ?: return null
        return runCatching { JSONObject(raw).toLessonSummaryOrNull() }.getOrNull()
    }

    fun clear(language: String) {
        prefs.edit().remove(language).apply()
    }
}
