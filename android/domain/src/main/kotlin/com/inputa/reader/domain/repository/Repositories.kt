package com.inputa.reader.domain.repository

import com.inputa.reader.domain.model.AnnotationsData
import com.inputa.reader.domain.model.AppTheme
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.model.ReadingProgress
import com.inputa.reader.domain.model.ReaderSettings
import com.inputa.reader.domain.model.SavedSentence
import com.inputa.reader.domain.model.TtsProvider
import com.inputa.reader.domain.model.WordStatus
import kotlinx.coroutines.flow.Flow

/**
 * 仓储接口。定义在领域层、实现在 `:app` 的数据层 —— 这样领域层的规则就不依赖
 * Room / Retrofit / DataStore，而且 `:domain` 的模块边界（不依赖 android/androidx）
 * 让「领域层不知道持久化」成为结构性事实而不是一句约定。
 */

/**
 * 词库。**这是词汇规则唯一的写入口** —— 改规则先看
 * [com.inputa.reader.domain.vocab.WordLevels] 与
 * [com.inputa.reader.domain.vocab.VocabularyRules]，再看这里几个写方法的文档。
 */
interface VocabularyRepository {

    /** 查一个词的状态，未收录返回 null。 */
    suspend fun statusOf(word: String): WordStatus?

    /** 手动指定熟练度（释义面板的 5 段选择器）。**覆盖**已有状态 —— 这是明确的用户意图。 */
    suspend fun setStatus(word: String, status: WordStatus)

    /**
     * 正文里点击一个词：只在尚未收录时记为 5 级「生词」。
     * 已有状态（1-5 级或已掌握）原样保留。
     */
    suspend fun markAsNewWord(word: String)

    /** 记为「掌握」：正文不再高亮。覆盖已有状态。 */
    suspend fun markMastered(word: String)

    /**
     * 移出一个词**及其笔记与例句**。
     *
     * 对应 Web 版 `VocabularyModal.handleRemoveWord` 里连着调用的
     * `removeWord` + `removeWordAnnotations` —— 那边是两个 store 各自删一份，
     * 这里是一个事务删三条，否则会留下查不到的孤儿笔记。
     */
    suspend fun removeWord(word: String)

    /**
     * 翻页时把本页没被点击的词记为「掌握」。
     *
     * @return 本次**真正新写入**的词；没有任何新写入时返回 [MasteryBatch.EMPTY]。
     *   撤销需要这份名单，而代价是零。
     */
    suspend fun applyPageTurn(pageWords: List<String>): MasteryBatch

    /**
     * 撤销一次翻页收录。只删那一批，且只删**之后没被用户改过**的行。
     *
     * @return 实际删掉的条数。少于批次大小是正常且正确的：说明读者在撤销窗口内
     *   手动改过其中某个词的熟练度，那属于用户判断，不该被撤销覆盖。
     */
    suspend fun undoMastery(batch: MasteryBatch): Int

    /** 整本词库，一次性读取。 */
    suspend fun all(): Map<String, WordStatus>

    /**
     * 整本词库的**实时**视图。
     *
     * 两个界面都用它：生词本的全部意义就是看见整个词库；阅读器也一样（底色要在翻页落地
     * 那一帧就是对的，见 `ReaderViewModel.statusesFlow` —— 按页裁剪会闪色）。
     *
     * 所以这条查询是**整表读**，而且 Room 的失效通知是表级的，每次写入都会重跑一遍。
     * 返回值里没有的词就是「未收录」—— 领域层用 null 表示未收录，不需要 `'unknown'` 哨兵。
     */
    fun observeAll(): Flow<Map<String, WordStatus>>

    /** 顶栏徽章上的两个数字。用一条 GROUP BY 算，不要把整张表拉到内存里再数。 */
    fun observeCounts(): Flow<com.inputa.reader.domain.vocab.VocabularyCounts>

    /** 清空整本词库（连同笔记与例句）。对应 Web 版的「清空词汇库」按钮。 */
    suspend fun clear()

    /**
     * 替换式导入一份备份：**原子地**换掉词库与笔记。
     *
     * 之所以是同一个方法而不是「先 importVocabulary 再 importAnnotations」：
     * 那样是两次事务，第二步失败会留下「有词无笔记」的半截状态 —— 比整份导入失败更糟，
     * 因为用户不会发现丢了什么。Web 版是两次独立的 localStorage 写入，没有这个问题
     * 也没有这个保证；这里顺手补上。
     */
    suspend fun importAll(words: Map<String, WordStatus>, annotations: AnnotationsData)
}

/**
 * 一次翻页收录的批次。
 *
 * [stamp] 同时写进 `updatedAt` 与 `pageTurnStamp`；撤销时两个条件都要求匹配，
 * 于是「读者在撤销窗口内手动改过这个词」不会被撤销掉。
 */
data class MasteryBatch(
    val stamp: Long,
    val words: List<String>,
) {
    val count: Int get() = words.size
    val isEmpty: Boolean get() = words.isEmpty()

    companion object {
        val EMPTY = MasteryBatch(0L, emptyList())
    }
}

/**
 * 笔记与例句。与词库分开存放，理由见
 * [com.inputa.reader.domain.annotation.AnnotationNormalizer] 的类注释。
 *
 * 这里没有 `clear` / `replaceAll`：整库的清空与导入都必须与词库在**同一个事务**里，
 * 那两个入口在 [VocabularyRepository] 上，不要在这里再开一条路径。
 */
interface AnnotationRepository {
    fun observeNotes(word: String): Flow<List<String>>
    fun observeSentences(word: String): Flow<List<SavedSentence>>

    suspend fun addNote(word: String, note: String)
    suspend fun removeNote(word: String, note: String)

    /** 同句只留一条；迟到但缺失的翻译会补进去，**已有翻译不会被覆盖**。 */
    suspend fun addSentence(word: String, sentence: SavedSentence)
    suspend fun removeSentence(word: String, sentence: String)

    /** 移出一个词的全部笔记与例句（词本身由 [VocabularyRepository.removeWord] 负责）。 */
    suspend fun removeWordAnnotations(word: String)

    /** 整份标注数据，供导出使用。 */
    suspend fun all(): AnnotationsData
}

/** 书籍与章节。Web 版不持久化书（刷新回到内置第一本），手机上必须存。 */
interface BookRepository {
    /** 书架列表，最近加入的在前。**不含正文**。 */
    fun observeShelf(): Flow<List<BookSummary>>

    suspend fun findMeta(bookId: String): BookSummary?

    suspend fun chapterTitles(bookId: String): List<String>

    /**
     * 取一章正文。刻意**按章取**而不是把整本读进内存 —— Gutendex 的书可以到 12MB，
     * 而阅读器同一时刻只需要当前这一章（分页本身也只在一章之内进行）。
     */
    suspend fun chapterContent(bookId: String, chapterIndex: Int): String?

    suspend fun save(book: Book)
    suspend fun delete(bookId: String)

    /** 内置样书；随包发行，不入库。 */
    suspend fun builtinBooks(): List<Book>

    /**
     * 搜 Gutendex。
     *
     * @throws com.inputa.reader.domain.book.BookDownloadException 网络或上游失败，消息可直接展示
     */
    suspend fun searchGutendex(query: String): List<com.inputa.reader.domain.book.GutendexBook>

    /**
     * 下载并切章，组装成一本可读的书（**不落盘** —— 由调用方决定何时 [save]）。
     *
     * @throws com.inputa.reader.domain.book.BookDownloadException
     */
    suspend fun loadFromGutendex(
        item: com.inputa.reader.domain.book.GutendexBook,
    ): Book
}

/** 书架条目：书的元信息 + 章节数，不含正文。 */
data class BookSummary(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    val source: com.inputa.reader.domain.model.BookSource? = null,
    val language: String? = null,
    val addedAt: Long = 0L,
    val charCount: Long = 0L,
    val chapterCount: Int = 0,
)

/** 阅读进度。每本书一行，按最近读过取。 */
interface ProgressRepository {
    suspend fun save(progress: ReadingProgress)
    suspend fun find(bookId: String): ReadingProgress?

    /** 最近读过的那本，用于启动时恢复。 */
    fun observeMostRecent(): Flow<ReadingProgress?>

    /** 内置样书没入库，所以进度表**不建到 books 的外键** —— 见 ReadingProgressEntity。 */
    suspend fun delete(bookId: String)
}

/**
 * 后端健康状态。探测一次比每次查词都试一遍便宜得多 ——
 * `/api/health` 顺带告诉我们服务器能不能读到 `data/stardict.db`，
 * 所以「后端在跑但没装词典库」不会每次查词都白试一轮。
 */
data class BackendHealth(
    val ok: Boolean = false,
    val service: String? = null,
    /**
     * 服务器自己配了 DeepSeek key，客户端可以不带 `Authorization`。
     *
     * `null` 表示**老版本服务器没告诉我们**，与「明确说了没有」不同 ——
     * 前者应该照常带上客户端 key。
     */
    val dictionaryAvailable: Boolean? = null,
    val deepseekKeyConfigured: Boolean = false,
    /** 探测失败的原因，用于设置页的「测试连接」。 */
    val error: String? = null,
)

/**
 * 健康探测。
 *
 * Web 版每次页面加载探一次并缓存 promise。Android 这边多一层：**服务器地址是用户
 * 可改的设置**，改了就必须重新探测 —— 否则换了地址还拿着旧后端的状态。
 */
interface BackendHealthRepository {
    /** 当前已知状态；地址一变会重新探测。 */
    val health: Flow<BackendHealth>

    /** 主动重探一次（设置页的「测试连接」）。 */
    suspend fun refresh(): BackendHealth

    /** 立即读一次，不等待 Flow —— 查词前要用。 */
    suspend fun current(): BackendHealth
}

/**
 * 词典查询。实现是四个来源的降级链，规则见
 * [com.inputa.reader.domain.dict.DictionaryLookup]。
 */
interface DictionaryRepository {
    /**
     * @param refreshBackend 先强制重探一次后端健康状态。
     *
     * **这是面板上「重新查询」按钮需要的**，而且是设备验证时才发现的：健康状态
     * 按服务器地址缓存（Web 版是每次页面加载探一次，浏览器有刷新，Android 没有），
     * 于是「用户刚把后端启起来 → 点重新查询」这个最该成功的场景，会因为还拿着
     * 旧的「后端不在」结论而**永远失败**。
     *
     * 只在显式重试时重探、不做 TTL，是有意的：后端没在跑时每次探测都要等
     * 2.5 秒超时，给普通查词加这个代价不值当 —— 而「重新查询」是用户明确说
     * 「再试一次」的时刻，那正是该付这个代价的地方。
     */
    suspend fun lookup(
        word: String,
        refreshBackend: Boolean = false,
    ): com.inputa.reader.domain.model.LookupResult
}

/**
 * 只提供「当前的服务器地址」。
 *
 * 之所以从 [SettingsRepository] 里切出一个更窄的接口：网络层只需要这一个值，
 * 不该为此拿到整个设置仓库。副作用是测试替身从十几个方法变成一个 lambda。
 */
fun interface ServerBaseUrlProvider {
    suspend fun current(): String
}

/** 设置。实现在 DataStore 上，每个字段单独取默认值。 */
interface SettingsRepository {
    val settings: Flow<ReaderSettings>

    suspend fun setFontSize(sp: Int)
    suspend fun setTheme(theme: AppTheme)
    suspend fun setLineHeight(height: Float)
    suspend fun setTtsProvider(provider: TtsProvider)
    suspend fun setTtsVoice(voice: String)
    suspend fun setTtsRate(rate: String)
    suspend fun setTtsCustomUrl(template: String)
    suspend fun setBionicEnabled(enabled: Boolean)
    suspend fun setReadingRulerEnabled(enabled: Boolean)
    suspend fun setWordsPerPage(count: Int)

    /**
     * 设置服务器地址。传入的是用户原始输入，由实现负责
     * [com.inputa.reader.domain.model.normalizeBaseUrl]。
     *
     * @return 规范化后的地址；输入不合法返回 null 且**不写入**。
     */
    suspend fun setServerBaseUrl(raw: String): String?

    /** 读一次当前值，不等待 Flow —— 网络层构造 baseUrl 时需要同步拿到。 */
    suspend fun currentServerBaseUrl(): String
}
