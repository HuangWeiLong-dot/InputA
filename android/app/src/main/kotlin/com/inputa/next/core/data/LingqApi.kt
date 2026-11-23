package com.inputa.next.core.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface LoginResult {
    data class Success(val username: String) : LoginResult
    data class Error(val message: String) : LoginResult
}

/**
 * LingQ 官方接口客户端。全部请求直连 `https://www.lingq.com/`，没有自建后端。
 *
 * 逆向文档（lingq-apk/agent-reference/03-api.md）确认请求头为
 * `Authorization: Token <token>`；列表接口是 DRF 分页，正文/卡片等响应模型
 * 没有公开 schema，所以这里全部用宽松解析（缺失字段返回 null，不抛异常）。
 */
@Singleton
class LingqApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
    private val session: SessionStore,
) {

    // -----------------------------------------------------------------------
    // 登录
    // -----------------------------------------------------------------------

    suspend fun login(username: String, password: String): LoginResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("username", username.trim())
            .add("password", password)
            .build()
        val request = Request.Builder()
            .url(BASE_URL + "api/v2/api-token-auth/")
            .header("Accept", "application/json")
            .post(body)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext LoginResult.Error(describeError(response.code, text))
                }
                val token = runCatching {
                    json.parseToJsonElement(text).jsonObject["token"]?.jsonPrimitive?.content
                }.getOrNull()
                if (token.isNullOrBlank()) {
                    LoginResult.Error("The login response did not contain a token")
                } else {
                    session.save(token, username.trim())
                    LoginResult.Success(username.trim())
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LoginResult.Error("Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}")
        }
    }

    /**
     * 用已有的 LingQ Token 登录（Google 账号也适用）。
     *
     * 先打一个需要认证的 `GET api/v3/profiles/` 验证 token；2xx 才保存。
     */
    suspend fun loginWithToken(rawToken: String): LoginResult = withContext(Dispatchers.IO) {
        val token = rawToken.trim().removePrefix("Token ").trim()
        if (token.isBlank()) return@withContext LoginResult.Error("Paste your LingQ token")
        val request = Request.Builder()
            .url(BASE_URL + "api/v3/profiles/")
            .header("Authorization", "Token $token")
            .header("Accept", "application/json")
            .get()
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LoginResult.Error(
                        if (response.code == 401) "Token is invalid or expired" else "Verification failed (HTTP ${response.code})",
                    )
                }
                session.save(token, "LingQ")
                LoginResult.Success("LingQ")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            LoginResult.Error("Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}")
        }
    }

    // -----------------------------------------------------------------------
    // 资料 / 语言
    // -----------------------------------------------------------------------

    suspend fun profile(): Profile? {
        val base = unwrapSingle(getJson("api/v3/profiles/"))?.toProfile() ?: return null
        if (base.knownWords != null && base.lingqCount != null) return base
        val stats = runCatching { languageStats(base.username, base.languageCode) }.getOrNull()
            ?: return base
        return base.copy(
            knownWords = base.knownWords ?: stats.knownWords,
            lingqCount = base.lingqCount ?: stats.lingqs,
            wordsRead = base.wordsRead ?: stats.wordsRead,
            streak = base.streak ?: stats.streak,
        )
    }

    /**
     * `GET api/v2/languages/?username=` 返回用户在学语言及其 knownWords/lingqs。
     * profiles 接口不带这些计数时，用它补首页最重要的数字。
     */
    private suspend fun languageStats(username: String, activeCode: String?): LanguageStats? {
        val root = getJson("api/v2/languages/", mapOf("username" to username))
        val objects = if (root is JsonArray) {
            root.mapNotNull { it as? JsonObject }
        } else {
            root.collectObjects { it.string("code", "languageCode", "language_code") != null }
        }
        val active = objects.firstOrNull {
            activeCode != null && it.string("code", "languageCode", "language_code")
                ?.startsWith(activeCode, ignoreCase = true) == true
        } ?: objects.firstOrNull { it.bool("isActive", "is_active") == true }
            ?: objects.firstOrNull { it.int("knownWords", "known_words") != null }
            ?: return null
        return LanguageStats(
            knownWords = active.int("knownWords", "known_words", "knownWordsCount"),
            lingqs = active.int("lingqsCount", "lingqs_count", "lingqCount"),
            wordsRead = active.int("wordsRead", "words_read"),
            streak = active.int("streak", "dayStreak", "day_streak"),
        )
    }

    private data class LanguageStats(
        val knownWords: Int?,
        val lingqs: Int?,
        val wordsRead: Int?,
        val streak: Int?,
    )

    suspend fun languages(): List<Pair<String, String>> {
        val root = getJson("api/v2/languages/")
        val objects = if (root is JsonArray) {
            root.mapNotNull { it as? JsonObject }
        } else {
            root.collectObjects { it.string("code", "languageCode") != null }
        }
        return objects.mapNotNull { obj ->
            val code = obj.string("code", "languageCode", "language_code") ?: return@mapNotNull null
            val name = obj.string("name", "title", "languageName") ?: code
            code to name
        }.distinctBy { it.first }
    }

    /**
     * 用户在学的语言 + 每门语言的 knownWords/lingqCount。
     * `api/v2/languages/?username=` 在 LingQ 网页端就是语言切换列表。
     */
    suspend fun learningLanguages(username: String? = null): List<LearningLanguage> {
        val root = runCatching {
            getJson("api/v2/languages/", mapOf("username" to username))
        }.getOrElse {
            getJson("api/v2/languages/")
        }
        val objects = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> root.collectObjects {
                it.string("code", "languageCode", "language_code") != null
            }

            else -> emptyList()
        }
        val mapped = objects.mapNotNull { obj ->
            val code = obj.string("code", "languageCode", "language_code")
                ?.let(::normalizeLanguage)
                ?: return@mapNotNull null
            LearningLanguage(
                code = code,
                name = obj.string("name", "title", "languageName", "language_name")
                    ?: code.uppercase(),
                knownWords = obj.int("knownWords", "known_words", "knownWordsCount"),
                lingqCount = obj.int("lingqsCount", "lingqs_count", "lingqCount"),
                wordsRead = obj.int("wordsRead", "words_read"),
                level = obj.int("level", "currentLevel", "current_level"),
                isActive = obj.bool("isActive", "is_active", "active") ?: false,
            )
        }.distinctBy { it.code }
        val learning = mapped.filter { it.knownWords != null || it.level != null || it.isActive }
        return (learning.ifEmpty { mapped }).sortedWith(
            compareByDescending<LearningLanguage> { it.isActive }
                .thenBy { it.name.lowercase() },
        )
    }

    // -----------------------------------------------------------------------
    // 课程列表
    // -----------------------------------------------------------------------

    /** 书架/推荐课程。 */
    suspend fun shelves(language: String, level: Int? = null): List<LessonSummary> {
        val root = getJson(
            "api/v3/$language/shelves/",
            mapOf("suppress" to "true", "level" to level?.toString()),
        )
        return root.toLessons()
    }

    suspend fun searchLessons(
        language: String,
        query: String? = null,
        level: Int? = null,
        sortBy: String? = null,
        type: String? = null,
        shelf: String? = null,
        provider: String? = null,
        page: Int = 1,
        pageSize: Int = 30,
    ): List<LessonSummary> {
        val params = linkedMapOf(
            "page" to page.toString(),
            "page_size" to pageSize.toString(),
            "suppress" to "true",
        )
        if (!query.isNullOrBlank()) params["q"] = query.trim()
        if (level != null) params["level"] = level.toString()
        if (!sortBy.isNullOrBlank()) params["sortBy"] = sortBy
        if (!type.isNullOrBlank()) params["type"] = type
        if (!shelf.isNullOrBlank()) params["shelf"] = shelf
        if (!provider.isNullOrBlank()) params["provider"] = provider
        return getJson("api/v3/$language/search/", params).toLessons()
    }

    /** Explore 顶部来源：LingQ / Netflix / TED 等，`provider` 过滤用它的 id。 */
    suspend fun featuredProviders(language: String): List<ExploreProvider> {
        val featured = runCatching {
            getJson("api/v3/$language/providers/featured/", timeoutMs = 8_000)
        }
        var objects = providerObjects(featured.getOrNull())
        if (objects.isEmpty()) {
            // 有些账号 featured 为空或 404，退回完整来源列表。
            val fallback = runCatching {
                getJson("api/v3/$language/providers/", timeoutMs = 8_000)
            }
            objects = providerObjects(fallback.getOrNull())
            if (featured.isFailure && fallback.isFailure) {
                Log.w(TAG, "providers $language failed: ${featured.exceptionOrNull()?.message}")
            }
        }
        val providers = objects.mapNotNull { obj ->
            val title = obj.string("title", "name", "displayName", "display_name")
                ?.trim()
                .orEmpty()
            val id = obj.string("id", "pk", "providerId", "provider_id", "slug")
                ?: obj.string("url")?.trim()?.trimEnd('/')?.substringAfterLast('/')
                    ?.takeIf { it.isNotBlank() }
            if (title.isBlank() || id.isNullOrBlank()) return@mapNotNull null
            ExploreProvider(
                id = id,
                title = title,
                imageUrl = obj.string("image", "imageUrl", "image_url"),
                imageDarkUrl = obj.string("image_dark", "imageDark", "image_dark_url"),
            )
        }.distinctBy { it.id }
        if (providers.isEmpty()) {
            Log.w(TAG, "providers $language empty: ${featured.getOrNull().toString().take(300)}")
        }
        return providers
    }

    private fun providerObjects(root: JsonElement?): List<JsonObject> = when (root) {
        null -> emptyList()
        is JsonArray -> root.mapNotNull { element ->
            when (element) {
                is JsonObject -> element
                is JsonPrimitive -> buildJsonObject {
                    put("id", element.content)
                    put("title", element.content)
                }

                else -> null
            }
        }

        is JsonObject -> root.array("results", "data", "providers")
            ?.mapNotNull { it as? JsonObject }
            ?: root.collectObjects { obj ->
                obj.string("title", "name") != null ||
                    obj.string("id", "pk", "slug", "url") != null
            }

        else -> emptyList()
    }

    /** 首页「Continue Studying」：优先取最近打开的课程，失败再退回普通列表。 */
    suspend fun continueStudying(language: String): List<LessonSummary> {
        val recent = runCatching {
            searchLessons(language, sortBy = "lastOpenTime", type = "continue", pageSize = 20)
        }.getOrNull().orEmpty()
        if (recent.isNotEmpty()) return recent
        val fallback = runCatching {
            searchLessons(language, sortBy = "lastOpenTime", pageSize = 20)
        }.getOrNull().orEmpty()
        val taken = fallback.filter { it.isTaken || it.isCompleted }
        return taken.ifEmpty { fallback }
    }

    /** 首页推荐：优先取指定等级，等级下没有内容再退回全部推荐。 */
    suspend fun pickedForYou(language: String, level: Int? = null): List<LessonSummary> {
        if (level != null) {
            val leveled = runCatching {
                searchLessons(language, level = level, sortBy = "recommended", pageSize = 40)
            }.getOrNull().orEmpty()
            if (leveled.isNotEmpty()) return leveled
        }
        val recommended = runCatching {
            searchLessons(language, sortBy = "recommended", pageSize = 20)
        }.getOrNull().orEmpty()
        if (recommended.isNotEmpty()) return recommended
        val plain = runCatching { searchLessons(language, pageSize = 20) }
            .getOrNull().orEmpty()
        if (plain.isNotEmpty()) return plain
        return runCatching { shelves(language) }.getOrDefault(emptyList())
    }

    /**
     * 资料库按书架分组。shelves 接口可能返回
     * `{shelves:[{title, lessons:[...]}]}`，也可能直接是课程列表；
     * 两种都兼容，解析不出分组时归到一个 “Lessons” 书架。
     */
    suspend fun shelfSections(language: String, level: Int? = null): List<ShelfSection> {
        val root = getJson(
            "api/v3/$language/shelves/",
            mapOf("suppress" to "true", "level" to level?.toString()),
        )
        val grouped = root.collectObjects { obj ->
            obj.string("title", "name") != null &&
                obj.array("lessons", "items", "contents") != null
        }.mapNotNull { obj ->
            val title = obj.string("title", "name")?.trim().orEmpty()
            val lessons = obj.array("lessons", "items", "contents")?.toLessons().orEmpty()
            if (title.isBlank() || lessons.isEmpty()) null else ShelfSection(title, lessons)
        }.distinctBy { it.title }
        if (grouped.isNotEmpty()) {
            // 同一课可能出现在多个书架里，按 id 全局去重，避免列表里重复出现。
            val seen = HashSet<Int>()
            return grouped.map { section ->
                section.copy(lessons = section.lessons.filter { seen.add(it.id) })
            }.filter { it.lessons.isNotEmpty() }.take(10)
        }
        val flat = root.toLessons()
        return if (flat.isEmpty()) emptyList() else listOf(ShelfSection("Lessons", flat))
    }

    // -----------------------------------------------------------------------
    // 课程操作：take / 进度 / 完成 / 收藏 / 归档
    // -----------------------------------------------------------------------

    /** 打开课程时调用，服务端会把课程计入 Continue Studying。 */
    suspend fun takeLesson(language: String, lessonId: Int): Boolean =
        runCatching {
            sendJson("api/v2/$language/lesson-stats/$lessonId/take/", "POST", "{}")
        }.isSuccess

    /** 阅读进度回写；RequestLessonUpdateStats 字段来自 APK serializer。 */
    suspend fun updateLessonStats(
        language: String,
        lessonId: Int,
        readTimes: Int = 1,
        listenDuration: Int = 0,
    ): Boolean {
        val body = buildJsonObject {
            put("readTimes", readTimes)
            put("listenTimes", 1)
            put("listenDuration", listenDuration)
            put("automatic", true)
            put("creation_date", nowIso())
        }
        return runCatching {
            sendJson("api/v2/$language/lesson-stats/$lessonId/", "PATCH", body.toString())
        }.isSuccess
    }

    suspend fun completeLesson(language: String, lessonId: Int, markKnown: Boolean = false): Boolean {
        val body = buildJsonObject {
            put("creation_date", nowIso())
            put("markKnown", markKnown)
        }
        return runCatching {
            sendJson("api/v3/$language/lessons/$lessonId/complete/", "POST", body.toString())
        }.isSuccess
    }

    suspend fun bookmarkState(language: String, lessonId: Int): Boolean {
        val root = runCatching {
            getJson("api/v3/$language/lessons/$lessonId/bookmark/")
        }.getOrNull() ?: return false
        return when (root) {
            is JsonPrimitive -> root.content.equals("true", ignoreCase = true)
            is JsonObject -> root.bool("bookmark", "isBookmarked", "is_bookmarked", "value") ?: false
            else -> false
        }
    }

    suspend fun setBookmark(language: String, lessonId: Int, bookmarked: Boolean): Boolean {
        val body = buildJsonObject { put("bookmark", bookmarked) }
        return runCatching {
            sendJson("api/v3/$language/lessons/$lessonId/bookmark/", "POST", body.toString())
        }.isSuccess
    }

    suspend fun setArchived(language: String, lessonId: Int, archived: Boolean): Boolean {
        val action = if (archived) "archive" else "unarchive"
        return runCatching {
            sendJson("api/v3/$language/lessons/$lessonId/$action/", "POST", "{}")
        }.isSuccess
    }

    suspend fun streak(language: String): StreakInfo {
        val root = runCatching { getJson("api/v2/$language/streak/") }.getOrNull()
        val obj = root?.let { unwrapSingle(it) }
        return StreakInfo(
            current = obj?.int("streak", "currentStreak", "current_streak", "days"),
            best = obj?.int("best", "bestStreak", "best_streak", "longest", "max"),
        )
    }

    // -----------------------------------------------------------------------
    // 课程详情 / 正文
    // -----------------------------------------------------------------------

    suspend fun lessonDetail(
        language: String,
        lessonId: Int,
        fallback: LessonSummary,
    ): LessonDetail {
        fetchLessonDetail(language, lessonId, fallback)?.let { return it }
        // 资料库 item 可能是课程/合集：把 id 当 collection 再解析一次真实 lesson id。
        val collectionId = fallback.libraryId ?: lessonId
        val resolved = resolveCourseLesson(language, collectionId)
        if (resolved != null && resolved.id != lessonId) {
            fetchLessonDetail(language, resolved.id, resolved)?.let { return it }
            return LessonDetail(resolved, null, null, null, null, null)
        }
        return LessonDetail(fallback, null, null, null, null, null)
    }

    private suspend fun fetchLessonDetail(
        language: String,
        lessonId: Int,
        fallback: LessonSummary,
    ): LessonDetail? {
        val lessonRoot = runCatching { getJson("api/v3/$language/lessons/$lessonId/") }.getOrNull()
        val lessonObject = lessonRoot?.let { unwrapSingle(it) }
        val infoRoot = runCatching {
            getJson("api/v3/$language/lessons/$lessonId/info/")
        }.getOrNull()
        val info = infoRoot?.let { unwrapSingle(it) }
        val text = runCatching { lessonText(language, lessonId) }.getOrNull()
        if (lessonObject == null && info == null && text.isNullOrBlank()) return null
        val summary = lessonObject?.toLessonSummary() ?: fallback
        return LessonDetail(
            lesson = summary,
            description = lessonObject?.string("description") ?: info?.string("description"),
            text = text,
            totalWords = info?.int("totalWords", "total_words", "wordCount", "word_count")
                ?: lessonObject?.int("wordCount", "word_count"),
            uniqueWords = info?.int("uniqueWords", "unique_words", "uniqueWordCount"),
            newWords = info?.int("newWordsCount", "new_words_count", "newWords", "new_words")
                ?: summary.newWordsCount,
        )
    }

    /** 把课程/合集 id 解析成第一门真实单课。 */
    suspend fun resolveCourseLesson(language: String, collectionId: Int): LessonSummary? {
        val root = runCatching {
            getJson("api/v3/$language/collections/$collectionId/")
        }.getOrNull() ?: return null
        val obj = unwrapSingle(root) ?: return null
        val lessonsElement = obj["lessons"]
        val lessons = when (lessonsElement) {
            is JsonArray -> lessonsElement.toLessons()
            is JsonObject -> JsonArray(lessonsElement.values.toList()).toLessons()
            else -> emptyList()
        }
        if (lessons.isNotEmpty()) return lessons.first()
        val primitiveId = (lessonsElement as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonPrimitive }
            ?.content
            ?.toIntOrNull()
        if (primitiveId != null) {
            return LessonSummary(
                id = primitiveId,
                title = obj.string("title", "name") ?: "Lesson",
                imageUrl = obj.string("imageUrl", "image_url"),
                audioUrl = null,
                durationSec = null,
                level = obj.int("level"),
                newWordsCount = null,
                collectionTitle = obj.string("title", "name"),
                collectionId = collectionId,
                isCompleted = false,
                isTaken = false,
                libraryId = collectionId,
            )
        }
        return obj.obj("lessonPreview", "lesson_preview")?.toLessonSummary()
    }

    /** 课程/合集详情 + 全部单集。 */
    suspend fun courseDetail(language: String, course: LessonSummary): CourseDetail {
        val collectionId = course.libraryId ?: course.id
        val root = runCatching {
            getJson("api/v3/$language/collections/$collectionId/")
        }.getOrNull()
        val obj = root?.let { unwrapSingle(it) }
        var lessons = obj.lessonsFromCollection()
        if (lessons.isEmpty()) {
            lessons = runCatching {
                searchCollectionLessons(language, collectionId)
            }.getOrDefault(emptyList())
        }
        if (lessons.isEmpty()) {
            obj?.obj("lessonPreview", "lesson_preview")
                ?.toLessonSummary()
                ?.let { lessons = listOf(it) }
        }
        if (lessons.isEmpty()) {
            obj?.int("lessonPreview", "lesson_preview")?.let { previewId ->
                lessons = listOf(
                    course.copy(id = previewId, isCourse = false, libraryId = collectionId),
                )
            }
        }
        return CourseDetail(
            id = collectionId,
            title = obj?.string("title", "name") ?: course.title,
            description = obj?.string("description"),
            imageUrl = obj?.string("imageUrl", "image_url") ?: course.imageUrl,
            level = obj?.int("level") ?: course.level,
            lessons = lessons,
        )
    }

    private fun JsonObject?.lessonsFromCollection(): List<LessonSummary> {
        val element = this?.get("lessons") ?: return emptyList()
        return when (element) {
            is JsonArray -> element.toLessons()
            is JsonObject -> JsonArray(element.values.toList()).toLessons()
            else -> emptyList()
        }
    }

    private suspend fun searchCollectionLessons(
        language: String,
        collectionId: Int,
    ): List<LessonSummary> {
        val params = linkedMapOf(
            "collection" to collectionId.toString(),
            "page" to "1",
            "page_size" to "100",
            "suppress" to "true",
        )
        return getJson("api/v3/$language/search/", params).toLessons()
    }

    private suspend fun lessonText(language: String, lessonId: Int): String? {
        val root = getJson("api/v3/$language/lessons/$lessonId/text/")
        return extractText(root)
    }

    /** 课程音频直链：部分列表接口不带 audioUrl，打开课程时按 id 再读一次。 */
    suspend fun lessonAudioUrl(language: String, lessonId: Int): String? {
        val root = runCatching { getJson("api/v3/$language/lessons/$lessonId/") }.getOrNull()
            ?: return null
        val obj = unwrapSingle(root) ?: return null
        return obj.string("audioUrl", "audio_url")
            ?: obj.obj("audio")?.string("url", "audioUrl", "audio_url")
    }

    suspend fun sentences(language: String, lessonId: Int): List<ReaderSentence> {
        val sentencesResult = runCatching {
            getJson("api/v3/$language/lessons/$lessonId/sentences/")
        }
        val parsed = parseSentences(sentencesResult.getOrNull(), language)
        if (parsed.isNotEmpty()) return parsed
        val textResult = runCatching { lessonText(language, lessonId) }
        val split = splitIntoSentences(textResult.getOrNull().orEmpty(), language)
        if (split.isNotEmpty()) return split
        // 两种来源都拿不到正文时，优先把真实 HTTP 错误抛给 UI，而不是伪装成“空课程”。
        (sentencesResult.exceptionOrNull() ?: textResult.exceptionOrNull())?.let { throw it }
        return emptyList()
    }

    // -----------------------------------------------------------------------
    // 词库 / 统计
    // -----------------------------------------------------------------------

    suspend fun cards(
        language: String,
        status: String? = null,
        query: String? = null,
        pageSize: Int = 100,
        srsDue: Boolean = false,
        sort: String? = null,
    ): CardPage {
        val params = linkedMapOf("page_size" to pageSize.toString())
        if (!status.isNullOrBlank()) params["status"] = status
        if (!query.isNullOrBlank()) params["search"] = query.trim()
        if (srsDue) params["srs_due"] = "true"
        if (!sort.isNullOrBlank()) params["sort"] = sort
        return parseCards(getJson("api/v3/$language/cards/", params))
    }

    /** LingQs of the Day：同一个 cards 接口，带 lotd_date。 */
    suspend fun lingqsOfTheDay(language: String): List<VocabCard> {
        val today = java.time.LocalDate.now().toString()
        val root = runCatching {
            getJson(
                "api/v3/$language/cards/",
                mapOf("lotd_date" to today, "page_size" to "12"),
            )
        }.getOrNull() ?: return emptyList()
        return parseCards(root).items
    }

    private fun parseCards(root: JsonElement): CardPage {
        val objects = root.collectObjects { obj ->
            obj.string("term") != null && obj.int("pk", "id") != null
        }
        val items = objects.mapNotNull { obj ->
            val term = obj.string("term")?.trim().orEmpty()
            if (term.isBlank()) return@mapNotNull null
            VocabCard(
                id = obj.int("pk", "id") ?: term.hashCode(),
                term = term,
                translation = obj.savedMeaning(),
                level = obj.int("importance", "level"),
                status = obj.int("status", "extended_status"),
                tags = obj.array("tags", "gTags")?.mapNotNull {
                    (it as? JsonPrimitive)?.content?.takeIf { tag -> tag.isNotBlank() }
                }.orEmpty(),
            )
        }.distinctBy { it.id }
        val total = (root as? JsonObject)?.int("count")
            ?: (root as? JsonObject)?.obj("data")?.int("count")
        return CardPage(items, total)
    }

    /** 卡片上保存的释义：优先 notes，其次 hints 里的第一条。 */
    private fun JsonObject.savedMeaning(): String? {
        string("notes", "fragment", "translation", "hint", "meaning")?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val hints = this["hints"] ?: return null
        if (hints is JsonArray) {
            hints.forEach { element ->
                val text = when (element) {
                    is JsonPrimitive -> element.content
                    is JsonObject -> element.string("text", "translation", "hint", "meaning", "term")
                    else -> null
                }
                if (!text.isNullOrBlank()) return text
            }
        }
        if (hints is JsonPrimitive) return hints.content.takeIf { it.isNotBlank() }
        return null
    }

    // -----------------------------------------------------------------------
    // 词状态写回（真实接口）
    //
    // RequestWordsUpdate 的字段来自 APK serializer：words / content_id /
    // creation_date；卡片字段为 term / status / extended_status / content。
    // 已知词与忽略词各自走独立端点，不是卡片。
    // -----------------------------------------------------------------------

    suspend fun markKnown(language: String, term: String, lessonId: Int?): Boolean =
        postWordList(term, lessonId, "api/v2/$language/known-words/")

    suspend fun markIgnored(language: String, term: String, lessonId: Int?): Boolean =
        postWordList(term, lessonId, "api/v2/$language/ignored-words/")

    private suspend fun postWordList(
        term: String,
        lessonId: Int?,
        path: String,
    ): Boolean {
        val body = buildJsonObject {
            putJsonArray("words") { add(term) }
            if (lessonId != null) put("content_id", lessonId)
        }
        return runCatching { sendJson(path, "POST", body.toString()) }.isSuccess
    }

    /**
     * 保存/更新一个 LingQ。先按 term 查已有卡片：有则 PATCH，没则 POST。
     * status 使用 CardStatus：1 Recognized、2 Familiar、3 Learned、4 兼容旧端。
     */
    suspend fun saveCard(language: String, term: String, status: Int, lessonId: Int?): Boolean {
        val existing = runCatching {
            cards(language = language, query = term, pageSize = 20)
                .items.firstOrNull { it.term.equals(term, ignoreCase = true) }
        }.getOrNull()
        val (path, body, method) = if (existing != null) {
            Triple(
                "api/v2/$language/cards/${existing.id}/",
                buildJsonObject {
                    put("status", status)
                    put("extended_status", status)
                }.toString(),
                "PATCH",
            )
        } else {
            Triple(
                "api/v2/$language/cards/",
                buildJsonObject {
                    put("term", term)
                    put("status", status)
                    if (lessonId != null) put("content", lessonId)
                }.toString(),
                "POST",
            )
        }
        return runCatching { sendJson(path, method, body) }.isSuccess
    }

    /** 卡片状态修改：1 Recognized / 2 Familiar / 3 Learned / 4 兼容旧端 / 5 Known。 */
    suspend fun updateCardStatus(language: String, cardId: Int, status: Int): Boolean {
        val body = buildJsonObject {
            put("status", status)
            put("extended_status", status)
        }
        return runCatching {
            sendJson("api/v2/$language/cards/$cardId/", "PATCH", body.toString())
        }.isSuccess
    }

    suspend fun deleteCard(language: String, cardId: Int): Boolean =
        runCatching {
            sendJson("api/v2/$language/cards/$cardId/", "DELETE", "{}")
        }.isSuccess

    suspend fun reviewCard(language: String, cardId: Int): Boolean =
        runCatching {
            sendJson("api/v3/$language/cards/$cardId/review/", "POST", "{}")
        }.isSuccess

    /** 导入 URL / 纯文本课程。RequestLessonImport 字段来自 APK serializer。 */
    suspend fun importLesson(
        language: String,
        title: String,
        text: String?,
        url: String?,
        level: Int?,
        collectionTitle: String?,
    ): LessonSummary? {
        val body = buildJsonObject {
            put("title", title)
            if (!text.isNullOrBlank()) put("text", text)
            if (!url.isNullOrBlank()) put("url", url)
            if (level != null) put("level", level)
            if (!collectionTitle.isNullOrBlank()) put("collection_title", collectionTitle)
            put("save", true)
            put("status", 2)
            put("source", "InputA")
        }
        val root = runCatching {
            sendJson("api/v3/$language/lessons/import/", "POST", body.toString())
        }.getOrNull() ?: return null
        return unwrapSingle(root)?.toLessonSummary()
    }

    private fun nowIso(): String = java.time.Instant.now().toString()

    // -----------------------------------------------------------------------
    // Lynx 聊天（SSE）
    // -----------------------------------------------------------------------

    /**
     * create-stream / respond-stream 返回 SSE。服务端增量字段没有公开文档，
     * 这里兼容 `content` / `text` / `delta` / OpenAI choices 几种常见形状。
     */
    suspend fun chatStream(
        language: String,
        message: String,
        chatId: Int? = null,
        onDelta: (String) -> Unit,
    ): Int? = withContext(Dispatchers.IO) {
        val token = session.token() ?: throw ApiException(401, "Your session expired. Sign in again.")
        val path = if (chatId == null) {
            "api/v3/$language/chat/create-stream/"
        } else {
            "api/v3/$language/chat/$chatId/respond-stream/"
        }
        val body = buildJsonObject {
            put("message", message)
            put("content", message)
            put("mode", "chat")
            put("accept_alternatives", false)
        }
        val url = (BASE_URL + path).toHttpUrlOrNull() ?: throw ApiException(400, "Invalid API address")
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $token")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json; charset=utf-8")
            .header("User-Agent", "InputA/0.2 Android")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val text = response.body.string()
                    throw ApiException(response.code, describeBodyError(response.code, text))
                }
                var resolvedChatId = chatId
                val contentType = response.body.contentType()?.toString().orEmpty()
                if (!contentType.contains("event-stream")) {
                    // 有些账号/模型会直接返回一段 JSON，而不是 SSE。
                    val text = response.body.string()
                    val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                    if (obj != null) {
                        resolvedChatId = resolvedChatId
                            ?: obj.int("chat_id", "chatId", "id")
                            ?: obj.obj("chat")?.int("id", "chat_id")
                        extractChatDelta(obj)?.let(onDelta)
                    }
                    return@use resolvedChatId
                }
                val source = response.body.source()
                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty() || payload == "[DONE]") continue
                    val element = runCatching { json.parseToJsonElement(payload) }.getOrNull()
                        ?: continue
                    val obj = element as? JsonObject ?: continue
                    if (resolvedChatId == null) {
                        resolvedChatId = obj.int("chat_id", "chatId", "id")
                            ?: obj.obj("chat")?.int("id", "chat_id")
                    }
                    extractChatDelta(obj)?.let(onDelta)
                }
                resolvedChatId
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (api: ApiException) {
            throw api
        } catch (error: Exception) {
            throw ApiException(0, "Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}")
        }
    }

    private fun extractChatDelta(obj: JsonObject): String? {
        obj.string("content", "text", "delta", "token", "response")?.let { return it }
        obj.obj("delta")?.string("content", "text")?.let { return it }
        obj.obj("message")?.string("content", "text")?.let { return it }
        val choice = obj.array("choices")?.firstOrNull() as? JsonObject ?: return null
        choice.obj("delta")?.string("content", "text")?.let { return it }
        choice.obj("message")?.string("content", "text")?.let { return it }
        return choice.string("text")
    }

    private suspend fun sendJson(path: String, method: String, bodyJson: String): JsonElement =
        withContext(Dispatchers.IO) {
            val token = session.token() ?: throw ApiException(401, "Your session expired. Sign in again.")
            val url = (BASE_URL + path).toHttpUrlOrNull()
                ?: throw ApiException(400, "Invalid API address")
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Token $token")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("User-Agent", "InputA/0.2 Android")
                .method(method, bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw ApiException(response.code, describeBodyError(response.code, text))
                    }
                    if (text.isBlank()) return@withContext JsonNull
                    runCatching { json.parseToJsonElement(text) }.getOrDefault(JsonNull)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (api: ApiException) {
                throw api
            } catch (error: Exception) {
                throw ApiException(0, "Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}")
            }
        }

    /** 词卡上的译文 / 词典释义。hints/search 是 APK 里词弹窗用的同一接口。 */
    suspend fun meanings(language: String, term: String): List<Meaning> {
        val root = runCatching {
            getJson(
                "api/v2/$language/hints/search/",
                mapOf("term" to term, "all" to "true", "locale" to "en"),
            )
        }.getOrElse {
            getJson("api/v2/$language/hints/search/", mapOf("term" to term))
        }
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("results", "data", "meanings") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        val parsed = array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val text = obj.string("text", "translation", "meaning")?.trim().orEmpty()
            if (text.isBlank()) null else Meaning(
                text = text,
                locale = obj.string("locale", "detected_locale", "dictionary_locale"),
            )
        }
        if (parsed.isNotEmpty()) return parsed.distinctBy { it.text }.take(6)
        val single = (root as? JsonObject)?.string("text", "translation", "meaning")?.trim()
        return if (single.isNullOrBlank()) {
            emptyList()
        } else {
            listOf(Meaning(single, (root as JsonObject).string("locale")))
        }
    }

    suspend fun stats(language: String, metric: String = "reading"): StatsSnapshot {
        val root = runCatching { getJson("api/v3/$language/progress/stats/", mapOf("period" to "week")) }
            .getOrNull()
        val source = root?.let { unwrapSingle(it) }
        val chartRoot = runCatching {
            getJson("api/v3/$language/progress/chart_data/", mapOf("period" to "week", "metric" to metric))
        }.getOrNull()
        val streakInfo = runCatching { streak(language) }.getOrNull()
        return StatsSnapshot(
            knownWords = source?.int("knownWords", "known_words", "knownWordsCount"),
            wordsRead = source?.int("wordsRead", "words_read", "words"),
            lingqs = source?.int("lingqsCreated", "lingqs_created", "lingqs"),
            hoursListened = source?.double("hoursListened", "hours_listened", "hours"),
            streak = source?.int("streak", "dayStreak", "day_streak") ?: streakInfo?.current,
            coins = source?.int("coins", "coinsEarned", "coins_earned"),
            chart = parseChart(chartRoot),
        )
    }

    private fun parseChart(root: JsonElement?): List<ChartPoint> {
        if (root == null) return emptyList()
        val objects = root.collectObjects(limit = 60) { obj ->
            obj.double("value", "y", "words", "count") != null && obj.string("date", "day", "label") != null
        }
        return objects.mapNotNull { obj ->
            val value = obj.double("value", "y", "words", "count") ?: return@mapNotNull null
            val label = obj.string("date", "day", "label") ?: return@mapNotNull null
            ChartPoint(label = label.takeLast(5), value = value.toFloat())
        }.distinctBy { it.label }
    }

    // -----------------------------------------------------------------------
    // 正文解析
    // -----------------------------------------------------------------------

    private fun extractText(root: JsonElement): String? {
        if (root is JsonPrimitive && root.isString) return cleanText(root.content).ifBlank { null }
        if (root is JsonArray) {
            return root.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.content.takeIf { item.isString && it.isNotBlank() }
                    is JsonObject -> item.textValue("text", "content", "sentence")
                    else -> null
                }
            }.joinToString("\n\n").let(::cleanText).ifBlank { null }
        }
        if (root is JsonObject) {
            // 有些正文接口把 text 继续包成数组/对象
            root["text"]?.let { nested ->
                if (nested is JsonArray || nested is JsonObject) {
                    extractText(nested)?.let { return it }
                }
            }
            root.textValue("text", "content")?.let { return cleanText(it).ifBlank { null } }
            val nested = root.array("results", "data", "sentences")
            if (nested != null) return extractText(nested)
            val longest = root.collectObjects { it.textValue("text", "content") != null }
                .mapNotNull { it.textValue("text", "content") }
                .maxByOrNull { it.length }
            return longest?.let { cleanText(it).ifBlank { null } }
        }
        return null
    }

    /**
     * 正文只认 JSON 字符串。像 `{"text": 1}` 这种数字/布尔字段是统计值，
     * 不能当成正文，否则整课会被算成 1 个词。
     */
    private fun JsonObject.textValue(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]
            if (value is JsonPrimitive && value.isString) {
                val content = value.content
                if (content.isNotBlank()) return content
            }
        }
        return null
    }

    /** 去掉课程正文里偶尔出现的 HTML 标签/实体，避免把 <p> 当成单词。 */
    private fun cleanText(raw: String): String {
        var text = raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]+>"), "")
        text = text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace('\u00A0', ' ')
        return text.trim()
    }

    private fun findSentenceArray(root: JsonElement): JsonArray? {
        val direct = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("results", "sentences", "data")
            else -> null
        }
        if (direct != null) return direct

        // 兼容 {"0": {...}, "1": {...}} 这种以 index 为 key 的句子对象
        val objectValues = (root as? JsonObject)?.values
            ?.filterIsInstance<JsonObject>()
            ?.takeIf { values ->
                values.isNotEmpty() && values.all {
                    it.string("text", "sentence", "content") != null
                }
            }
        if (objectValues != null) return JsonArray(objectValues)

        val candidates = ArrayList<JsonArray>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> element.values.forEach(::walk)
                is JsonArray -> {
                    if (element.any { item ->
                            (item as? JsonObject)?.string("text", "sentence", "content") != null
                        }
                    ) {
                        candidates += element
                    }
                    element.forEach(::walk)
                }

                else -> Unit
            }
        }
        walk(root)
        return candidates.maxByOrNull { it.size }
    }

    private fun parseSentences(root: JsonElement?, language: String): List<ReaderSentence> {
        if (root == null) return emptyList()
        val array = findSentenceArray(root) ?: return emptyList()
        return array.mapIndexedNotNull { position, element ->
            if (element is JsonPrimitive && element.isString) {
                val text = cleanText(element.content)
                if (text.isBlank()) return@mapIndexedNotNull null
                return@mapIndexedNotNull ReaderSentence(
                    index = position,
                    text = text,
                    tokens = tokenizeWords(text, language),
                )
            }
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val apiTokens = obj.parseApiTokens()
            val raw = obj.string("text", "sentence", "content")?.let(::cleanText)
            val text = raw?.takeIf { it.isNotBlank() } ?: assembleText(apiTokens)
            if (text.isBlank()) return@mapIndexedNotNull null
            ReaderSentence(
                index = obj.int("index", "sentenceIndex", "sentence_index") ?: position,
                text = text,
                tokens = buildWordTokens(text, apiTokens, language),
                paragraphStart = obj.bool("startParagraph", "start_paragraph", "isParagraphStart") ?: false,
            )
        }
    }

    private data class ApiToken(
        val text: String,
        val punct: String?,
        val isKnown: Boolean,
        val isUnknown: Boolean,
        val isIgnored: Boolean,
        val wordId: Int?,
        val translation: String?,
        val transliteration: String?,
        val level: Int?,
    )

    /** `<tokens>` 或 `<words>` 可能是数组，也可能是 index→token 的对象。 */
    private fun JsonObject.parseApiTokens(): List<ApiToken> {
        val element = this["tokens"] ?: this["words"] ?: this["lessonWords"] ?: return emptyList()
        val objects = when (element) {
            is JsonArray -> element.mapNotNull { it as? JsonObject }
            is JsonObject -> element.values.mapNotNull { it as? JsonObject }
            else -> emptyList()
        }
        return objects.mapNotNull { obj ->
            val text = obj.string("text", "word", "term") ?: return@mapNotNull null
            ApiToken(
                text = text,
                punct = obj.string("punct", "punctuation"),
                isKnown = obj.bool("isKnown", "is_known") ?: false,
                isUnknown = obj.bool("isUnknown", "is_unknown") ?: false,
                isIgnored = obj.bool("isIgnored", "is_ignored") ?: false,
                wordId = obj.int("wordId", "word_id"),
                translation = obj.string("translation", "hint", "meaning"),
                transliteration = obj.string("transliteration"),
                // 不同版本给 status / level；只接受 1–4，0 表示新词。
                level = obj.int("level", "status", "wordStatus", "word_status", "extendedStatus", "extended_status")
                    ?.takeIf { it in 1..4 },
            )
        }
    }

    /**
     * 服务端 token 只用来定位“哪些字是词、词的状态是什么”；
     * 展示文本始终以 sentence.text 为准，避免 text+punct+whitespace 重复拼接。
     */
    private fun buildWordTokens(text: String, api: List<ApiToken>, language: String): List<ReaderToken> {
        val aligned = alignServerTokens(text, api)
        return aligned.ifEmpty { tokenizeWords(text, language) }
    }

    private fun alignServerTokens(text: String, api: List<ApiToken>): List<ReaderToken> {
        if (api.isEmpty()) return emptyList()
        val haystack = text.lowercase().replace('’', '\'')
        var cursor = 0
        val result = ArrayList<ReaderToken>(api.size)
        for (token in api) {
            val word = normalizeWord(token.text)
            if (word.isEmpty()) continue
            val needle = word.lowercase().replace('’', '\'')
            var index = haystack.indexOf(needle, cursor)
            if (index < 0) index = haystack.indexOf(needle)
            if (index < cursor || index < 0) continue
            val end = index + word.length
            if (end > text.length) continue
            result += ReaderToken(
                text = text.substring(index, end),
                start = index,
                end = end,
                isKnown = token.isKnown,
                isUnknown = token.isUnknown,
                isIgnored = token.isIgnored,
                wordId = token.wordId,
                translation = token.translation,
                transliteration = token.transliteration,
                level = token.level,
            )
            cursor = end
        }
        return result
    }

    /** 去掉 token 首尾的标点/空格，保留词内部的连字符、撇号和组合记号（泰文/天城文等）。 */
    private fun normalizeWord(raw: String): String {
        var start = 0
        var end = raw.length
        while (start < end && !isWordChar(raw[start])) start++
        while (end > start && !isWordChar(raw[end - 1])) end--
        return if (start >= end) "" else raw.substring(start, end)
    }

    private fun isWordChar(char: Char): Boolean {
        if (char.isLetterOrDigit()) return true
        return when (char.category) {
            CharCategory.NON_SPACING_MARK,
            CharCategory.COMBINING_SPACING_MARK,
            CharCategory.ENCLOSING_MARK,
            -> true

            else -> false
        }
    }

    private val wordRegex = Regex("""[\p{L}\p{N}]+(?:['’\-][\p{L}\p{N}]+)*""")

    private val dictionaryLanguages = setOf("zh", "ja", "th", "ko", "lo", "km", "my", "yue")

    /**
     * 与服务端 token 同一套切词规则，供**本地**词量统计复用（不依赖网络）。
     * 没有空格的语言（中日泰等）走 ICU 词典分词；其余拉丁/西里尔/阿拉伯等按词边界切。
     */
    fun tokenizeWords(text: String, language: String): List<ReaderToken> =
        if (normalizeLanguage(language) in dictionaryLanguages) {
            dictionaryTokens(text, language)
        } else {
            wordRegexTokens(text)
        }

    /** ICU 分词：日文假名/汉字、泰文、中文都按真实词切分，而不是一个字一个词。 */
    private fun dictionaryTokens(text: String, language: String): List<ReaderToken> {
        val iterator = android.icu.text.BreakIterator.getWordInstance(
            java.util.Locale.forLanguageTag(normalizeLanguage(language)),
        )
        iterator.setText(text)
        val result = ArrayList<ReaderToken>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != android.icu.text.BreakIterator.DONE) {
            var wordStart = start
            var wordEnd = end
            while (wordStart < wordEnd && text[wordStart].isWhitespace()) wordStart++
            while (wordEnd > wordStart && text[wordEnd - 1].isWhitespace()) wordEnd--
            val segment = text.substring(wordStart, wordEnd)
            if (segment.isNotEmpty() && segment.any { it.isLetterOrDigit() }) {
                result += ReaderToken(
                    text = segment,
                    start = wordStart,
                    end = wordEnd,
                    isKnown = false,
                    isUnknown = false,
                    isIgnored = false,
                    wordId = null,
                    translation = null,
                    transliteration = null,
                )
            }
            start = end
            end = iterator.next()
        }
        return result
    }

    /** 没有服务端分词时的兜底：拉丁按词。 */
    private fun wordRegexTokens(text: String): List<ReaderToken> {
        val result = ArrayList<ReaderToken>()
        wordRegex.findAll(text).forEach { match ->
            val value = match.value
            result += ReaderToken(
                text = value,
                start = match.range.first,
                end = match.range.last + 1,
                isKnown = false,
                isUnknown = false,
                isIgnored = false,
                wordId = null,
                translation = null,
                transliteration = null,
            )
        }
        return result
    }

    private fun assembleText(tokens: List<ApiToken>): String = buildString {
        tokens.forEachIndexed { index, token ->
            val word = token.text.trim()
            if (word.isEmpty()) return@forEachIndexed
            append(word)
            val punct = token.punct.orEmpty()
            if (punct.isNotEmpty() && !word.endsWith(punct)) append(punct)
            val next = tokens.getOrNull(index + 1)?.text?.trim().orEmpty()
            if (next.isNotEmpty() && needsSpace(word + punct, next)) append(' ')
        }
    }

    private fun needsSpace(current: String, next: String): Boolean {
        val closing = ",.;:!?%)]}…，。；：！？、）》」』”’"
        val opening = "([{（《「『“‘"
        val nextChar = next.firstOrNull()
        val currentChar = current.lastOrNull()
        if (nextChar != null && nextChar in closing) return false
        if (currentChar != null && currentChar in opening) return false
        if (isCjk(currentChar) || isCjk(nextChar)) return false
        if (isNoSpaceScript(currentChar) || isNoSpaceScript(nextChar)) return false
        return true
    }

    /** 泰文、老挝文、高棉文、缅文这类词间不空格的文字。 */
    private fun isNoSpaceScript(char: Char?): Boolean {
        if (char == null) return false
        val code = char.code
        return code in 0x0E00..0x0E7F ||
            code in 0x0E80..0x0EFF ||
            code in 0x1000..0x109F ||
            code in 0x1780..0x17FF
    }

    private fun isCjk(char: Char?): Boolean {
        if (char == null) return false
        val code = char.code
        return code in 0x3040..0x30FF ||
            code in 0x3400..0x4DBF ||
            code in 0x4E00..0x9FFF ||
            code in 0xF900..0xFAFF ||
            code in 0xAC00..0xD7AF
    }

    private fun splitIntoSentences(text: String, language: String): List<ReaderSentence> {
        if (text.isBlank()) return emptyList()
        val result = ArrayList<ReaderSentence>()
        var index = 0
        val paragraphs = text.split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val sentenceRegex = Regex("""[^.!?。！？…]+[.!?。！？…]*""")
        for (paragraph in paragraphs) {
            val sentences = sentenceRegex.findAll(paragraph)
                .map { it.value.trim() }
                .filter { it.isNotBlank() }
                .toList()
            sentences.forEachIndexed { position, sentence ->
                result += ReaderSentence(
                    index = index++,
                    text = sentence,
                    tokens = tokenizeWords(sentence, language),
                    paragraphStart = position == 0,
                )
            }
        }
        return result
    }

    // -----------------------------------------------------------------------
    // TTS 音频
    // -----------------------------------------------------------------------

    /**
     * 单词/短语发音。LingQ 的 TTS 返回 `ResultTtsUtterance`：生成完成时
     * `generated.audio` 是音频直链；还没生成时只有 `requested`，返回 null
     * 交给系统 TTS，避免点词没有声音。
     *
     * 请求必须带该语言真实可用的 `app_name` + `voice`；写死 `android` 会被
     * 服务端拒绝（`["TTS is not available."]`），所以先读 supported-voices。
     */
    suspend fun ttsAudioUrl(language: String, text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        val type = ttsType(trimmed)
        val options = loadTtsVoices(language)
        val preferred = preferredTtsVoice[language]
        val ordered = buildList {
            preferred?.let { add(it) }
            addAll(options)
        }.distinctBy { it.appName to it.voice }
        if (ordered.isEmpty()) {
            return requestTts(language, trimmed, type, null)
        }
        // 默认音色优先；第一个失败了再试下一个，最多试 3 个，避免点词一直等。
        for (option in ordered.take(3)) {
            val url = requestTts(language, trimmed, type, option)
            if (url != null) {
                preferredTtsVoice[language] = option
                return url
            }
        }
        return null
    }

    private suspend fun requestTts(
        language: String,
        text: String,
        type: String,
        option: TtsVoiceOption?,
    ): String? {
        val params = linkedMapOf("text" to text, "type" to type)
        if (option != null) {
            params["app_name"] = option.appName
            params["voice"] = option.voice
        }
        val result = runCatching {
            getJson(
                "api/v3/$language/tts/",
                params,
                // 点词要立刻有反馈：接口慢就回退系统 TTS，而不是让用户干等。
                timeoutMs = 3_500,
            )
        }
        val root = result.getOrNull()
        if (root == null) {
            Log.w(TAG, "tts '$text' failed: ${result.exceptionOrNull()?.message}")
            return null
        }
        val url = findAudioUrl(root, depth = 0)
        if (url == null) {
            Log.w(TAG, "tts '$text' has no audio: ${root.toString().take(300)}")
        }
        return url
    }

    private data class TtsVoiceOption(
        val appName: String,
        val voice: String,
        val isDefault: Boolean,
    )

    private val ttsVoiceOptions = HashMap<String, List<TtsVoiceOption>>()
    private val preferredTtsVoice = HashMap<String, TtsVoiceOption>()

    /**
     * `GET api/v2/tts/supported-voices?language=`：每种语言返回若干音色，
     * 每个音色带 `voicesByApp: [{appName, name}]`，这两个值就是 TTS 请求参数。
     */
    private suspend fun loadTtsVoices(language: String): List<TtsVoiceOption> {
        ttsVoiceOptions[language]?.let { return it }
        val root = runCatching {
            getJson(
                "api/v2/tts/supported-voices",
                mapOf("language" to language),
                timeoutMs = 4_000,
            )
        }.getOrNull() ?: return emptyList()
        val objects = when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> root.collectObjects { it.array("voicesByApp") != null }
            else -> emptyList()
        }
        val options = objects.flatMap { obj ->
            val premium = obj.bool("premium") ?: false
            val selectable = obj.bool("isSelectable") ?: true
            if (!selectable || premium) return@flatMap emptyList()
            val isDefault = obj.array("tags")?.any { tag ->
                (tag as? JsonPrimitive)?.content == "default"
            } == true
            obj.array("voicesByApp").orEmpty().mapNotNull { element ->
                val entry = element as? JsonObject ?: return@mapNotNull null
                val appName = entry.string("appName", "app_name") ?: return@mapNotNull null
                val voice = entry.string("name", "voice") ?: return@mapNotNull null
                TtsVoiceOption(appName = appName, voice = voice, isDefault = isDefault)
            }
        }.distinctBy { it.appName to it.voice }
            .sortedByDescending { it.isDefault }
        if (options.isNotEmpty()) {
            ttsVoiceOptions[language] = options
        }
        return options
    }

    /** 和 LingQ 客户端一致：1 个词 = word，2–4 个词 = phrase，更多 = sentence。 */
    private fun ttsType(text: String): String {
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        return when {
            words <= 1 -> "word"
            words <= 4 -> "phrase"
            else -> "sentence"
        }
    }

    private fun findAudioUrl(element: JsonElement, depth: Int): String? {
        if (depth > 6) return null
        when (element) {
            is JsonPrimitive -> {
                return if (element.isString) element.content.asAudioUrl() else null
            }

            is JsonArray -> element.forEach { child ->
                findAudioUrl(child, depth + 1)?.let { return it }
            }

            is JsonObject -> {
                element.string("audio", "audioUrl", "audio_url", "mp3", "file", "fileUrl", "path", "url")
                    ?.asAudioUrl()
                    ?.let { return it }
                listOf("generated", "requested", "data", "result", "utterance", "audio")
                    .forEach { key ->
                        element[key]?.let { nested ->
                            findAudioUrl(nested, depth + 1)?.let { return it }
                        }
                    }
            }
        }
        return null
    }

    /** 只接受看起来像音频地址的字符串；base64 / 纯文本保持 null，走系统 TTS。 */
    private fun String.asAudioUrl(): String? {
        val value = trim()
        if (value.isEmpty() || value.startsWith("data:")) return null
        return when {
            value.startsWith("http://") || value.startsWith("https://") -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> "https://www.lingq.com$value"
            value.contains('/') -> value.absoluteUrl()
            else -> null
        }
    }

    // -----------------------------------------------------------------------
    // 传输
    // -----------------------------------------------------------------------

    private suspend fun getJson(
        path: String,
        params: Map<String, String?> = emptyMap(),
        timeoutMs: Long? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val token = session.token() ?: throw ApiException(401, "Your session expired. Sign in again.")
        val base = (BASE_URL + path).toHttpUrlOrNull()
            ?: throw ApiException(400, "Invalid API address")
        val url = base.newBuilder().apply {
            params.forEach { (key, value) -> if (!value.isNullOrBlank()) addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Token $token")
            .header("Accept", "application/json")
            .header("User-Agent", "InputA/0.2 Android")
            .get()
            .build()
        try {
            val call = client.newCall(request)
            if (timeoutMs != null) {
                call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            }
            call.execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (path.contains("/tts/")) {
                        Log.w(TAG, "tts HTTP ${response.code}: ${body.take(500)}")
                    }
                    throw ApiException(response.code, describeBodyError(response.code, body))
                }
                if (body.isBlank()) return@withContext JsonNull
                runCatching { json.parseToJsonElement(body) }.getOrElse {
                    throw ApiException(response.code, "The response was not valid JSON")
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (api: ApiException) {
            throw api
        } catch (error: Exception) {
            throw ApiException(0, "Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}")
        }
    }

    /** DRF 的错误形状：`{"non_field_errors":["..."]}` / `{"detail":"..."}`。 */
    private fun describeError(code: Int, body: String): String {
        val detail = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["detail"]?.jsonPrimitive?.content
                ?: root.entries.firstOrNull { it.value is JsonArray }
                    ?.value?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        }.getOrNull()
        val hint = when (code) {
            400 -> "Incorrect username or password"
            401 -> "Sign-in failed: invalid credentials"
            403 -> "Account is restricted"
            in 500..599 -> "LingQ service is temporarily unavailable"
            else -> "Sign-in failed (HTTP $code)"
        }
        return if (detail.isNullOrBlank()) hint else "$hint：$detail"
    }

    private fun describeBodyError(code: Int, body: String): String {
        val detail = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root["detail"]?.jsonPrimitive?.content
                ?: root.entries.firstOrNull { it.value is JsonArray }
                    ?.value?.jsonArray?.firstOrNull()?.jsonPrimitive?.content
        }.getOrNull()
        return when {
            code == 401 -> "Your session expired. Sign in again."
            code == 403 -> "You do not have permission to access this content"
            !detail.isNullOrBlank() -> detail
            code >= 500 -> "LingQ service is temporarily unavailable"
            else -> "Request failed (HTTP $code)"
        }
    }

    private companion object {
        const val BASE_URL = "https://www.lingq.com/"
        const val TAG = "LingqApi"
    }
}
