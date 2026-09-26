package com.inputa.reader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.inputa.reader.data.local.dao.BookDao
import com.inputa.reader.data.local.dao.NoteDao
import com.inputa.reader.data.local.dao.ReadingProgressDao
import com.inputa.reader.data.local.dao.SavedSentenceDao
import com.inputa.reader.data.local.dao.VocabularyDao
import com.inputa.reader.data.local.entity.BookEntity
import com.inputa.reader.data.local.entity.ChapterEntity
import com.inputa.reader.data.local.entity.NoteEntity
import com.inputa.reader.data.local.entity.ReadingProgressEntity
import com.inputa.reader.data.local.entity.SavedSentenceEntity
import com.inputa.reader.data.local.entity.VocabularyEntity

/**
 * 本地库。
 *
 * `exportSchema = true` 且 schema JSON 提交进仓库（`app/schemas/`）：写迁移的时候
 * 没有它就只能靠猜，而迁移写错的后果是用户几个月的生词本没了。
 * 目前是 v1，还没有迁移可写，但脚手架先立起来。
 *
 * 表之间只有一处外键（`chapters → books`），其余刻意不建 —— 理由写在
 * [VocabularyEntity] 与 [ReadingProgressEntity] 的注释里。
 */
@Database(
    entities = [
        VocabularyEntity::class,
        NoteEntity::class,
        SavedSentenceEntity::class,
        BookEntity::class,
        ChapterEntity::class,
        ReadingProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class InputaDatabase : RoomDatabase() {
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun noteDao(): NoteDao
    abstract fun savedSentenceDao(): SavedSentenceDao
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        const val NAME = "inputa.db"
    }
}
