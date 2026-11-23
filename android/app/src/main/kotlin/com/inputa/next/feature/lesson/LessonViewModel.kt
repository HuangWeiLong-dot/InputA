package com.inputa.next.feature.lesson

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inputa.next.core.data.CurrentLesson
import com.inputa.next.core.data.LessonDetail
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.LessonTextCache
import com.inputa.next.core.data.LingqApi
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.computeLessonWordStats
import com.inputa.next.core.data.local.BookmarkStore
import com.inputa.next.core.data.local.LocalWordStore
import com.inputa.next.core.data.local.PlaylistStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LessonViewModel @Inject constructor(
    private val api: LingqApi,
    private val session: SessionStore,
    private val current: CurrentLesson,
    private val playlist: PlaylistStore,
    private val localWords: LocalWordStore,
    private val textCache: LessonTextCache,
    private val bookmarks: BookmarkStore,
) : ViewModel() {

    data class State(
        val loading: Boolean = true,
        val error: String? = null,
        val selected: LessonSummary? = null,
        val detail: LessonDetail? = null,
        val bookmarked: Boolean = false,
        val inPlaylist: Boolean = false,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val language: String get() = session.language()

    init {
        viewModelScope.launch {
            // collectLatest：用户连续点不同课程时，取消上一门课的加载，避免旧结果覆盖新课程。
            current.changes.collectLatest { lesson ->
                if (lesson == null) {
                    _state.update {
                        it.copy(
                            selected = null,
                            detail = null,
                            loading = false,
                            error = "No lesson selected",
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            selected = lesson,
                            detail = null,
                            loading = true,
                            error = null,
                            bookmarked = bookmarks.isBookmarked(language, lesson.id),
                            inPlaylist = playlist.contains(lesson.id),
                            notice = null,
                        )
                    }
                    loadFor(lesson)
                }
            }
        }
    }

    fun load() {
        val lesson = _state.value.selected ?: current.lesson ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            loadFor(lesson)
        }
    }

    private suspend fun loadFor(lesson: LessonSummary) {
        val result = runCatching { api.lessonDetail(language, lesson.id, lesson) }
        var detail = result.getOrNull()
        // 资料库 item 是课程/合集时，lessonDetail 会解析出第一门真实单课；
        // 把当前课切过去，预览和阅读器就不会再拿课程 id 去请求 /lessons/。
        if (detail != null && detail.lesson.id != lesson.id && !detail.lesson.isCourse) {
            current.select(detail.lesson)
            return
        }
        // 详情页有正文：Total / Unique / New 一律按本机词库算，服务端数字只作缺省。
        // 词状态只写本地、不回写 LingQ，服务端统计必然与阅读器里的颜色对不上。
        detail?.let { loaded ->
            val text = resolveText(loaded.lesson.id, loaded.text)
            if (!text.isNullOrBlank()) {
                textCache.put(language, loaded.lesson.id, text)
                val stats = computeLessonWordStats(api, text, language, localWords.load(language))
                detail = loaded.copy(
                    text = text,
                    totalWords = stats.total,
                    uniqueWords = stats.unique,
                    newWords = stats.newWords,
                    lesson = loaded.lesson.copy(newWordsCount = stats.newWords),
                )
            }
        }
        // 详情接口失败但本机有缓存正文时，仍然用本地词库把详情页填起来。
        if (detail == null) {
            val cachedText = textCache.get(language, lesson.id)?.takeIf { it.hasLetters() }
            if (!cachedText.isNullOrBlank()) {
                val stats = computeLessonWordStats(api, cachedText, language, localWords.load(language))
                detail = LessonDetail(
                    lesson = lesson.copy(newWordsCount = stats.newWords),
                    description = null,
                    text = cachedText,
                    totalWords = stats.total,
                    uniqueWords = stats.unique,
                    newWords = stats.newWords,
                )
            }
        }
        // 打开/收藏/完成都属于本地记录，不再读写 LingQ。
        _state.update { state ->
            if (state.selected?.id != lesson.id) {
                state
            } else {
                state.copy(
                    loading = false,
                    detail = detail,
                    bookmarked = bookmarks.isBookmarked(language, lesson.id),
                    inPlaylist = playlist.contains(lesson.id),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    /**
     * 详情接口的 /text/ 有时返回空或统计字段；依次回落到本机缓存和阅读器同款 sentences 接口，
     * 拿到真正的正文后就能完全用本机词库计算 Total / Unique / New。
     */
    private suspend fun resolveText(lessonId: Int, text: String?): String? {
        if (!text.isNullOrBlank() && text.hasLetters()) return text
        textCache.get(language, lessonId)?.takeIf { it.hasLetters() }?.let { return it }
        return runCatching {
            api.sentences(language, lessonId).joinToString("\n") { it.text }
        }.getOrNull()?.takeIf { it.hasLetters() }
    }

    /** 正文必须含字母；`{"text": 1}` 这类统计值不能被当成课文。 */
    private fun String.hasLetters(): Boolean = any { it.isLetter() }

    fun addToPlaylist() {
        val lesson = currentLessonSummary() ?: return
        val added = playlist.add(lesson)
        _state.update {
            it.copy(
                inPlaylist = true,
                notice = if (added) "Added to playlist" else "Already in playlist",
            )
        }
    }

    fun toggleBookmark() {
        val lesson = currentLessonSummary() ?: return
        val target = bookmarks.set(language, lesson, !_state.value.bookmarked)
        _state.update {
            it.copy(bookmarked = target, notice = if (target) "Saved to bookmarks" else "Bookmark removed")
        }
    }

    fun markComplete() {
        _state.update { state ->
            val detail = state.detail
            state.copy(
                notice = "Lesson marked complete",
                detail = if (detail != null) {
                    detail.copy(lesson = detail.lesson.copy(isCompleted = true))
                } else {
                    detail
                },
            )
        }
    }

    fun consumeNotice() = _state.update { it.copy(notice = null) }

    private fun currentLessonSummary(): LessonSummary? =
        _state.value.detail?.lesson ?: _state.value.selected ?: current.lesson
}
