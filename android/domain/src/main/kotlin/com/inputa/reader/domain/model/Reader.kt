package com.inputa.reader.domain.model

/**
 * 共享领域模型。字段名与 Web 版的 src/types/reader.ts 一一对应，
 * 便于逐字段 diff 对照移植。
 */

/** 一个段落一段正文；`title` 是章节标题。 */
data class BookChapter(
    val title: String,
    val content: String,
)

/** 书的来源。`BUILTIN` 是随包发的样书，不入库。 */
enum class BookSource(val key: String) {
    GUTENBERG("gutenberg"),
    CUSTOM("custom"),
    BUILTIN("builtin");

    companion object {
        /** 未知值返回 null 由调用方决定怎么办 —— 与 WordLevelCodec 的「丢弃未知」姿态一致。 */
        fun fromKey(raw: String?): BookSource? = entries.firstOrNull { it.key == raw }
    }
}

data class Book(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String? = null,
    val chapters: List<BookChapter> = emptyList(),
    val source: BookSource? = null,
    /**
     * ISO 639-1（可带地区），正文的主要语言。
     *
     * 来源优先级：书源元数据（Gutendex 会在响应里给 languages）优先于本地检测。
     * 用来选朗读音色、显示语言标签。
     */
    val language: String? = null,
)

/**
 * 阅读进度。每本书一行，按 [updatedAt] 取最近读的那本。
 *
 * Web 版把整张 `bookId → 进度` 表存成一个 JSON 对象，每次保存都要「读整张、改一条、
 * 写整张」；Room 这边就是一次 upsert，天然没有并发丢更新的问题。
 */
data class ReadingProgress(
    val bookId: String,
    val bookTitle: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val updatedAt: Long,
)

/**
 * 一条保存下来的例句。`sentence` 是去重键 —— 同一句只留一条。
 *
 * 移植自 LingKuma 的 `sentences: [{sentence, translation, url}]`
 * （见其 background.js 的 addSentenceToDB）。原文里的 `url` 是网页地址，
 * 对阅读器没有意义，这里换成书内位置，回看时能定位到出处。
 */
data class SavedSentence(
    val sentence: String,
    /** AI 整句翻译，目标词以 `**粗体**` 标出；还没翻译时为空。 */
    val translation: String? = null,
    val bookId: String? = null,
    val bookTitle: String? = null,
    val chapterIndex: Int? = null,
    val pageIndex: Int? = null,
    /** 导入的旧备份可能没有这个字段，所以可空。epoch 毫秒，用 Long。 */
    val createdAt: Long? = null,
)

/**
 * 一个词的笔记与例句。
 *
 * **刻意与熟练度分开存放**：熟练度在 `vocabulary` 表里，值是标量（L1-L5 或 MASTERED）。
 * 把笔记塞进那张表会让所有现存用户的词库被清空 —— 原因见
 * [com.inputa.reader.domain.vocab.WordLevelCodec] 的说明，Web 版有测试钉住这个行为。
 */
data class AnnotationsData(
    /** 单词 → 多条笔记。手写笔记与采纳的 AI 建议同构，无法事后区分。 */
    val notes: Map<String, List<String>> = emptyMap(),
    val sentences: Map<String, List<SavedSentence>> = emptyMap(),
) {
    val isEmpty: Boolean get() = notes.isEmpty() && sentences.isEmpty()
}
