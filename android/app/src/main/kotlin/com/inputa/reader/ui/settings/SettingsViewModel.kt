package com.inputa.reader.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.reader.domain.model.AppTheme
import com.inputa.reader.domain.model.FONT_SIZE_RANGE
import com.inputa.reader.domain.model.ReaderSettings
import com.inputa.reader.domain.model.displayedServerBaseUrl
import com.inputa.reader.domain.repository.BackendHealth
import com.inputa.reader.domain.repository.BackendHealthRepository
import com.inputa.reader.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val health: BackendHealthRepository,
) : ViewModel() {

    data class State(
        val settings: ReaderSettings = ReaderSettings(),
        /** 正在编辑的地址原文。null 表示还没动过，显示已保存的值。 */
        val baseUrlDraft: String? = null,
        /** 地址不合法时的提示；合法时为 null。 */
        val baseUrlError: String? = null,
        val health: BackendHealth = BackendHealth(),
        val isTesting: Boolean = false,
    ) {
        /**
         * 输入框里该显示什么。规则本身在领域层（`displayedServerBaseUrl`），
         * 这里只是把它接到当前状态上 —— 那样才测得到，`:app` 的测试任务不可靠。
         */
        val shownBaseUrl: String
            get() = displayedServerBaseUrl(settings.serverBaseUrl, baseUrlDraft)
    }

    private val draft = MutableStateFlow<String?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val testing = MutableStateFlow(false)
    private val healthState = MutableStateFlow(BackendHealth())

    val state: StateFlow<State> = combine(
        settings.settings,
        draft,
        error,
        testing,
        healthState,
    ) { current, draftValue, errorValue, isTesting, health ->
        State(
            settings = current,
            baseUrlDraft = draftValue,
            baseUrlError = errorValue,
            health = health,
            isTesting = isTesting,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    /**
     * 保存服务器地址。
     *
     * 校验与补斜杠由仓储负责（`normalizeBaseUrl`），返回 null 表示输入不合法 ——
     * 那时**不写入**，并在输入框下面给出原因。让用户看到「地址不合法」而不是
     * 「连不上服务」，排查方向完全不同。
     *
     * 空输入是**恢复内置默认**而不是错误：输入框不预填默认地址（见 `shownBaseUrl`），
     * 所以「清空再保存」是最自然的恢复方式，不该撞上「地址不合法」。
     */
    fun saveBaseUrl() {
        val raw = draft.value ?: return
        viewModelScope.launch {
            if (raw.isBlank()) {
                settings.resetServerBaseUrl()
                error.value = null
                draft.value = null
                return@launch
            }
            val saved = settings.setServerBaseUrl(raw)
            if (saved == null) {
                error.value = "地址不合法：需要是 http(s)://主机[:端口] 的形式"
            } else {
                error.value = null
                draft.value = null
            }
        }
    }

    fun editBaseUrl(raw: String) {
        draft.value = raw
        error.value = null
    }

    /** 「测试连接」：**强制重探**，否则拿到的还是缓存里那个旧结论。 */
    fun testConnection() {
        viewModelScope.launch {
            testing.value = true
            healthState.value = health.refresh()
            testing.value = false
        }
    }

    fun setFontSize(size: Int) {
        viewModelScope.launch { settings.setFontSize(size.coerceIn(FONT_SIZE_RANGE)) }
    }

    fun stepFontSize(delta: Int) {
        setFontSize(state.value.settings.fontSizeSp + delta)
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settings.setTheme(theme) }
    }

    fun setWordsPerPage(count: Int) {
        // 下界 50 是防止把一页切得太碎；上界 600 是防止一次渲染过多词元。
        viewModelScope.launch { settings.setWordsPerPage(count.coerceIn(50, 600)) }
    }

    fun stepWordsPerPage(delta: Int) {
        setWordsPerPage(state.value.settings.wordsPerPage + delta)
    }

    /**
     * 仿生阅读开关。
     *
     * 渲染那一侧早就实现了（`WordParagraph` 按词长的 40% 加粗词首），所以这个开关
     * 是接上就能用的 —— 不放进设置里反而是浪费。
     *
     * 段落聚焦标尺**故意没有开关**：它的 Web 实现依赖鼠标 hover，触摸设备上没有对应
     * 交互，改造方案（视口中心段落保持清晰）排在第二阶段。发一个什么都不做的设置项
     * 比不发更糟。
     */
    fun setBionic(enabled: Boolean) {
        viewModelScope.launch { settings.setBionicEnabled(enabled) }
    }
}
