package com.inputa.next.feature.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.Meaning
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.VocabCard
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 复习池完全来自本机词库；LingQ 只用于查释义。 */
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val localWords: LocalWordStore,
    private val settings: SettingsStore,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val cards: List<VocabCard> = emptyList(),
        val index: Int = 0,
        val revealed: Boolean = false,
        val meanings: List<Meaning> = emptyList(),
        val meaningsLoading: Boolean = false,
        val done: Boolean = false,
        val notice: String? = null,
    ) {
        val current: VocabCard? get() = cards.getOrNull(index)
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val language: String get() = session.language()

    fun load() {
        val base = localWords.load(language)
            .filterValues { it in 1..4 }
            .map { (term, status) ->
                VocabCard(
                    id = term.hashCode(),
                    term = term,
                    translation = null,
                    level = status,
                    status = status,
                    tags = emptyList(),
                )
            }
        val ordered = if (settings.reviewRandom.value) base.shuffled() else base.sortedBy { it.level }
        val limit = settings.reviewSessionSize.value
        val pool = if (limit > 0) ordered.take(limit) else ordered
        _state.update {
            State(
                loading = false,
                cards = pool,
                done = pool.isEmpty(),
                index = 0,
                revealed = false,
                error = if (pool.isEmpty()) "No Inputa words available to review." else null,
            )
        }
    }

    fun reveal() {
        val card = _state.value.current ?: return
        _state.update { it.copy(revealed = true, meanings = emptyList(), meaningsLoading = true) }
        viewModelScope.launch {
            val meanings = runCatching { api.meanings(language, card.term) }.getOrDefault(emptyList())
            _state.update {
                if (it.current?.id == card.id) {
                    it.copy(meanings = meanings, meaningsLoading = false)
                } else {
                    it
                }
            }
        }
    }

    /** 复习结果只改本机熟练度，不写回 LingQ。 */
    fun grade(known: Boolean) {
        val card = _state.value.current ?: return
        val base = card.status ?: 1
        val target = if (known) (base + 1).coerceAtMost(4) else 1
        localWords.put(language, card.term, target)
        advance()
    }

    fun skip() {
        advance()
    }

    private fun advance() {
        _state.update { state ->
            val next = state.index + 1
            if (next >= state.cards.size) {
                state.copy(done = true, revealed = false, meanings = emptyList())
            } else {
                state.copy(index = next, revealed = false, meanings = emptyList(), meaningsLoading = false)
            }
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }
}
