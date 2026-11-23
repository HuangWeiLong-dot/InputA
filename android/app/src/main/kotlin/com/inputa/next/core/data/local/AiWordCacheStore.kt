package com.inputa.next.core.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI 单词解释的本地缓存。key = 学习语言 | 输出语言 | 小写单词，
 * 同一词同一输出语言只请求一次大模型，之后完全离线读取。
 */
@Singleton
class AiWordCacheStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("inputa_next_ai_words", Context.MODE_PRIVATE)

    fun get(learningLanguage: String, outputLanguage: String, word: String): String? =
        prefs.getString(key(learningLanguage, outputLanguage, word), null)
            ?.takeIf { it.isNotBlank() }

    fun put(
        learningLanguage: String,
        outputLanguage: String,
        word: String,
        text: String,
    ) {
        if (text.isBlank()) return
        prefs.edit()
            .putString(key(learningLanguage, outputLanguage, word), text.trim())
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(learningLanguage: String, outputLanguage: String, word: String): String =
        "${normalizeLanguageKey(learningLanguage)}|${normalizeLanguageKey(outputLanguage)}|" +
            word.trim().lowercase()

    private fun normalizeLanguageKey(code: String): String =
        code.trim().lowercase().substringBefore('-').substringBefore('_').ifBlank { "en" }
}
