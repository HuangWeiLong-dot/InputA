package com.inputa.next.core.media

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 导入页生成的本地 WAV 试听，不接入课程播放器或播放列表。 */
class PreviewAudioPlayer {
    data class State(
        val preparing: Boolean = false,
        val playing: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var generation = 0

    fun play(file: File) {
        if (!file.isFile || file.length() == 0L) {
            _state.value = State(error = "Could not play the preview audio.")
            return
        }
        val requestGeneration = ++generation
        releasePlayer()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        _state.value = State(preparing = true)
        runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setOnPreparedListener { prepared ->
                if (requestGeneration == generation) {
                    prepared.start()
                    _state.value = State(playing = true)
                }
            }
            mediaPlayer.setOnCompletionListener {
                if (requestGeneration == generation) {
                    releasePlayer()
                    _state.value = State()
                }
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                if (requestGeneration == generation) {
                    releasePlayer()
                    _state.value = State(error = "Could not play the preview audio.")
                }
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure {
            if (requestGeneration == generation) {
                releasePlayer()
                _state.value = State(error = "Could not play the preview audio.")
            }
        }
    }

    fun toggle(file: File) {
        if (_state.value.playing) {
            stop()
        } else {
            play(file)
        }
    }

    fun stop() {
        generation++
        releasePlayer()
        _state.value = State()
    }

    fun release() {
        generation++
        releasePlayer()
        _state.value = State()
    }

    private fun releasePlayer() {
        player?.let { mediaPlayer ->
            runCatching {
                mediaPlayer.setOnPreparedListener(null)
                mediaPlayer.setOnCompletionListener(null)
                mediaPlayer.setOnErrorListener(null)
                mediaPlayer.stop()
            }
            runCatching { mediaPlayer.release() }
        }
        player = null
    }
}
