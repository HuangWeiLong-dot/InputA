package com.inputa.reader.ui.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏那几颗按钮该不该出现。
 *
 * 值得单独测是因为**漏一个组合，读者就走进死路**：章内翻页只靠滑动，而翻页器只装
 * 当前章的页面，所以章节边界上那颗按钮一旦不出现，那个方向就再也过不去了。而这几个
 * 边界组合恰好是最容易写成 if/else 链的形态（写了链，「完成阅读」就会盖掉「上一章」）。
 *
 * 纯数据类、不碰 Android，所以不需要 Robolectric。
 */
class ReaderFooterButtonsTest {

    /** 一章 [pagesPerChapter] 页；`sizes` 的每一项是一章。 */
    private fun state(
        chapterSizes: List<Int>,
        chapterIndex: Int,
        pageIndex: Int,
    ): ReaderUiState = ReaderUiState(
        bookId = "book",
        chapterTitles = chapterSizes.mapIndexed { i, _ -> "第 $i 章" },
        chapterIndex = chapterIndex,
        pages = List(chapterSizes[chapterIndex]) { "" },
        pageIndex = pageIndex,
    )

    // ------------------------------------------------------- 章内：一颗都不出现

    @Test
    fun `mid-chapter shows no buttons at all`() {
        val mid = state(listOf(10, 10), chapterIndex = 0, pageIndex = 4)

        assertFalse("章首不是它", mid.showPrevChapterButton)
        assertFalse("章末不是它", mid.showNextChapterButton)
        assertFalse("书还没完", mid.showFinishButton)
    }

    // ---------------------------------------------------------------- 章末 / 章首

    @Test
    fun `the end of a chapter offers the next chapter`() {
        val end = state(listOf(10, 10), chapterIndex = 0, pageIndex = 9)

        assertTrue(end.showNextChapterButton)
        assertFalse("还有下一章，就不该说读完了", end.showFinishButton)
        assertFalse(end.showPrevChapterButton)
    }

    @Test
    fun `the start of a chapter offers the previous chapter`() {
        val start = state(listOf(10, 10), chapterIndex = 1, pageIndex = 0)

        assertTrue(start.showPrevChapterButton)
        assertFalse(start.showNextChapterButton)
        assertFalse(start.showFinishButton)
    }

    // ------------------------------------------------------------ 几个叠起来的边界

    /**
     * 单页的章节同时是章首与章末，两个方向都滑不动，所以两颗按钮要一起出现。
     */
    @Test
    fun `a single-page chapter offers both directions`() {
        val only = state(listOf(10, 1, 10), chapterIndex = 1, pageIndex = 0)

        assertTrue(only.showPrevChapterButton)
        assertTrue(only.showNextChapterButton)
        assertFalse(only.showFinishButton)
    }

    /**
     * **最容易被 if/else 链写坏的一个**：单页的最后一章既是章末（但其实全书末页没有
     * 「下一章」）又是章首。写成链式分支时「完成阅读」会把它盖掉，读者卡在这一页 ——
     * 右边滑不动，左边滑不动，除了「完成阅读」没有别的出路。
     */
    @Test
    fun `a single-page final chapter offers both going back and finishing`() {
        val last = state(listOf(10, 1), chapterIndex = 1, pageIndex = 0)

        assertTrue("退得回去", last.showPrevChapterButton)
        assertTrue(last.showFinishButton)
        assertFalse("全书末页没有下一章", last.showNextChapterButton)
    }

    // -------------------------------------------------------------------- 全书两端

    /**
     * 全书第一页是**章首**而不是章末，所以三颗都不出现 —— 往前的方向本该由第一颗
     * 按钮提供，但书前面已经没有东西了。这一页底栏就是干净的页码，正好是最常见的
     * 打开状态。
     */
    @Test
    fun `the very first page of the book offers nothing`() {
        val first = state(listOf(10, 10), chapterIndex = 0, pageIndex = 0)

        assertFalse("书前面没有东西了", first.showPrevChapterButton)
        assertFalse("这不是章末", first.showNextChapterButton)
        assertFalse(first.showFinishButton)
    }

    @Test
    fun `the last page of the book offers finishing only`() {
        val last = state(listOf(10, 10), chapterIndex = 1, pageIndex = 9)

        assertTrue(last.showFinishButton)
        assertFalse(last.showNextChapterButton)
        assertFalse(last.showPrevChapterButton)
    }

    @Test
    fun `a single-chapter book ends with finish and nothing else`() {
        val last = state(listOf(3), chapterIndex = 0, pageIndex = 2)

        assertTrue(last.showFinishButton)
        assertFalse(last.showPrevChapterButton)
        assertFalse(last.showNextChapterButton)
    }

    // ------------------------------------------------------------------ 还没读进去

    /**
     * 没有页面时不该冒出「完成阅读」—— 那不是"读完了"，是"没书可读"。
     */
    @Test
    fun `an empty book offers nothing`() {
        val empty = ReaderUiState()

        assertFalse(empty.showFinishButton)
        assertFalse(empty.showNextChapterButton)
        assertFalse(empty.showPrevChapterButton)
    }
}
