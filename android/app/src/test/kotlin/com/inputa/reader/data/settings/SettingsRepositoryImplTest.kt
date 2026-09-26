package com.inputa.reader.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inputa.reader.domain.model.AppTheme
import com.inputa.reader.domain.model.DEFAULT_FONT_SIZE_SP
import com.inputa.reader.domain.model.DEFAULT_LINE_HEIGHT
import com.inputa.reader.domain.model.DEFAULT_SERVER_BASE_URL
import com.inputa.reader.domain.model.TtsProvider
import com.inputa.reader.domain.text.WORDS_PER_PAGE
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 设置持久化。
 *
 * 这一批测试保护的是那条**会把 `undefined` 灌进渲染**的路径。Web 版当年
 * `loadSettings` 直接返回 `JSON.parse` 的结果，于是每次新增设置项，老用户的该字段
 * 就是 `undefined`，字号渲染成 `undefinedpx`。DataStore 的「每键独立默认值」从结构上
 * 根除了它，但前提是**每个字段都写了默认值** —— 这就是第一条断言的意义。
 *
 * 用内存替身而不是真正的文件 DataStore，原因具体：DataStore 的文件实现在 Windows 上
 * 无法对同一个文件做第二次写入（写流程是「删目标 → 重命名」，而 Windows 不允许删除
 * 或覆盖有打开句柄的文件），所以 `PreferenceDataStoreFactory` + 临时文件在这里只能写一次。
 * Android 是 Linux，不存在这个问题，所以那是测试环境的产物而非产品缺陷。
 *
 * 代价是这里不再覆盖 DataStore 自身的落盘行为 —— 那是库的职责。被测的是
 * [SettingsRepositoryImpl] 的键映射、默认值与地址规范化，全部落在这一个类里。
 */
class SettingsRepositoryImplTest {

    /** `DataStore<T>` 只有 `data` 与 `updateData` 两个成员，所以替身可以很短。 */
    private class FakePreferencesDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state.asStateFlow()

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }
    }

    private val dataStore = FakePreferencesDataStore()
    private val repository = SettingsRepositoryImpl(dataStore)

    /**
     * 一个键都没有的时候，每个字段都必须是有意义的默认值 —— 一个 null 都不能漏到界面上。
     */
    @Test
    fun `an empty store yields every default`() = runTest {
        val settings = repository.settings.first()

        assertEquals(DEFAULT_FONT_SIZE_SP, settings.fontSizeSp)
        assertEquals(AppTheme.Sepia, settings.theme)
        assertEquals(DEFAULT_LINE_HEIGHT, settings.lineHeight)
        assertEquals(TtsProvider.Edge, settings.ttsProvider)
        assertEquals("", settings.ttsVoice)
        assertEquals("default", settings.ttsRate)
        assertEquals("", settings.ttsCustomUrlTemplate)
        assertEquals(false, settings.bionicEnabled)
        assertEquals(false, settings.readingRulerEnabled)
        assertEquals(DEFAULT_SERVER_BASE_URL, settings.serverBaseUrl)
        assertEquals(WORDS_PER_PAGE, settings.wordsPerPage)
    }

    /**
     * 「新增设置项时漏了默认值」的回归测试：只写入**一个**键，
     * 其余字段仍须落到各自的默认值，而不是变成 null。
     */
    @Test
    fun `a partially written store still fills the rest with defaults`() = runTest {
        dataStore.updateData {
            it.toMutablePreferences().apply { set(stringPreferencesKey("theme"), AppTheme.Dark.key) }
        }

        val settings = repository.settings.first()

        assertEquals(AppTheme.Dark, settings.theme)
        assertEquals(DEFAULT_FONT_SIZE_SP, settings.fontSizeSp)
        assertEquals(DEFAULT_SERVER_BASE_URL, settings.serverBaseUrl)
        assertEquals(WORDS_PER_PAGE, settings.wordsPerPage)
    }

    /**
     * 持久化里出现枚举不认识的值时必须退回默认，而不是抛异常。
     * 用 `valueOf` 就会在这里炸 —— 降级安装、手改文件都会产生这种值。
     */
    @Test
    fun `an unknown persisted enum falls back to the default`() = runTest {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                set(stringPreferencesKey("theme"), "neon")
                set(stringPreferencesKey("tts_provider"), "telepathy")
            }
        }

        val settings = repository.settings.first()

        assertEquals(AppTheme.Sepia, settings.theme)
        assertEquals(TtsProvider.Edge, settings.ttsProvider)
    }

    @Test
    fun `each setting round trips on its own`() = runTest {
        repository.setFontSize(26)
        repository.setTheme(AppTheme.Dark)
        repository.setLineHeight(2.2f)
        repository.setTtsProvider(TtsProvider.Custom)
        repository.setTtsVoice("en-US-AriaNeural")
        repository.setTtsRate("+20%")
        repository.setTtsCustomUrl("https://example.com/tts?text={text}")
        repository.setBionicEnabled(true)
        repository.setReadingRulerEnabled(true)
        repository.setWordsPerPage(150)

        val settings = repository.settings.first()
        assertEquals(26, settings.fontSizeSp)
        assertEquals(AppTheme.Dark, settings.theme)
        assertEquals(2.2f, settings.lineHeight, 0.001f)
        assertEquals(TtsProvider.Custom, settings.ttsProvider)
        assertEquals("en-US-AriaNeural", settings.ttsVoice)
        assertEquals("+20%", settings.ttsRate)
        assertEquals("https://example.com/tts?text={text}", settings.ttsCustomUrlTemplate)
        assertEquals(true, settings.bionicEnabled)
        assertEquals(true, settings.readingRulerEnabled)
        assertEquals(150, settings.wordsPerPage)
    }

    /** 单键写入不该动到邻居 —— 这正是不用「一坨 JSON 整体覆盖」的理由。 */
    @Test
    fun `writing one setting leaves the others alone`() = runTest {
        repository.setFontSize(24)
        repository.setTheme(AppTheme.Dark)

        repository.setFontSize(28)

        val settings = repository.settings.first()
        assertEquals(28, settings.fontSizeSp)
        assertEquals(AppTheme.Dark, settings.theme)
    }

    @Test
    fun `settings flow emits again after a write`() = runTest {
        assertEquals(DEFAULT_FONT_SIZE_SP, repository.settings.first().fontSizeSp)

        repository.setFontSize(30)

        assertEquals(30, repository.settings.first().fontSizeSp)
    }

    @Test
    fun `the server url is normalized before it is stored`() = runTest {
        val stored = repository.setServerBaseUrl("192.168.1.20:8787")

        assertEquals("http://192.168.1.20:8787/", stored)
        assertEquals("http://192.168.1.20:8787/", repository.currentServerBaseUrl())
        assertEquals("http://192.168.1.20:8787/", repository.settings.first().serverBaseUrl)
    }

    @Test
    fun `an invalid server url is rejected without being written`() = runTest {
        repository.setServerBaseUrl("http://10.0.2.2:8787")

        assertNull(repository.setServerBaseUrl("ftp://nope"))
        assertNull(repository.setServerBaseUrl(""))
        assertNull(repository.setServerBaseUrl("http://"))

        // 上一次的有效值还在 —— 输入错误不该把已经能用的地址清掉。
        assertEquals("http://10.0.2.2:8787/", repository.currentServerBaseUrl())
    }

    /** 空存储时 `currentServerBaseUrl` 也要给出可用值，网络层靠它建 Retrofit。 */
    @Test
    fun `the current server url falls back to the default`() = runTest {
        assertEquals(DEFAULT_SERVER_BASE_URL, repository.currentServerBaseUrl())
    }
}
