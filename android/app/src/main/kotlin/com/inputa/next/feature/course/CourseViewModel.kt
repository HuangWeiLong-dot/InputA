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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            current.changes.collectLatest { course ->
                if (course == null) {
                    _state.update { State(loading = false) }
                } else {
                    _state.update {
                        it.copy(
                            loading = true,
                            error = null,
                            course = course,
                            detail = null,
                        )
                    }
                    loadFor(course)
                }
            }
        }
    }

    fun load() {
        val course = _state.value.course ?: current.course ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            loadFor(course)
        }
    }

    private suspend fun loadFor(course: LessonSummary) {
        val result = runCatching {
            api.courseDetail(session.language(), course)
        }
        val language = session.language()
        val records = localWords.load(language)
        // 每课的生词数优先按本机词库算（正文已缓存时），否则保留服务端数字。
        val detail = result.getOrNull()?.let { loaded ->
            loaded.copy(
                lessons = loaded.lessons.map { lesson ->
                    val text = textCache.get(language, lesson.id) ?: return@map lesson
                    val stats = computeLessonWordStats(api, text, language, records)
                    lesson.copy(newWordsCount = stats.newWords)
                },
            )
        }
        _state.update { state ->
            val sameCourse = state.course?.let { it.id == course.id && it.libraryId == course.libraryId }
                ?: false
            if (!sameCourse) {
                state
            } else {
                state.copy(
                    loading = false,
                    detail = detail,
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }
}
