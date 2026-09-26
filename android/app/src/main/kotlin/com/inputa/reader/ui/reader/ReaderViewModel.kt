package com.inputa.reader.ui.reader

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.reader.BuildConfig
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.DictionaryEntry
import com.inputa.reader.domain.model.DictionarySource
import com.inputa.reader.domain.model.LookupResult
import com.inputa.reader.domain.model.ReadingProgress
import com.inputa.reader.domain.model.ReaderSettings
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.repository.BookRepository
import com.inputa.reader.domain.repository.DictionaryRepository
import com.inputa.reader.domain.repository.MasteryBatch
import com.inputa.reader.domain.repository.ProgressRepository
import com.inputa.reader.domain.repository.SettingsRepository
import com.inputa.reader.domain.repository.VocabularyRepository
import com.inputa.reader.domain.text.Pagination
import com.inputa.reader.domain.text.Tokenizer
import com.inputa.reader.domain.util.Clock
import com.inputa.reader.domain.vocab.PageTurnAction
import com.inputa.reader.domain.vocab.PageTurnRule
import com.inputa.reader.domain.vocab.VocabularyCounts
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * 一次释义查询的状态。
 *
 * 直接对应领域层的 `LookupResult` 三态，外加两个 UI 态（还没查 / 正在查）。
 * 用密封类型而不是「entry + unavailable 两个字段」的组合，是为了让
 * 「有词条同时又是故障」这种状态**根本构造不出来**。
 */
sealed interface WordLookupState {
    data object Idle : WordLookupState
    data object Loading : WordLookupState
    data class Loaded(val entry: DictionaryEntry, val source: DictionarySource) : WordLookupState

    /** 所有来源都答了，但都不认识这个词。显示「词典均未收录该词」。 */
    data object NotFound : WordLookupState

    /** 必需来源传输失败。显示「词典服务暂时无法访问」+ 重新查询。 */
    data object Unavailable : WordLookupState
}

data class ReaderUiState(
    val bookId: String = "",
    val bookTitle: String = "",
    val chapterTitles: List<String> = emptyList(),
    val chapterIndex: Int = 0,
    val chapterTitle: String = "",
    val pages: List<String> = emptyList(),
    val pageIndex: Int = 0,
    /**
     * 翻页器的重建键。**必须和 [initialPage] 来自同一次发射**，否则切章时翻页器
     * 可能用旧的页号初始化 —— 因为 `rememberPagerState` 的 `initialPage` 只在
     * 首次组合时生效，而章号与页号若分别发射就会产生一个中间态。
     * 见 [ReaderViewModel.CurrentChapter.loadId]。
     */
    val pagerKey: Long = 0,
    /** 进入当前章时落在第几页。只在翻页器重建时被读取。 */
    val initialPage: Int = 0,
    /**
     * **整本词库**的状态表。刻意不按页裁剪 —— 翻页落地那一刻的色块靠它，
     * 见 [ReaderViewModel.statusesFlow]。
     */
    val statuses: Map<String, WordStatus> = emptyMap(),
    /** 被点开、正在描边的那个词。 */
    val activeWord: String? = null,
    val activeSentence: String = "",
    val lookup: WordLookupState = WordLookupState.Idle,
    /**
     * 刚刚因为翻页被自动记为「已掌握」的一批词。非空时界面显示提示条与「撤销」。
     * 放在状态里而不是弹一次就走：配置变更（旋转）不该让撤销窗口消失。
     */
    val masteryBatch: MasteryBatch? = null,
    /** 跨多页快速滑动跳过的页数。非空时提示「已跳过 N 页，未自动记为已掌握」。 */
    val skippedPages: Int? = null,
    /** 当前打开的覆盖层。**至多一个** —— 那是类型层面的事实，不用五个布尔值互斥。 */
    val overlay: Overlay? = null,
    /** 顶栏徽章上的两个数字，来自一条 `GROUP BY` 而不是整张表。 */
    val counts: VocabularyCounts = VocabularyCounts(active = 0, mastered = 0),
    val settings: ReaderSettings = ReaderSettings(),
    val isLoading: Boolean = true,
) {
    val pageCount: Int get() = pages.size
    val currentPageText: String get() = pages.getOrNull(pageIndex).orEmpty()
    val hasNextPage: Boolean
        get() = pageIndex < pageCount - 1 || chapterIndex < chapterTitles.size - 1
    val hasPrevPage: Boolean get() = pageIndex > 0 || chapterIndex > 0
}

/**
 * 阅读器。
 *
 * 这一屏是「翻页自动标掌握」规则唯一落地的地方，而**翻页会写数据** —— 所以下面
 * 每条关于手势与边界的决定都值得读一遍：
 *
 *   - **标记写在「已吸附」而不是「拖动中」**：半途滑回原页从不改变 settledPage，
 *     于是什么都不会写。这一条免费挡掉了最常见的误触。
 *   - **跨多页的快速滑动不标记任何词**，只提示跳过了几页。为读者根本没看到的页面
 *     收录生词，正是 `highlightLevel` 那套设计存在的理由的反面。
 *   - **章节边界与 Web 版逐条对齐**（那边是 `PaginationBar` + `useReaderStore`）。
 */
@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val books: BookRepository,
    private val vocabulary: VocabularyRepository,
    private val dictionary: DictionaryRepository,
    private val progress: ProgressRepository,
    private val clock: Clock,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    /**
     * 书从哪来。内置样书**随包发行、不入库**（Web 版也是分开放的），所以阅读器要能
     * 同时从两个地方取正文。分支收敛在这里，UI 不必知道书是从哪来的。
     */
    private sealed interface Source {
        data class Builtin(val book: Book) : Source
        data class Stored(val bookId: String) : Source
    }

    /**
     * 当前章。
     *
     * [loadId] 每次重新载入都递增，**它是翻页器的 key**。把「章号 + 起始页 + 载入序号」
     * 打包进一个数据类，是为了让它们在同一次发射里到达界面 —— 否则切章时可能先看到
     * 新的章号配旧的页号，而翻页器的 `initialPage` 只在首次组合时生效。
     */
    private data class CurrentChapter(
        val index: Int,
        val title: String,
        val content: String,
        val startPage: Int,
        val loadId: Long,
    )

    private val source = MutableStateFlow<Source?>(null)
    private val bookId = MutableStateFlow("")
    private val bookTitle = MutableStateFlow("")
    private val titles = MutableStateFlow<List<String>>(emptyList())
    private val current = MutableStateFlow<CurrentChapter?>(null)
    private val pageIndex = MutableStateFlow(0)
    private val activeWord = MutableStateFlow<String?>(null)
    private val activeSentence = MutableStateFlow("")
    private val lookupState = MutableStateFlow<WordLookupState>(WordLookupState.Idle)
    private val masteryBatch = MutableStateFlow<MasteryBatch?>(null)
    private val skippedPages = MutableStateFlow<Int?>(null)
    private val overlay = MutableStateFlow<Overlay?>(null)
    private val loading = MutableStateFlow(true)

    private var lookupJob: Job? = null

    private val settingsFlow: StateFlow<ReaderSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReaderSettings())

    /** 分页结果只在**章节或每页词数**变化时重算 —— 翻页只是换个下标，不该重新分页。 */
    private val pagesFlow: StateFlow<List<String>> =
        combine(current, settingsFlow) { chapter, settings ->
            chapter?.content?.let { Pagination.paginate(it, settings.wordsPerPage) }.orEmpty()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 词库状态：**整本常驻内存**。
     *
     * 这条是 Web 版的做法，也是本移植原先刻意避开的做法（原先只按页查一两百个词，
     * 理由是手机上两万词不该常驻）。改回来是因为一个看得见的问题，而它在「只查一部分」
     * 这个前提下**无法根治**：
     *
     * 翻页时 `pageIndex` 先变、这一页的状态要等一次查询才回来，在那之前界面用的是上一个
     * 值 —— 只要那张表的键不覆盖新页的词，新页就会整页按「未收录」上色（生词色），几个
     * 毫秒后才变成它们真正的样子。翻回一页刚刚被自动标成已掌握的词上，看到的就是
     * **先闪一下生词色、再变无色**。把窗口放大到邻页能盖住大部分情况，但「从没观察过的页」
     * 依然会闪 —— 除非表里**永远**有每一个词。
     *
     * 代价写清楚，别装作没有：Room 的失效通知是表级的，所以**每次写入都会重读整张表**
     * （翻一页约 100 行写入 → 一次全表 `SELECT`），两三万词的库上是几十毫秒的后台活，
     * 外加一次同样量级的临时分配。它在 IO 线程上、只在翻页/点词时发生，所以换来的是
     * 一个结构上不可能闪色的界面。
     *
     * 顶栏计数仍然走那条 `GROUP BY`（[VocabularyRepository.observeCounts]），不需要跟着
     * 整表读。
     */
    private val statusesFlow: StateFlow<Map<String, WordStatus>> =
        vocabulary.observeAll()
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /**
     * 程序化翻页的通道（按钮、章切换、进度恢复）。
     *
     * **只向一个方向走**：翻页器的「已吸附」回调从不往这里发。所以按钮滚动到某页之后，
     * 翻页器回传同一个页号，`pageIndex` 写的是同一个值，不会触发第二次滚动 ——
     * 一个 Echo 环就这样被结构性地排除了，而不是靠比较前后值去猜。
     */
    private val scrollRequests = Channel<Int>(Channel.CONFLATED)
    val scrollToPage: Flow<Int> = scrollRequests.receiveAsFlow()

    private data class ChapterModel(
        val bookId: String,
        val bookTitle: String,
        val current: CurrentChapter?,
        val titles: List<String>,
    )

    private data class ActiveSelection(
        val word: String?,
        val sentence: String,
        val lookup: WordLookupState,
    )

    private data class Notices(val mastery: MasteryBatch?, val skipped: Int?)

    private data class Paging(
        val pages: List<String>,
        val pageIndex: Int,
        val loading: Boolean,
    )

    /** 翻页与提示打包在一起，好让下面那个 combine 保持在五个参数以内。 */
    private data class Reading(val notices: Notices, val paging: Paging)

    private data class Chrome(
        val overlay: Overlay?,
        val counts: VocabularyCounts,
        val settings: ReaderSettings,
    )

    private val chapterModel = combine(bookId, bookTitle, current, titles) { id, title, chapter, all ->
        ChapterModel(id, title, chapter, all)
    }

    private val activeSelection = combine(activeWord, activeSentence, lookupState) { word, sentence, lookup ->
        ActiveSelection(word, sentence, lookup)
    }

    private val notices = combine(masteryBatch, skippedPages) { mastery, skipped ->
        Notices(mastery, skipped)
    }

    private val paging = combine(pagesFlow, pageIndex, loading) { pages, index, isLoading ->
        Paging(pages, index, isLoading)
    }

    private val reading = combine(notices, paging) { notice, page -> Reading(notice, page) }

    /**
     * 设置**必须作为 combine 的输入**，不能在下面读 `settingsFlow.value`。
     *
     * 读 `.value` 不会订阅那个流 —— 于是改主题时这个 combine 不重跑，
     * `ReaderUiState.settings` 一直是旧值，界面上的主题要等**别的输入变化**
     * （比如翻一次页）才顺带更新。设备验证时正是这样发现的：分段控件已经高亮到
     * 「夜间」，而面板还是羊皮纸色。
     */
    private val chrome = combine(overlay, vocabulary.observeCounts(), settingsFlow) { open, counts, settings ->
        Chrome(open, counts, settings)
    }

    val state: StateFlow<ReaderUiState> = combine(
        chapterModel,
        activeSelection,
        reading,
        statusesFlow,
        chrome,
    ) { chapters, active, reading, statuses, chrome ->
        val notice = reading.notices
        val paging = reading.paging
        val pages = paging.pages
        ReaderUiState(
            bookId = chapters.bookId,
            bookTitle = chapters.bookTitle,
            chapterTitles = chapters.titles,
            chapterIndex = chapters.current?.index ?: 0,
            chapterTitle = chapters.current?.title.orEmpty(),
            pages = pages,
            pageIndex = paging.pageIndex.coerceIn(0, maxOf(0, pages.size - 1)),
            pagerKey = chapters.current?.loadId ?: 0L,
            // 起始页也要夹住：它可能来自**上次会话保存的进度**，而那次会话的每页词数
            // 设置与现在未必相同 —— 算出来的页数变了，旧页号就可能越界。
            // 翻页器的 initialPage 拿到越界值是不安全的，所以在唯一知道 pages 的地方夹。
            initialPage = (chapters.current?.startPage ?: 0).coerceIn(0, maxOf(0, pages.size - 1)),
            statuses = statuses,
            activeWord = active.word,
            activeSentence = active.sentence,
            lookup = active.lookup,
            masteryBatch = notice.mastery,
            skippedPages = notice.skipped,
            overlay = chrome.overlay,
            counts = chrome.counts,
            settings = chrome.settings,
            isLoading = paging.loading,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState())

    init {
        viewModelScope.launch { openInitialBook() }
    }

    // ------------------------------------------------------------------ 翻页

    /**
     * 用户滑动翻页后回传。**这是唯一会因翻页而写词库的地方。**
     *
     * 四个分支对应四种手势，与 Web 版的差别写在下面：
     *
     *   - **前进正好一页** → 把**离开的那一页**上没点过的词记为已掌握。
     *   - **后退** → 只记进度，不标记任何词（往回看不该改变词库）。
     *   - **跨多页** → 只记进度并提示跳过了几页，**不标记**。Web 版一次只翻一页
     *     （方向键/按钮），没有对应手势；为读者根本没看到的页面收录生词，正是
     *     `highlightLevel` 那套设计存在的理由的反面。这是对 Web 规则的有意细化。
     *   - **页号没变**（半途滑回原页）→ 什么都不做。
     */
    fun onPageSettled(newPage: Int) {
        val old = pageIndex.value
        // 半途滑回原页：页号没变，连进度都不必写。
        if (newPage == old) return

        val outgoingPageText = pagesFlow.value.getOrNull(old).orEmpty()
        pageIndex.value = newPage

        // 判定本身在领域层，见 PageTurnRule —— 它是「唯一会因翻页而写词库」的那条规则，
        // 放在那里才能在纯 JVM 上逐分支测。
        when (PageTurnRule.actionFor(old, newPage)) {
            PageTurnAction.MASTER_OUTGOING -> masterOutgoingPage(outgoingPageText)

            PageTurnAction.SKIPPED -> {
                skippedPages.value = abs(newPage - old)
                viewModelScope.launch { persistProgress() }
            }

            // 后退一页：只记进度，词库不动。
            PageTurnAction.NONE -> viewModelScope.launch { persistProgress() }
        }
    }

    /** 底部「下一页」。最后一页时滚进下一章。 */
    fun nextPage() {
        val index = pageIndex.value
        if (index < pagesFlow.value.lastIndex) {
            // 交给翻页器滚动，由 onPageSettled 完成标记 —— 这样「写在 settle
            // 而不是拖动中」这条规则对按钮与滑动是同一条路径。
            requestPage(index + 1)
        } else {
            // 章节边界：翻页器滚不过去，所以在这里把整个动作走完。
            // 顺序与 Web 版一致 —— 先标记离开的那一页，再切章。
            masterOutgoingPage(pagesFlow.value.getOrNull(index).orEmpty()) {
                // advanceChapter 是挂起的，而 andThen 是挂起 lambda —— 见它的签名。
                advanceChapter(delta = +1, landOnLastPage = false)
            }
        }
    }

    /** 底部「上一页」。第一页时退到上一章的**最后一页**。 */
    fun prevPage() {
        val index = pageIndex.value
        if (index > 0) {
            requestPage(index - 1)
        } else {
            // 落到上一章的最后一页，以免跳过页面 —— 与 Web 版 `handlePrevPage` 一致。
            // **不标记任何词**：往回看不该改变词库。
            viewModelScope.launch { advanceChapter(delta = -1, landOnLastPage = true) }
        }
    }

    /**
     * 撤销一次翻页收录。
     *
     * 批次里带着时间戳，仓储只删**这两个条件都还匹配**的行 —— 所以读者在撤销窗口内
     * 手动改过熟练度的词不会被撤销掉，那属于用户判断。
     */
    fun undoMastery() {
        val batch = masteryBatch.value ?: return
        masteryBatch.value = null
        viewModelScope.launch {
            val deleted = vocabulary.undoMastery(batch)
            // 删掉的条数少于批次大小是正常的：说明读者在撤销窗口内手动改过其中某个词，
            // 那属于用户判断，不该被撤销覆盖。
            if (BuildConfig.DEBUG) Log.d(TAG, "undo: deleted $deleted of ${batch.count}")
        }
    }

    fun dismissMasteryNotice() {
        masteryBatch.value = null
    }

    fun dismissSkippedNotice() {
        skippedPages.value = null
    }

    // ------------------------------------------------------------------ 点词

    /**
     * 点词的**唯一入口** —— 面板的「重新查询」、以后的原形链接与词爆面板都复用它。
     * 对应 Web 版 `App.tsx` 的 `handleSelectWord`。
     *
     * 两处顺序是有意的：
     *
     *   1. **收录先于查询**，且不随查询一起取消。Web 版那边 `markAsNewWord` 是同步调用、
     *      排在 `await` 之前，所以「点了但没查到」的词也已经记为用户判断过的了。
     *      如果反过来，那个词下次翻页会被自动标成「已掌握」—— 而读者明明点过它。
     *   2. 前一次查询被取消：连点两个词时不该让先到的结果盖住后点的那个。
     */
    fun onWordSelected(word: String, sentence: String, refreshBackend: Boolean = false) {
        val clean = word.trim().lowercase()
        if (clean.isEmpty()) return

        activeWord.value = clean
        activeSentence.value = sentence
        lookupState.value = WordLookupState.Loading

        viewModelScope.launch { vocabulary.markAsNewWord(clean) }

        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            lookupState.value = when (val result = dictionary.lookup(clean, refreshBackend)) {
                is LookupResult.Found -> WordLookupState.Loaded(result.entry, result.source)
                LookupResult.NotFound -> WordLookupState.NotFound
                LookupResult.Unavailable -> WordLookupState.Unavailable
            }
        }
    }

    /**
     * 「重新查询」。
     *
     * 与点词走同一条路径（所以**不会重复收录**），唯一的差别是**强制重探后端健康状态**
     * —— 「我刚把后端启起来，再试一次」正是这个按钮存在的场景，而健康状态是按地址
     * 缓存的，不重探就永远停在旧结论上。
     */
    fun retryLookup() {
        val word = activeWord.value ?: return
        onWordSelected(word, activeSentence.value, refreshBackend = true)
    }

    /** 释义面板里的 5 段选择器。手动指定是明确的用户意图，所以覆盖已有状态。 */
    fun setStatus(status: WordStatus) {
        val word = activeWord.value ?: return
        viewModelScope.launch { vocabulary.setStatus(word, status) }
    }

    fun markMastered() {
        val word = activeWord.value ?: return
        viewModelScope.launch { vocabulary.markMastered(word) }
    }

    /** 移出词库：恢复未标记状态，下次翻页它会被重新收为已掌握。 */
    fun removeWord() {
        val word = activeWord.value ?: return
        viewModelScope.launch { vocabulary.removeWord(word) }
    }

    fun clearActiveWord() {
        lookupJob?.cancel()
        activeWord.value = null
        activeSentence.value = ""
        lookupState.value = WordLookupState.Idle
    }

    // ------------------------------------------------------------------ 覆盖层

    fun openOverlay(target: Overlay) {
        overlay.value = target
    }

    fun closeOverlay() {
        overlay.value = null
    }

    /**
     * 按 id 打开一本书，**两种来源都认**。
     *
     * 书架把导入的书与内置样书列在同一个列表里，所以调用方不必先分辨它属于哪一边 ——
     * 先查库，库里没有就当成内置样书找。找不到就什么都不做（书可能刚被删掉）。
     */
    fun openBookById(id: String) {
        viewModelScope.launch {
            if (books.findMeta(id) != null) {
                open(Source.Stored(id), chapterIndex = 0, startPage = 0)
            } else {
                val builtin = books.builtinBooks().firstOrNull { it.id == id } ?: return@launch
                open(Source.Builtin(builtin), chapterIndex = 0, startPage = 0)
            }
            closeOverlay()
        }
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 请求翻页器滚到某一页。
     *
     * **刻意不在这里写 `pageIndex`。** 页号由 [onPageSettled] 独占 —— 那是「写在 settle
     * 而不是拖动中」这条设计的落点。提前写入会有一个具体的后果：settle 回调随后看到
     * 「页号没变」而直接早退，于是**标记与进度双双被跳过**。设备验证时正是这样发现的
     * （按钮翻页后词库与进度都是 0 行，而滑动正常 —— 因为滑动不经过这里）。
     */
    private fun requestPage(index: Int) {
        val target = index.coerceIn(0, maxOf(0, pagesFlow.value.size - 1))
        scrollRequests.trySend(target)
    }

    /**
     * 把一页上没点过的词记为已掌握，写入完成后执行 [andThen]（切章用）。
     *
     * [andThen] 排在**同一个协程里、写入之后**，这一点是必须的：Web 版里
     * `markPageWordsAsMastered` 是同步的，所以紧接着的 `nextPage()` 天然在它之后；
     * 这里是挂起的，不显式串起来就会变成「先切章、再标记」—— 标记的仍是旧那一页的
     * 文本，所以结果碰巧一样，但顺序一旦在别处被依赖就会出错。
     */
    private fun masterOutgoingPage(pageText: String, andThen: (suspend () -> Unit)? = null) {
        viewModelScope.launch {
            val words = Tokenizer.extractWords(Tokenizer.tokenize(pageText))
            val batch = vocabulary.applyPageTurn(words)
            // batch 为空表示这一页没有新收录的词 —— 那就没有可撤销的东西，
            // 界面也不该弹出提示条（与 Web 版「count > 0 才提示」一致）。
            if (!batch.isEmpty) masteryBatch.value = batch
            persistProgress()
            andThen?.invoke()
        }
    }

    private suspend fun chapterContentOf(target: Source, index: Int): String? = when (target) {
        is Source.Builtin -> target.book.chapters.getOrNull(index)?.content
        is Source.Stored -> books.chapterContent(target.bookId, index)
    }

    private suspend fun loadChapter(target: Source, index: Int, startPage: Int) {
        val content = chapterContentOf(target, index)
        val title = titles.value.getOrNull(index).orEmpty()
        if (content == null) {
            loading.value = false
            return
        }
        // 先写 current（含 loadId 与 startPage）再写 pageIndex：两者在同一次
        // 协程恢复里连续写入，界面看到的组合是一致的。loadId 保证翻页器一定重建。
        current.value = CurrentChapter(
            index = index,
            title = title,
            content = content,
            startPage = startPage,
            loadId = (current.value?.loadId ?: 0L) + 1,
        )
        pageIndex.value = startPage
        loading.value = false
    }

    /**
     * 切到相邻章节。
     *
     * @param landOnLastPage true = 落在该章最后一页（从第 0 页往回退时用，
     *   与 Web 版 `handlePrevPage` 的「以免跳过页面」一致）。
     */
    private suspend fun advanceChapter(delta: Int, landOnLastPage: Boolean) {
        val target = source.value ?: return
        val next = (current.value?.index ?: 0) + delta
        if (next !in titles.value.indices) return

        val content = chapterContentOf(target, next) ?: return
        // 需要先拿到正文才能算出「最后一页」是第几页。
        val pages = Pagination.paginate(content, settingsFlow.value.wordsPerPage)
        val startPage = if (landOnLastPage) pages.lastIndex else 0

        current.value = CurrentChapter(
            index = next,
            title = titles.value.getOrNull(next).orEmpty(),
            content = content,
            startPage = startPage,
            loadId = (current.value?.loadId ?: 0L) + 1,
        )
        pageIndex.value = startPage
        persistProgress()
    }

    private suspend fun persistProgress() {
        val id = bookId.value
        if (id.isEmpty()) return
        progress.save(
            ReadingProgress(
                bookId = id,
                bookTitle = bookTitle.value,
                chapterIndex = current.value?.index ?: 0,
                pageIndex = pageIndex.value,
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    /**
     * 启动时打开哪本书、落在哪一页。
     *
     * **优先恢复上次读到的地方** —— 这是手机上的必要改进：Web 版连 `currentBook`
     * 都不持久化（刷新就回到内置第一本），而手机上读者期望回来还在原处。
     *
     * 逐级回退：上次那本已经不在库里（也没在内置里）→ 书架第一本 → 内置第一本。
     */
    private suspend fun openInitialBook() {
        val builtin = books.builtinBooks()

        val recent = progress.observeMostRecent().first()
        if (recent != null) {
            val target = when {
                books.findMeta(recent.bookId) != null -> Source.Stored(recent.bookId)
                builtin.any { it.id == recent.bookId } -> Source.Builtin(builtin.first { it.id == recent.bookId })
                else -> null
            }
            if (target != null) {
                open(target, chapterIndex = recent.chapterIndex, startPage = recent.pageIndex)
                return
            }
        }

        val stored = books.observeShelf().first()
        when {
            stored.isNotEmpty() -> open(Source.Stored(stored.first().id), chapterIndex = 0, startPage = 0)
            builtin.isNotEmpty() -> open(Source.Builtin(builtin.first()), chapterIndex = 0, startPage = 0)
            else -> loading.value = false
        }
    }

    private suspend fun open(target: Source, chapterIndex: Int, startPage: Int) {
        source.value = target
        loading.value = true
        pageIndex.value = 0

        val allTitles: List<String>
        when (target) {
            is Source.Builtin -> {
                bookId.value = target.book.id
                bookTitle.value = target.book.title
                allTitles = target.book.chapters.map { it.title }
            }
            is Source.Stored -> {
                bookId.value = target.bookId
                bookTitle.value = books.findMeta(target.bookId)?.title.orEmpty()
                allTitles = books.chapterTitles(target.bookId)
            }
        }
        titles.value = allTitles
        loadChapter(target, chapterIndex.coerceIn(0, maxOf(0, allTitles.size - 1)), startPage)

        // **打开一本书也要记进度。** 只在翻页/切章时写是不够的：从书架打开一本新书
        // 之后如果直接强杀，回去就会落到别的书上 —— 而「我刚才在读哪本」正是进度要
        // 回答的问题。设备验收时就是这样发现的（词库里有那本书，进度表却是空的）。
        persistProgress()
    }

    private companion object {
        const val TAG = "Reader"
    }
}
