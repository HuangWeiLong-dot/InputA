package com.inputa.next.core.data

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 当前打开的课程/合集；课程详情页和“开始课程”共用。 */
@Singleton
class CurrentCourse @Inject constructor() {
    private val _changes = MutableStateFlow<LessonSummary?>(null)
    val changes: StateFlow<LessonSummary?> = _changes.asStateFlow()

    val course: LessonSummary? get() = _changes.value

    fun select(value: LessonSummary) {
        _changes.value = value
    }

    fun clear() {
        _changes.value = null
    }
}
