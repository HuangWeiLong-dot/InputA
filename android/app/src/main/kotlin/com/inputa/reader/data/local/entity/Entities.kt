package com.inputa.reader.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room 表结构（v1）。
 *
 * 关于「为什么词库表与笔记表之间不建外键」这条关键决定，见 [VocabularyEntity] 的注释 ——
 * 那不是风格选择，建了会让一份合法备份导入失败。
 */

/**
 * 词库。**值的类型必须是标量**，这条不变量是 Web 版用一段长篇注释和一批测试守住的，
 * 这里用列类型来守：`status` 是 INTEGER，装不下笔记。
 *
 * `status` 的取值是 [com.inputa.reader.domain.model.WordStatus.code]：1-5 是熟练度，
 * 6 是「已掌握」。刻意**不用 TypeConverter** 把枚举直接映射到列上：那一层无法表达
 * 「读到无法识别的值时怎么办」，只能二选一 —— 抛异常（一行脏数据让应用起不来）
 * 或静默兜底（悄悄给用户一个他没设过的熟练度）。两者都比在仓储层显式丢弃更糟。
 * 所以列是裸 Int，转换发生在仓储读取处，与 `normalizeWordMap` 同姿态。
 *
 * [pageTurnStamp] 只由翻页收录写入，[updatedAt] 任何写入都会更新。撤销时两个都要求
 * 匹配当前批次的时间戳，于是「读者在撤销窗口内手动改过这个词」不会被撤销掉。
 *
 * **与 notes / saved_sentences 之间没有外键，这是有意的。** 理由具体而非审美：
 * 备份的三份数据是**独立**规范化的，所以 `{words:{}, notes:{run:["跑"]}, sentences:{}}`
 * 是一份合法且非空的备份（Web 版有测试钉住这种情形）。加了
 * `FOREIGN KEY(word) REFERENCES vocabulary(word)` 这份备份就会插入失败。
 * Web 版的级联删除是**调用点决定**的（`VocabularyModal.handleRemoveWord` 同时调两个
 * store），不是 schema 决定；搬成 `ON DELETE CASCADE` 还会让「导入时先清空词库」
 * 那一步顺手抹掉同一事务里刚要写回的笔记 —— 一个真实的事务内顺序陷阱。
 * 所以移出一个词时由仓储开一个事务发三条 delete。
 */
@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "status") val status: Int,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
    @ColumnInfo(name = "pageTurnStamp") val pageTurnStamp: Long? = null,
)

/**
 * 一个词的一条笔记。手写笔记与采纳的 AI 建议同构，无法事后区分 —— 这是有意的。
 *
 * 唯一索引用**默认的 BINARY 排序**，不是 NOCASE：Web 版的去重判据是
 * `existing.includes(text)`，精确匹配，用户可能刻意保留两条只差大小写的笔记。
 * 加 `COLLATE NOCASE` 会把 "Note" 与 "note" 静默合并。
 */
@Entity(
    tableName = "notes",
    indices = [Index(value = ["word", "body"], unique = true)],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "body") val body: String,
)

/**
 * 一句保存下来的例句。`(word, sentence)` 唯一 —— `sentence` 是去重键，同一句只留一条。
 */
@Entity(
    tableName = "saved_sentences",
    indices = [Index(value = ["word", "sentence"], unique = true)],
)
data class SavedSentenceEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "word") val word: String,
    @ColumnInfo(name = "sentence") val sentence: String,
    @ColumnInfo(name = "translation") val translation: String? = null,
    @ColumnInfo(name = "bookId") val bookId: String? = null,
    @ColumnInfo(name = "bookTitle") val bookTitle: String? = null,
    @ColumnInfo(name = "chapterIndex") val chapterIndex: Int? = null,
    @ColumnInfo(name = "pageIndex") val pageIndex: Int? = null,
    @ColumnInfo(name = "createdAt") val createdAt: Long? = null,
)

/**
 * 一本书。[charCount] 是正文的**字符数**（不是字节数），只用于书架上的体量显示。
 *
 * 刻意留字符数而不是字节数：算字节要对整本正文做一次 `toByteArray()`，
 * 一本 12MB 的书就是一次 12MB 的分配，只为显示一个概数不值当。
 * 真要判断能不能塞进一个 SQLite 行，看的是 [ChapterEntity.content] 的长度上限
 * （见 GutenbergTextProcessor.MAX_CHAPTER_CHARS），不是这个值。
 */
@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "coverUrl") val coverUrl: String? = null,
    /** [com.inputa.reader.domain.model.BookSource.key]，未知值按 null 处理。 */
    @ColumnInfo(name = "source") val source: String? = null,
    @ColumnInfo(name = "language") val language: String? = null,
    @ColumnInfo(name = "addedAt") val addedAt: Long,
    @ColumnInfo(name = "charCount") val charCount: Long,
)

/**
 * 一章的正文。
 *
 * 外键到 [BookEntity] 带 `ON DELETE CASCADE` —— 删书即删章，这里不存在上面词库表
 * 那种「三份数据独立」的问题。
 *
 * **但正因如此，`books` 行绝不能用 `@Insert(onConflict = REPLACE)` 保存**：
 * REPLACE 是 DELETE-then-INSERT，会先把父行删掉、连带把所有章节级联删除。
 * 在 `BookDao.saveBook` 的事务里紧接着重插章节所以看不出问题，任何一步失败就是灾难，
 * 而且 code review 看不出来。见那边的显式序列。
 *
 * [content] 有条数上限：Room 读取单行时受 `CursorWindow` 的 2MB 限制，
 * 而 Gutenberg 切章有可能产出整本书作一章。上限在摄取时施加
 * （见 GutenbergTextProcessor 的 MAX_CHAPTER_CHARS）。
 */
@Entity(
    tableName = "chapters",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["bookId"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChapterEntity(
    @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "chapterIndex") val chapterIndex: Int,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "content") val content: String,
)

/**
 * 阅读进度。
 *
 * **不建到 `books` 的外键。** 内置样书是随包发行的、没有入库，建了外键它们的进度
 * 就写不进去 —— 而 Web 版从来不需要书入库就能存进度（它只存 `bookId → 进度`）。
 * 书架按 id 关联，容得下「进度行指向一本已删掉的书」。
 */
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "bookTitle") val bookTitle: String,
    @ColumnInfo(name = "chapterIndex") val chapterIndex: Int,
    @ColumnInfo(name = "pageIndex") val pageIndex: Int,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)

/** 书架列表的投影：不带章节正文，否则加载书架会把整本书读进内存。 */
data class BookSummaryRow(
    @ColumnInfo(name = "bookId") val bookId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author") val author: String,
    @ColumnInfo(name = "coverUrl") val coverUrl: String?,
    @ColumnInfo(name = "source") val source: String?,
    @ColumnInfo(name = "language") val language: String?,
    @ColumnInfo(name = "addedAt") val addedAt: Long,
    @ColumnInfo(name = "charCount") val charCount: Long,
    @ColumnInfo(name = "chapterCount") val chapterCount: Int,
)
