package com.inputa.reader.ui.vocabulary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.repository.VocabularyRepository
import com.inputa.reader.domain.vocab.VocabularyCounts
import com.inputa.reader.domain.vocab.VocabularyFilter
import com.inputa.reader.domain.vocab.VocabularyItem
import com.inputa.reader.domain.vocab.VocabularyPresenter
import com.inputa.reader.domain.vocab.WordLevels
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 生词本。
 *
 * 筛选与计数是领域层的纯函数（`VocabularyPresenter`），这里只管状态与写入。
 *
 * 顶栏的徽章与这个面板读的是**同一个** `observeCounts()` 流，所以两边永远一致 ——
 * 不需要任何手动同步。
 */
@HiltViewModel
class VocabularyViewModel @Inject constructor(
    private val vocabulary: VocabularyRepository,
) : ViewModel() {

    data class State(
        val filter: VocabularyFilter = VocabularyFilter.ACTIVE,
        val query: String = "",
        val counts: VocabularyCounts = VocabularyCounts(active = 0, mastered = 0),
        val items: List<VocabularyItem> = emptyList(),
    ) {
        val isEmpty: Boolean get() = items.isEmpty()
        /** 词库整体是空的（而不是被搜索/筛选筛掉了）—— 两种情况该给不同的提示。 */
        val libraryIsEmpty: Boolean get() = counts.total == 0
    }

    private val filter = MutableStateFlow(VocabularyFilter.ACTIVE)
    private val query = MutableStateFlow("")

    val state: StateFlow<State> = combine(vocabulary.observeAll(), filter, query) { words, currentFilter, currentQuery ->
        val all = words.map { (word, status) -> VocabularyItem(word, status) }
        State(
            filter = currentFilter,
            query = currentQuery,
            counts = VocabularyPresenter.counts(all),
            items = VocabularyPresenter.filter(all, currentFilter, currentQuery),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    fun setFilter(next: VocabularyFilter) {
        filter.value = next
    }

    fun setQuery(next: String) {
        query.value = next
    }

    /** 面板里的熟练度胶囊：点一下就覆盖写。 */
    fun setLevel(word: String, level: WordStatus) {
        viewModelScope.launch { vocabulary.setStatus(word, level) }
    }

    fun markMastered(word: String) {
        viewModelScope.launch { vocabulary.markMastered(word) }
    }

    /** 标为生词：把已掌握的词改回 5 级，这是明确的用户意图，所以覆盖。 */
    fun markAsNew(word: String) {
        viewModelScope.launch { vocabulary.setStatus(word, WordLevels.LEVELS.last()) }
    }

    fun removeWord(word: String) {
        viewModelScope.launch { vocabulary.removeWord(word) }
    }

    fun clearLibrary() {
        viewModelScope.launch { vocabulary.clear() }
    }
}
