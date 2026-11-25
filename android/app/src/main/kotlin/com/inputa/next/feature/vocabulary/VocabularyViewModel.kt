package com.inputa.next.feature.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.Meaning
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.VocabCard
import com.inputa.next.core.data.WordMeanings
import com.inputa.next.core.data.local.LocalWordStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VocabFilter(val label: String) {
    All("All"),
    Inputa("Inputa"),
    Known("Known"),
    Ignored("Ignored"),
}

/**
 * 词库全部来自本机记录；LingQ 接口只用来查释义。
 *
 * 本地状态编码：-1 忽略、0 已知、1–4 熟练度。
 */
@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val wordMeanings: WordMeanings,
    private val session: SessionStore,
    private val localWords: LocalWordStore,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val query: String = "",
        val filter: VocabFilter = VocabFilter.All,
        val cards: List<VocabCard> = emptyList(),
        val total: Int? = null,
        val selected: VocabCard? = null,
        val meanings: List<Meaning> = emptyList(),
        val meaningsLoading: Boolean = false,
        /** 词卡最后一行的机器译文，单独渲染，不受 meanings 条数上限影响。 */
        val translation: String? = null,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val language: String get() = session.language()

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        val cards = localCards()
        _state.update {
            it.copy(loading = false, cards = cards, total = cards.size)
        }
    }

    private fun localCards(): List<VocabCard> =
        localWords.load(language)
            .map { (term, status) ->
                VocabCard(
                    id = term.hashCode(),
                    term = term,
                    translation = null,
                    level = status.takeIf { it in 1..4 },
                    status = status,
                    tags = emptyList(),
                )
            }
            .sortedBy { it.term }

    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setFilter(filter: VocabFilter) = _state.update { it.copy(filter = filter) }

    fun select(card: VocabCard) {
        _state.update {
            it.copy(selected = card, meanings = emptyList(), meaningsLoading = true, translation = null)
        }
        viewModelScope.launch {
            // 先出词典，再补译文：弹窗不会因为等翻译而整体晚一个请求。
            val entries = wordMeanings.dictionary(language, card.term)
            _state.update {
                if (it.selected?.id == card.id) it.copy(meanings = entries, meaningsLoading = false) else it
            }
            val translation = wordMeanings.translation(language, card.term)
            _state.update {
                if (it.selected?.id == card.id) it.copy(translation = translation) else it
            }
        }
    }

    fun dismissSelection() = _state.update { it.copy(selected = null, meanings = emptyList()) }

    /** UI 用 5 表示已知，本地存 0；1–4 与 -1 原样保存。 */
    fun updateStatus(status: Int) {
        val card = _state.value.selected ?: return
        val stored = if (status == 5) 0 else status
        localWords.put(language, card.term, stored)
        val cards = localCards()
        _state.update {
            it.copy(
                cards = cards,
                total = cards.size,
                selected = null,
                meanings = emptyList(),
                notice = "Updated \"${card.term}\"",
            )
        }
    }

    fun deleteSelected() {
        val card = _state.value.selected ?: return
        localWords.remove(language, card.term)
        val cards = localCards()
        _state.update {
            it.copy(
                cards = cards,
                total = cards.size,
                selected = null,
                meanings = emptyList(),
                notice = "Removed \"${card.term}\"",
            )
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun filtered(): List<VocabCard> {
        val state = _state.value
        val byStatus = state.cards.filter { card ->
            when (state.filter) {
                VocabFilter.All -> true
                VocabFilter.Inputa -> card.status in 1..4
                VocabFilter.Known -> card.status == 0
                VocabFilter.Ignored -> card.status == -1
            }
        }
        if (state.query.isBlank()) return byStatus
        val needle = state.query.trim().lowercase()
        return byStatus.filter { it.term.lowercase().contains(needle) }
    }
}
