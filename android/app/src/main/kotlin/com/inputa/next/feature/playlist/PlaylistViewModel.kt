package com.inputa.next.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.PlaylistEntry
import com.inputa.next.core.data.local.PlaylistStore
import com.inputa.next.core.media.LessonPlayer
import com.inputa.next.core.media.RepeatMode
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
                    when (state.repeatMode) {
                        // 单曲循环由 LessonPlayer 在 completion 里自行重播。
                        RepeatMode.One -> Unit
                        RepeatMode.All -> playNext(entry)
                        // 关闭循环：顺序播下一首，列表播完就停。
                        RepeatMode.Off -> playNextLinear(entry)
                    }
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

    /** 循环关闭时的自动推进：不回绕，当前课不在列表或已到末尾就停。 */
    private fun playNextLinear(current: PlaylistEntry) {
        val list = items.value
        val index = list.indexOfFirst { it.lessonId == current.lessonId }
        if (index < 0) return
        val next = list.getOrNull(index + 1) ?: return
        if (next.lessonId != current.lessonId) {
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
