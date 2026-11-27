package com.inputa.next.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.AiClient
import com.inputa.next.core.data.CurrentLesson
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LessonTextCache
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.Meaning
import com.inputa.next.core.data.ReaderSentence
import com.inputa.next.core.data.ReaderToken
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.local.AiWordCacheStore
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.SettingsStore
import com.inputa.next.core.data.toPlaylistEntry
import com.inputa.next.core.media.LessonPlayer
import com.inputa.next.core.media.RepeatMode
import com.inputa.next.core.media.WordPronouncer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ReaderMode(val label: String) {
    Page("Page"),
    Sentence("Sentence"),
    Scroll("Scroll"),
}

/** 用户对某个词做过的本地状态变更；这一阶段只影响当前阅读会话。 */
sealed interface WordState {
    data object Known : WordState
    data object Ignored : WordState
    data class Lingq(val level: Int) : WordState
}

data class WordSelection(
    val sentenceIndex: Int,
    val tokenIndex: Int,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val current: CurrentLesson,
    private val settings: SettingsStore,
    private val localWords: LocalWordStore,
    private val textCache: LessonTextCache,
    private val player: LessonPlayer,
    private val pronouncer: WordPronouncer,
    private val ai: AiClient,
    private val aiCache: AiWordCacheStore,
) : ViewModel() {

    /** AI 单词精讲的当前请求与结果。 */
    data class AiWordState(
        val word: String,
        val context: String,
        val lessonTitle: String?,
        val outputLanguage: String,
        val loading: Boolean = false,
        val text: String? = null,
        val error: String? = null,
        val fromCache: Boolean = false,
    )

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val lesson: LessonSummary? = null,
        val sentences: List<ReaderSentence> = emptyList(),
        val mode: ReaderMode = ReaderMode.Page,
        val page: Int = 0,
        /** 按实测排版高度算出的每页句子区间；空表示还没测量，回退到 PAGE_SIZE。 */
        val pageBreaks: List<IntRange> = emptyList(),
        val sentenceIndex: Int = 0,
        val selection: WordSelection? = null,
        val wordStates: Map<String, WordState> = emptyMap(),
        val meanings: List<Meaning> = emptyList(),
        val meaningsLoading: Boolean = false,
        val notice: String? = null,
        val finished: Boolean = false,
        val aiWord: AiWordState? = null,
    )

    private val _state = MutableStateFlow(State(mode = initialMode()))
    val state: StateFlow<State> = _state.asStateFlow()

    /** 本次阅读会话内的释义缓存，重复点同一个词不再请求。 */
    private val meaningCache = HashMap<String, List<Meaning>>()

    private var aiJob: Job? = null

    val language: String get() = session.language()
    val fontSize = settings.fontSize
    val lineHeight = settings.lineHeight
    val paragraphSpacing = settings.paragraphSpacing
    val wordSpacing = settings.wordSpacing
    val autoTts = settings.autoTts
    val playerState = player.state

    init {
        viewModelScope.launch {
            // 换课时彻底重置阅读状态；collectLatest 会取消上一门课还没完成的加载。
            current.changes.collectLatest { lesson ->
                if (lesson == null) {
                    player.stop()
                    _state.update { State(loading = false, error = "No lesson selected") }
                } else {
                    if (player.state.value.entry?.lessonId != lesson.id) {
                        player.stop()
                    }
                    _state.update { previous ->
                        State(
                            loading = true,
                            lesson = lesson,
                            // 同一课重载保留当前模式；换课回到设置里的默认模式。
                            mode = if (previous.lesson?.id == lesson.id) previous.mode else initialMode(),
                        )
                    }
                    loadFor(lesson)
                }
            }
        }
    }

    fun load() {
        val lesson = _state.value.lesson ?: current.lesson ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            loadFor(lesson)
        }
    }

    private suspend fun loadFor(lesson: LessonSummary) {
        val result = runCatching { api.sentences(language, lesson.id) }
        // 服务端 sentence.index 不保证等于列表位置，统一改成 0..n-1，
        // 否则点词时按 index 取句子会取到下一句，弹窗词和点击词就对不上。
        val sentences = result.getOrDefault(emptyList())
            .mapIndexed { position, sentence ->
                if (sentence.index == position) sentence else sentence.copy(index = position)
            }
        if (sentences.isEmpty()) {
            // 如果当前 id 其实是课程/合集，解析出第一门真实单课后重新进入。
            val resolved = runCatching {
                api.resolveCourseLesson(language, lesson.libraryId ?: lesson.id)
            }.getOrNull()
            if (resolved != null && resolved.id != lesson.id && !resolved.isCourse) {
                current.select(resolved)
                return
            }
        }
        if (sentences.isNotEmpty()) {
            // 正文到手就记下来：首页/详情的 new words 要用本机词库本地算。
            textCache.put(language, lesson.id, sentences.joinToString("\n") { it.text })
        }
        _state.update { state ->
            if (state.lesson?.id != lesson.id) {
                state
            } else {
                state.copy(
                    loading = false,
                    sentences = sentences,
                    error = result.exceptionOrNull()?.message,
                    // 熟练度/词状态全部来自本机记录，不再读 LingQ 词库。
                    wordStates = storedWordStates(),
                )
            }
        }
    }

    /** -1 忽略、0 已知、1–4 LingQ 等级。 */
    private fun storedWordStates(): Map<String, WordState> =
        localWords.load(language).mapValues { (_, status) ->
            when {
                status < 0 -> WordState.Ignored
                status == 0 -> WordState.Known
                else -> WordState.Lingq(status.coerceIn(1, 4))
            }
        }

    private fun initialMode(): ReaderMode = when (settings.readerMode.value) {
        "Sentence" -> ReaderMode.Sentence
        "Scroll" -> ReaderMode.Scroll
        else -> ReaderMode.Page
    }

    fun syncProgress() {
        // 阅读位置属于本地记录，不写回 LingQ。
    }

    fun playPauseAudio() {
        val lesson = _state.value.lesson ?: return
        val playing = player.state.value
        if (playing.entry?.lessonId == lesson.id) {
            player.toggle()
            return
        }
        if (!lesson.audioUrl.isNullOrBlank()) {
            player.play(lesson.toPlaylistEntry())
            return
        }
        // 列表接口有时不带 audioUrl，播放前按课程 id 再读一次真实音频地址。
        viewModelScope.launch {
            val url = runCatching { api.lessonAudioUrl(language, lesson.id) }.getOrNull()
            if (url.isNullOrBlank()) {
                _state.update { it.copy(notice = "This lesson has no audio.") }
            } else {
                player.play(lesson.toPlaylistEntry().copy(audioUrl = url))
            }
        }
    }

    /** 点词发音：LingQ TTS 音频优先，失败回退系统 TTS。 */
    fun pronounce(text: String) {
        pronouncer.speak(language, text)
    }

    /** 拖动/点击音频进度条后跳转到对应位置。 */
    fun seekAudio(progress: Float) {
        val duration = player.state.value.durationMs
        if (duration <= 0) return
        player.seekTo((duration * progress.coerceIn(0f, 1f)).toInt())
    }

    /** 设置课程音频循环模式：关闭 / 单曲循环 / 列表循环。 */
    fun setRepeatMode(mode: RepeatMode) = player.setRepeatMode(mode)

    /** 应用内课程音频音量（0f..1f），与系统媒体音量独立。 */
    fun setVolume(volume: Float) = player.setVolume(volume)

    fun stopAudio() {
        player.stop()
        pronouncer.stop()
    }

    fun cycleFontSize() {
        val sizes = listOf(16f, 19f, 22f, 25f, 28f)
        val currentSize = settings.fontSize.value
        val next = sizes.firstOrNull { it > currentSize + 0.1f } ?: sizes.first()
        settings.setFontSize(next)
    }

    fun setFontSize(value: Float) = settings.setFontSize(value)

    fun setLineHeight(value: Float) = settings.setLineHeight(value)

    fun setParagraphSpacing(value: Float) = settings.setParagraphSpacing(value)

    fun setWordSpacing(value: Float) = settings.setWordSpacing(value)

    fun finishLesson() {
        // 完成记录只保存在本地。
        _state.update { it.copy(finished = true) }
    }

    fun dismissFinished() = _state.update { it.copy(finished = false) }

    fun setMode(mode: ReaderMode) = _state.update { it.copy(mode = mode, page = 0, sentenceIndex = 0, selection = null) }

    /** 分页器跟手滑动时回报当前页。 */
    fun setPage(page: Int) {
        _state.update { state ->
            val maxPage = maxOf(0, pageCount() - 1)
            state.copy(page = page.coerceIn(0, maxPage), selection = null)
        }
    }

    /**
     * 阅读器把实测好的分页结果交回来，翻页/进度都以此为准，
     * 这样一页正好铺满一屏，不会出现页内滚动。
     */
    fun setPageBreaks(breaks: List<IntRange>) {
        _state.update { state ->
            if (state.pageBreaks == breaks) {
                state
            } else {
                state.copy(
                    pageBreaks = breaks,
                    page = state.page.coerceIn(0, (breaks.size - 1).coerceAtLeast(0)),
                )
            }
        }
    }

    fun next() {
        _state.update { state ->
            when (state.mode) {
                ReaderMode.Scroll -> state
                ReaderMode.Page -> {
                    val maxPage = maxOf(0, pageCount() - 1)
                    state.copy(page = (state.page + 1).coerceAtMost(maxPage), selection = null)
                }

                ReaderMode.Sentence -> state.copy(
                    sentenceIndex = (state.sentenceIndex + 1).coerceAtMost((state.sentences.size - 1).coerceAtLeast(0)),
                    selection = null,
                )
            }
        }
    }

    fun previous() {
        _state.update { state ->
            when (state.mode) {
                ReaderMode.Scroll -> state
                ReaderMode.Page -> state.copy(page = (state.page - 1).coerceAtLeast(0), selection = null)
                ReaderMode.Sentence -> state.copy(
                    sentenceIndex = (state.sentenceIndex - 1).coerceAtLeast(0),
                    selection = null,
                )
            }
        }
    }

    fun select(sentenceIndex: Int, tokenIndex: Int) {
        val token = _state.value.sentences.getOrNull(sentenceIndex)?.tokens?.getOrNull(tokenIndex)
        val selection = WordSelection(sentenceIndex, tokenIndex)
        _state.update {
            it.copy(
                selection = selection,
                meanings = emptyList(),
                meaningsLoading = token != null,
            )
        }
        if (token == null) return
        val term = token.text.trim()
        if (term.isBlank()) return
        val key = term.lowercase()

        // 点开一个还没记录的新词就视为收录 1 级：蓝色块立即变黄。
        // 用户可以在 Word Popup 设置里关掉自动收录。
        if (settings.autoCreateLingq.value && _state.value.wordStates[key] == null) {
            localWords.put(language, term, 1)
            _state.update {
                if (it.selection == selection) {
                    it.copy(wordStates = it.wordStates + (key to WordState.Lingq(1)))
                } else {
                    it
                }
            }
        }

        if (settings.pronounceOnTap.value) pronouncer.speak(language, term)

        // 已查过的词直接用缓存，弹窗不再等接口。
        meaningCache[key]?.let { cached ->
            _state.update {
                if (it.selection == selection) {
                    it.copy(meanings = cached, meaningsLoading = false)
                } else {
                    it
                }
            }
            return
        }
        viewModelScope.launch {
            val result = runCatching { api.meanings(language, term) }.getOrDefault(emptyList())
            if (result.isNotEmpty()) meaningCache[key] = result
            _state.update {
                if (it.selection == selection) {
                    it.copy(meanings = result, meaningsLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun dismissSelection() = _state.update { it.copy(selection = null) }

    /** 点词弹窗里的 AI 按钮：优先读本机缓存，没有才请求大模型。 */
    fun explainWord(token: ReaderToken, context: String) {
        val word = token.text.trim()
        if (word.isEmpty()) return
        val request = AiWordState(
            word = word,
            context = context.trim(),
            lessonTitle = _state.value.lesson?.title,
            outputLanguage = settings.effectiveAiExplainLanguage(),
            loading = true,
        )
        _state.update { it.copy(selection = null, aiWord = request) }
        loadAiWord(request, force = false)
    }

    /** 切换解释输出语言：为同一单词重新查缓存/请求。 */
    fun setAiOutputLanguage(code: String) {
        settings.setAiExplainLanguage(code)
        val current = _state.value.aiWord ?: return
        val request = current.copy(
            outputLanguage = code,
            loading = true,
            text = null,
            error = null,
            fromCache = false,
        )
        _state.update { it.copy(aiWord = request) }
        loadAiWord(request, force = false)
    }

    /** 强制重新生成并覆盖本地缓存。 */
    fun refreshAiWord() {
        val current = _state.value.aiWord ?: return
        val request = current.copy(loading = true, error = null)
        _state.update { it.copy(aiWord = request) }
        loadAiWord(request, force = true)
    }

    fun closeAiWord() {
        aiJob?.cancel()
        aiJob = null
        _state.update { it.copy(aiWord = null) }
    }

    private fun loadAiWord(request: AiWordState, force: Boolean) {
        aiJob?.cancel()
        if (!force) {
            aiCache.get(language, request.outputLanguage, request.word)?.let { cached ->
                _state.update { state ->
                    if (state.aiWord?.word == request.word &&
                        state.aiWord?.outputLanguage == request.outputLanguage
                    ) {
                        state.copy(
                            aiWord = request.copy(loading = false, text = cached, error = null, fromCache = true),
                        )
                    } else {
                        state
                    }
                }
                return
            }
        }
        if (!session.hasAiConfig()) {
            _state.update { state ->
                if (state.aiWord?.word == request.word) {
                    state.copy(aiWord = request.copy(loading = false, error = "AI is not configured"))
                } else {
                    state
                }
            }
            return
        }
        aiJob = viewModelScope.launch {
            val result = runCatching {
                ai.explainWord(
                    word = request.word,
                    context = request.context,
                    learningLanguage = language,
                    outputLanguage = request.outputLanguage,
                    lessonTitle = request.lessonTitle,
                )
            }
            val text = result.getOrNull()?.trim().orEmpty()
            _state.update { state ->
                val current = state.aiWord
                if (current == null ||
                    current.word != request.word ||
                    current.outputLanguage != request.outputLanguage
                ) {
                    state
                } else if (text.isNotBlank()) {
                    aiCache.put(language, request.outputLanguage, request.word, text)
                    state.copy(
                        aiWord = current.copy(
                            loading = false,
                            text = text,
                            error = null,
                            fromCache = false,
                        ),
                    )
                } else {
                    state.copy(
                        aiWord = current.copy(
                            loading = false,
                            error = result.exceptionOrNull()?.message ?: "AI request failed",
                        ),
                    )
                }
            }
        }
    }

    fun markWord(token: ReaderToken, wordState: WordState) {
        val term = token.text.trim()
        if (term.isBlank()) return
        val status = when (wordState) {
            WordState.Known -> 0
            WordState.Ignored -> -1
            is WordState.Lingq -> wordState.level.coerceIn(1, 4)
        }
        localWords.put(language, term, status)
        _state.update {
            it.copy(
                selection = null,
                wordStates = it.wordStates + (term.lowercase() to wordState),
            )
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    fun tokenAt(selection: WordSelection): ReaderToken? =
        _state.value.sentences.getOrNull(selection.sentenceIndex)
            ?.tokens?.getOrNull(selection.tokenIndex)

    fun visibleSentences(): List<ReaderSentence> {
        val state = _state.value
        return when (state.mode) {
            ReaderMode.Scroll -> state.sentences
            ReaderMode.Page -> pageSentences(state.page)
            ReaderMode.Sentence -> listOfNotNull(state.sentences.getOrNull(state.sentenceIndex))
        }
    }

    fun pageSentences(page: Int): List<ReaderSentence> {
        val currentState = _state.value
        val range = currentState.pageBreaks.getOrNull(page)
        return if (range != null) {
            currentState.sentences.subList(
                range.first.coerceAtLeast(0),
                (range.last + 1).coerceAtMost(currentState.sentences.size),
            )
        } else {
            currentState.sentences.drop(page * PAGE_SIZE).take(PAGE_SIZE)
        }
    }

    fun pageCount(): Int {
        val currentState = _state.value
        return currentState.pageBreaks.size.takeIf { it > 0 }
            ?: maxOf(1, (currentState.sentences.size + PAGE_SIZE - 1) / PAGE_SIZE)
    }

    companion object {
        const val PAGE_SIZE = 8
    }
}
