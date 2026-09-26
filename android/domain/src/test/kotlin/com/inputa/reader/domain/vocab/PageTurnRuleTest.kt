package com.inputa.reader.domain.vocab

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 翻页该不该写词库。
 *
 * 这是**唯一会因翻页而写数据**的判定，所以每个分支都单独钉住。剩下的部分
 * （真的写进去哪些词、撤销删掉哪些行）由 `:app` 里跑在内存 Room 上的
 * VocabularyRepositoryTest 覆盖 —— 两边合起来才是完整的规则。
 */
class PageTurnRuleTest {

    @Test
    fun `moving forward exactly one page masters the page left behind`() {
        assertEquals(PageTurnAction.MASTER_OUTGOING, PageTurnRule.actionFor(from = 0, to = 1))
        assertEquals(PageTurnAction.MASTER_OUTGOING, PageTurnRule.actionFor(from = 7, to = 8))
    }

    /** 半途滑回原页：页号没变，什么都不写。这是最常见的误触，也免费挡掉了。 */
    @Test
    fun `a page that snapped back changes nothing`() {
        assertEquals(PageTurnAction.NONE, PageTurnRule.actionFor(from = 3, to = 3))
    }

    /** 往回看不该改变词库 —— 读者只是回去重读，不是没见过那些词。 */
    @Test
    fun `going back one page changes nothing`() {
        assertEquals(PageTurnAction.NONE, PageTurnRule.actionFor(from = 4, to = 3))
        assertEquals(PageTurnAction.NONE, PageTurnRule.actionFor(from = 1, to = 0))
    }

    /**
     * 跨多页的快速滑动**不标记任何词**。这是对 Web 规则的有意细化：
     * 那边一次只翻一页，没有对应手势，所以没有「跳过的页怎么办」这个问题。
     */
    @Test
    fun `a multi-page fling never masters`() {
        assertEquals(PageTurnAction.SKIPPED, PageTurnRule.actionFor(from = 0, to = 2))
        assertEquals(PageTurnAction.SKIPPED, PageTurnRule.actionFor(from = 5, to = 2))
        assertEquals(PageTurnAction.SKIPPED, PageTurnRule.actionFor(from = 0, to = 20))
    }

    /**
     * 边界：后退**一页**与后退**多页**要分开 —— 前者静默、后者提示。
     * 差一页是最常见的滑动，弹个「已跳过 1 页」会很吵。
     */
    @Test
    fun `exactly one page back is silent but more than one is not`() {
        assertEquals(PageTurnAction.NONE, PageTurnRule.actionFor(from = 2, to = 1))
        assertEquals(PageTurnAction.SKIPPED, PageTurnRule.actionFor(from = 3, to = 1))
    }
}
