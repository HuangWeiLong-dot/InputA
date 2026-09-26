package com.inputa.reader.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.inputa.reader.data.local.entity.NoteEntity
import com.inputa.reader.data.local.entity.SavedSentenceEntity
import com.inputa.reader.data.local.entity.VocabularyEntity
import kotlinx.coroutines.flow.Flow

/**
 * 词库、笔记、例句的 SQL。
 *
 * **这里刻意保持「笨」**：只有单条 SQL，没有任何跨语句的规则。所有规则
 * （点击只写一次、翻页不覆盖、移出一个词要连同笔记一起删）都在仓储层，
 * 用 `withTransaction` 包起来。这样做的好处是规则读起来是一段 Kotlin 而不是
 * 散在若干条 SQL 的 WHERE 里，而且能对着 Web 版的 store 方法逐行 diff。
 */
@Dao
interface VocabularyDao {

    @Query("SELECT * FROM vocabulary WHERE word = :word LIMIT 1")
    suspend fun find(word: String): VocabularyEntity?

    @Query("SELECT * FROM vocabulary")
    suspend fun all(): List<VocabularyEntity>

    /**
     * 整张表的实时视图。
     *
     * 生词本要看见整张表 —— 那就是那个界面的全部意义；阅读器也一样，它的底色要在翻页
     * 落地那一帧就是对的，按页裁剪会闪色（见 `ReaderViewModel.statusesFlow`）。
     * 代价是这是**整表读**，且 Room 的失效通知是表级的，每次写入都会重跑一遍。
     */
    @Query("SELECT * FROM vocabulary ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<VocabularyEntity>>

    /**
     * 顶栏徽章的两个数字。
     *
     * 用一条 `GROUP BY` 算，而不是把整张表拉进内存里再遍历 —— 两万词的库会白拉两万行。
     */
    @Query("SELECT status, COUNT(*) AS count FROM vocabulary GROUP BY status")
    fun observeStatusCounts(): Flow<List<StatusCount>>

    /**
     * 冲突即忽略 —— 返回的 rowId 在忽略时是 -1，这就是「这个词已经有状态了」的信号。
     * 翻页收录靠它判断本次究竟写进了哪些词（撤销需要这份名单）。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: VocabularyEntity): Long

    /** 覆盖写。用于手动指定熟练度与「标为已掌握」—— 那是明确的用户意图。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: VocabularyEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<VocabularyEntity>)

    @Query("DELETE FROM vocabulary WHERE word = :word")
    suspend fun delete(word: String)

    @Query("DELETE FROM vocabulary")
    suspend fun clear()

    /**
     * 撤销一次翻页收录。
     *
     * 两个条件都要求匹配：[pageTurnStamp] 保证只删这一批，[updatedAt] 保证
     * **之后被用户手动改过的行不会被删** —— 那属于用户判断，不该被撤销覆盖。
     */
    @Query(
        "DELETE FROM vocabulary WHERE word IN (:words) " +
            "AND pageTurnStamp = :stamp AND updatedAt = :stamp",
    )
    suspend fun deletePageTurned(words: List<String>, stamp: Long): Int
}

/** `GROUP BY status` 的投影。 */
data class StatusCount(
    @ColumnInfo(name = "status") val status: Int,
    @ColumnInfo(name = "count") val count: Int,
)

@Dao
interface NoteDao {

    @Query("SELECT body FROM notes WHERE word = :word ORDER BY id ASC")
    fun observeBodies(word: String): Flow<List<String>>

    /** 精确匹配去重 —— 唯一索引是 BINARY 排序，见 [NoteEntity]。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: NoteEntity): Long

    @Query("DELETE FROM notes WHERE word = :word AND body = :body")
    suspend fun delete(word: String, body: String)

    @Query("DELETE FROM notes WHERE word = :word")
    suspend fun deleteAllForWord(word: String): Int

    @Query("SELECT * FROM notes ORDER BY id ASC")
    suspend fun all(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<NoteEntity>)

    @Query("DELETE FROM notes")
    suspend fun clear()
}

@Dao
interface SavedSentenceDao {

    @Query("SELECT * FROM saved_sentences WHERE word = :word ORDER BY id ASC")
    fun observe(word: String): Flow<List<SavedSentenceEntity>>

    @Query("SELECT * FROM saved_sentences ORDER BY id ASC")
    suspend fun all(): List<SavedSentenceEntity>

    /** 同句只留一条。已存在时返回 -1，由仓储决定要不要补翻译。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(row: SavedSentenceEntity): Long

    /**
     * 补上一个缺失的翻译，**绝不覆盖已有的**。
     *
     * 这里之所以是一条单独的 UPDATE 而不是 SQLite 的 `ON CONFLICT ... DO UPDATE`：
     * UPSERT 语法需要 SQLite 3.24+，而 minSdk 26 对应的 Android 8.0 只有 3.18 ——
     * 用 UPSERT 会在老设备上抛语法错误，且只有真机才暴露。`WHERE translation IS NULL`
     * 与 `AND :translation IS NOT NULL` 合起来正好是 Web 版那段合并逻辑。
     */
    @Query(
        "UPDATE saved_sentences SET translation = :translation " +
            "WHERE word = :word AND sentence = :sentence AND translation IS NULL",
    )
    suspend fun fillTranslationIfMissing(word: String, sentence: String, translation: String): Int

    @Query("DELETE FROM saved_sentences WHERE word = :word AND sentence = :sentence")
    suspend fun delete(word: String, sentence: String)

    @Query("DELETE FROM saved_sentences WHERE word = :word")
    suspend fun deleteAllForWord(word: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<SavedSentenceEntity>)

    @Query("DELETE FROM saved_sentences")
    suspend fun clear()
}
