package com.inputa.next.core.data

import com.inputa.next.core.data.local.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/**
 * 点词弹窗的两块内容：
 *
 *  - [dictionary]：LingQ 词典释义，和以前完全一样（不替换、不删减）；
 *  - [translation]：弹窗最后一行那句译文。
 *
 * 译文只用词典和翻译服务，**不碰 App 里的 AI 配置**：
 *  1. 有道词典（英/日/韩都有中文词条）；
 *  2. Bing 网页翻译兜底。
 *
 * 全部失败返回 null，UI 那边就不显示最后一行。
 */
@Singleton
class WordMeanings @Inject constructor(
    private val api: LingqApi,
    private val translator: WordTranslator,
    private val settings: SettingsStore,
) {

    suspend fun dictionary(language: String, term: String): List<Meaning> = try {
        api.meanings(language, term)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        emptyList()
    }

    /** 关掉「翻译单词」或翻译失败时返回 null。 */
    suspend fun translation(language: String, term: String): String? {
        if (!settings.autoTranslate.value) return null
        val target = settings.effectiveTranslationLanguage()
        return try {
            translator.translate(term, target, language)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    /** 整句翻译。和单词共用同一个开关与目标语言。 */
    suspend fun sentence(language: String, text: String): String? {
        if (!settings.autoTranslate.value) return null
        val target = settings.effectiveTranslationLanguage()
        return try {
            translator.translateSentence(text, target, language)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }
}
