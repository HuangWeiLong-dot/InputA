package com.inputa.reader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inputa.reader.BuildConfig
import com.inputa.reader.domain.model.AppTheme
import com.inputa.reader.domain.model.DEFAULT_FONT_SIZE_SP
import com.inputa.reader.domain.model.DEFAULT_LINE_HEIGHT
import com.inputa.reader.domain.model.DEFAULT_SERVER_BASE_URL
import com.inputa.reader.domain.model.ReaderSettings
import com.inputa.reader.domain.model.TtsProvider
import com.inputa.reader.domain.model.normalizeBaseUrl
import com.inputa.reader.domain.repository.SettingsRepository
import com.inputa.reader.domain.text.WORDS_PER_PAGE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 设置的持久化。
 *
 * **每个 key 单独取默认值**（`prefs[KEY] ?: default`），这正是 Web 版那段
 * `{ ...DEFAULT_SETTINGS, ...parsed }` 字段级合并要做的事，但这里是结构性的：
 * DataStore 里每个键独立存在，漏了一个就落到它自己的默认值，不可能拿到 `undefined`。
 * Web 版当年就是因为直接把 `JSON.parse` 的结果返回，害得每次新增设置项，
 * 老用户的字号都变成 `undefinedpx`。
 *
 * 写入也是单键 `edit`，不用「一坨 JSON 整体覆盖」：单键写入不可能损坏相邻的设置。
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<ReaderSettings> = dataStore.data
        // 磁盘读失败不该让整个应用崩溃 —— 退回默认值，用户至少还能读书。
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it.toSettings() }

    // 块体而不是表达式体：这些方法在接口上返回 Unit，而 `edit` 返回 Preferences，
    // 写成 `= edit { ... }` 会推断出错误的返回类型。
    override suspend fun setFontSize(sp: Int) { edit { it[Keys.FONT_SIZE_SP] = sp } }
    override suspend fun setTheme(theme: AppTheme) { edit { it[Keys.THEME] = theme.key } }
    override suspend fun setLineHeight(height: Float) { edit { it[Keys.LINE_HEIGHT] = height } }
    override suspend fun setTtsProvider(provider: TtsProvider) { edit { it[Keys.TTS_PROVIDER] = provider.key } }
    override suspend fun setTtsVoice(voice: String) { edit { it[Keys.TTS_VOICE] = voice } }
    override suspend fun setTtsRate(rate: String) { edit { it[Keys.TTS_RATE] = rate } }
    override suspend fun setTtsCustomUrl(template: String) { edit { it[Keys.TTS_CUSTOM_URL] = template } }
    override suspend fun setBionicEnabled(enabled: Boolean) { edit { it[Keys.BIONIC_ENABLED] = enabled } }
    override suspend fun setReadingRulerEnabled(enabled: Boolean) { edit { it[Keys.RULER_ENABLED] = enabled } }
    override suspend fun setWordsPerPage(count: Int) { edit { it[Keys.WORDS_PER_PAGE] = count } }

    override suspend fun setServerBaseUrl(raw: String): String? {
        val normalized = normalizeBaseUrl(raw) ?: return null
        edit { it[Keys.SERVER_BASE_URL] = normalized }
        return normalized
    }

    /**
     * 新装时用的后端地址：debug 指向模拟器里的宿主机，release 用 `:domain` 的线上常量。
     *
     * 这层包装放在 app 模块而不是 `:domain`，是因为构建类型的差异只有这里看得见 ——
     * `:domain` 不依赖 android/androidx，因此读不到 `BuildConfig`（而那正是它能在纯 JVM
     * 上跑测试的原因）。Gradle 侧只定义 debug 的覆盖值，线上值仍然以
     * `DEFAULT_SERVER_BASE_URL` 为唯一来源，不会多出一份会悄悄漂移的副本。
     */
    private val defaultServerBaseUrl: String
        get() = BuildConfig.DEBUG_SERVER_BASE_URL.ifEmpty { DEFAULT_SERVER_BASE_URL }

    override suspend fun currentServerBaseUrl(): String =
        dataStore.data.first()[Keys.SERVER_BASE_URL] ?: defaultServerBaseUrl

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }

    private fun Preferences.toSettings(): ReaderSettings = ReaderSettings(
        fontSizeSp = this[Keys.FONT_SIZE_SP] ?: DEFAULT_FONT_SIZE_SP,
        theme = AppTheme.fromKey(this[Keys.THEME]),
        lineHeight = this[Keys.LINE_HEIGHT] ?: DEFAULT_LINE_HEIGHT,
        ttsProvider = TtsProvider.fromKey(this[Keys.TTS_PROVIDER]),
        ttsVoice = this[Keys.TTS_VOICE] ?: "",
        ttsRate = this[Keys.TTS_RATE] ?: "default",
        ttsCustomUrlTemplate = this[Keys.TTS_CUSTOM_URL] ?: "",
        bionicEnabled = this[Keys.BIONIC_ENABLED] ?: false,
        readingRulerEnabled = this[Keys.RULER_ENABLED] ?: false,
        serverBaseUrl = this[Keys.SERVER_BASE_URL] ?: defaultServerBaseUrl,
        wordsPerPage = this[Keys.WORDS_PER_PAGE] ?: WORDS_PER_PAGE,
    )

    private object Keys {
        val FONT_SIZE_SP = intPreferencesKey("font_size_sp")
        val THEME = stringPreferencesKey("theme")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val TTS_PROVIDER = stringPreferencesKey("tts_provider")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val TTS_RATE = stringPreferencesKey("tts_rate")
        val TTS_CUSTOM_URL = stringPreferencesKey("tts_custom_url")
        val BIONIC_ENABLED = booleanPreferencesKey("bionic_enabled")
        val RULER_ENABLED = booleanPreferencesKey("ruler_enabled")
        val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
        val WORDS_PER_PAGE = intPreferencesKey("words_per_page")
    }
}
