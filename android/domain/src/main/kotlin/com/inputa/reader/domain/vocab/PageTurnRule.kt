package com.inputa.reader.domain.vocab

import kotlin.math.abs

/**
 * 一次翻页该对词库做什么。
 *
 * 抽成纯函数不是为了好看：这个判定是**会写数据**的那个判定，而它原本埋在
 * ViewModel 的手势回调里，只能靠跑起整个界面才能验。放在这里就有单测。
 */
enum class PageTurnAction {
    /** 什么都不做。页号没变（半途滑回原页），或者只是往回看。 */
    NONE,

    /** 把**离开的那一页**上没点过的词记为已掌握。 */
    MASTER_OUTGOING,

    /**
     * 跨了多页。只记进度并提示，**不标记任何词**。
     *
     * Web 版没有这个分支 —— 它一次只翻一页（方向键或按钮），不存在跨页手势。
     * 为读者根本没看到的页面收录生词，正是 `highlightLevel` 那套设计存在的理由的反面：
     * 那些词会静默地从词库里消失，而读者从未见过它们。
     *
     * **诚实说明：这个分支目前很难被触发。** Compose 的 `HorizontalPager` 默认
     * `pagerSnapDistance = PagerSnapDistance.atMost(1)`，即一次 fling 最多只跳一页
     * （设备上验过：再快的 fling 也只前进一页）。而那个默认对阅读器来说是对的 ——
     * 甩一下跳过好几页会让人丢失位置。
     *
     * 所以它是一条**防御性规则**而不是日常路径：若将来有人调大 `pagerSnapDistance`，
     * 或者页数因 `wordsPerPage` 变化而重排导致页号一次跳多格，静默标记就会毁掉读者
     * 从未见过的那些词。留着它比删掉便宜得多 —— 但不要以为它每天都会发生。
     */
    SKIPPED,
}

object PageTurnRule {

    /**
     * @param from 离开的页号。
     * @param to 落到的页号。
     */
    fun actionFor(from: Int, to: Int): PageTurnAction = when {
        to == from -> PageTurnAction.NONE
        to == from + 1 -> PageTurnAction.MASTER_OUTGOING
        abs(to - from) > 1 -> PageTurnAction.SKIPPED
        // 只剩「后退一页」这一种。
        else -> PageTurnAction.NONE
    }
}
