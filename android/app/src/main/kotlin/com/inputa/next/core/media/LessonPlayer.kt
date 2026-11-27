package com.inputa.next.core.media

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.inputa.next.core.data.PlaylistEntry
import com.inputa.next.core.data.absoluteUrl
import com.inputa.next.core.data.local.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** 循环模式：关闭 / 单曲循环 / 列表循环。 */
enum class RepeatMode { Off, One, All }

/**
 * 课程音频播放。
 *
 * MediaPlayer 直接读 LingQ 的远端音频会因为没有鉴权/跳转处理而报 `error -38`，
 * 所以先经 [AudioDownloader] 把音频落到本地缓存，再播放本地文件；同一课程
 * 第二次播放直接命中缓存。
 *
 * 播放期间会拉起 [LessonAudioService] 前台服务：应用切后台后进程不被回收，
 * 通知栏展示播放状态并提供播放/暂停、停止控制。
 */
@Singleton
class LessonPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: AudioDownloader,
    private val settings: SettingsStore,
) {
    data class State(
        val entry: PlaylistEntry? = null,
        val playing: Boolean = false,
        val preparing: Boolean = false,
        val positionMs: Int = 0,
        val durationMs: Int = 0,
        val error: String? = null,
        val repeatMode: RepeatMode = RepeatMode.All,
        val volume: Float = 1f,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(State(volume = settings.lessonVolume.value))
    val state = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var generation = 0

    fun play(entry: PlaylistEntry) {
        val url = entry.audioUrl?.absoluteUrl()
        if (url == null) {
            _state.value = persistedState(entry = entry, error = "This lesson has no audio.")
            return
        }
        releasePlayer()
        val requestGeneration = ++generation
        _state.value = persistedState(entry = entry, preparing = true)
        startPlaybackService()
        scope.launch {
            val file = downloader.download(url, "lesson-${entry.lessonId}")
            if (requestGeneration != generation) return@launch
            if (file == null) {
                _state.update {
                    it.copy(preparing = false, error = "Could not load this lesson's audio.")
                }
                return@launch
            }
            playFile(entry, file, requestGeneration)
        }
    }

    private fun playFile(entry: PlaylistEntry, file: File, requestGeneration: Int) {
        releasePlayer()
        val mediaPlayer = MediaPlayer()
        player = mediaPlayer
        runCatching {
            mediaPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            mediaPlayer.setDataSource(file.absolutePath)
            mediaPlayer.setVolume(_state.value.volume, _state.value.volume)
            mediaPlayer.setOnPreparedListener { prepared ->
                if (requestGeneration != generation) return@setOnPreparedListener
                _state.update {
                    it.copy(
                        playing = true,
                        preparing = false,
                        durationMs = prepared.duration.coerceAtLeast(0),
                    )
                }
                prepared.start()
                startTicker()
            }
            mediaPlayer.setOnCompletionListener {
                if (requestGeneration != generation) return@setOnCompletionListener
                if (_state.value.repeatMode == RepeatMode.One) {
                    // 单曲循环：回到开头继续播，状态不落进“已结束”。
                    runCatching {
                        mediaPlayer.seekTo(0)
                        mediaPlayer.start()
                    }
                    _state.update { it.copy(playing = true, positionMs = 0) }
                } else {
                    _state.update { state -> state.copy(playing = false, positionMs = state.durationMs) }
                }
            }
            mediaPlayer.setOnErrorListener { _, what, extra ->
                if (requestGeneration == generation) {
                    _state.update {
                        it.copy(playing = false, preparing = false, error = "Audio error ($what/$extra).")
                    }
                }
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure { error ->
            _state.update {
                it.copy(
                    playing = false,
                    preparing = false,
                    error = error.message ?: "Could not play this audio.",
                )
            }
        }
    }

    fun toggle() {
        val mediaPlayer = player ?: return
        if (_state.value.preparing) return
        runCatching {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                _state.update { it.copy(playing = false) }
            } else {
                // 播到结尾再按播放：从头开始，而不是立刻再次触发 completion。
                val duration = mediaPlayer.duration
                if (duration > 0 && mediaPlayer.currentPosition >= duration - 200) {
                    mediaPlayer.seekTo(0)
                }
                mediaPlayer.start()
                _state.update { it.copy(playing = true) }
                startTicker()
            }
        }
    }

    fun seekTo(positionMs: Int) {
        player?.let { mediaPlayer ->
            runCatching {
                mediaPlayer.seekTo(positionMs.coerceIn(0, mediaPlayer.duration.coerceAtLeast(0)))
                _state.update { it.copy(positionMs = positionMs) }
            }
        }
    }

    fun setRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    /** 应用内音量（0f..1f），持久化到设置，独立于系统媒体音量。 */
    fun setVolume(value: Float) {
        val volume = value.coerceIn(0f, 1f)
        settings.setLessonVolume(volume)
        _state.update { it.copy(volume = volume) }
        runCatching { player?.setVolume(volume, volume) }
    }

    fun stop() {
        generation++
        releasePlayer()
        _state.value = persistedState()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    /** 换曲/停止时保留循环模式和音量这两个用户偏好。 */
    private fun persistedState(
        entry: PlaylistEntry? = null,
        preparing: Boolean = false,
        error: String? = null,
    ): State {
        val current = _state.value
        return State(
            entry = entry,
            preparing = preparing,
            error = error,
            repeatMode = current.repeatMode,
            volume = current.volume,
        )
    }

    private fun startPlaybackService() {
        runCatching {
            context.startForegroundService(Intent(context, LessonAudioService::class.java))
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val mediaPlayer = player ?: break
                val position = runCatching { mediaPlayer.currentPosition }.getOrDefault(0)
                _state.update { it.copy(positionMs = position) }
                delay(500)
            }
        }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        ticker = null
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
