package com.inputa.reader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.inputa.reader.data.local.InputaDatabase
import com.inputa.reader.data.local.dao.BookDao
import com.inputa.reader.data.local.dao.NoteDao
import com.inputa.reader.data.local.dao.ReadingProgressDao
import com.inputa.reader.data.local.dao.SavedSentenceDao
import com.inputa.reader.data.local.dao.VocabularyDao
import com.inputa.reader.data.repository.AnnotationRepositoryImpl
import com.inputa.reader.data.repository.BookRepositoryImpl
import com.inputa.reader.data.repository.ProgressRepositoryImpl
import com.inputa.reader.data.repository.VocabularyRepositoryImpl
import com.inputa.reader.data.settings.SettingsRepositoryImpl
import com.inputa.reader.domain.repository.AnnotationRepository
import com.inputa.reader.domain.repository.BookRepository
import com.inputa.reader.domain.repository.ProgressRepository
import com.inputa.reader.domain.repository.SettingsRepository
import com.inputa.reader.domain.repository.VocabularyRepository
import com.inputa.reader.domain.util.Clock
import com.inputa.reader.domain.util.SystemClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DataStore 的文件名。改了它等于清空所有用户的设置，别改。 */
private const val SETTINGS_DATASTORE_NAME = "inputa_settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): InputaDatabase =
        Room.databaseBuilder(context, InputaDatabase::class.java, InputaDatabase.NAME).build()

    @Provides
    fun provideVocabularyDao(database: InputaDatabase): VocabularyDao = database.vocabularyDao()

    @Provides
    fun provideNoteDao(database: InputaDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideSavedSentenceDao(database: InputaDatabase): SavedSentenceDao =
        database.savedSentenceDao()

    @Provides
    fun provideBookDao(database: InputaDatabase): BookDao = database.bookDao()

    @Provides
    fun provideReadingProgressDao(database: InputaDatabase): ReadingProgressDao =
        database.readingProgressDao()
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    /**
     * 做成可注入的接口而不是到处调 `System.currentTimeMillis()`：翻页收录的撤销规则
     * 依赖时间戳，测试里必须能固定时间才能验「读者改过就撤不掉」那条分支。
     */
    @Provides
    @Singleton
    fun provideClock(): Clock = SystemClock
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindVocabularyRepository(impl: VocabularyRepositoryImpl): VocabularyRepository

    @Binds
    abstract fun bindAnnotationRepository(impl: AnnotationRepositoryImpl): AnnotationRepository

    @Binds
    abstract fun bindProgressRepository(impl: ProgressRepositoryImpl): ProgressRepository

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
