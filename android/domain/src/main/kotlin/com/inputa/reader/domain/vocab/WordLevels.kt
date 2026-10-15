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
 * 另有一条**只关于显示**的规则：没收录过的词在正文里用一条中性灰虚线标出来
 * （见 [isUnfiled]），既不填色也不写词库。之所以要有一条只关显示的规则：读者需要
 * 一眼看到这一页还有哪些词没处理过。
 *
 * 它不改变上面任何一条 —— 未收录的词翻页后照样被记为「掌握」、虚线照样消失。
 * 别把它「顺手」改成真写入：那样会让点击永远进不了「用户判断过」的名单
 * （`markAsNewWord` 的早退在写入之前），翻页随即清掉读者刚点过的词；词库也会
 * 从几百条涨到几万条。
 *
 * 虚线而不是色块，是因为未收录**曾经**按 5 级「生词」着色，于是「还有哪些词
 * 没处理」和「我真的把它标成了 5 级生词」在屏幕上完全一样，读者看不出自己的标记
 * 有没有生效。现在形式不同（虚线 vs 实心块）、色相也不同（中性灰 vs 墨黄），
 * 任意一档已收录的词都不会和它混淆。
 *
 * 这条不变量的强制力在 Kotlin 侧比 Web 版更强：[highlightLevel] 与 [isUnfiled]
 * 都是纯函数，它们连写入词库的手段都没有，只能返回一个用于绘制的值。
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
     * 正文里该用哪一档**底色** —— 只回答「填充色」，不回答「怎么标出来」。
     *
     * 未收录（null）与 MASTERED 都返回 null：两者都不填充。它们的区别由 [isUnfiled]
     * 回答 —— 未收录的词改用一条中性灰虚线，已掌握的词什么都不画。
     *
     * **未收录曾经返回 L5**（按生词着色），那样读者分不清「还没处理的词」和「自己
     * 标成 5 级的词」。现在填充与标记各有一个谓词，调用点也不必从 null 里猜是哪种。
     *
     * 这是纯函数 —— 它没有任何写入词库的途径，所以「着色不等于收录」这条不变量
     * 在类型层面就成立了，不靠注释提醒。
     */
    fun highlightLevel(status: WordStatus?): WordStatus? = when (status) {
        null -> null
        WordStatus.MASTERED -> null
        else -> status
    }

    /**
     * 是否从未收录 —— 正文里改用中性灰虚线与已收录的词区分（见 Web 版的 `isUnknown`）。
     *
     * 光有 [highlightLevel] 分不出「未收录」和「已掌握」：两者都返回 null。这条谓词
     * 就是那个区别，而且它和 [highlightLevel] 一样是纯函数、一样不碰词库。
     *
     * Kotlin 侧没有 `'unknown'` 哨兵（未收录就是 null），所以实现就是判空 —— 保留这个
     * 名字是因为**调用点问的是语义**（"这个词还没处理过吗"），而 `status == null` 在
     * 一个数据流里读不出这层意思，也留不出将来加状态时的余地。
     */
    fun isUnfiled(status: WordStatus?): Boolean = status == null

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
