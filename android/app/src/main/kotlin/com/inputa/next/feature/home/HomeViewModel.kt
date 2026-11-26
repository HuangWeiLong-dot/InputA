package com.inputa.next.feature.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.LearningLanguage
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LessonTextCache
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.computeLessonWordStats
import com.inputa.next.core.data.local.BookmarkStore
import com.inputa.next.core.data.local.LastLessonStore
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.SettingsStore
import com.inputa.next.core.data.normalizeLanguage
import com.inputa.next.core.data.sortedForDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * 首页只从 LingQ 取课程内容；词汇/学习记录全部读本地。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val localWords: LocalWordStore,
    private val textCache: LessonTextCache,
    private val settings: SettingsStore,
    private val lastLessonStore: LastLessonStore,
    private val bookmarks: BookmarkStore,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val refreshing: Boolean = false,
        val error: String? = null,
        val username: String = "Inputa",
        val avatarPath: String? = null,
        val languageCode: String = "en",
        val languageName: String = "EN",
        val languageLevel: Int? = null,
        val knownCount: Int = 0,
        val inputaCount: Int = 0,
        val ignoredCount: Int = 0,
        val languages: List<LearningLanguage> = emptyList(),
        val languagesLoading: Boolean = false,
        val continueLessons: List<LessonSummary> = emptyList(),
        val picked: List<LessonSummary> = emptyList(),
        val pickedSections: List<LevelSection> = emptyList(),
        /** 上次进入阅读的课程；用于「继续上一课程」卡。 */
        val lastLesson: LessonSummary? = null,
        /** 本机书签课程。 */
        val savedLessons: List<LessonSummary> = emptyList(),
    )

    /** 首页「Picked For You」按课程等级划分。 */
    data class LevelSection(
        val title: String,
        val level: Int?,
        val lessons: List<LessonSummary>,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var loadJob: Job? = null

    val lessonSort = settings.lessonSort

    fun toggleLessonSort() {
        settings.toggleLessonSort()
        val sort = settings.lessonSort.value
        _state.update { state ->
            val sorted = state.picked.sortedForDisplay(sort)
            state.copy(picked = sorted, pickedSections = sorted.groupByLevel(state.languageLevel))
        }
    }

    /** 语言选择页的等级滑块：null = 全部等级。 */
    fun setLanguageLevel(level: Int?) {
        session.saveLanguageLevel(session.language(), level)
        _state.update { it.copy(languageLevel = level) }
        load(refresh = true)
    }

    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val language = normalizeLanguage(session.storedLanguage())
            val previousState = _state.value
            val languageChanged = previousState.languageCode != language
            _state.update {
                it.copy(
                    loading = languageChanged || !refresh,
                    refreshing = refresh && !languageChanged,
                    error = null,
                    languagesLoading = it.languages.isEmpty(),
                    continueLessons = if (languageChanged) emptyList() else it.continueLessons,
                    picked = if (languageChanged) emptyList() else it.picked,
                    pickedSections = if (languageChanged) emptyList() else it.pickedSections,
                )
            }

            val (local, records) = withContext(Dispatchers.IO) {
                val words = localWords.load(language)
                State(
                    username = session.displayUsername() ?: "Inputa",
                    avatarPath = session.avatarPath(),
                    languageCode = language,
                    languageName = LanguageNames[language] ?: language.uppercase(),
                    knownCount = words.values.count { it == 0 },
                    inputaCount = words.values.count { it in 1..4 },
                    ignoredCount = words.values.count { it == -1 },
                    lastLesson = lastLessonStore.load(language),
                    savedLessons = bookmarks.list(language),
                ) to words
            }
            val storedLevel = session.storedLanguageLevel(language)
            val selectedLevel = storedLevel?.takeIf { it in 1..6 }
            val previousLevel = previousState.languageLevel.takeIf { previousState.languageCode == language }
            _state.update { state ->
                state.copy(
                    username = local.username,
                    avatarPath = local.avatarPath,
                    languageCode = language,
                    languageName = local.languageName,
                    languageLevel = if (storedLevel == null) previousLevel else selectedLevel,
                    knownCount = local.knownCount,
                    inputaCount = local.inputaCount,
                    ignoredCount = local.ignoredCount,
                    lastLesson = local.lastLesson,
                    savedLessons = local.savedLessons,
                )
            }

            coroutineScope {
                val continueDeferred = async { request { api.continueStudying(language) } }
                val languagesDeferred = async {
                    // 语言等级只用于展示和首次推荐筛选，慢接口不能阻塞继续学习列表。
                    request { withTimeoutOrNull(4_000) { api.learningLanguages() } }
                        .getOrNull()
                        .orEmpty()
                }
                val languageListDeferred = async {
                    val remote = languagesDeferred.await()
                    withContext(Dispatchers.IO) {
                        LanguageNames.map { (code, name) ->
                            val entries = if (code == language) records.values else localWords.load(code).values
                            LearningLanguage(
                                code = code,
                                name = name,
                                knownWords = entries.count { it == 0 },
                                lingqCount = entries.count { it in 1..4 },
                                wordsRead = null,
                                level = remote.firstOrNull { it.code == code }?.level,
                                isActive = code == language,
                            )
                        }
                    }
                }
                val pickedDeferred = async {
                    val level = if (storedLevel != null) {
                        selectedLevel
                    } else {
                        languagesDeferred.await().firstOrNull { it.code == language }?.level
                    }
                    request { api.pickedForYou(language, level) }
                }

                val continueResult = continueDeferred.await()
                val continueLessons = withContext(Dispatchers.Default) {
                    continueResult.getOrDefault(emptyList()).map { it.withLocalCounts(language, records) }
                }
                _state.update { state ->
                    if (state.languageCode != language) {
                        state
                    } else {
                        state.copy(
                            continueLessons = continueLessons,
                            loading = continueLessons.isEmpty() && state.picked.isEmpty(),
                        )
                    }
                }

                val pickedResult = pickedDeferred.await()
                val activeLevel = if (storedLevel != null) {
                    selectedLevel
                } else {
                    languagesDeferred.await().firstOrNull { it.code == language }?.level
                }
                val continueIds = continueLessons.mapTo(mutableSetOf()) { it.id }
                val sort = settings.lessonSort.value
                val picked = withContext(Dispatchers.Default) {
                    pickedResult.getOrDefault(emptyList())
                        .filterNot { it.id in continueIds }
                        .map { it.withLocalCounts(language, records) }
                        .sortedForDisplay(sort)
                }
                val error = (continueResult.exceptionOrNull() ?: pickedResult.exceptionOrNull())
                    ?.message
                    ?.takeIf { continueLessons.isEmpty() && picked.isEmpty() }

                _state.update { state ->
                    if (state.languageCode != language) {
                        state
                    } else {
                        state.copy(
                            loading = false,
                            refreshing = false,
                            error = error,
                            languageLevel = activeLevel,
                            continueLessons = continueLessons,
                            picked = picked,
                            pickedSections = picked.groupByLevel(activeLevel),
                        )
                    }
                }

                val languages = languageListDeferred.await()
                _state.update { state ->
                    if (state.languageCode != language) {
                        state
                    } else {
                        state.copy(languages = languages, languagesLoading = false)
                    }
                }
            }
        }
    }

    /** 左上角头像菜单里切换语言；本地记住选择，首页和资料库随之刷新。 */
    fun selectLanguage(code: String) {
        session.saveLanguage(normalizeLanguage(code))
        load(refresh = true)
    }

    /** 保存本机自定义显示名；空串表示恢复登录用户名。 */
    fun saveDisplayName(name: String) {
        session.saveDisplayUsername(name)
        _state.update { it.copy(username = session.displayUsername() ?: "Inputa") }
    }

    /** 选完头像后复制到应用私有目录，成功后立即刷新首页头像。 */
    fun saveAvatar(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { session.saveAvatar(uri) } ?: return@launch
            _state.update { it.copy(avatarPath = path) }
        }
    }

    /** 从首页取消书签后立刻刷新 Saved Lessons。 */
    fun removeBookmark(lesson: LessonSummary) {
        bookmarks.remove(session.language(), lesson.id)
        _state.update { it.copy(savedLessons = bookmarks.list(session.language())) }
    }

    private suspend fun <T> request(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }

    /**
     * 按接口返回的课程等级分成三档；没有等级的课程放最后。
     * 和学习语言的当前等级同一档的分组排在最前面，首页最相关的推荐先出现。
     */
    private fun List<LessonSummary>.groupByLevel(learnerLevel: Int?): List<LevelSection> {
        val sections = listOf(
            LevelSection("Beginner", 1, filter { it.level?.let { level -> level <= 2 } == true }),
            LevelSection("Intermediate", 3, filter { it.level?.let { level -> level in 3..4 } == true }),
            LevelSection("Advanced", 5, filter { it.level?.let { level -> level >= 5 } == true }),
            LevelSection("More for you", null, filter { it.level == null }),
        ).filter { it.lessons.isNotEmpty() }
        val preferred = when {
            learnerLevel == null -> null
            learnerLevel <= 2 -> 1
            learnerLevel <= 4 -> 3
            else -> 5
        }
        return sections.sortedWith(
            compareBy<LevelSection> { if (it.level == preferred) 0 else 1 }
                .thenBy { it.level ?: Int.MAX_VALUE },
        )
    }

    /**
     * 卡片上的 new words 优先按本机词库算：正文取自已加载过的课程（阅读器/详情页）。
     * 还没拿到正文的课继续用服务端数字 —— 不为列表额外发请求。
     */
    private fun LessonSummary.withLocalCounts(
        language: String,
        records: Map<String, Int>,
    ): LessonSummary {
        val text = textCache.get(language, id) ?: return this
        val stats = computeLessonWordStats(api, text, language, records)
        return copy(newWordsCount = stats.newWords)
    }

    private companion object {
        /** 本地语言选项，不依赖 LingQ 的账号语言列表。 */
        val LanguageNames = linkedMapOf(
            "en" to "English",
            "es" to "Spanish",
            "fr" to "French",
            "de" to "German",
            "it" to "Italian",
            "pt" to "Portuguese",
            "ru" to "Russian",
            "ja" to "Japanese",
            "ko" to "Korean",
            "zh" to "Chinese",
            "ar" to "Arabic",
            "nl" to "Dutch",
            "sv" to "Swedish",
            "pl" to "Polish",
            "tr" to "Turkish",
            "he" to "Hebrew",
            "fi" to "Finnish",
            "no" to "Norwegian",
            "da" to "Danish",
            "cs" to "Czech",
            "el" to "Greek",
            "uk" to "Ukrainian",
            "vi" to "Vietnamese",
            "th" to "Thai",
            "id" to "Indonesian",
            "ro" to "Romanian",
            "hu" to "Hungarian",
            "ca" to "Catalan",
        )
    }
}
