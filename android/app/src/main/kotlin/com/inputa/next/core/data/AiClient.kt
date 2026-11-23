package com.inputa.next.core.data

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiMessage(val role: String, val content: String)

class AiException(message: String) : Exception(message)

/**
 * 用户自己的 AI 接口。默认按 OpenAI 兼容协议请求
 * `POST {baseUrl}/chat/completions`，所以 OpenAI / DeepSeek / Ollama / 自建网关都能用。
 */
@Singleton
class AiClient @Inject constructor(
    private val json: Json,
    private val session: SessionStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    suspend fun chat(
        messages: List<AiMessage>,
        temperature: Double = 0.7,
        maxTokens: Int = 700,
    ): String = withContext(Dispatchers.IO) {
        val base = session.aiBaseUrl().trim().trimEnd('/')
        val key = session.aiApiKey().trim()
        val model = session.aiModel().trim()
        if (base.isBlank() || key.isBlank() || model.isBlank()) {
            throw AiException("Fill in Base URL, API Key and Model in AI settings first.")
        }
        val url = if (base.endsWith("/chat/completions")) {
            base
        } else {
            "$base/chat/completions"
        }
        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                messages.forEach { message ->
                    addJsonObject {
                        put("role", message.role)
                        put("content", message.content)
                    }
                }
            }
            put("stream", false)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $key")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body.string()
                if (!response.isSuccessful) {
                    throw AiException(describe(response.code, text))
                }
                val root = runCatching {
                    json.parseToJsonElement(text).jsonObject
                }.getOrElse { throw AiException("The AI response was not JSON") }
                extractText(root) ?: throw AiException("The AI response contained no text")
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (ai: AiException) {
            throw ai
        } catch (error: Exception) {
            throw AiException("Network error: ${error.message ?: error::class.simpleName ?: "Unknown"}")
        }
    }

    /**
     * 单词精讲：输出语言独立于学习语言，返回带固定标签的纯文本，
     * UI 解析 [MEANING] / [GRAMMAR] / [USAGE] / [EXAMPLES] 后分区展示。
     */
    suspend fun explainWord(
        word: String,
        context: String,
        learningLanguage: String,
        outputLanguage: String,
        lessonTitle: String? = null,
    ): String {
        val target = languageEnglishName(learningLanguage)
        val output = explanationLanguageName(outputLanguage)
        val system = """
            You are an expert $target tutor for a learner reading $target content.
            Explain exactly one word from a lesson. Write the whole answer in $output, except the target word, its forms and example sentences.
            Use this exact plain-text structure and keep the bracketed tags:
            [MEANING] Explain the meaning in the given context in one or two sentences, then mention other common meanings briefly.
            [GRAMMAR] Part of speech, base form, gender/plural or conjugation, and any irregularity worth knowing.
            [USAGE] Register, collocations, common mistakes and when to use it.
            [EXAMPLES] Two short $target sentences with $output translations.
            Be accurate, practical and concise. No markdown headings and no code blocks. At most 220 words.
        """.trimIndent()
        val user = buildString {
            appendLine("Word: $word")
            if (!lessonTitle.isNullOrBlank()) appendLine("Lesson: $lessonTitle")
            appendLine("Context: $context")
            append("Explain this word as it is used in the context.")
        }
        return chat(
            messages = listOf(AiMessage("system", system), AiMessage("user", user)),
            temperature = 0.3,
            maxTokens = 700,
        )
    }

    private fun languageEnglishName(code: String): String = when (normalizeLanguage(code)) {
        "en" -> "English"
        "es" -> "Spanish"
        "fr" -> "French"
        "de" -> "German"
        "it" -> "Italian"
        "pt" -> "Portuguese"
        "ru" -> "Russian"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        "zh" -> "Chinese"
        "ar" -> "Arabic"
        "nl" -> "Dutch"
        "sv" -> "Swedish"
        "pl" -> "Polish"
        "tr" -> "Turkish"
        "he" -> "Hebrew"
        "fi" -> "Finnish"
        "no" -> "Norwegian"
        "da" -> "Danish"
        "cs" -> "Czech"
        "el" -> "Greek"
        "uk" -> "Ukrainian"
        "vi" -> "Vietnamese"
        "th" -> "Thai"
        "id" -> "Indonesian"
        "ro" -> "Romanian"
        "hu" -> "Hungarian"
        "ca" -> "Catalan"
        else -> code.uppercase()
    }

    private fun explanationLanguageName(code: String): String = when (normalizeLanguage(code)) {
        "zh" -> "Simplified Chinese"
        "ja" -> "Japanese"
        "ko" -> "Korean"
        else -> "English"
    }

    private fun extractText(root: JsonObject): String? {
        root.array("choices")?.firstOrNull()?.let { it as? JsonObject }?.let { choice ->
            contentOf(choice.obj("message")?.get("content"))?.let { return it }
            choice.string("text", "content")?.takeIf { it.isNotBlank() }?.let { return it }
            contentOf(choice.obj("delta")?.get("content"))?.let { return it }
        }
        contentOf(root["output_text"])?.let { return it }
        contentOf(root["response"])?.let { return it }
        contentOf(root["content"])?.let { return it }
        contentOf(root["text"])?.let { return it }
        contentOf(root.obj("message")?.get("content"))?.let { return it }
        contentOf(root.obj("data")?.get("content"))?.let { return it }
        return null
    }

    private fun contentOf(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.content.takeIf { it.isNotBlank() }
        is JsonArray -> element.mapNotNull { part ->
            (part as? JsonObject)?.string("text", "content")
                ?: (part as? JsonPrimitive)?.content
        }.joinToString("").ifBlank { null }

        is JsonObject -> element.string("text", "content")
        else -> null
    }

    private fun describe(code: Int, body: String): String {
        val detail = runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            root.obj("error")?.string("message")
                ?: root.string("message", "detail")
                ?: root.string("error")
        }.getOrNull()
        val hint = when (code) {
            401, 403 -> "Invalid API key or no permission"
            404 -> "Base URL is wrong; /chat/completions was not found"
            429 -> "Too many requests"
            in 500..599 -> "AI service is temporarily unavailable"
            else -> "AI request failed (HTTP $code)"
        }
        return if (detail.isNullOrBlank()) hint else "$hint: $detail"
    }
}
