package com.inputa.next.core.data.local

import com.inputa.next.core.data.LessonSummary
import org.json.JSONArray
import org.json.JSONObject

/** 本地保存课程卡片所需的字段；足够首页/书签页直接打开课程。 */
internal fun LessonSummary.toJsonString(): String = JSONObject().apply {
    put("id", id)
    put("title", title)
    put("imageUrl", imageUrl ?: JSONObject.NULL)
    put("audioUrl", audioUrl ?: JSONObject.NULL)
    put("durationSec", durationSec ?: JSONObject.NULL)
    put("level", level ?: JSONObject.NULL)
    put("newWordsCount", newWordsCount ?: JSONObject.NULL)
    put("collectionTitle", collectionTitle ?: JSONObject.NULL)
    put("collectionId", collectionId ?: JSONObject.NULL)
    put("isCompleted", isCompleted)
    put("isTaken", isTaken)
    put("isCourse", isCourse)
    put("lessonCount", lessonCount ?: JSONObject.NULL)
    put("libraryId", libraryId ?: JSONObject.NULL)
}.toString()

internal fun JSONObject.toLessonSummaryOrNull(): LessonSummary? {
    val id = optInt("id", -1)
    val title = optString("title").trim()
    if (id < 0 || title.isEmpty()) return null
    fun optionalInt(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null
    fun optionalString(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
    return LessonSummary(
        id = id,
        title = title,
        imageUrl = optionalString("imageUrl"),
        audioUrl = optionalString("audioUrl"),
        durationSec = optionalInt("durationSec"),
        level = optionalInt("level"),
        newWordsCount = optionalInt("newWordsCount"),
        collectionTitle = optionalString("collectionTitle"),
        collectionId = optionalInt("collectionId"),
        isCompleted = optBoolean("isCompleted", false),
        isTaken = optBoolean("isTaken", false),
        isCourse = optBoolean("isCourse", false),
        lessonCount = optionalInt("lessonCount"),
        libraryId = optionalInt("libraryId"),
    )
}

internal fun JSONArray.toLessons(): List<LessonSummary> = buildList {
    for (index in 0 until length()) {
        optJSONObject(index)?.toLessonSummaryOrNull()?.let(::add)
    }
}
