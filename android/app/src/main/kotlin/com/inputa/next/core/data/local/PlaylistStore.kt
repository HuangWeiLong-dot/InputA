package com.inputa.next.core.data.local

import android.content.Context
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.PlaylistEntry
import com.inputa.next.core.data.int
import com.inputa.next.core.data.string
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * 本地播放列表。LingQ 自己的 PlaylistEntity 也是纯本地的，
 * 服务端没有 playlist 接口，所以这里按同样的思路只存本机。
 */
@Singleton
class PlaylistStore @Inject constructor(
    @ApplicationContext context: Context,
    private val json: Json,
) {
    private val prefs = context.getSharedPreferences("inputa_next_playlist", Context.MODE_PRIVATE)

    private val _items = MutableStateFlow(load())
    val items = _items.asStateFlow()

    fun add(lesson: LessonSummary): Boolean {
        if (_items.value.any { it.lessonId == lesson.id }) return false
        val entry = PlaylistEntry(
            lessonId = lesson.id,
            title = lesson.title,
            imageUrl = lesson.imageUrl,
            audioUrl = lesson.audioUrl,
            collectionTitle = lesson.collectionTitle,
            durationSec = lesson.durationSec,
            level = lesson.level,
        )
        _items.value = _items.value + entry
        persist()
        return true
    }

    fun remove(lessonId: Int) {
        _items.value = _items.value.filterNot { it.lessonId == lessonId }
        persist()
    }

    fun clear() {
        _items.value = emptyList()
        persist()
    }

    fun contains(lessonId: Int): Boolean = _items.value.any { it.lessonId == lessonId }

    private fun persist() {
        val array = JsonArray(
            _items.value.map { entry ->
                buildJsonObject {
                    put("lessonId", entry.lessonId)
                    put("title", entry.title)
                    entry.imageUrl?.let { put("imageUrl", it) }
                    entry.audioUrl?.let { put("audioUrl", it) }
                    entry.collectionTitle?.let { put("collectionTitle", it) }
                    entry.durationSec?.let { put("durationSec", it) }
                    entry.level?.let { put("level", it) }
                }
            },
        )
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private fun load(): List<PlaylistEntry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val id = obj.int("lessonId") ?: return@mapNotNull null
                PlaylistEntry(
                    lessonId = id,
                    title = obj.string("title") ?: "Lesson",
                    imageUrl = obj.string("imageUrl"),
                    audioUrl = obj.string("audioUrl"),
                    collectionTitle = obj.string("collectionTitle"),
                    durationSec = obj.int("durationSec"),
                    level = obj.int("level"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "items"
    }
}
