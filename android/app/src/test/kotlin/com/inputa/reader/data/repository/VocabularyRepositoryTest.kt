package com.inputa.reader.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.inputa.reader.data.local.InputaDatabase
import com.inputa.reader.domain.annotation.AnnotationNormalizer
import com.inputa.reader.domain.model.AnnotationsData
import com.inputa.reader.domain.model.SavedSentence
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.repository.MasteryBatch
import com.inputa.reader.domain.util.Clock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 词汇规则的端到端测试 —— 对应 Web 版 `reader.test.ts` 的
 * `describe('Vocabulary Store: levels and the page-turn rule')`。
 *
 * 这些断言每一条都对应一个会**静默毁数据**的场景，所以哪怕实现换了（zustand → Room），
 * 断言必须原样保留。
 *
 * 覆写的手动编辑规则（撤销窗口内改过的词不该被撤销）是 Web 版没有的行为，
 * 因为 Web 版根本没有撤销 —— 那是本次移植新增的，见计划里的「有意偏离」。
 */
@RunWith(RobolectricTestRunner::class)
class VocabularyRepositoryTest {

    private lateinit var database: InputaDatabase
    private lateinit var repository: VocabularyRepositoryImpl

    /** 可变时钟：撤销规则依赖时间戳，必须能控制它。 */
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, InputaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = VocabularyRepositoryImpl(
            database = database,
            vocabularyDao = database.vocabularyDao(),
            noteDao = database.noteDao(),
            savedSentenceDao = database.savedSentenceDao(),
            clock = Clock { now },
        )
    }

    @After
    fun tearDown() = database.close()

    // ---------- 手动指定熟练度 ----------

    @Test
    fun `stores a manually picked level, case-insensitively`() = runTest {
        repository.setStatus("ubiquitous", WordStatus.L3)
        assertEquals(WordStatus.L3, repository.statusOf("ubiquitous"))
        assertEquals(WordStatus.L3, repository.statusOf("UBIQUITOUS"))
        assertEquals(WordStatus.L3, repository.statusOf("  Ubiquitous  "))
    }

    // ---------- 点击收录：首次生效，之后绝不覆盖 ----------

    @Test
    fun `files a clicked word as the newest level only when it is not already filed`() = runTest {
        repository.markAsNewWord("ubiquitous")
        assertEquals(WordStatus.L5, repository.statusOf("ubiquitous"))

        // 再点一次什么都不改，而且大小写无关。
        repository.markAsNewWord("UBIQUITOUS")
        assertEquals(WordStatus.L5, repository.statusOf("ubiquitous"))

        // 读者手动定过的级别，点正文不该冲掉它。
        repository.setStatus("wonderland", WordStatus.L2)
        repository.markAsNewWord("wonderland")
        assertEquals(WordStatus.L2, repository.statusOf("wonderland"))

        // 「已掌握」同理：点击不能把已掌握的词复活成生词。
        repository.markMastered("alice")
        repository.markAsNewWord("alice")
        assertEquals(WordStatus.MASTERED, repository.statusOf("alice"))
    }

    @Test
    fun `marks a word as mastered`() = runTest {
        repository.markMastered("ephemeral")
        assertEquals(WordStatus.MASTERED, repository.statusOf("ephemeral"))
    }

    @Test
    fun `an empty or blank word is ignored`() = runTest {
        repository.markAsNewWord("")
        repository.markAsNewWord("   ")
        assertEquals(emptyMap<String, WordStatus>(), repository.all())
    }

    // ---------- 翻页收录：绝不覆盖已有状态 ----------

    @Test
    fun `files unclicked page words as mastered and never overwrites a level`() = runTest {
        // 读的时候点过 → 5 级；手动定过 → 2 级。
        repository.setStatus("curiosity", WordStatus.L5)
        repository.setStatus("wonderland", WordStatus.L2)

        val batch = repository.applyPageTurn(listOf("alice", "curiosity", "wonderland", "rabbit"))

        // 只有两个从未见过的词被收录。
        assertEquals(2, batch.count)
        assertEquals(listOf("alice", "rabbit"), batch.words)

        assertEquals(WordStatus.L5, repository.statusOf("curiosity")) // 仍是生词，读者点过
        assertEquals(WordStatus.L2, repository.statusOf("wonderland")) // 读者的判断优先
        assertEquals(WordStatus.MASTERED, repository.statusOf("alice"))
        assertEquals(WordStatus.MASTERED, repository.statusOf("rabbit"))
    }

    @Test
    fun `leaves already-mastered words alone and ignores single letters`() = runTest {
        repository.markMastered("already")

        // 对应 Web 版那条 markPageWordsAsMastered(['already','a','I','b']) === 0。
        val batch = repository.applyPageTurn(listOf("already", "a", "I", "b"))

        assertTrue("expected nothing to be filed, got ${batch.words}", batch.isEmpty)
        assertEquals(WordStatus.MASTERED, repository.statusOf("already"))
        assertNull(repository.statusOf("a"))
        assertNull(repository.statusOf("i"))
    }

    @Test
    fun `a page whose words are all known produces an empty batch`() = runTest {
        repository.markMastered("one")
        // UI 靠 batch.isEmpty 决定不弹「本页 N 个词已标为掌握」的提示条。
        assertTrue(repository.applyPageTurn(listOf("one")).isEmpty)
        assertEquals(MasteryBatch.EMPTY, repository.applyPageTurn(emptyList()))
    }

    // ---------- 撤销（Web 版没有的能力） ----------

    @Test
    fun `undo removes exactly the words that page turn filed`() = runTest {
        now = 1_000
        val batch = repository.applyPageTurn(listOf("alice", "rabbit"))
        assertEquals(2, batch.count)
        assertEquals(WordStatus.MASTERED, repository.statusOf("alice"))

        val deleted = repository.undoMastery(batch)

        assertEquals(2, deleted)
        assertNull(repository.statusOf("alice"))
        assertNull(repository.statusOf("rabbit"))
    }

    /**
     * 这条是撤销规则里唯一微妙的地方：读者在撤销窗口内手动改过的词属于用户判断，
     * 不该被撤销抹掉。实现靠的是 pageTurnStamp 与 updatedAt 两个条件同时匹配。
     */
    @Test
    fun `undo keeps words the reader edited during the window`() = runTest {
        now = 1_000
        val batch = repository.applyPageTurn(listOf("alice", "rabbit", "curiosity"))

        now = 2_000
        repository.setStatus("rabbit", WordStatus.L2) // 手动改成模糊

        val deleted = repository.undoMastery(batch)

        assertEquals("only the two untouched words", 2, deleted)
        assertEquals(WordStatus.L2, repository.statusOf("rabbit"))
        assertNull(repository.statusOf("alice"))
        assertNull(repository.statusOf("curiosity"))
    }

    @Test
    fun `undo does not touch words filed by an earlier page turn`() = runTest {
        now = 1_000
        val first = repository.applyPageTurn(listOf("alice"))

        now = 2_000
        val second = repository.applyPageTurn(listOf("rabbit"))

        repository.undoMastery(second)

        assertNull(repository.statusOf("rabbit"))
        assertEquals("the first batch is a different stamp", WordStatus.MASTERED, repository.statusOf("alice"))
        assertTrue(first.stamp != second.stamp)
    }

    @Test
    fun `undo of an empty batch is a no-op`() = runTest {
        assertEquals(0, repository.undoMastery(MasteryBatch.EMPTY))
    }

    // ---------- 整库视图 ----------

    /**
     * 返回值里**没有**的词就是「未收录」—— 领域层用「缺失」表示未收录，不需要哨兵。
     */
    @Test
    fun `observeAll reports every filed word and omits the rest`() = runTest {
        repository.setStatus("alice", WordStatus.L2)
        repository.markMastered("rabbit")

        val statuses = repository.observeAll().first()

        assertEquals(mapOf("alice" to WordStatus.L2, "rabbit" to WordStatus.MASTERED), statuses)
    }

    /**
     * 一次 SQL 里能绑定的变量数有上限（老设备 999），仓储按 500 分块。
     *
     * 这条原先是挂在「按页查一批词」那条查询上的，阅读器改成整库常驻之后那条路没了；
     * 分块这件事仍然活着 —— 现在落在撤销上（`undoMastery` 分批 DELETE）—— 所以断言
     * 搬到这里，别让这个边界失去看守。
     */
    @Test
    fun `undo survives a batch larger than one SQL bind`() = runTest {
        val words = (1..1200).map { "word$it" }
        val batch = repository.applyPageTurn(words)
        assertEquals(1200, batch.count)

        assertEquals(1200, repository.undoMastery(batch))

        assertTrue("撤销之后库里不该剩下任何一条", repository.all().isEmpty())
    }

    // ---------- 移出一个词 ----------

    @Test
    fun `removing a word restores it to uncollected`() = runTest {
        repository.setStatus("temporary", WordStatus.L4)
        assertEquals(WordStatus.L4, repository.statusOf("temporary"))

        repository.removeWord("temporary")

        assertNull(repository.statusOf("temporary"))
    }

    /** 留下孤儿笔记是查不到的脏数据，所以必须一起删。 */
    @Test
    fun `removing a word takes its notes and sentences with it`() = runTest {
        val annotations = AnnotationRepositoryImpl(
            database = database,
            noteDao = database.noteDao(),
            savedSentenceDao = database.savedSentenceDao(),
        )
        repository.markMastered("run")
        annotations.addNote("run", "跑")
        annotations.addSentence("run", SavedSentence("He runs."))
        annotations.addNote("other", "别的")

        repository.removeWord("run")

        assertNull(repository.statusOf("run"))
        assertEquals(emptyList<String>(), annotations.observeNotes("run").first())
        assertEquals(emptyList<SavedSentence>(), annotations.observeSentences("run").first())
        // 别的词不受影响。
        assertEquals(listOf("别的"), annotations.observeNotes("other").first())
    }

    // ---------- 导入与清空 ----------

    @Test
    fun `importAll replaces rather than merges`() = runTest {
        repository.setStatus("old", WordStatus.L1)

        repository.importAll(
            words = mapOf("new" to WordStatus.L3),
            annotations = AnnotationsData(notes = mapOf("new" to listOf("新"))),
        )

        assertNull("the previous library is gone", repository.statusOf("old"))
        assertEquals(WordStatus.L3, repository.statusOf("new"))
        assertEquals(
            listOf("新"),
            AnnotationRepositoryImpl(database, database.noteDao(), database.savedSentenceDao())
                .observeNotes("new").first(),
        )
    }

    @Test
    fun `clear empties vocabulary and annotations together`() = runTest {
        val annotations = AnnotationRepositoryImpl(database, database.noteDao(), database.savedSentenceDao())
        repository.markMastered("run")
        annotations.addNote("run", "跑")

        repository.clear()

        assertEquals(emptyMap<String, WordStatus>(), repository.all())
        assertEquals(emptyMap<String, List<String>>(), annotations.all().notes)
    }

    /**
     * 导入进来的词**不属于任何翻页批次**。否则用户导入完按一下「撤销」，
     * 会以为在撤销翻页，实际上会删掉刚导入的一大片词。
     */
    @Test
    fun `imported words cannot be undone by a stale mastery batch`() = runTest {
        now = 1_000
        repository.importAll(mapOf("imported" to WordStatus.MASTERED), AnnotationsData())

        // 伪造一个时间戳相同的批次（真实场景里不可能，但能验 pageTurnStamp 是 null）。
        val deleted = repository.undoMastery(MasteryBatch(1_000, listOf("imported")))

        assertEquals(0, deleted)
        assertEquals(WordStatus.MASTERED, repository.statusOf("imported"))
    }

    @Test
    fun `all returns the whole library`() = runTest {
        repository.setStatus("a", WordStatus.L1)
        repository.markMastered("b")

        assertEquals(
            mapOf("a" to WordStatus.L1, "b" to WordStatus.MASTERED),
            repository.all(),
        )
    }

    /** 与 Web 版一致：规范化的入口只有一个，两边不该有第二套。 */
    @Test
    fun `annotation normalization keeps the same key shape as the vocabulary`() = runTest {
        val annotations = AnnotationRepositoryImpl(database, database.noteDao(), database.savedSentenceDao())
        annotations.addNote("  Ubiquitous  ", "到处都是")

        assertEquals(mapOf("ubiquitous" to listOf("到处都是")), annotations.all().notes)
        assertEquals(AnnotationNormalizer.normalizeKey("Ubiquitous"), "ubiquitous")
    }
}
