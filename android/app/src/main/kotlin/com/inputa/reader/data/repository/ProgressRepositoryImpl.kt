package com.inputa.reader.data.repository

import com.inputa.reader.data.local.dao.ReadingProgressDao
import com.inputa.reader.data.local.entity.ReadingProgressEntity
import com.inputa.reader.domain.model.ReadingProgress
import com.inputa.reader.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 阅读进度。每本书一行。
 *
 * Web 版把整张 `bookId → 进度` 表存成一个 JSON 对象，每次保存都要「读整张、改一条、
 * 写整张」—— 一次 upsert 就够的事，而且读改写之间被打断就会丢更新。
 */
@Singleton
class ProgressRepositoryImpl @Inject constructor(
    private val dao: ReadingProgressDao,
) : ProgressRepository {

    override suspend fun save(progress: ReadingProgress) {
        dao.upsert(
            ReadingProgressEntity(
                bookId = progress.bookId,
                bookTitle = progress.bookTitle,
                chapterIndex = progress.chapterIndex,
                pageIndex = progress.pageIndex,
                updatedAt = progress.updatedAt,
            ),
        )
    }

    override suspend fun find(bookId: String): ReadingProgress? = dao.find(bookId)?.toDomain()

    override fun observeMostRecent(): Flow<ReadingProgress?> =
        dao.observeMostRecent().map { it?.toDomain() }

    override suspend fun delete(bookId: String) = dao.delete(bookId)
}

private fun ReadingProgressEntity.toDomain(): ReadingProgress = ReadingProgress(
    bookId = bookId,
    bookTitle = bookTitle,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    updatedAt = updatedAt,
)
