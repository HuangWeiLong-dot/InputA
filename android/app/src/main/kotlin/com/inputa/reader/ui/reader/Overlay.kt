package com.inputa.reader.ui.reader

/**
 * 阅读器之上的一层模态。
 *
 * Web 版没有 router：`App.tsx` 常驻挂载阅读器，用五个**布尔标志**控制五个覆盖层。
 * 那套写法已经踩过一个坑 —— 两个句子面板都用 `''` 作默认值时 React key 冲突，
 * 只能靠 `explosion:` / `analysis:` 前缀绕过。
 *
 * 换成可空密封类型之后，「至多一个覆盖层打开」成了类型层面的事实，而句子这类参数
 * 随值一起走，不再需要一个可能失同步的兄弟字段。第二阶段加句子分析时，直接写
 * `data class SentenceAnalysis(val sentence: String) : Overlay` 即可 —— 前缀技巧
 * 不再需要。
 */
sealed interface Overlay {
    data object BookCatalog : Overlay
    data object Vocabulary : Overlay
    data object Settings : Overlay
}
