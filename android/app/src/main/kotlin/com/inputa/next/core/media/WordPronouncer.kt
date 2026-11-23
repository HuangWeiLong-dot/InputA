package com.inputa.next.core.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import com.inputa.next.core.data.LingqApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 单词发音：优先播 LingQ 官方 TTS 音频，接口没有返回音频或播放失败时
 * 回退系统 TTS，保证点词总有声音。
 *
 * TTS 返回的音频同样经 [AudioDownloader] 落到本地缓存再播放，避免 MediaPlayer
 * 直接拉远端音频时的 `error -38`；音频 URL 按 `语言|小写单词` 缓存。
 */
@Singleton
class WordPronouncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: LingqApi,
    private val downloader: AudioDownloader,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val urlCache = HashMap<String, String>()

    private var player: MediaPlayer? = null
    private var playGeneration = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingTts: Pair<String, String>? = null

    fun speak(language: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val cacheKey = language + "|" + trimmed.lowercase()
        val requestGeneration = ++playGeneration
        scope.launch {
            val url = urlCache[cacheKey] ?: runCatching {
                api.ttsAudioUrl(language, trimmed)
            }.getOrNull()?.also { urlCache[cacheKey] = it }
            if (requestGeneration != playGeneration) return@launch
            if (url.isNullOrBlank()) {
                speakWithSystem(language, trimmed)
                return@launch
            }
            val file = downloader.download(url, "word-$cacheKey")
            if (requestGeneration != playGeneration) return@launch
            if (file != null) {
                playFile(file, requestGeneration) { speakWithSystem(language, trimmed) }
            } else {
                speakWithSystem(language, trimmed)
            }
        }
    }

    fun stop() {
        playGeneration++
        releasePlayer()
        runCatching { tts?.stop() }
    }

    private fun playFile(file: File, requestGeneration: Int, fallback: () -> Unit) {
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
            mediaPlayer.setOnPreparedListener { prepared ->
                if (requestGeneration == playGeneration) prepared.start()
            }
            mediaPlayer.setOnCompletionListener {
                if (requestGeneration == playGeneration) releasePlayer()
            }
            mediaPlayer.setOnErrorListener { _, _, _ ->
                if (requestGeneration == playGeneration) {
                    releasePlayer()
                    fallback()
                }
                true
            }
            mediaPlayer.prepareAsync()
        }.onFailure {
            if (requestGeneration == playGeneration) {
                releasePlayer()
                fallback()
            }
        }
    }

    private fun speakWithSystem(language: String, text: String) {
        val engine = ensureTts() ?: return
        if (!ttsReady) {
            pendingTts = language to text
            return
        }
        applyLanguage(engine, language)
        runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
        }
    }

    private fun ensureTts(): TextToSpeech? {
        tts?.let { return it }
        val created = TextToSpeech(context.applicationContext) { status ->
            mainHandler.post {
                if (status != TextToSpeech.SUCCESS) return@post
                ttsReady = true
                pendingTts?.let { (language, text) ->
                    pendingTts = null
                    tts?.let { engine ->
                        applyLanguage(engine, language)
                        runCatching {
                            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                        }
                    }
                }
            }
        }
        tts = created
        return created
    }

    private fun applyLanguage(engine: TextToSpeech, language: String) {
        val locale = Locale.forLanguageTag(language.replace('_', '-'))
        val result = runCatching { engine.setLanguage(locale) }.getOrNull()
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            engine.language = Locale.getDefault()
        }
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

    private companion object {
        const val UTTERANCE_ID = "inputa-word"
    }
}
