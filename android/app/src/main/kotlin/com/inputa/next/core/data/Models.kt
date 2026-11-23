package com.inputa.next.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 账号资料。字段按 LingQ 实际返回做了多别名兜底，缺失就保持 null，不编造。 */
data class Profile(
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val knownWords: Int?,
    val lingqCount: Int?,
    val wordsRead: Int?,
    val hoursListened: Double?,
    val streak: Int?,
    val coins: Int?,
    val languageCode: String?,
    val languageName: String?,
    val flag: String?,
)

data class LessonSummary(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val audioUrl: String?,
    val durationSec: Int?,
    val level: Int?,
    val newWordsCount: Int?,
    val collectionTitle: String?,
    val collectionId: Int?,
    val isCompleted: Boolean,
    val isTaken: Boolean,
    val isCourse: Boolean = false,
    val lessonCount: Int? = null,
    val libraryId: Int? = null,
) {
    fun levelLabel(): String = levelLabel(level)

    companion object {
        fun levelLabel(level: Int?): String = when {
            level == null -> "—"
            level <= 2 -> "Beg."
            level <= 4 -> "Int."
            else -> "Adv."
        }
    }
}

data class LessonDetail(
    val lesson: LessonSummary,
    val description: String?,
    val text: String?,
    val totalWords: Int?,
    val uniqueWords: Int?,
    val newWords: Int?,
)

/**
 * 阅读器里的一个可点词。只描述“词”本身，标点和空格属于句子文本，
 * 由 UI 原样渲染；start/end 是它在 sentence.text 里的字符范围。
 */
data class ReaderToken(
    val text: String,
    val start: Int,
    val end: Int,
    val isKnown: Boolean,
    val isUnknown: Boolean,
    val isIgnored: Boolean,
    val wordId: Int?,
    val translation: String?,
    val transliteration: String?,
    /** LingQ 熟练度 1–4；null 表示不是 LingQ 词或服务端没给。 */
    val level: Int? = null,
)

data class ReaderSentence(
    val index: Int,
    val text: String,
    val tokens: List<ReaderToken>,
    val paragraphStart: Boolean = false,
)

data class VocabCard(
    val id: Int,
    val term: String,
    val translation: String?,
    val level: Int?,
    val status: Int?,
    val tags: List<String>,
)

data class StatsSnapshot(
    val knownWords: Int?,
    val wordsRead: Int?,
    val lingqs: Int?,
    val hoursListened: Double?,
    val streak: Int?,
    val coins: Int?,
    val chart: List<ChartPoint>,
)

data class ChartPoint(val label: String, val value: Float)

data class CardPage(val items: List<VocabCard>, val total: Int?)

/** 资料库书架：一个标题 + 一组课程。 */
data class ShelfSection(val title: String, val lessons: List<LessonSummary>)

/** Explore 顶部的来源筛选（LingQ / Netflix / TED 等）。 */
data class ExploreProvider(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val imageDarkUrl: String?,
)

/** 课程序号排序方向。 */
enum class LessonSort { Ascending, Descending }

/** LingQ 1–6 级的展示文案。 */
fun lessonLevelLabel(level: Int?): String = when (level) {
    null -> "All levels"
    1 -> "Beginner 1"
    2 -> "Beginner 2"
    3 -> "Intermediate 1"
    4 -> "Intermediate 2"
    5 -> "Advanced 1"
    6 -> "Advanced 2"
    else -> "Level $level"
}

private val LessonNumberRegex = Regex("""\d+""")

private fun sequenceNumber(text: String?): Int? =
    text?.let { LessonNumberRegex.find(it)?.value?.toIntOrNull() }

/** 课程标题/所属系列里的第一个数字，用来给“第 1、2、3 课”排序。 */
fun lessonSequence(lesson: LessonSummary): Int? =
    sequenceNumber(lesson.title) ?: sequenceNumber(lesson.collectionTitle)

/**
 * 带序号的课程按数字正序/倒序排列；没有序号的课程保持标题字母序放在最后。
 */
fun List<LessonSummary>.sortedForDisplay(sort: LessonSort): List<LessonSummary> {
    val numbered = filter { lessonSequence(it) != null }
        .sortedBy { lessonSequence(it) ?: Int.MAX_VALUE }
    val ordered = if (sort == LessonSort.Ascending) numbered else numbered.reversed()
    val unnumbered = filter { lessonSequence(it) == null }
        .sortedBy { it.title.lowercase() }
    return ordered + unnumbered
}

/** 课程/合集详情：标题、封面、简介和全部单集。 */
data class CourseDetail(
    val id: Int,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val level: Int?,
    val lessons: List<LessonSummary>,
)

data class StreakInfo(val current: Int?, val best: Int?)

/** 用户在学的语言（用于左上角头像的语言切换）。 */
data class LearningLanguage(
    val code: String,
    val name: String,
    val knownWords: Int?,
    val lingqCount: Int?,
    val wordsRead: Int?,
    val level: Int?,
    val isActive: Boolean,
)

/** 本地播放队列的一项。课程音频直连 LingQ，不需要自建缓存服务。 */
data class PlaylistEntry(
    val lessonId: Int,
    val title: String,
    val imageUrl: String?,
    val audioUrl: String?,
    val collectionTitle: String?,
    val durationSec: Int?,
    val level: Int?,
)

fun LessonSummary.toPlaylistEntry(): PlaylistEntry = PlaylistEntry(
    lessonId = id,
    title = title,
    imageUrl = imageUrl,
    audioUrl = audioUrl,
    collectionTitle = collectionTitle,
    durationSec = durationSec,
    level = level,
)

/** 词典释义（hints/search）：`text` 是释义，`locale` 是词典语言。 */
data class Meaning(val text: String, val locale: String?)

class ApiException(val code: Int, message: String) : Exception(message)

/** LingQ 路径只接受 `en` / `pt` 这类短代码，把 `en-US`、`zh_Hans` 归一。 */
fun normalizeLanguage(code: String?): String {
    val base = code?.trim()?.lowercase().orEmpty()
        .substringBefore('-')
        .substringBefore('_')
    return base.ifBlank { "en" }
}

fun flagForLanguage(code: String): String = when (normalizeLanguage(code)) {
    "en" -> "🇺🇸"
    "es" -> "🇪🇸"
    "fr" -> "🇫🇷"
    "de" -> "🇩🇪"
    "it" -> "🇮🇹"
    "pt" -> "🇵🇹"
    "ru" -> "🇷🇺"
    "ja" -> "🇯🇵"
    "ko" -> "🇰🇷"
    "zh" -> "🇨🇳"
    "ar" -> "🇸🇦"
    "nl" -> "🇳🇱"
    "sv" -> "🇸🇪"
    "pl" -> "🇵🇱"
    "tr" -> "🇹🇷"
    "he" -> "🇮🇱"
    "fi" -> "🇫🇮"
    "no" -> "🇳🇴"
    "da" -> "🇩🇰"
    "cs" -> "🇨🇿"
    "el" -> "🇬🇷"
    "uk" -> "🇺🇦"
    "vi" -> "🇻🇳"
    "th" -> "🇹🇭"
    "id" -> "🇮🇩"
    "ro" -> "🇷🇴"
    "hu" -> "🇭🇺"
    "ca" -> "🇪🇸"
    else -> "🌐"
}

// ---------------------------------------------------------------------------
// 宽松 JSON 读取：LingQ 的响应模型没有公开 schema，字段名在不同版本里
// 有 camelCase / snake_case 两种写法，这里统一兜底，缺失返回 null。
// ---------------------------------------------------------------------------

internal fun JsonObject.string(vararg keys: String): String? {
    for (key in keys) {
        val value = this[key] ?: continue
        if (value is JsonNull) continue
        if (value is JsonPrimitive) return value.content
    }
    return null
}

internal fun JsonObject.int(vararg keys: String): Int? {
    for (key in keys) {
        val value = this[key] ?: continue
        if (value !is JsonPrimitive || value is JsonNull) continue
        value.content.toIntOrNull()?.let { return it }
        value.content.toDoubleOrNull()?.let { return it.toInt() }
    }
    return null
}

internal fun JsonObject.double(vararg keys: String): Double? {
    for (key in keys) {
        val value = this[key] ?: continue
        if (value !is JsonPrimitive || value is JsonNull) continue
        value.content.toDoubleOrNull()?.let { return it }
    }
    return null
}

internal fun JsonObject.bool(vararg keys: String): Boolean? {
    for (key in keys) {
        val value = this[key] ?: continue
        if (value !is JsonPrimitive || value is JsonNull) continue
        when (value.content.lowercase()) {
            "true", "1" -> return true
            "false", "0" -> return false
        }
    }
    return null
}

internal fun JsonObject.obj(vararg keys: String): JsonObject? {
    for (key in keys) {
        val value = this[key]
        if (value is JsonObject) return value
    }
    return null
}

internal fun JsonObject.array(vararg keys: String): JsonArray? {
    for (key in keys) {
        val value = this[key]
        if (value is JsonArray) return value
    }
    return null
}

/** 展开 DRF 的 `{results:[...]}` / `{data:{...}}` / 单对象包装。 */
internal fun unwrapSingle(root: JsonElement): JsonObject? {
    if (root is JsonObject) {
        root.array("results")?.firstOrNull()?.let { if (it is JsonObject) return it }
        root.obj("data", "profile", "result")?.let { return it }
        return root
    }
    if (root is JsonArray) return root.firstOrNull() as? JsonObject
    return null
}

internal fun JsonElement.collectObjects(
    limit: Int = 400,
    predicate: (JsonObject) -> Boolean,
): List<JsonObject> {
    val out = ArrayList<JsonObject>()
    fun walk(element: JsonElement) {
        if (out.size >= limit) return
        when (element) {
            is JsonObject -> {
                if (predicate(element)) {
                    out += element
                    return
                }
                element.values.forEach(::walk)
            }

            is JsonArray -> element.forEach(::walk)
            else -> Unit
        }
    }
    walk(this)
    return out
}

internal fun JsonObject.looksLikeLesson(): Boolean {
    val hasId = int("id", "lessonId", "lesson_id", "contentId", "content_id", "pk") != null
    val hasTitle = string("title", "name") != null ||
        obj("lessonPreview", "lesson_preview")?.string("title", "name") != null
    val hasMarker = keys.any {
        it in setOf(
            "audioUrl", "audio_url", "imageUrl", "image_url", "newWordsCount", "new_words_count",
            "duration", "collectionTitle", "collection_title", "isCompleted", "is_completed",
            "isTaken", "is_taken", "originalImageUrl", "lessonPreview", "lesson_preview",
            "lessonsCount", "lessons_count", "lessons", "type",
        )
    }
    return hasId && hasTitle && hasMarker
}

/**
 * 资料库里的 item 既可能是单课，也可能是课程/合集（带 lessonPreview / lessonsCount）。
 * 课程用 lessonPreview 的真实 lesson id 打开，避免拿课程 id 去请求 /lessons/ 导致 404。
 */
internal fun JsonObject.toLessonSummary(): LessonSummary? {
    val previewElement = this["lessonPreview"] ?: this["lesson_preview"]
    val previewObject = previewElement as? JsonObject
    val directId = int("contentId", "content_id", "lessonId", "lesson_id", "id", "pk")
        ?: return null
    val title = string("title", "name")?.trim().orEmpty()
        .ifBlank { previewObject?.string("title", "name")?.trim().orEmpty() }
        .ifBlank { return null }

    // lessonPreview 既可能是嵌套对象，也可能只是一个 lesson id。
    val previewId = when (previewElement) {
        is JsonObject -> previewElement.int(
            "contentId", "content_id", "lessonId", "lesson_id", "id", "pk",
        )

        is JsonPrimitive -> previewElement.content.toIntOrNull()
        else -> null
    }
    val lessonsArray = array("lessons")
    val firstLessonElement = lessonsArray?.firstOrNull()
        ?: (this["lessons"] as? JsonObject)?.values?.firstOrNull()
    val firstLessonId = when (firstLessonElement) {
        is JsonObject -> firstLessonElement.int(
            "contentId", "content_id", "lessonId", "lesson_id", "id", "pk",
        )

        is JsonPrimitive -> firstLessonElement.content.toIntOrNull()
        else -> null
    }
    val lessonCount = int("lessonsCount", "lessons_count")
        ?: lessonsArray?.size?.takeIf { it > 0 }
        ?: (this["lessons"] as? JsonObject)?.size?.takeIf { it > 0 }
    val hasPreview = previewElement != null && previewElement !is JsonNull
    // LibraryItemType 的取值来自 APK：content / collection / folder。
    val type = string("type")?.lowercase()
    val isCourse = when (type) {
        "collection", "folder" -> true
        "content" -> false
        else -> hasPreview || lessonCount != null || lessonsArray != null
    }
    val lessonId = if (isCourse) {
        previewId ?: firstLessonId ?: directId
    } else {
        directId
    }
    val stats = previewObject ?: this

    val collection = obj("collection")
    val durationRaw = stats.double("duration", "durationSec", "duration_sec", "audioDuration")
        ?: double("duration")
    // LingQ 大部分接口给秒；极少数字段给毫秒，超过 6 小时就按毫秒处理。
    val duration = durationRaw?.let { if (it > 21_600) (it / 1000.0).toInt() else it.toInt() }
    return LessonSummary(
        id = lessonId,
        title = title,
        imageUrl = string(
            "imageUrl", "image_url", "originalImageUrl", "original_image_url",
            "mediaImageUrl", "media_image_url",
        ) ?: stats.string("imageUrl", "image_url")
            ?: collection?.string("imageUrl", "image_url"),
        audioUrl = stats.string("audioUrl", "audio_url")
            ?: stats.obj("audio")?.string("url", "audioUrl", "audio_url")
            ?: string("audioUrl", "audio_url")
            ?: obj("audio")?.string("url", "audioUrl", "audio_url"),
        durationSec = duration,
        level = stats.int("level") ?: int("level")
            ?: collection?.int("level"),
        newWordsCount = stats.int("newWordsCount", "new_words_count", "newWords", "new_words"),
        collectionTitle = if (isCourse) {
            null
        } else {
            string("collectionTitle", "collection_title")
                ?: collection?.string("title", "name")
        },
        collectionId = if (isCourse) {
            null
        } else {
            int("collectionId", "collection_id")
                ?: collection?.int("id", "collectionId", "collection_id")
        },
        isCompleted = stats.bool("isCompleted", "is_completed") ?: false,
        isTaken = bool("isTaken", "is_taken") ?: stats.bool("isTaken", "is_taken") ?: false,
        isCourse = isCourse,
        lessonCount = lessonCount,
        libraryId = int("id", "pk"),
    )
}

internal fun JsonElement.toLessons(): List<LessonSummary> =
    collectObjects { it.looksLikeLesson() }
        .mapNotNull { it.toLessonSummary() }
        .distinctBy { it.id }

internal fun JsonObject.toProfile(): Profile {
    val languageObject = obj("learnLanguage", "learningLanguage", "activeLanguage", "language")
    val languageArray = array("languages")?.mapNotNull { it as? JsonObject }
    val activeLanguage = languageArray?.firstOrNull { it.bool("isActive", "is_active") == true }
        ?: languageArray?.firstOrNull()
    val source = activeLanguage ?: this
    val first = string("first_name", "firstName").orEmpty()
    val last = string("last_name", "lastName").orEmpty()
    val display = string("displayName", "display_name", "fullName", "full_name", "name")
        ?: listOf(first, last).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null }
        ?: string("username")
        ?: "LingQ"
    return Profile(
        username = string("username", "email") ?: "LingQ",
        displayName = display,
        avatarUrl = string("avatarUrl", "avatar_url", "avatar", "image", "imageUrl", "image_url"),
        knownWords = source.int("knownWords", "known_words", "knownWordsCount", "known_words_count")
            ?: int("knownWords", "known_words", "knownWordsCount"),
        lingqCount = source.int("lingqsCount", "lingqs_count", "lingqCount", "lingq_count")
            ?: int("lingqsCount", "lingqs_count"),
        wordsRead = source.int("wordsRead", "words_read") ?: int("wordsRead", "words_read"),
        hoursListened = source.double("hoursListened", "hours_listened", "hours")
            ?: double("hoursListened", "hours_listened"),
        streak = int("streak", "dayStreak", "day_streak", "dailyStreak")
            ?: source.int("streak", "dayStreak", "day_streak"),
        coins = int("coins", "coinBalance", "coin_balance"),
        languageCode = languageObject?.string("code", "languageCode", "language_code", "isoCode")
            ?: source.string("languageCode", "language_code", "code")
            ?: string("learnLanguageCode", "learn_language_code"),
        languageName = languageObject?.string("name", "title", "languageName")
            ?: source.string("languageName", "language_name"),
        flag = languageObject?.string("flag", "flagUrl", "flag_url", "country"),
    )
}

internal fun String.absoluteUrl(): String? {
    val value = trim()
    if (value.isBlank()) return null
    return when {
        value.startsWith("http://") || value.startsWith("https://") -> value
        value.startsWith("//") -> "https:$value"
        value.startsWith("/") -> "https://www.lingq.com$value"
        else -> "https://www.lingq.com/$value"
    }
}
