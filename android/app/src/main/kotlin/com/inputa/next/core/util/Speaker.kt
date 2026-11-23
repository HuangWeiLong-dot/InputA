package com.inputa.next.core.util

import android.speech.tts.TextToSpeech
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * 系统 TTS 朗读。词库和阅读器都要发音，避免为单词再走一次网络。
 * 语言代码用 LingQ 的语言代码（en / es / ja…）。
 */
class Speaker internal constructor(
    private val holder: Array<TextToSpeech?>,
    private val languageCode: String,
) {
    fun speak(text: String) {
        val engine = holder[0] ?: return
        if (text.isBlank()) return
        runCatching {
            engine.language = Locale.forLanguageTag(languageCode)
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "inputa-next")
        }
    }

    fun stop() {
        runCatching { holder[0]?.stop() }
    }
}

@Composable
fun rememberSpeaker(languageCode: String): Speaker {
    val context = LocalContext.current
    val holder = remember { arrayOfNulls<TextToSpeech>(1) }
    DisposableEffect(languageCode) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                runCatching { holder[0]?.language = Locale.forLanguageTag(languageCode) }
            }
        }
        holder[0] = engine
        onDispose {
            runCatching {
                engine.stop()
                engine.shutdown()
            }
            holder[0] = null
        }
    }
    return remember(languageCode) { Speaker(holder, languageCode) }
}
