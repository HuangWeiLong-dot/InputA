package com.inputa.reader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.inputa.reader.data.local.entity.BookEntity
import com.inputa.reader.data.local.entity.BookSummaryRow
import com.inputa.reader.data.local.entity.ChapterEntity
import com.inputa.reader.data.local.entity.ReadingProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BookDao {

    /**
     * 书架列表。章节数用子查询算出来，不把正文带进来 —— 否则加载书架会把
     * 每本书的全文都读进内存。
     */
    @Query(
        """
        SELECT b.bookId, b.title, b.author, b.coverUrl, b.source, b.language,
               b.addedAt, b.charCount,
               (SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.bookId) AS chapterCount
        FROM books b
        ORDER BY b.addedAt DESC
        """,
    )
    abstract fun observeShelf(): Flow<List<BookSummaryRow>>

    @Query(
        """
        SELECT b.bookId, b.title, b.author, b.coverUrl, b.source, b.language,
               b.addedAt, b.charCount,
               (SELECT COUNT(*) FROM chapters c WHERE c.bookId = b.bookId) AS chapterCount
        FROM books b WHERE b.bookId = :bookId
        """,
    )
    abstract suspend fun findSummary(bookId: String): BookSummaryRow?

    @Query("SELECT * FROM books WHERE bookId = :bookId")
    abstract suspend fun find(bookId: String): BookEntity?

    @Query("SELECT title FROM chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    abstract suspend fun chapterTitles(bookId: String): List<String>

    @Query("SELECT content FROM chapters WHERE bookId = :bookId AND chapterIndex = :index")
    abstract suspend fun chapterContent(bookId: String, index: Int): String?

    @Query("SELECT COUNT(*) FROM chapters WHERE bookId = :bookId")
    abstract suspend fun chapterCount(bookId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(book: BookEntity): Long

    @Query(
        """
        UPDATE books SET title = :title, author = :author, coverUrl = :coverUrl,
                         source = :source, language = :language, charCount = :charCount
        WHERE bookId = :bookId
        """,
    )
    abstract suspend fun updateMeta(
        bookId: String,
        title: String,
        author: String,
        coverUrl: String?,
        source: String?,
        language: String?,
        charCount: Long,
    )

    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    abstract suspend fun deleteChapters(bookId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertChapters(chapters: List<ChapterEntity>)

    @Query("DELETE FROM books WHERE bookId = :bookId")
    abstract suspend fun deleteBook(bookId: String)

    /**
     * 存一本书（元信息 + 全部章节）。
     *
     * **刻意不用 `@Insert(onConflict = REPLACE)` 存 `books` 行。** REPLACE 是
     * DELETE-then-INSERT，会先把父行删掉、连带触发 `chapters` 的
     * `ON DELETE CASCADE` 把整本书的章节删光。紧接着重插所以结果看起来是对的，
     * 但只要中间任何一步失败，用户就永久丢失这本书的正文 —— 而且这个写法在
     * code review 里看不出问题。下面四步显式写出来，代价是四行，收益是读得懂。
     */
    @Transaction
    open suspend fun saveBook(book: BookEntity, chapters: List<ChapterEntity>) {
        insertIfAbsent(book)
        updateMeta(
            bookId = book.bookId,
            title = book.title,
            author = book.author,
            coverUrl = book.coverUrl,
            source = book.source,
            language = book.language,
            charCount = book.charCount,
        )
        deleteChapters(book.bookId)
        insertChapters(chapters)
    }
}

@Dao
interface ReadingProgressDao {

    @Query("SELECT * FROM reading_progress WHERE bookId = :bookId")
    suspend fun find(bookId: String): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT 1")
    fun observeMostRecent(): Flow<ReadingProgressEntity?>

    /**
     * REPLACE 在这里是安全的：`reading_progress` 没有被任何表外键引用，
     * 不存在 [BookDao.saveBook] 注释里那种级联删除的陷阱。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE bookId = :bookId")
    suspend fun delete(bookId: String)
}
