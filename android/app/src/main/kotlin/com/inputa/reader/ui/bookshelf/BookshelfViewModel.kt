package com.inputa.reader.ui.bookshelf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.reader.domain.book.BookDownloadException
import com.inputa.reader.domain.book.GutendexBook
import com.inputa.reader.domain.lang.LanguageDetect
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.BookChapter
import com.inputa.reader.domain.model.BookSource
import com.inputa.reader.domain.repository.BookRepository
import com.inputa.reader.domain.repository.BookSummary
import com.inputa.reader.domain.util.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 书架。
 *
 * 相对 Web 版多了一件事：**书是持久化的**。那边 `useReaderStore` 只存进度与设置，
 * `currentBook` 不落盘，刷新就回到内置第一本。所以那边的书库只有「内置 / 搜索 / 粘贴」
 * 三个入口而没有「我导入过的书」这一栏 —— 这里必须有。
 */
@HiltViewModel
class BookshelfViewModel @Inject constructor(
    private val books: BookRepository,
    private val clock: Clock,
) : ViewModel() {

    enum class Tab { SHELF, SEARCH, PASTE }

    data class State(
        val tab: Tab = Tab.SHELF,
        val shelf: List<BookSummary> = emptyList(),
        val builtin: List<Book> = emptyList(),
        val query: String = "",
        val results: List<GutendexBook> = emptyList(),
        val isSearching: Boolean = false,
        val isLoadingBook: Boolean = false,
        val error: String? = null,
        val pasteTitle: String = "",
        val pasteContent: String = "",
        val busyLabel: String? = null,
    )

    private val tab = MutableStateFlow(Tab.SHELF)
    private val query = MutableStateFlow("")
    private val results = MutableStateFlow<List<GutendexBook>>(emptyList())
    private val isSearching = MutableStateFlow(false)
    private val isLoadingBook = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private val pasteTitle = MutableStateFlow("")
    private val pasteContent = MutableStateFlow("")

    /**
     * 载入完成、可以打开的书 id。
     *
     * 用通道而不是让书架自己去开书：开书是阅读器的职责（它持有章节与分页状态），
     * 而这里的动作只是「哪本书好了」。
     */
    private val opened = Channel<String>(Channel.BUFFERED)
    val openRequests: Flow<String> = opened.receiveAsFlow()

    /** 内置样书随包发行，读一次就够 —— asset 的内容不会变。 */
    private val builtinBooks = MutableStateFlow<List<Book>>(emptyList())

    private data class Job(
        val results: List<GutendexBook>,
        val searching: Boolean,
        val loading: Boolean,
        val error: String?,
        val builtin: List<Book>,
    )

    val state: StateFlow<State> = combine(
        books.observeShelf(),
        tab,
        query,
        combine(results, isSearching, isLoadingBook, error, builtinBooks) { r, searching, loading, err, builtin ->
            Job(r, searching, loading, err, builtin)
        },
        combine(pasteTitle, pasteContent) { t, c -> t to c },
    ) { shelf, currentTab, currentQuery, job, paste ->
        State(
            tab = currentTab,
            shelf = shelf,
            builtin = job.builtin,
            query = currentQuery,
            results = job.results,
            isSearching = job.searching,
            isLoadingBook = job.loading,
            error = job.error,
            pasteTitle = paste.first,
            pasteContent = paste.second,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), State())

    init {
        viewModelScope.launch { builtinBooks.value = books.builtinBooks() }
    }

    fun setTab(next: Tab) {
        tab.value = next
        error.value = null
    }

    fun setQuery(next: String) {
        query.value = next
    }

    fun setPasteTitle(next: String) {
        pasteTitle.value = next
    }

    fun setPasteContent(next: String) {
        pasteContent.value = next
    }

    fun dismissError() {
        error.value = null
    }

    fun search() {
        val text = query.value.trim()
        if (text.isEmpty()) return
        error.value = null
        viewModelScope.launch {
            isSearching.value = true
            try {
                results.value = books.searchGutendex(text)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: Exception) {
                results.value = emptyList()
                error.value = failure.message ?: "搜索失败"
            } finally {
                isSearching.value = false
            }
        }
    }

    /** 下载一本 Gutendex 的书、入库，然后请阅读器打开它。 */
    fun loadGutendex(item: GutendexBook) {
        error.value = null
        viewModelScope.launch {
            isLoadingBook.value = true
            try {
                val book = books.loadFromGutendex(item)
                books.save(book)
                opened.trySend(book.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (failure: BookDownloadException) {
                // 这些消息是给读者看的（比如「请在项目根目录运行 npm run server」），
                // 所以原样显示，不要包一层。
                error.value = failure.message
            } catch (failure: Exception) {
                error.value = failure.message ?: "加载失败"
            } finally {
                isLoadingBook.value = false
            }
        }
    }

    /**
     * 保存粘贴的正文。
     *
     * 语言用本地检测 —— 粘贴的文本没有书源元数据可依（Gutendex 那边有 `languages`，
     * 所以那边优先用元数据）。检测结果只用来挑朗读音色与显示标签。
     */
    fun savePasted() {
        val content = pasteContent.value.trim()
        if (content.isEmpty()) return

        val book = Book(
            id = "custom-${clock.nowMillis()}",
            title = pasteTitle.value.trim().ifEmpty { "自定义导入" },
            author = "User Imported",
            chapters = listOf(BookChapter("Section 1", content)),
            source = BookSource.CUSTOM,
            language = LanguageDetect.detect(content).language,
        )

        viewModelScope.launch {
            books.save(book)
            pasteTitle.value = ""
            pasteContent.value = ""
            opened.trySend(book.id)
        }
    }

    fun openBook(id: String) {
        viewModelScope.launch { opened.trySend(id) }
    }

    fun deleteBook(id: String) {
        viewModelScope.launch { books.delete(id) }
    }
}
