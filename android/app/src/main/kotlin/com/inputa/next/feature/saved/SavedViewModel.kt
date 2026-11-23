package com.inputa.next.feature.saved

import androidx.lifecycle.ViewModel
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.local.BookmarkStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SavedViewModel @Inject constructor(
    private val session: SessionStore,
    private val bookmarks: BookmarkStore,
) : ViewModel() {

    data class State(val lessons: List<LessonSummary> = emptyList())

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load() {
        _state.value = State(bookmarks.list(session.language()))
    }

    fun remove(lesson: LessonSummary) {
        bookmarks.remove(session.language(), lesson.id)
        load()
    }
}
