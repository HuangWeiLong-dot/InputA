package com.inputa.reader.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.inputa.reader.data.local.entity.BookEntity
import com.inputa.reader.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 文件级常量而不是类成员：默认参数值引用实例成员容易踩到作用域的坑。 */
private const val DEFAULT_BOOK_ID = "gutendex-1342"

@RunWith(RobolectricTestRunner::class)
class BookDaoTest {

    private lateinit var database: InputaDatabase
    private lateinit var dao: com.inputa.reader.data.local.dao.BookDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InputaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.bookDao()
    }

    @After
    fun tearDown() = database.close()

    private fun book(id: String = "gutendex-1342", title: String = "Pride and Prejudice") =
        BookEntity(
            bookId = id,
            title = title,
            author = "Austen, Jane",
            source = "gutenberg",
            language = "en",
            addedAt = 1_000,
            charCount = 42,
        )

    /**
     * 章节必须挂在真实存在的书上：`chapters` 表有到 `books` 的外键（带级联删除），
     * 用一个没存过的 bookId 会撞 SQLITE_CONSTRAINT_FOREIGNKEY。
     *
     * `vararg` 放前面、带默认值的 `bookId` 放后面，这样最常见的调用
     * `chapters("One", "Two")` 不会把标题误绑到 bookId 上。Kotlin 允许 vararg
     * 不在末位，但之后的参数只能用命名实参传。
     */
    private fun chapters(vararg titles: String, bookId: String = DEFAULT_BOOK_ID) =
        titles.mapIndexed { index, title ->
            ChapterEntity(
                bookId = bookId,
                chapterIndex = index,
                title = title,
                content = "content of $title",
            )
        }

    @Test
    fun `saving a book stores its chapters`() = runTest {
        dao.saveBook(book(), chapters("One", "Two", "Three"))

        assertEquals(3, dao.chapterCount("gutendex-1342"))
        assertEquals(listOf("One", "Two", "Three"), dao.chapterTitles("gutendex-1342"))
        assertEquals("content of Two", dao.chapterContent("gutendex-1342", 1))
    }

    /**
     * 重存同一本书必须**替换**章节而不是追加。这是 `saveBook` 不用
     * `@Insert(REPLACE)` 存父行的原因之一 —— 见 BookDao.saveBook 的注释：
     * REPLACE 是 DELETE-then-INSERT，会触发 chapters 的级联删除。
     * 那条隐患只在事务中途失败时才显现，所以这里钉的是它正常时的语义。
     */
    @Test
    fun `re-saving a book replaces its chapters instead of duplicating them`() = runTest {
        dao.saveBook(book(), chapters("One", "Two"))
        dao.saveBook(book(title = "Pride and Prejudice (revised)"), chapters("First", "Second", "Third"))

        assertEquals(3, dao.chapterCount("gutendex-1342"))
        assertEquals(listOf("First", "Second", "Third"), dao.chapterTitles("gutendex-1342"))

        val meta = dao.find("gutendex-1342")
        assertEquals("Pride and Prejudice (revised)", meta?.title)
    }

    @Test
    fun `saving a book with no chapters leaves an empty book, not a missing one`() = runTest {
        dao.saveBook(book(), emptyList())

        assertEquals(0, dao.chapterCount("gutendex-1342"))
        assertEquals("Pride and Prejudice", dao.find("gutendex-1342")?.title)
    }

    @Test
    fun `deleting a book cascades to its chapters`() = runTest {
        dao.saveBook(book(), chapters("One", "Two"))

        dao.deleteBook("gutendex-1342")

        assertNull(dao.find("gutendex-1342"))
        assertEquals(0, dao.chapterCount("gutendex-1342"))
    }

    @Test
    fun `the shelf lists books newest first with a chapter count`() = runTest {
        dao.saveBook(book("a", "Older").copy(addedAt = 1_000), chapters("One", bookId = "a"))
        dao.saveBook(book("b", "Newer").copy(addedAt = 2_000), chapters("One", "Two", bookId = "b"))

        val shelf = dao.observeShelf().first()

        assertEquals(listOf("Newer", "Older"), shelf.map { it.title })
        assertEquals(listOf(2, 1), shelf.map { it.chapterCount })
    }

    @Test
    fun `the shelf projection does not read chapter text`() = runTest {
        dao.saveBook(book(), chapters("One", "Two"))

        // BookSummaryRow 里根本没有 content 字段 —— 这是它存在的意义：
        // 加载书架不能把每本书的全文读进内存。
        val row = dao.findSummary("gutendex-1342")
        assertEquals("Pride and Prejudice", row?.title)
        assertEquals(2, row?.chapterCount)
    }

    @Test
    fun `an unknown book has no meta and no content`() = runTest {
        assertNull(dao.find("nope"))
        assertNull(dao.findSummary("nope"))
        assertNull(dao.chapterContent("nope", 0))
        assertEquals(emptyList<String>(), dao.chapterTitles("nope"))
    }
}
