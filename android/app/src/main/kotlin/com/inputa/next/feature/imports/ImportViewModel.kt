package com.inputa.next.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
) : ViewModel() {

    data class State(
        val title: String = "",
        val url: String = "",
        val text: String = "",
        val collection: String = "",
        val level: Int = 2,
        val loading: Boolean = false,
        val error: String? = null,
        val imported: LessonSummary? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun setTitle(value: String) = _state.update { it.copy(title = value, error = null) }
    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }
    fun setText(value: String) = _state.update { it.copy(text = value, error = null) }
    fun setCollection(value: String) = _state.update { it.copy(collection = value) }
    fun setLevel(value: Int) = _state.update { it.copy(level = value) }
    fun consumeImported() = _state.update { it.copy(imported = null) }

    fun submit() {
        val current = _state.value
        if (current.loading) return
        if (current.title.isBlank()) {
            _state.update { it.copy(error = "Give the lesson a title.") }
            return
        }
        if (current.text.isBlank() && current.url.isBlank()) {
            _state.update { it.copy(error = "Paste an article URL or some text.") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                api.importLesson(
                    language = session.language(),
                    title = current.title.trim(),
                    text = current.text.takeIf { it.isNotBlank() },
                    url = current.url.takeIf { it.isNotBlank() },
                    level = current.level,
                    collectionTitle = current.collection.takeIf { it.isNotBlank() },
                )
            }.onFailure { cancellation ->
                if (cancellation is CancellationException) throw cancellation
            }
            val lesson = result.getOrNull()
            _state.update {
                if (lesson != null) {
                    it.copy(loading = false, imported = lesson)
                } else {
                    it.copy(
                        loading = false,
                        error = result.exceptionOrNull()?.message
                            ?: "Import started but LingQ didn't return the lesson yet. Check your library shortly.",
                    )
                }
            }
        }
    }
}
