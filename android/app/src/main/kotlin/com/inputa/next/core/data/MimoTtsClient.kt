package com.inputa.next.core.data

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MimoTtsException(message: String) : Exception(message)

/**
 * MiMo Speech Synthesis v2.5 的非流式客户端。
 * 文章文本必须放在 assistant 消息中，音频以 Base64 WAV 返回。
 */
@Singleton
class MimoTtsClient @Inject constructor(
    private val json: Json,
    private val session: SessionStore,
    @param:ApplicationContext private val context: Context,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()

    suspend fun synthesize(text: String): File = withContext(Dispatchers.IO) {
        val apiKey = session.mimoApiKey().trim()
        if (apiKey.isBlank()) throw MimoTtsException("MiMo API Key is not configured")

        val article = text.trim()
        if (article.isBlank()) throw MimoTtsException("Paste some text first to generate audio.")
        if (article.length > MAX_CHARS) {
            throw MimoTtsException("Text is too long for one audio request")
        }

        val directory = File(context.cacheDir, "mimo_tts").apply { mkdirs() }
        val target = File(directory, "${cacheKey(article)}.wav")
        if (target.isFile && target.length() > 0L) return@withContext target
        cleanupOldCache(directory)

        val body = buildJsonObject {
            put("model", MODEL)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put(
                        "content",
                        "Read the following article aloud in a clear, natural narrator voice. " +
                            "Output only the speech audio.",
                    )
                }
                addJsonObject {
                    put("role", "assistant")
                    put("content", article)
                }
            }
            put("stream", false)
            putJsonObject("audio") {
                put("format", FORMAT)
                put("voice", VOICE)
            }
        }
        val request = Request.Builder()
            .url(URL)
            .header("api-key", apiKey)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val temporary = File(directory, "${target.name}.part")
        try {
            val responseBody = awaitResponse(client.newCall(request))
            val root = runCatching { json.parseToJsonElement(responseBody).jsonObject }
                .getOrElse { throw MimoTtsException("The AI response was not JSON") }
            val audioData = root.array("choices")?.firstOrNull()
                ?.let { it as? kotlinx.serialization.json.JsonObject }
                ?.obj("message")
                ?.obj("audio")
                ?.string("data")
                ?.takeIf { it.isNotBlank() }
                ?: throw MimoTtsException("MiMo returned no audio data")

            val wav = Base64.decode(audioData, Base64.DEFAULT)
            if (wav.size < 12 || wav.copyOfRange(0, 4).decodeToString() != "RIFF" ||
                wav.copyOfRange(8, 12).decodeToString() != "WAVE"
            ) {
                throw MimoTtsException("MiMo returned unexpected audio data")
            }

            temporary.outputStream().use { it.write(wav) }
            if (!temporary.renameTo(target)) {
                target.delete()
                if (!temporary.renameTo(target)) {
                    temporary.copyTo(target, overwrite = true)
                    temporary.delete()
                }
            }
            target
        } catch (cancellation: CancellationException) {
            temporary.delete()
            throw cancellation
        } catch (error: MimoTtsException) {
            temporary.delete()
            throw error
        } catch (error: Exception) {
            temporary.delete()
            throw MimoTtsException(
                "Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}",
            )
        }
    }

    private suspend fun awaitResponse(call: Call): String =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = it.body.string()
                        if (!it.isSuccessful) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(
                                    MimoTtsException(describe(it.code, body)),
                                )
                            }
                        } else if (continuation.isActive) {
                            continuation.resume(body)
                        }
                    }
                }
            })
        }

    private fun describe(code: Int, body: String): String {
        val detail = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root.obj("error")?.string("message")
                ?: root.string("message", "detail")
                ?: root.string("error")
        }.getOrNull()
        val hint = when (code) {
            401, 403 -> "Invalid MiMo API key"
            429 -> "Too many requests"
            in 500..599 -> "MiMo service is temporarily unavailable"
            else -> "MiMo audio request failed (HTTP $code)"
        }
        return if (detail.isNullOrBlank()) hint else "$hint: $detail"
    }

    private fun cacheKey(article: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((article + "|" + VOICE + "|" + FORMAT).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun cleanupOldCache(directory: File) {
        val cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".wav") && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    private companion object {
        const val URL = "https://api.xiaomimimo.com/v1/chat/completions"
        const val MODEL = "mimo-v2.5-tts"
        const val VOICE = "mimo_default"
        const val FORMAT = "wav"
        const val MAX_CHARS = 5000
        const val CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
