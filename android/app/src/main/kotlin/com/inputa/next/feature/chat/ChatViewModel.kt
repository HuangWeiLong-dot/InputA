package com.inputa.next.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.AiClient
import com.inputa.next.core.data.AiMessage
import com.inputa.next.core.data.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val ai: AiClient,
    private val session: SessionStore,
) : ViewModel() {

    data class Message(val id: Long, val fromUser: Boolean, val text: String)

    data class State(
        val messages: List<Message> = emptyList(),
        val input: String = "",
        val sending: Boolean = false,
        val error: String? = null,
        val showSetup: Boolean = false,
        val baseUrl: String = "",
        val apiKey: String = "",
        val model: String = "",
    ) {
        val configured: Boolean
            get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    private val _state = MutableStateFlow(
        State(
            showSetup = !session.hasAiConfig(),
            baseUrl = session.aiBaseUrl(),
            apiKey = session.aiApiKey(),
            model = session.aiModel(),
        ),
    )
    val state: StateFlow<State> = _state.asStateFlow()

    private var nextId = 0L

    fun setInput(value: String) = _state.update { it.copy(input = value, error = null) }

    fun setBaseUrl(value: String) = _state.update { it.copy(baseUrl = value, error = null) }
    fun setApiKey(value: String) = _state.update { it.copy(apiKey = value, error = null) }
    fun setModel(value: String) = _state.update { it.copy(model = value, error = null) }

    fun openSetup() = _state.update { it.copy(showSetup = true) }

    fun closeSetup() {
        if (session.hasAiConfig()) _state.update { it.copy(showSetup = false, error = null) }
    }

    fun saveConfig() {
        val current = _state.value
        val base = current.baseUrl.trim().trimEnd('/')
        val key = current.apiKey.trim()
        val model = current.model.trim()
        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            _state.update { it.copy(error = "Base URL, API Key and Model are all required") }
            return
        }
        session.saveAiConfig(base, key, model)
        _state.update {
            it.copy(
                baseUrl = base,
                apiKey = key,
                model = model,
                showSetup = false,
                error = null,
            )
        }
    }

    fun send(preset: String? = null) {
        val current = _state.value
        if (current.sending) return
        if (!session.hasAiConfig()) {
            _state.update { it.copy(showSetup = true, error = "Configure your own AI endpoint first") }
            return
        }
        val text = (preset ?: current.input).trim()
        if (text.isBlank()) return
        val userId = nextId++
        val assistantId = nextId++
        _state.update {
            it.copy(
                messages = it.messages +
                    Message(userId, fromUser = true, text = text) +
                    Message(assistantId, fromUser = false, text = ""),
                input = "",
                sending = true,
                error = null,
            )
        }
        val history = current.messages.takeLast(10).map {
            AiMessage(if (it.fromUser) "user" else "assistant", it.text)
        }
        val payload = buildList {
            add(
                AiMessage(
                    role = "system",
                    content = "You are a concise, accurate language tutor inside a reading app. " +
                        "The learner is studying ${session.language()}. Answer in the language the user writes in. " +
                        "Focus on practical meaning, grammar and usage. When correcting writing, list only the key fixes. " +
                        "Avoid long introductions, markdown tables and repeating the question.",
                ),
            )
            addAll(history)
            add(AiMessage("user", text))
        }
        viewModelScope.launch {
            try {
                val reply = ai.chat(payload).trim()
                _state.update { state ->
                    if (reply.isBlank()) {
                        state.copy(
                            sending = false,
                            error = "The AI returned no content",
                            messages = state.messages.filterNot { it.id == assistantId },
                        )
                    } else {
                        state.copy(
                            sending = false,
                            messages = state.messages.map { message ->
                                if (message.id == assistantId) message.copy(text = reply) else message
                            },
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update { state ->
                    state.copy(
                        sending = false,
                        error = error.message ?: "AI request failed",
                        messages = state.messages.filterNot {
                            it.id == assistantId && it.text.isBlank()
                        },
                    )
                }
            }
        }
    }
}
