package com.inputa.reader.domain.text

/**
 * 从词元序列里取出包含某个词的整句，用于释义面板的「当前所在句子」与例句保存。
 *
 * 从 Web 版的 `ReaderArea.getSentenceForToken` 提取而来。**提取是这次移植特意做的事**：
 * 那段逻辑原本长在组件里，而 Web 版的测试跑在无 DOM 的 node 环境、没有任何组件渲染覆盖，
 * 所以它从未被测试过。搬到领域层之后就有了单测 —— 这是移植带来的净增覆盖。
 *
 * 已知且**有意保留**的行为：不处理缩写。`Mr. Bennet was...` 会被切在 `Mr.` 之后，
 * 因为 `.` 就是判据。这是已发布的行为，不是 bug；换一套缩写规则会改变面板里
 * 显示的句子、进而改变 AI 分析与保存的例句，属于产品决策而不是移植。
 */
object Sentence {

    private val SENTENCE_END = Regex("[.!?]")

    /**
     * @param targetIndex 被点击词元在 [tokens] 中的下标。
     * @return 句子的纯文本（首尾已 trim）。找不到边界时返回从头/到尾的整段。
     */
    fun forToken(tokens: List<Token>, targetIndex: Int): String {
        if (tokens.isEmpty()) return ""
        if (targetIndex !in tokens.indices) return ""

        // 往回找最近的终止符，句子从它之后开始。刻意跳过目标词元自己
        // （`i != targetIndex`）：万一被点的词元本身含 `.`，它不该把自己判成句首。
        var startIndex = 0
        for (i in targetIndex downTo 0) {
            if (SENTENCE_END.containsMatchIn(tokens[i].raw) && i != targetIndex) {
                startIndex = i + 1
                break
            }
        }

        // 往前找最近的终止符，句子到它为止。这里**不**跳过目标词元 —— 与 Web 版一致。
        var endIndex = tokens.size - 1
        for (i in targetIndex until tokens.size) {
            if (SENTENCE_END.containsMatchIn(tokens[i].raw)) {
                endIndex = i
                break
            }
        }

        // JS 的 slice 在区间反转时返回空数组；Kotlin 的 subList 会抛异常，所以显式挡住。
        if (startIndex > endIndex) return ""

        return tokens.subList(startIndex, endIndex + 1)
            .joinToString(separator = "") { it.raw }
            .trim()
    }
}
