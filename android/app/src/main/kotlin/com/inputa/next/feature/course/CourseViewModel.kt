package com.inputa.next.feature.course

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.CourseDetail
import com.inputa.next.core.data.CurrentCourse
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LessonTextCache
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.computeLessonWordStats
import com.inputa.next.core.data.local.LocalWordStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class CourseViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val current: CurrentCourse,
    private val localWords: LocalWordStore,
    private val textCache: LessonTextCache,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val course: LessonSummary? = null,
        val detail: CourseDetail? = null,
    )

    private data class CachedDetail(
        val detail: CourseDetail,
        val loadedAtNanos: Long,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var loadJob: Job? = null
    private var generation = 0L
    private val detailCache = object : LinkedHashMap<String, CachedDetail>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetail>?): Boolean =
            size > MAX_CACHED_COURSES
    }

    init {
        viewModelScope.launch {
            current.changes.collect { course ->
                if (course == null) {
                    loadJob?.cancel()
                    generation++
                    detailCache.clear()
                    _state.update { State(loading = false) }
                } else {
                    startLoad(course, forceNetwork = false)
                }
            }
        }
    }

    fun load() {
        val course = _state.value.course ?: current.course ?: return
        startLoad(course, forceNetwork = true)
    }

    private fun startLoad(course: LessonSummary, forceNetwork: Boolean) {
        loadJob?.cancel()
        val requestId = ++generation
        val language = session.language()
        val cached = detailCache[cacheKey(language, course)]
        val sameCourse = _state.value.course.let { selected ->
            selected?.id == course.id && selected.libraryId == course.libraryId
        }
        val visibleDetail = cached?.detail ?: _state.value.detail.takeIf { sameCourse }
        _state.update {
            it.copy(
                loading = visibleDetail == null,
                error = null,
                course = course,
                detail = visibleDetail,
            )
        }
        loadJob = viewModelScope.launch {
            loadFor(course, language, requestId, forceNetwork, cached)
        }
    }

    private suspend fun loadFor(
        course: LessonSummary,
        language: String,
        requestId: Long,
        forceNetwork: Boolean,
        cached: CachedDetail?,
    ) {
        if (cached != null) {
            val decorated = withLocalCounts(cached.detail, language)
            _state.update { state ->
                if (isCurrent(state, course, language, requestId)) {
                    state.copy(detail = decorated, loading = false)
                } else {
                    state
                }
            }
            val ageNanos = System.nanoTime() - cached.loadedAtNanos
            if (!forceNetwork && ageNanos < CACHE_TTL_NANOS) return
        }

        val result = request { api.courseDetail(language, course) }
        val loaded = result.getOrNull()
        if (loaded != null && loaded.lessons.isNotEmpty()) {
            detailCache[cacheKey(language, course)] = CachedDetail(loaded, System.nanoTime())
        }
        val usableLoaded = loaded?.let {
            if (cached != null && it.lessons.isEmpty() && cached.detail.lessons.isNotEmpty()) {
                cached.detail
            } else {
                it
            }
        }
        val detail = usableLoaded?.let { withLocalCounts(it, language) }
        _state.update { state ->
            if (isCurrent(state, course, language, requestId)) {
                state.copy(
                    loading = false,
                    detail = detail ?: state.detail,
                    error = result.exceptionOrNull()?.message?.takeIf { detail == null },
                )
            } else {
                state
            }
        }
    }

    private suspend fun withLocalCounts(detail: CourseDetail, language: String): CourseDetail {
        val records = withContext(Dispatchers.IO) { localWords.load(language) }
        return withContext(Dispatchers.Default) {
            detail.copy(
                lessons = detail.lessons.map { lesson ->
                    val text = textCache.get(language, lesson.id) ?: return@map lesson
                    val stats = computeLessonWordStats(api, text, language, records)
                    lesson.copy(newWordsCount = stats.newWords)
                },
            )
        }
    }

    private fun isCurrent(
        state: State,
        course: LessonSummary,
        language: String,
        requestId: Long,
    ): Boolean = requestId == generation &&
        session.language() == language &&
        state.course?.id == course.id &&
        state.course?.libraryId == course.libraryId

    private fun cacheKey(language: String, course: LessonSummary): String =
        "$language:${course.libraryId ?: course.id}"

    private suspend fun <T> request(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Result.failure(error)
        }

    private companion object {
        const val MAX_CACHED_COURSES = 8
        val CACHE_TTL_NANOS = TimeUnit.MINUTES.toNanos(5)
    }
}
