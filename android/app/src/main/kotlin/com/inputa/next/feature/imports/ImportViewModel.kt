package com.inputa.next.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.AiClient
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.MimoTtsClient
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.media.PreviewAudioPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val ai: AiClient,
    private val tts: MimoTtsClient,
) : ViewModel() {

    enum class Phase { Idle, AiRunning, TtsGenerating, Importing }

    data class State(
        val title: String = "",
        val url: String = "",
        val text: String = "",
        val collection: String = "",
        val level: Int = 2,
        val phase: Phase = Phase.Idle,
        val aiProcessed: Boolean = false,
        val ttsEnabled: Boolean = false,
        val ttsAudioPath: String? = null,
        val ttsPlaying: Boolean = false,
        val ttsPreparing: Boolean = false,
        val error: String? = null,
        val imported: LessonSummary? = null,
    ) {
        val loading: Boolean get() = phase == Phase.Importing
        val aiLoading: Boolean get() = phase == Phase.AiRunning
        val ttsLoading: Boolean get() = phase == Phase.TtsGenerating
        val ttsReady: Boolean get() = ttsAudioPath != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val previewPlayer = PreviewAudioPlayer()
    private var operationId = 0
    private var activeJob: Job? = null

    init {
        viewModelScope.launch {
            previewPlayer.state.collect { player ->
                _state.update {
                    it.copy(
                        ttsPlaying = player.playing,
                        ttsPreparing = player.preparing,
                        error = player.error ?: it.error,
                    )
                }
            }
        }
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value, error = null) }
    fun setUrl(value: String) = _state.update { it.copy(url = value, error = null) }

    fun setText(value: String) {
        when (_state.value.phase) {
            Phase.AiRunning -> cancelAi()
            Phase.TtsGenerating -> cancelTts()
            else -> Unit
        }
        _state.update {
            it.copy(text = value, aiProcessed = false, ttsAudioPath = null, error = null)
        }
        stopTtsPlayback()
    }

    fun setCollection(value: String) = _state.update { it.copy(collection = value) }
    fun setLevel(value: Int) = _state.update { it.copy(level = value) }
    fun consumeImported() = _state.update { it.copy(imported = null) }

    fun toggleAi() {
        val current = _state.value
        if (current.phase != Phase.Idle) return
        if (current.text.isBlank()) {
            _state.update { it.copy(error = "Paste some text first for AI to clean.") }
            return
        }
        if (!session.hasAiConfig()) {
            _state.update { it.copy(error = "AI is not configured") }
            return
        }

        val opId = ++operationId
        _state.update { it.copy(phase = Phase.AiRunning, error = null) }
        activeJob = viewModelScope.launch {
            try {
                val cleaned = ai.cleanArticleForImport(
                    text = current.text,
                    title = current.title.trim().takeIf { it.isNotBlank() },
                    learningLanguage = session.language(),
                )
                if (opId != operationId) return@launch
                previewPlayer.stop()
                _state.update {
                    if (opId == operationId) {
                        it.copy(
                            phase = Phase.Idle,
                            text = cleaned,
                            aiProcessed = true,
                            ttsAudioPath = null,
                            error = null,
                        )
                    } else {
                        it
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (opId == operationId) {
                    _state.update {
                        it.copy(
                            phase = Phase.Idle,
                            error = error.message ?: "AI processing failed.",
                        )
                    }
                }
            }
        }
    }

    private fun cancelAi() {
        operationId++
        activeJob?.cancel()
        activeJob = null
        _state.update { it.copy(phase = Phase.Idle, error = null) }
    }

    fun setTtsEnabled(enabled: Boolean) {
        if (_state.value.phase != Phase.Idle) return
        if (enabled) {
            _state.update { it.copy(ttsEnabled = true, error = null) }
        } else {
            previewPlayer.stop()
            _state.update {
                it.copy(ttsEnabled = false, ttsAudioPath = null, error = null)
            }
        }
    }

    fun toggleTts() {
        val current = _state.value
        if (current.phase == Phase.TtsGenerating) {
            cancelTts()
            return
        }
        if (current.phase != Phase.Idle || !current.ttsEnabled) return
        if (current.text.isBlank()) {
            _state.update { it.copy(error = "Paste some text first to generate audio.") }
            return
        }
        if (!session.hasMimoKey()) {
            _state.update { it.copy(error = "MiMo API Key is not configured") }
            return
        }

        previewPlayer.stop()
        val opId = ++operationId
        _state.update { it.copy(phase = Phase.TtsGenerating, error = null) }
        activeJob = viewModelScope.launch {
            try {
                val audio = tts.synthesize(current.text)
                if (opId != operationId) return@launch
                _state.update {
                    if (opId == operationId) {
                        it.copy(
                            phase = Phase.Idle,
                            ttsAudioPath = audio.absolutePath,
                            ttsPlaying = false,
                            ttsPreparing = false,
                            error = null,
                        )
                    } else {
                        it
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (opId == operationId) {
                    _state.update {
                        it.copy(
                            phase = Phase.Idle,
                            error = error.message ?: "Speech generation failed.",
                        )
                    }
                }
            }
        }
    }

    private fun cancelTts() {
        operationId++
        activeJob?.cancel()
        activeJob = null
        previewPlayer.stop()
        _state.update {
            it.copy(phase = Phase.Idle, ttsPlaying = false, ttsPreparing = false, error = null)
        }
    }

    fun toggleTtsPlayback() {
        val path = _state.value.ttsAudioPath ?: return
        previewPlayer.toggle(File(path))
    }

    fun stopTtsPlayback() {
        previewPlayer.stop()
    }

    fun submit() {
        val current = _state.value
        if (current.phase != Phase.Idle) return
        if (current.title.isBlank()) {
            _state.update { it.copy(error = "Give the lesson a title.") }
            return
        }
        if (current.text.isBlank() && current.url.isBlank()) {
            _state.update { it.copy(error = "Paste an article URL or some text.") }
            return
        }

        previewPlayer.stop()
        val opId = ++operationId
        _state.update { it.copy(phase = Phase.Importing, error = null) }
        activeJob = viewModelScope.launch {
            try {
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
                if (opId != operationId) return@launch
                val lesson = result.getOrNull()
                val failure = result.exceptionOrNull()
                _state.update {
                    if (opId != operationId) {
                        it
                    } else if (lesson != null) {
                        it.copy(phase = Phase.Idle, imported = lesson, error = null)
                    } else {
                        it.copy(
                            phase = Phase.Idle,
                            error = failure?.message
                                ?: "Import started but LingQ didn't return the lesson yet. Check your library shortly.",
                        )
                    }
                }
            } finally {
                if (opId == operationId) {
                    _state.update {
                        if (it.phase == Phase.Importing) it.copy(phase = Phase.Idle) else it
                    }
                }
            }
        }
    }

    override fun onCleared() {
        previewPlayer.release()
    }
}
