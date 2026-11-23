package com.inputa.next.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 当前打开的课程。手写导航（单 Activity）下，详情页/阅读器共享同一个选择。
 * 用 StateFlow 暴露变化，ViewModel 才能在用户换课时重新加载，而不是一直复用旧课程。
 */
@Singleton
class CurrentLesson @Inject constructor() {
    private val _changes = MutableStateFlow<LessonSummary?>(null)
    val changes: StateFlow<LessonSummary?> = _changes.asStateFlow()

    val lesson: LessonSummary? get() = _changes.value

    fun select(value: LessonSummary) {
        _changes.value = value
    }

    fun clear() {
        _changes.value = null
    }
}
