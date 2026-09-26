package com.inputa.reader.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.repository.MasteryBatch
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.inputa.reader.ui.components.AppIcon
import com.inputa.reader.ui.components.ButtonSize
import com.inputa.reader.ui.components.IconButton
import com.inputa.reader.ui.components.LoadingIndicator
import com.inputa.reader.ui.components.NoticeBar
import com.inputa.reader.ui.components.PanelButton
import com.inputa.reader.ui.components.Tone
import com.inputa.reader.ui.components.cardSurface
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** 页内段落的分隔，与 `Pagination` 拼接页面时用的 `\n\n` 对应。 */
private val PARAGRAPH_BREAK = Regex("\\n\\s*\\n")

/**
 * 阅读器。
 *
 * 结构照 Web 版：顶部是书名与章节名，中间是正文，底部是翻页条。
 *
 * 每页在水平翻页器内部**垂直滚动**。两个轴不同，所以不需要 `nestedScroll`，
 * 翻页器与滚动不会互相抢手势 —— 但对角线拖动会被翻页器接管，这一点手测时值得注意。
 *
 * 刻意**不做**「整屏一页的垂直翻页」：那会让「页」不再是词数分块，破坏与
 * `Pagination.paginate` 的对齐，也改变翻页规则该收录哪些词。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    scrollToPage: Flow<Int>,
    /** (单词, 它所在的句子)。句子是释义面板的「当前所在句子」与保存例句要用的。 */
    onWordTap: (String, String) -> Unit,
    onPageSettled: (Int) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDismissWord: () -> Unit,
    onRetryLookup: () -> Unit,
    onSetStatus: (WordStatus) -> Unit,
    onMarkMastered: () -> Unit,
    onRemoveWord: () -> Unit,
    onUndoMastery: () -> Unit,
    onDismissMastery: () -> Unit,
    onDismissSkipped: () -> Unit,
    onOpenOverlay: (Overlay) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalThemeTokens.current

    // 外面这一层 Box 是为了让「正在载入…」能落在**屏幕**正中，而不是正文区正中。
    // 正文区被顶栏与底栏夹着，它的几何中心比屏幕中心低/高出一截（顶栏两行 + 底栏一张卡片，
    // 两者不一样高），把加载指示器放在正文区里，看起来就是「偏了一点」。
    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ReaderHeader(state, onOpenOverlay)

            Box(modifier = Modifier.weight(1f)) {
                if (!state.isLoading && state.pages.isEmpty()) {
                    // 这个不是加载中，是「没书可读」—— 它属于正文区，留在正文区正中即可。
                    Text(
                        text = "暂无打开的读物",
                        color = tokens.textMuted,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else if (!state.isLoading) {
                    // 翻页器的 initialPage 只在**首次组合**时生效，所以切章必须让它重建。
                    // key 用 pagerKey（每次载入章节递增）而不是章号：连续两次落到同一章
                    // （比如从第 0 页退到上一章的最后一页、又退回来）时章号可能相同，
                    // 而页号已经变了 —— 只有载入序号能保证一定重建。
                    key(state.pagerKey) {
                        ReaderPager(
                            state = state,
                            scrollToPage = scrollToPage,
                            onWordTap = onWordTap,
                            onPageSettled = onPageSettled,
                        )
                    }
                }
            }

            MasteryNotice(
                batch = state.masteryBatch,
                skippedPages = state.skippedPages,
                onUndo = onUndoMastery,
                onDismissMastery = onDismissMastery,
                onDismissSkipped = onDismissSkipped,
            )

            ReaderFooter(state = state, onPrev = onPrev, onNext = onNext)
        }

        // 加载指示器盖在**整屏**之上并居中，与顶栏、底栏的高度无关。
        if (state.isLoading) {
            LoadingIndicator(
                text = "正在载入…",
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    if (state.activeWord != null) {
        DefinitionSheet(
            word = state.activeWord,
            status = state.statuses[state.activeWord],
            lookup = state.lookup,
            sentence = state.activeSentence,
            onDismiss = onDismissWord,
            onRetry = onRetryLookup,
            onSetStatus = onSetStatus,
            onMarkMastered = onMarkMastered,
            onRemoveWord = onRemoveWord,
        )
    }
}

/**
 * 顶栏：书名与章节名，加三个入口。
 *
 * 生词本按钮上带计数，取自 `observeCounts()` 的 `GROUP BY` —— 与生词本面板读的是
 * 同一个流，所以两边永远一致，不需要任何手动同步。
 */
@Composable
private fun ReaderHeader(state: ReaderUiState, onOpenOverlay: (Overlay) -> Unit) {
    val tokens = LocalThemeTokens.current

    // 标题一行，右上角一个汉堡。
    //
    // 三个入口（书架 / 生词本 / 设置）原本各自是一个「图标 + 小字」的按钮，合计约 250dp ——
    // 挤在同一行里会把书名压到五六个字符，于是当时把入口单独排了一行，代价是顶栏高 35dp。
    // 收进汉堡之后这一行就放得下了，那 35dp 还给了正文。
    //
    // 两行标题**始终渲染**，哪怕内容是空串。加载时书名已知、章名还不知道，若缺一行就不画，
    // 顶栏会在「正在载入…」那一帧与加载完那一帧之间变矮一截 —— 下面的正文与居中的
    // 加载文字都会跟着跳一下，那正是打开应用时看到的错位。空串同样占一行的高度。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.lg, end = Space.sm, top = Space.md, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.bookTitle,
                color = tokens.textMuted,
                fontSize = TypeScale.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = state.chapterTitle,
                color = tokens.textStrong,
                fontSize = TypeScale.title,
                fontWeight = TypeScale.titleWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HeaderMenu(state = state, onOpenOverlay = onOpenOverlay)
    }
}

/**
 * 右上角的汉堡与它展开的菜单。
 *
 * `Box` 只为了让 `DropdownMenu` 贴着按钮弹出 —— 它按父节点的位置对齐。
 *
 * 生词本那一项把计数放在 `trailingIcon` 里（而不是拼进文字）：菜单项的文字左对齐、
 * 计数右对齐，两行条目扫下来数字是齐的。计数为 0 时仍然显示 0，因为「一本都没收」
 * 本身是读者要知道的信息。
 */
@Composable
private fun HeaderMenu(state: ReaderUiState, onOpenOverlay: (Overlay) -> Unit) {
    val tokens = LocalThemeTokens.current
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(
            icon = InputaIcons.Menu,
            contentDescription = "更多：书架、生词本、设置",
            onClick = { expanded = true },
            tone = Tone.Neutral,
            size = ButtonSize.Sm,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // 菜单里的文字用正文色与标度里的字号；M3 默认会拿 MaterialTheme.typography，
            // 而我们那套只填了两个槽位，这里显式写清楚更稳。
            containerColor = tokens.bgSurface,
        ) {
            MenuEntry(
                label = "书架",
                icon = InputaIcons.BookOpen,
                onClick = {
                    expanded = false
                    onOpenOverlay(Overlay.BookCatalog)
                },
            )
            MenuEntry(
                label = "生词本",
                icon = InputaIcons.BookMarked,
                trailing = "${state.counts.active}",
                onClick = {
                    expanded = false
                    onOpenOverlay(Overlay.Vocabulary)
                },
            )
            MenuEntry(
                label = "设置",
                icon = InputaIcons.Settings,
                onClick = {
                    expanded = false
                    onOpenOverlay(Overlay.Settings)
                },
            )
        }
    }
}

@Composable
private fun MenuEntry(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    trailing: String? = null,
) {
    val tokens = LocalThemeTokens.current
    DropdownMenuItem(
        text = { Text(text = label, color = tokens.textMain, fontSize = TypeScale.bodyLg) },
        onClick = onClick,
        leadingIcon = { AppIcon(icon, contentDescription = null, tint = tokens.textMuted, size = 18.dp) },
        trailingIcon = trailing?.let { count ->
            {
                Text(
                    text = count,
                    color = tokens.textMuted,
                    fontSize = TypeScale.label,
                    fontWeight = TypeScale.labelWeight,
                )
            }
        },
    )
}

@Composable
private fun ReaderPager(
    state: ReaderUiState,
    scrollToPage: Flow<Int>,
    onWordTap: (String, String) -> Unit,
    onPageSettled: (Int) -> Unit,
) {
    // initialPage 用 state.initialPage（进入本页时的起始页）而不是 state.pageIndex：
    // 后者是**实时**页号，翻页时一直在变，而 initialPage 只在首次组合生效 ——
    // 用页号会让「返回同一章」时的落点不确定。
    val pagerState = rememberPagerState(initialPage = state.initialPage) { state.pages.size }

    // 「已吸附」而不是「当前页」：拖动途中 currentPage 就变了，而半途滑回原页
    // 从不改变 settledPage。这一条免费挡掉了最常见的误触，而翻页在步骤 8 会写数据。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect(onPageSettled)
    }

    // 程序化翻页。这个方向是单向的 —— 上面的 settle 回调从不往通道里发，
    // 所以按钮滚过去之后不会又被回显一次。
    LaunchedEffect(pagerState) {
        scrollToPage.collect { target ->
            if (target != pagerState.currentPage) pagerState.animateScrollToPage(target)
        }
    }

    HorizontalPager(
        state = pagerState,
        // 预组合相邻页，滑动时不会看到空白。取 1 而不是更多：一页正文不便宜。
        beyondViewportPageCount = 1,
        modifier = Modifier.fillMaxSize(),
    ) { index ->
        PageContent(
            page = state.pages.getOrNull(index).orEmpty(),
            statuses = state.statuses,
            activeWord = state.activeWord,
            bionic = state.settings.bionicEnabled,
            fontSizeSp = state.settings.fontSizeSp,
            lineHeight = state.settings.lineHeight,
            onWordTap = onWordTap,
        )
    }
}

@Composable
private fun PageContent(
    page: String,
    statuses: Map<String, com.inputa.reader.domain.model.WordStatus>,
    activeWord: String?,
    bionic: Boolean,
    fontSizeSp: Int,
    lineHeight: Float,
    onWordTap: (String, String) -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val paragraphs = remember(page) {
        page.split(PARAGRAPH_BREAK).filter { it.isNotBlank() }
    }

    // **正文的边距与段间距刻意不走设计标度。** 它们不是「界面上的空白」，而是排版本身：
    // 每行多少字、段落之间留多少，决定了读起来累不累。所以 [Space] 那套档位改了这里也不动，
    // 就像字号与行高不走标度一样 —— 别顺手把它们「统一」进来。
    // （顺带：分页按词数切，与这里的像素宽度无关，所以改这里的数值不会改变哪一页有哪些词。）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        paragraphs.forEachIndexed { index, paragraph ->
            WordParagraph(
                text = paragraph,
                paragraphIndex = index,
                statuses = statuses,
                activeWord = activeWord,
                bionic = bionic,
                tokens = tokens,
                fontSizeSp = fontSizeSp,
                lineHeight = lineHeight,
                onWordTap = onWordTap,
            )
            if (index != paragraphs.lastIndex) Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ReaderFooter(state: ReaderUiState, onPrev: () -> Unit, onNext: () -> Unit) {
    val tokens = LocalThemeTokens.current
    // 注意用 hasNextPage / hasPrevPage 而不是页号比较：本章最后一页之后还有下一章，
    // 「下一页」在那时仍然可用（它会滚进下一章）。
    val isFirst = !state.hasPrevPage
    val isLast = !state.hasNextPage

    // 底栏做成一张浮起来的卡片：留出左右外边距，它就不必跟着屏幕边缘切成直角。
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md)
            .cardSurface()
            .padding(horizontal = Space.md, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        PanelButton(
            label = "上一页",
            onClick = onPrev,
            icon = InputaIcons.ChevronLeft,
            size = ButtonSize.Lg,
            enabled = !isFirst,
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${state.pageIndex + 1} / ${state.pageCount} 页",
                color = tokens.textMain,
                fontSize = TypeScale.bodyLg,
                fontWeight = TypeScale.labelWeight,
                textAlign = TextAlign.Center,
            )
            if (state.chapterTitles.size > 1) {
                Text(
                    text = "第 ${state.chapterIndex + 1} / ${state.chapterTitles.size} 章",
                    color = tokens.textMuted,
                    fontSize = TypeScale.label,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // 「下一页」用实心：它是读者绝大多数时候要按的那一个。
        PanelButton(
            label = "下一页",
            onClick = onNext,
            trailingIcon = InputaIcons.ChevronRight,
            tone = Tone.Invert,
            size = ButtonSize.Lg,
            enabled = !isLast,
        )
    }
}

/**
 * 翻页提示条。
 *
 * 两种情况：
 *
 *   - **本页 N 个未点击的词已自动记为「已掌握」** + 「撤销」。文案沿用 Web 版，
 *     但多了一个撤销动作 —— Web 版那个 2.6 秒、无动作的提示条撑不住撤销操作，
 *     所以这里放宽到 6 秒。
 *   - **已跳过 N 页，未自动记为已掌握**。跨页快速滑动不标记任何词，但读者需要知道
 *     自己跳过了东西、而且那些词不会被自动处理（见 onPageSettled 的说明）。
 *
 * 两种提示都放在状态里而不是弹一次就走：配置变更（旋转）不该让撤销窗口消失。
 */
@Composable
private fun MasteryNotice(
    batch: MasteryBatch?,
    skippedPages: Int?,
    onUndo: () -> Unit,
    onDismissMastery: () -> Unit,
    onDismissSkipped: () -> Unit,
) {
    val tokens = LocalThemeTokens.current

    LaunchedEffect(batch) {
        if (batch != null) {
            delay(MASTERY_NOTICE_MILLIS)
            onDismissMastery()
        }
    }
    LaunchedEffect(skippedPages) {
        if (skippedPages != null) {
            delay(SKIPPED_NOTICE_MILLIS)
            onDismissSkipped()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        batch?.let { current ->
            NoticeBar(
                text = "本页 ${current.count} 个未点击的词已自动记为「已掌握」",
                actionLabel = "撤销",
                actionIcon = InputaIcons.RotateCcw,
                onAction = onUndo,
                modifier = Modifier.padding(horizontal = Space.lg),
            )
        }
        skippedPages?.let { count ->
            NoticeBar(
                text = "已跳过 $count 页，未自动记为已掌握",
                tone = Tone.Neutral,
                modifier = Modifier.padding(horizontal = Space.lg),
            )
        }
    }
}

/**
 * 提示条的停留时间。
 *
 * Web 版是 2.6 秒且没有动作 —— 那撑不住撤销操作（用户要读完、理解、再决定）。
 * 6 秒是这次移植的一处有意偏离。
 */
private const val MASTERY_NOTICE_MILLIS = 6_000L
private const val SKIPPED_NOTICE_MILLIS = 3_000L
