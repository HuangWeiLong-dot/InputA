package com.inputa.next.core.data

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 已经拿到过的课程正文本机缓存。
 *
 * 列表接口只给“这课有几个生词”，而本应用的词状态只在本地，服务端数字对不上；
 * 有正文就能按本机词库精确算。这里只缓存**已经加载过**的正文（阅读器/课程详情），
 * 不为了列表数字额外发请求 —— 没有正文时调用方继续用服务端值。
 */
@Singleton
class LessonTextCache @Inject constructor() {

    private val texts = ConcurrentHashMap<String, String>()

    fun put(language: String, lessonId: Int, text: String?) {
        if (text.isNullOrBlank()) return
        texts[key(language, lessonId)] = text
    }

    fun get(language: String, lessonId: Int): String? = texts[key(language, lessonId)]

    private fun key(language: String, lessonId: Int): String =
        "${normalizeLanguage(language)}:$lessonId"
}
