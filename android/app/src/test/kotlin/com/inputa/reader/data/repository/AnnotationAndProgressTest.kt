package com.inputa.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.inputa.reader.data.local.InputaDatabase
import com.inputa.reader.domain.model.ReadingProgress
import com.inputa.reader.domain.model.SavedSentence
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 笔记、例句与阅读进度。
 *
 * 笔记与例句的行为对应 Web 版 `annotations.test.ts` 里测 store 的那一半
 * （规范化的那一半在 :domain 的 AnnotationNormalizerTest 里）。
 */
@RunWith(RobolectricTestRunner::class)
class AnnotationAndProgressTest {

    private lateinit var database: InputaDatabase
    private lateinit var annotations: AnnotationRepositoryImpl
    private lateinit var progress: ProgressRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InputaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        annotations = AnnotationRepositoryImpl(database, database.noteDao(), database.savedSentenceDao())
        progress = ProgressRepositoryImpl(database.readingProgressDao())
    }

    @After
    fun tearDown() = database.close()

    // ---------- 笔记 ----------

    @Test
    fun `adds, lists and removes notes for a word`() = runTest {
        annotations.addNote("run", "跑")
        annotations.addNote("run", "奔跑")

        assertEquals(listOf("跑", "奔跑"), annotations.observeNotes("run").first())

        annotations.removeNote("run", "跑")
        assertEquals(listOf("奔跑"), annotations.observeNotes("run").first())
    }

    @Test
    fun `trims notes, ignores blanks, and rejects exact duplicates`() = runTest {
        annotations.addNote("run", "  跑  ")
        annotations.addNote("run", "跑")
        annotations.addNote("run", "   ")
        annotations.addNote("run", "")

        assertEquals(listOf("跑"), annotations.observeNotes("run").first())
    }

    @Test
    fun `notes are stored per word and do not leak across words`() = runTest {
        annotations.addNote("run", "跑")
        annotations.addNote("walk", "走")

        assertEquals(listOf("跑"), annotations.observeNotes("run").first())
        assertEquals(listOf("走"), annotations.observeNotes("walk").first())
    }

    // ---------- 例句 ----------

    @Test
    fun `saving the same sentence twice keeps one entry`() = runTest {
        annotations.addSentence("run", SavedSentence("He runs every morning."))
        annotations.addSentence("run", SavedSentence("He runs every morning."))

        assertEquals(1, annotations.observeSentences("run").first().size)
    }

    /**
     * Web 版的行为：翻译是**迟到**的（先存句子，AI 翻译回来再补），
     * 所以补的时候只在缺失时写入，绝不覆盖已有的。
     */
    @Test
    fun `a late translation fills in a missing one`() = runTest {
        annotations.addSentence("run", SavedSentence("He runs."))
        assertNull(annotations.observeSentences("run").first().first().translation)

        annotations.addSentence("run", SavedSentence("He runs.", translation = "他跑步。"))

        assertEquals("他跑步。", annotations.observeSentences("run").first().first().translation)
    }

    @Test
    fun `a late translation never overwrites an existing one`() = runTest {
        annotations.addSentence("run", SavedSentence("He runs.", translation = "他跑步。"))

        annotations.addSentence("run", SavedSentence("He runs.", translation = "他奔跑了。"))

        assertEquals("他跑步。", annotations.observeSentences("run").first().first().translation)
    }

    @Test
    fun `sentence provenance round trips`() = runTest {
        val saved = SavedSentence(
            sentence = "He runs.",
            translation = "他跑步。",
            bookId = "gutendex-1342",
            bookTitle = "Pride and Prejudice",
            chapterIndex = 2,
            pageIndex = 7,
            createdAt = 1_700_000_000_000L,
        )
        annotations.addSentence("run", saved)

        assertEquals(saved, annotations.observeSentences("run").first().first())
    }

    @Test
    fun `removing annotations for a word leaves other words alone`() = runTest {
        annotations.addNote("run", "跑")
        annotations.addSentence("run", SavedSentence("He runs."))
        annotations.addNote("walk", "走")

        annotations.removeWordAnnotations("run")

        assertEquals(emptyList<String>(), annotations.observeNotes("run").first())
        assertEquals(emptyList<SavedSentence>(), annotations.observeSentences("run").first())
        assertEquals(listOf("走"), annotations.observeNotes("walk").first())
    }

    // ---------- 阅读进度 ----------

    @Test
    fun `progress is a single row per book, overwritten on each save`() = runTest {
        progress.save(ReadingProgress("book-a", "Book A", chapterIndex = 0, pageIndex = 0, updatedAt = 100))
        progress.save(ReadingProgress("book-a", "Book A", chapterIndex = 1, pageIndex = 5, updatedAt = 200))

        val saved = progress.find("book-a")
        assertEquals(1, saved?.chapterIndex)
        assertEquals(5, saved?.pageIndex)
        assertEquals(200L, saved?.updatedAt)
    }

    @Test
    fun `the most recently read book is the one that comes back`() = runTest {
        progress.save(ReadingProgress("old", "Old", 0, 0, updatedAt = 100))
        progress.save(ReadingProgress("new", "New", 2, 3, updatedAt = 200))

        assertEquals("new", progress.observeMostRecent().first()?.bookId)
    }

    @Test
    fun `progress exists for a book that was never stored`() = runTest {
        // 内置样书随包发行、不入库，所以进度表不建到 books 的外键 —— 这条钉住它。
        progress.save(ReadingProgress("alice-in-wonderland", "Alice", 1, 4, updatedAt = 100))

        assertEquals(4, progress.find("alice-in-wonderland")?.pageIndex)
    }

    @Test
    fun `deleting progress works and unknown books return null`() = runTest {
        progress.save(ReadingProgress("book-a", "Book A", 0, 0, updatedAt = 100))
        progress.delete("book-a")

        assertNull(progress.find("book-a"))
        assertNull(progress.find("never-seen"))
        assertNull(progress.observeMostRecent().first())
    }
}
