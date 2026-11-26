package com.inputa.next.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.ExploreProvider
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LessonTextCache
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.ShelfSection
import com.inputa.next.core.data.computeLessonWordStats
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.SettingsStore
import com.inputa.next.core.data.sortedForDisplay
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val settings: SettingsStore,
    private val localWords: LocalWordStore,
    private val textCache: LessonTextCache,
) : ViewModel() {

    data class State(
        val query: String = "",
        val level: Int? = null,
        val providers: List<ExploreProvider> = emptyList(),
        val providerId: String? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val shelves: List<ShelfSection> = emptyList(),
        val lessons: List<LessonSummary> = emptyList(),
        val hasLoaded: Boolean = false,
    ) {
        /** 没有关键词、没有来源筛选时才是书架模式。 */
        val showingShelves: Boolean get() = query.isBlank() && providerId == null
    }

    private val _state = MutableStateFlow(
        State(level = session.storedLanguageLevel(session.language())?.takeIf { it in 1..6 }),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    val lessonSort = settings.lessonSort

    private var searchJob: Job? = null
    private var providersJob: Job? = null
    private var generation = 0

    /** 上一次加载用的学习语言；换语言后旧筛选条件全部作废。 */
    private var loadedLanguage: String? = null

    fun toggleLessonSort() {
        settings.toggleLessonSort()
        val sort = settings.lessonSort.value
        _state.update { state ->
            state.copy(
                shelves = state.shelves.map { shelf ->
                    shelf.copy(lessons = shelf.lessons.sortedForDisplay(sort))
                },
                lessons = state.lessons.sortedForDisplay(sort),
            )
        }
    }

    /** 输入时防抖，避免每敲一个字都打一次搜索接口。 */
    fun setQuery(value: String) {
        if (_state.value.query == value) return
        _state.update { it.copy(query = value) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (value.isNotBlank()) delay(350)
            load()
        }
    }

    fun setLevel(level: Int?) {
        if (_state.value.level == level) return
        session.saveLanguageLevel(session.language(), level)
        _state.update { it.copy(level = level) }
        refresh()
    }

    fun setProvider(providerId: String?) {
        if (_state.value.providerId == providerId) return
        _state.update { it.copy(providerId = providerId) }
        refresh()
    }

    fun refresh() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val language = session.language()
        // 换了学习语言：旧的来源筛选 / 搜索词对新语言毫无意义，
        // 不清掉的话会带着上一个语言的 provider 去搜，页面就是空的。
        if (loadedLanguage != null && loadedLanguage != language) {
            providersJob?.cancel()
            _state.update {
                it.copy(
                    query = "",
                    providerId = null,
                    providers = emptyList(),
                    shelves = emptyList(),
                    lessons = emptyList(),
                    level = null,
                )
            }
        }
        loadedLanguage = language
        // 首页滑块改了等级后，回到资料库也要跟着变。
        val storedLevel = session.storedLanguageLevel(language)?.takeIf { it in 1..6 }
        if (_state.value.level != storedLevel) {
            _state.update { it.copy(level = storedLevel) }
        }
        val snapshot = _state.value
        val token = ++generation
        _state.update { it.copy(loading = true, error = null) }

        // 来源胶囊单独加载，不阻塞书架/搜索结果。
        loadProviders(language)

        val words = localWords.load(language)
        val sort = settings.lessonSort.value
        val shelvesMode = snapshot.showingShelves
        var shelves: List<ShelfSection> = emptyList()
        var lessons: List<LessonSummary> = emptyList()
        var error: String? = null
        // 实际生效的等级：内容为空时会放宽到全部等级，并同步回 UI。
        var appliedLevel = snapshot.level

        if (shelvesMode) {
            var shelvesResult = fetch { api.shelfSections(language, snapshot.level) }
            var sections = shelvesResult.getOrNull().orEmpty()
            // 这个等级没有书架时，换成不限等级再取一次。
            // 只在请求成功且确实为空时放宽；网络失败不再重试一遍。
            if (sections.isEmpty() && snapshot.level != null && shelvesResult.isSuccess) {
                appliedLevel = null
                shelvesResult = fetch { api.shelfSections(language, null) }
                sections = shelvesResult.getOrNull().orEmpty()
            }
            if (sections.isNotEmpty()) {
                val mapped = sections.map { shelf ->
                    shelf.copy(
                        lessons = shelf.lessons
                            .map { it.withLocalCounts(language, words) }
                            .sortedForDisplay(sort),
                    )
                }
                // 书架是 LingQ 按学习历史推的，换语言或长期没用时会很薄。
                // 内容太少就再拉一页全部资源补在后面，避免「全部来源」只有几篇。
                val total = mapped.sumOf { it.lessons.size }
                if (total < MIN_SHELF_LESSONS) {
                    val extra = fetch { api.searchLessons(language, pageSize = 40) }
                        .getOrNull()
                        .orEmpty()
                        .map { it.withLocalCounts(language, words) }
                        .sortedForDisplay(sort)
                    val seen = mapped.flatMap { it.lessons }.mapTo(HashSet()) { it.id }
                    val add = extra.filter { seen.add(it.id) }
                    shelves = if (add.isEmpty()) mapped else mapped + ShelfSection("All resources", add)
                } else {
                    shelves = mapped
                }
            } else {
                // shelves 为空或失败时用全部课程兜底，至少能显示 All resources。
                // 等级筛选在 LingQ 那边可能过窄（某个等级没内容），所以逐级放宽：
                // 先带等级搜，空了就不带等级再搜一次。
                var fallback = fetch {
                    api.searchLessons(language, level = snapshot.level, pageSize = 40)
                }
                var all = fallback.getOrNull().orEmpty()
                if (all.isEmpty() && snapshot.level != null && fallback.isSuccess) {
                    appliedLevel = null
                    fallback = fetch { api.searchLessons(language, pageSize = 40) }
                    all = fallback.getOrNull().orEmpty()
                }
                val mapped = all
                    .map { it.withLocalCounts(language, words) }
                    .sortedForDisplay(sort)
                shelves = if (mapped.isEmpty()) emptyList() else listOf(ShelfSection("All resources", mapped))
                if (mapped.isEmpty()) {
                    error = shelvesResult.exceptionOrNull()?.message
                        ?: fallback.exceptionOrNull()?.message
                }
            }
        } else {
            var lessonsResult = fetch {
                api.searchLessons(
                    language = language,
                    query = snapshot.query.trim().ifBlank { null },
                    level = snapshot.level,
                    provider = snapshot.providerId,
                    pageSize = 40,
                )
            }
            var items = lessonsResult.getOrNull().orEmpty()
            // 该等级下没有结果时去掉等级再搜，避免整页空白。
            if (items.isEmpty() && snapshot.level != null && lessonsResult.isSuccess) {
                appliedLevel = null
                lessonsResult = fetch {
                    api.searchLessons(
                        language = language,
                        query = snapshot.query.trim().ifBlank { null },
                        level = null,
                        provider = snapshot.providerId,
                        pageSize = 40,
                    )
                }
                items = lessonsResult.getOrNull().orEmpty()
            }
            lessons = items
                .map { it.withLocalCounts(language, words) }
                .sortedForDisplay(sort)
            error = lessonsResult.exceptionOrNull()?.message
        }
        if (token != generation) return
        // 等级被放宽过就写回本地设置，否则下次加载又会带着旧等级试一遍、再放宽。
        if (appliedLevel != snapshot.level) session.saveLanguageLevel(language, null)

        _state.update { state ->
            // 用户已经改了条件，旧请求的结果直接丢弃。
            val levelOk = state.level == snapshot.level || state.level == appliedLevel
            if (state.query != snapshot.query ||
                !levelOk ||
                state.providerId != snapshot.providerId
            ) {
                state
            } else {
                state.copy(
                    loading = false,
                    hasLoaded = true,
                    level = appliedLevel,
                    shelves = shelves,
                    lessons = lessons,
                    error = error,
                )
            }
        }
    }

    /** 来源胶囊单独加载；失败就只保留 All resources，不影响课程。 */
    private fun loadProviders(language: String) {
        if (_state.value.providers.isNotEmpty() || providersJob?.isActive == true) return
        providersJob = viewModelScope.launch {
            val providers = fetch { api.featuredProviders(language) }.getOrDefault(emptyList())
            if (providers.isNotEmpty() && session.language() == language) {
                _state.update { it.copy(providers = providers) }
            }
        }
    }

    /** 卡片上的 new words 用本机词库算；没有缓存正文的课程保留服务端数字。 */
    private fun LessonSummary.withLocalCounts(
        language: String,
        records: Map<String, Int>,
    ): LessonSummary {
        val text = textCache.get(language, id) ?: return this
        return copy(newWordsCount = computeLessonWordStats(api, text, language, records).newWords)
    }

    /** runCatching 会吞掉协程取消；这里把取消继续抛出去，避免旧请求写回状态。 */
    private suspend fun <T> fetch(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        /** 书架里的课程少于这个数就补一页「全部资源」。 */
        const val MIN_SHELF_LESSONS = 12
    }
}
