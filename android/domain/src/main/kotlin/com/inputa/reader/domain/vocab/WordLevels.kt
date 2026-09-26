package com.inputa.reader.domain.vocab

import com.inputa.reader.domain.model.WordStatus

/**
 * 熟练度色阶的元数据与状态转换规则 —— **整个应用的词汇逻辑，改动前先看这里**。
 *
 * 规则（逐条对应 Web 版的 src/utils/wordLevel.ts）：
 *   - 在正文里点击一个词  → 尚未收录时记为 5 级「生词」（最深）
 *   - 翻到下一页时，本页未被点击的词 → 记为「掌握」（正文不再高亮）
 *   - 释义面板里的 5 段选择器可以手动指定任意一级
 *   - 状态只由用户改写：点击与翻页都不会覆盖已有状态（1-5 级或已掌握），
 *     要改回 5 级请用「标为生词」按钮 —— 用户的判断优先
 *
 * 另有一条**只关于显示**的规则：没收录过的词在正文里按 5 级「生词」着色
 * （见 [highlightLevel]）。它不写词库、不改变上面任何一条 —— 未收录的词翻页后
 * 照样被记为「掌握」、高亮照样消失。别把它「顺手」改成真写入：那样会让点击
 * 永远进不了「用户判断过」的名单（`markAsNewWord` 的早退在写入之前），翻页随即
 * 清掉读者刚点过的词；词库也会从几百条涨到几万条。
 *
 * 这条不变量的强制力在 Kotlin 侧比 Web 版更强：`highlightLevel` 是纯函数，它连
 * 写入词库的手段都没有，只能返回一个用于着色的值。
 */
object WordLevels {

    /** 由浅至深，1 = 最浅。 */
    val LEVELS: List<WordStatus> = listOf(
        WordStatus.L1, WordStatus.L2, WordStatus.L3, WordStatus.L4, WordStatus.L5,
    )

    /** 1 = 最浅（熟知），5 = 最深（生词）。 */
    private val LABELS: Map<WordStatus, String> = mapOf(
        WordStatus.L1 to "熟知",
        WordStatus.L2 to "熟悉",
        WordStatus.L3 to "一般",
        WordStatus.L4 to "模糊",
        WordStatus.L5 to "生词",
    )

    /** 档位名，如「一般」。界面上呈现状态一律用它（不带数字）。 */
    fun label(status: WordStatus): String? = LABELS[status]

    /** 完整标签，如「3 一般」。只用于 tooltip / contentDescription 等提示文字。 */
    fun levelLabel(status: WordStatus): String? =
        LABELS[status]?.let { "${status.code} $it" }

    /** 1-5 → 对应等级；其余（含 6、0、-1）返回 null。用于把外部来的数字收敛成合法等级。 */
    fun fromLevel(level: Int): WordStatus? = LEVELS.getOrNull(level - 1)

    /** 是否为熟练度等级（1-5）。与 [WordStatus.isLevel] 同义，供 `WordStatus?` 调用点使用。 */
    fun isLevel(status: WordStatus?): Boolean = status != null && status.isLevel

    /**
     * 正文里该用哪一档底色。
     *
     * 未收录（null）按 5「生词」着色：读者一眼就能看到这一页还有哪些词没处理过。
     * 它和真正标为 5 级的词看起来一样，但词库里没有任何记录；MASTERED 返回 null，
     * 彻底不高亮。
     *
     * 这是纯函数 —— 它没有任何写入词库的途径，所以「着色不等于收录」这条不变量
     * 在类型层面就成立了，不靠注释提醒。
     */
    fun highlightLevel(status: WordStatus?): WordStatus? = when (status) {
        null -> WordStatus.L5
        WordStatus.MASTERED -> null
        else -> status
    }

    /**
     * 词库的键规范化：去首尾空白后小写。与 [com.inputa.reader.domain.text.TextNormalize.cleanToken]
     * 是两件事 —— 后者还负责去掉首尾的撇号与连字符，供分词器使用。调用点拿到的通常已经是
     * `token.cleanWord`，两者结果一致。
     *
     * 用 `lowercase()` 而不是 `toLowerCase()`：后者受默认 Locale 影响，土耳其语区域设置下
     * `"I".toLowerCase()` 得到 `"ı"`，会静默毁掉所有词库键。Web 版（JS）没有这个 bug。
     */
    fun normalizeKey(raw: String): String = raw.trim().lowercase()
}
