package com.inputa.next.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.PlaylistEntry
import com.inputa.next.core.data.local.PlaylistStore
import com.inputa.next.core.media.LessonPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val store: PlaylistStore,
    private val player: LessonPlayer,
) : ViewModel() {

    val items: StateFlow<List<PlaylistEntry>> = store.items
    val playerState = player.state

    private var advancedFor: Int? = null

    init {
        viewModelScope.launch {
            player.state.collect { state ->
                val entry = state.entry ?: return@collect
                val ended = !state.playing &&
                    state.durationMs > 0 &&
                    state.positionMs >= state.durationMs - 800
                if (ended && advancedFor != entry.lessonId) {
                    advancedFor = entry.lessonId
                    playNext(entry)
                }
            }
        }
    }

    fun play(entry: PlaylistEntry) {
        advancedFor = null
        player.play(entry)
    }

    fun toggle() = player.toggle()

    fun stop() = player.stop()

    fun remove(lessonId: Int) {
        if (player.state.value.entry?.lessonId == lessonId) player.stop()
        store.remove(lessonId)
    }

    fun clear() {
        player.stop()
        store.clear()
    }

    fun playNext(current: PlaylistEntry? = player.state.value.entry) {
        val list = items.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.lessonId == current?.lessonId }
        val next = list.getOrNull(index + 1) ?: list.first()
        if (next.lessonId != current?.lessonId) {
            advancedFor = null
            player.play(next)
        }
    }

    fun playPrevious() {
        val list = items.value
        val current = player.state.value.entry ?: return
        val index = list.indexOfFirst { it.lessonId == current.lessonId }
        val previous = list.getOrNull(index - 1) ?: list.last()
        if (previous.lessonId != current.lessonId) {
            advancedFor = null
            player.play(previous)
        }
    }

    fun clearError() = player.clearError()
}
