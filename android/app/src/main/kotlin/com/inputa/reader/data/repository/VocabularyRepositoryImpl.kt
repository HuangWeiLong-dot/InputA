package com.inputa.reader.data.repository

import androidx.room.withTransaction
import com.inputa.reader.data.local.InputaDatabase
import com.inputa.reader.data.local.dao.NoteDao
import com.inputa.reader.data.local.dao.SavedSentenceDao
import com.inputa.reader.data.local.dao.VocabularyDao
import com.inputa.reader.data.local.entity.NoteEntity
import com.inputa.reader.data.local.entity.SavedSentenceEntity
import com.inputa.reader.data.local.entity.VocabularyEntity
import com.inputa.reader.domain.annotation.AnnotationNormalizer
import com.inputa.reader.domain.model.AnnotationsData
import com.inputa.reader.domain.model.SavedSentence
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.repository.AnnotationRepository
import com.inputa.reader.domain.repository.MasteryBatch
import com.inputa.reader.domain.repository.VocabularyRepository
import com.inputa.reader.domain.util.Clock
import com.inputa.reader.domain.vocab.VocabularyCounts
import com.inputa.reader.domain.vocab.VocabularyRules
import com.inputa.reader.domain.vocab.WordLevelCodec
import com.inputa.reader.domain.vocab.WordLevels
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 词库的实现。**词汇规则的写入口都在这里** —— 三个写方法各自对应 Web 版 store 上
 * 一个同名方法，改动前先对着那边 diff。
 */
@Singleton
class VocabularyRepositoryImpl @Inject constructor(
    private val database: InputaDatabase,
    private val vocabularyDao: VocabularyDao,
    private val noteDao: NoteDao,
    private val savedSentenceDao: SavedSentenceDao,
    private val clock: Clock,
) : VocabularyRepository {

    override suspend fun statusOf(word: String): WordStatus? {
        val clean = WordLevels.normalizeKey(word)
        if (clean.isEmpty()) return null
        return vocabularyDao.find(clean)?.let { WordLevelCodec.fromCode(it.status) }
    }

    override suspend fun setStatus(word: String, status: WordStatus) {
        val clean = WordLevels.normalizeKey(word)
        if (clean.isEmpty()) return
        // 手动指定是明确的用户意图，所以覆盖。pageTurnStamp 置空：这一行从此不再属于
        // 任何一次翻页批次，撤销翻页不该动它。
        vocabularyDao.upsert(
            VocabularyEntity(
                word = clean,
                status = status.code,
                updatedAt = clock.nowMillis(),
                pageTurnStamp = null,
            ),
        )
    }

    /**
     * 点击收录：**只在此前没有任何状态时才写**。
     *
     * 已有状态的词（1-5 级或已掌握）照样会被查词，但状态原样保留 —— 用户的判断优先。
     * 想改回 5 级要用「标为生词」按钮，那才是明确的意图。
     *
     * 实现上不需要「先读再判断」：`INSERT OR IGNORE` 的冲突策略**就是**这条规则，
     * 而且是一次原子写，没有读改写的竞态。
     *
     * 这条也是「未收录的词只着色不写入」那个设计成立的前提：如果点击会覆盖，
     * 预先把整页词都写成 5 级就没有后果 —— 而那恰恰会让点击彻底失去意义。
     */
    override suspend fun markAsNewWord(word: String) {
        val clean = WordLevels.normalizeKey(word)
        if (clean.isEmpty()) return
        vocabularyDao.insertIfAbsent(
            VocabularyEntity(
                word = clean,
                status = VocabularyRules.CLICK_STATUS.code,
                updatedAt = clock.nowMillis(),
                pageTurnStamp = null,
            ),
        )
    }

    override suspend fun markMastered(word: String) {
        val clean = WordLevels.normalizeKey(word)
        if (clean.isEmpty()) return
        vocabularyDao.upsert(
            VocabularyEntity(
                word = clean,
                status = WordStatus.MASTERED.code,
                updatedAt = clock.nowMillis(),
                pageTurnStamp = null,
            ),
        )
    }

    override suspend fun removeWord(word: String) {
        val clean = WordLevels.normalizeKey(word)
        if (clean.isEmpty()) return
        // 一个事务删三处：留下任何一处都是查不到的孤儿数据（笔记还在，词没了）。
        database.withTransaction {
            vocabularyDao.delete(clean)
            noteDao.deleteAllForWord(clean)
            savedSentenceDao.deleteAllForWord(clean)
        }
    }

    override suspend fun applyPageTurn(pageWords: List<String>): MasteryBatch {
        val candidates = VocabularyRules.pageTurnCandidates(pageWords)
        if (candidates.isEmpty()) return MasteryBatch.EMPTY

        val stamp = clock.nowMillis()
        val filed = database.withTransaction {
            candidates.mapNotNull { word ->
                // insertIfAbsent 返回 -1 表示这个词已经有状态，被 IGNORE 掉了 ——
                // 于是这里得到的正好是「本次真正写进去的词」，撤销要用。
                val rowId = vocabularyDao.insertIfAbsent(
                    VocabularyEntity(
                        word = word,
                        status = VocabularyRules.PAGE_TURN_STATUS.code,
                        updatedAt = stamp,
                        pageTurnStamp = stamp,
                    ),
                )
                if (rowId != -1L) word else null
            }
        }

        // 一条都没写就不算一个批次：UI 据此决定不弹提示条，与 Web 版
        // 「count > 0 才 persist」一致。
        return if (filed.isEmpty()) MasteryBatch.EMPTY else MasteryBatch(stamp, filed)
    }

    override suspend fun undoMastery(batch: MasteryBatch): Int {
        if (batch.isEmpty) return 0
        return database.withTransaction {
            batch.words.chunked(MAX_SQL_VARIABLES).sumOf { chunk ->
                vocabularyDao.deletePageTurned(chunk, batch.stamp)
            }
        }
    }

    override suspend fun all(): Map<String, WordStatus> =
        vocabularyDao.all().mapNotNull { row ->
            WordLevelCodec.fromCode(row.status)?.let { row.word to it }
        }.toMap()

    override fun observeAll(): Flow<Map<String, WordStatus>> =
        vocabularyDao.observeAll().map { rows -> rows.toStatusMap() }

    /**
     * 顶栏徽章的数字。
     *
     * 只把 `GROUP BY` 的结果搬进内存（最多六行），而不是整张表 —— 两万词的库不该
     * 为了显示两个数字而拉两万行过来。
     */
    override fun observeCounts(): Flow<VocabularyCounts> =
        vocabularyDao.observeStatusCounts().map { rows ->
            var active = 0
            var mastered = 0
            for (row in rows) {
                val status = WordLevelCodec.fromCode(row.status) ?: continue
                if (status.isLevel) active += row.count else mastered += row.count
            }
            VocabularyCounts(active = active, mastered = mastered)
        }

    private fun List<VocabularyEntity>.toStatusMap(): Map<String, WordStatus> =
        mapNotNull { row -> WordLevelCodec.fromCode(row.status)?.let { row.word to it } }.toMap()

    override suspend fun clear() {
        database.withTransaction {
            vocabularyDao.clear()
            noteDao.clear()
            savedSentenceDao.clear()
        }
    }

    override suspend fun importAll(words: Map<String, WordStatus>, annotations: AnnotationsData) {
        val now = clock.nowMillis()
        database.withTransaction {
            vocabularyDao.clear()
            noteDao.clear()
            savedSentenceDao.clear()

            // 分块写入：一次绑定的变量数有上限（老设备上 SQLITE_MAX_VARIABLE_NUMBER 是 999），
            // 而一份备份里可能有上万条。
            words.entries.chunked(IMPORT_CHUNK).forEach { chunk ->
                vocabularyDao.insertAll(
                    chunk.map { (word, status) ->
                        // 导入进来的词不属于任何翻页批次，pageTurnStamp 用 null：
                        // 否则用户在导入后按「撤销」会误删这一整批。
                        VocabularyEntity(word, status.code, now, null)
                    },
                )
            }

            annotations.notes.entries.chunked(IMPORT_CHUNK).forEach { chunk ->
                noteDao.insertAll(
                    chunk.flatMap { (word, notes) -> notes.map { NoteEntity(word = word, body = it) } },
                )
            }

            annotations.sentences.entries.chunked(IMPORT_CHUNK).forEach { chunk ->
                savedSentenceDao.insertAll(
                    chunk.flatMap { (word, sentences) ->
                        sentences.map { it.toEntity(word) }
                    },
                )
            }
        }
    }
}

/**
 * 笔记与例句的读写。
 *
 * 只负责**单个词**范围的操作；整库的清空与替换在 [VocabularyRepositoryImpl] 上，
 * 因为那必须与词库在同一个事务里。
 */
@Singleton
class AnnotationRepositoryImpl @Inject constructor(
    private val database: InputaDatabase,
    private val noteDao: NoteDao,
    private val savedSentenceDao: SavedSentenceDao,
) : AnnotationRepository {

    override fun observeNotes(word: String): Flow<List<String>> =
        noteDao.observeBodies(normalize(word))

    override fun observeSentences(word: String): Flow<List<SavedSentence>> =
        savedSentenceDao.observe(normalize(word)).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addNote(word: String, note: String) {
        val clean = normalize(word)
        val body = note.trim()
        if (clean.isEmpty() || body.isEmpty()) return
        // 精确匹配去重，且**不 trim 之外做任何规范化** —— 用户可能刻意保留两条
        // 只差大小写的笔记。唯一索引是 BINARY 排序，行为与 Web 版的 includes() 一致。
        noteDao.insertIfAbsent(NoteEntity(word = clean, body = body))
    }

    override suspend fun removeNote(word: String, note: String) {
        noteDao.delete(normalize(word), note)
    }

    override suspend fun addSentence(word: String, sentence: SavedSentence) {
        val clean = normalize(word)
        if (clean.isEmpty() || sentence.sentence.isBlank()) return

        database.withTransaction {
            savedSentenceDao.insertIfAbsent(sentence.toEntity(clean))
            // 已存在时不覆盖，只在**缺失**时补上 —— 见 SavedSentenceDao 的注释
            // （用 UPDATE 而不是 SQLite 的 UPSERT，因为 minSdk 26 的 SQLite 太老）。
            sentence.translation?.let { translation ->
                savedSentenceDao.fillTranslationIfMissing(clean, sentence.sentence, translation)
            }
        }
    }

    override suspend fun removeSentence(word: String, sentence: String) {
        savedSentenceDao.delete(normalize(word), sentence)
    }

    override suspend fun removeWordAnnotations(word: String) {
        val clean = normalize(word)
        if (clean.isEmpty()) return
        database.withTransaction {
            noteDao.deleteAllForWord(clean)
            savedSentenceDao.deleteAllForWord(clean)
        }
    }

    override suspend fun all(): AnnotationsData = AnnotationsData(
        notes = noteDao.all().groupBy({ it.word }, { it.body }),
        sentences = savedSentenceDao.all().groupBy({ it.word }, { it.toDomain() }),
    )

    private fun normalize(word: String): String = WordLevels.normalizeKey(word)
}

private fun SavedSentence.toEntity(word: String): SavedSentenceEntity = SavedSentenceEntity(
    word = word,
    sentence = sentence,
    translation = translation,
    bookId = bookId,
    bookTitle = bookTitle,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    createdAt = createdAt,
)

private fun SavedSentenceEntity.toDomain(): SavedSentence = SavedSentence(
    sentence = sentence,
    translation = translation,
    bookId = bookId,
    bookTitle = bookTitle,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    createdAt = createdAt,
)

/**
 * 单条 SQL 里能绑定的最大变量数。老设备上 `SQLITE_MAX_VARIABLE_NUMBER` 是 999，
 * 新的是 32766 —— 取小值，因为分块本身没有成本。
 */
private const val MAX_SQL_VARIABLES = 500

/** 单条 INSERT 里最多塞多少行。 */
private const val IMPORT_CHUNK = 500
