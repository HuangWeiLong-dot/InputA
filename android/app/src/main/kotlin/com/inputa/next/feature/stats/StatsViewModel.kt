package com.inputa.next.feature.stats

import androidx.lifecycle.ViewModel
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.StatsSnapshot
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** 统计页只读本机词库，不再请求 LingQ 的 profile / stats。 */
@HiltViewModel
class StatsViewModel @Inject constructor(
    private val session: SessionStore,
    private val localWords: LocalWordStore,
    private val settings: SettingsStore,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val stats: StatsSnapshot? = null,
        val dailyGoal: Int = 1000,
        val learnedToday: Int = 0,
        val ignoredWords: Int = 0,
        val totalWords: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val language: String get() = session.language()

    fun load() {
        val entries = localWords.load(language).values
        val known = entries.count { it == 0 }
        val inputa = entries.count { it in 1..4 }
        val ignored = entries.count { it == -1 }
        _state.update {
            it.copy(
                loading = false,
                stats = StatsSnapshot(
                    knownWords = known,
                    wordsRead = known + inputa,
                    lingqs = inputa,
                    hoursListened = null,
                    streak = null,
                    coins = null,
                    chart = emptyList(),
                ),
                dailyGoal = settings.dailyGoal.value,
                learnedToday = localWords.todayLearned(language),
                ignoredWords = ignored,
                totalWords = known + inputa + ignored,
            )
        }
    }
}
