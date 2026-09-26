package com.inputa.reader.ui.bookshelf

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.inputa.reader.domain.book.GutendexBook
import com.inputa.reader.domain.model.Book
import com.inputa.reader.domain.repository.BookSummary
import com.inputa.reader.ui.components.AlertBox
import com.inputa.reader.ui.components.ButtonSize
import com.inputa.reader.ui.components.CloseFooter
import com.inputa.reader.ui.components.HintText
import com.inputa.reader.ui.components.IconButton
import com.inputa.reader.ui.components.ListBlock
import com.inputa.reader.ui.components.ListRow
import com.inputa.reader.ui.components.ModalPanel
import com.inputa.reader.ui.components.PanelButton
import com.inputa.reader.ui.components.PanelTextField
import com.inputa.reader.ui.components.PanelTitle
import com.inputa.reader.ui.components.SectionLabel
import com.inputa.reader.ui.components.Segmented
import com.inputa.reader.ui.components.Tone
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 书架：导入过的书 + 内置样书 + Gutendex 搜索 + 粘贴正文。
 *
 * 与 Web 版的差别在第一个 tab：那边书不落盘，所以只有「内置 / 搜索 / 粘贴」；
 * 这里导入过的书会一直在。
 */
@Composable
fun BookshelfDialog(
    state: BookshelfViewModel.State,
    onTab: (BookshelfViewModel.Tab) -> Unit,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onLoadGutendex: (GutendexBook) -> Unit,
    onPasteTitle: (String) -> Unit,
    onPasteContent: (String) -> Unit,
    onSavePasted: () -> Unit,
    onImportFile: (Uri) -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // imePadding：键盘弹起时面板底部（footer 那对按钮）不该被压在键盘下面。
    // 本应用此前哪都没用过 insets 修饰符，而 ModalPanel 的高度上限取自
    // `LocalConfiguration.screenHeightDp`，那个值**不随键盘收缩**，所以这个补丁是必需的。
    // 若某个机型上 Dialog 窗口本身已随 insets 收缩，`WindowInsets.ime` 会是 0，那就是 no-op。
    ModalPanel(onDismiss = onDismiss, modifier = Modifier.imePadding()) {
        PanelTitle(text = "书架", icon = InputaIcons.BookOpen)
        Spacer(Modifier.height(Space.lg))

        Segmented(
            options = listOf(
                BookshelfViewModel.Tab.SHELF,
                BookshelfViewModel.Tab.SEARCH,
                BookshelfViewModel.Tab.FILE,
                BookshelfViewModel.Tab.PASTE,
            ),
            selected = state.tab,
            label = { tab ->
                when (tab) {
                    BookshelfViewModel.Tab.SHELF -> "书架"
                    BookshelfViewModel.Tab.SEARCH -> "搜索"
                    BookshelfViewModel.Tab.FILE -> "文件"
                    BookshelfViewModel.Tab.PASTE -> "粘贴"
                }
            },
            onSelect = onTab,
            icon = { tab ->
                when (tab) {
                    BookshelfViewModel.Tab.SHELF -> InputaIcons.BookOpen
                    BookshelfViewModel.Tab.SEARCH -> InputaIcons.Search
                    // 图标集里没有「文件/上传」这一类，而新增图标必须挂进
                    // `InputaIcons.all`（测试按它遍历）。粘贴页本来就是 null，照抄即可。
                    BookshelfViewModel.Tab.FILE -> null
                    BookshelfViewModel.Tab.PASTE -> null
                }
            },
        )
        Spacer(Modifier.height(Space.lg))

        state.error?.let { message ->
            AlertBox(tone = Tone.Highlight, icon = InputaIcons.AlertTriangle) {
                Text(text = message, color = LocalThemeTokens.current.highlightText, fontSize = TypeScale.body)
            }
            Spacer(Modifier.height(Space.lg))
        }

        // 占剩余高度：三个 tab 里都有列表，而列表撑出面板高度上限会被裁掉。
        // **必须是 Column，不能是 Box** —— 一个 Box 会把它的多个子节点叠在一起，
        // 而搜索页与粘贴页都往外发好几条同级 composable（搜索行 / 两个输入框 / 提示 / 按钮）。
        // 之前这里是 Box，那两个 tab 的元素就叠成了一团（书架页只发一个列表，所以看不出来）。
        Column(modifier = Modifier.weight(1f, fill = false)) {
            when (state.tab) {
                BookshelfViewModel.Tab.SHELF -> ShelfTab(state, onOpen, onDelete)
                BookshelfViewModel.Tab.SEARCH -> SearchTab(state, onQuery, onSearch, onLoadGutendex)
                BookshelfViewModel.Tab.FILE -> FileTab(state, onImportFile)
                BookshelfViewModel.Tab.PASTE -> PasteTab(state, onPasteTitle, onPasteContent)
            }
        }

        Spacer(Modifier.height(Space.lg))
        // 「载入阅读器」放在 footer，而不是粘贴页的正文里：正文那块是
        // `weight(1f, fill = false)`，内容一长就会被挤出面板、被 cardSurface 裁掉
        // （这个对话框里没有任何滚动容器，所以滚也滚不到）。footer 在 weight 之外，永远在。
        val pasteAction: (@Composable () -> Unit)? =
            if (state.tab == BookshelfViewModel.Tab.PASTE) {
                {
                    PanelButton(
                        label = "载入阅读器",
                        onClick = onSavePasted,
                        tone = Tone.Accent,
                        enabled = state.pasteContent.isNotBlank(),
                    )
                }
            } else {
                null
            }
        CloseFooter(onClose = onDismiss, action = pasteAction)
    }
}

@Composable
private fun ShelfTab(
    state: BookshelfViewModel.State,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    ListBlock {
        if (state.shelf.isEmpty()) {
            item {
                HintText(
                    "还没有导入过书。去「搜索」找一本 Gutenberg 的书，或把正文粘贴进来。",
                    modifier = Modifier.padding(Space.lg),
                )
            }
        } else {
            item { BoxedLabel("我导入的") }
            itemsIndexed(state.shelf, key = { _, it -> it.id }) { index, summary ->
                ShelfRow(
                    summary = summary,
                    showDivider = index != state.shelf.lastIndex || state.builtin.isNotEmpty(),
                    onOpen = onOpen,
                    onDelete = onDelete,
                )
            }
        }

        // 内置样书列在下面而不是单独一个 tab：它们永远是「能立刻读」的那几本，
        // 混在同一列里读者一眼就能看到，多一个 tab 反而要多点一下。
        item { BoxedLabel("内置样书（随应用提供）") }
        itemsIndexed(state.builtin, key = { _, it -> it.id }) { index, book ->
            BuiltinRow(book, showDivider = index != state.builtin.lastIndex, onOpen = onOpen)
        }
    }
}

/** 列表里的小节标题，带一点内边距好与行对齐。 */
@Composable
private fun BoxedLabel(text: String) {
    SectionLabel(text, modifier = Modifier.padding(start = Space.lg, top = Space.lg, bottom = Space.xs))
}

@Composable
private fun ShelfRow(
    summary: BookSummary,
    showDivider: Boolean,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    ListRow(
        title = summary.title,
        subtitle = "${summary.author} · ${summary.chapterCount} 章",
        showDivider = showDivider,
        trailing = {
            PanelButton(
                label = "阅读",
                onClick = { onOpen(summary.id) },
                tone = Tone.Accent,
                size = ButtonSize.Sm,
            )
            IconButton(
                icon = InputaIcons.Trash2,
                contentDescription = "删除《${summary.title}》",
                onClick = { onDelete(summary.id) },
                tone = Tone.DangerGhost,
                size = ButtonSize.Sm,
            )
        },
    )
}

@Composable
private fun BuiltinRow(book: Book, showDivider: Boolean, onOpen: (String) -> Unit) {
    ListRow(
        title = book.title,
        subtitle = "${book.author} · ${book.chapters.size} 章",
        showDivider = showDivider,
        trailing = {
            PanelButton(
                label = "阅读",
                onClick = { onOpen(book.id) },
                tone = Tone.AccentSoft,
                size = ButtonSize.Sm,
            )
        },
    )
}

@Composable
private fun SearchTab(
    state: BookshelfViewModel.State,
    onQuery: (String) -> Unit,
    onSearch: () -> Unit,
    onLoad: (GutendexBook) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        PanelTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = "书名或作者英文，例如 dracula / austen",
            modifier = Modifier.weight(1f),
        )
        PanelButton(
            label = if (state.isSearching) "搜索中…" else "搜索",
            onClick = onSearch,
            tone = Tone.Accent,
            icon = InputaIcons.Search,
            enabled = !state.isSearching && !state.isLoadingBook,
        )
    }

    // 下载可能很久（后端要去 Gutenberg 取整本书），所以必须有可见的进度提示 ——
    // 否则读者会以为按钮没反应而反复点。
    if (state.isLoadingBook) {
        Spacer(Modifier.height(Space.lg))
        HintText("正在获取正文并切分章节…")
    }

    Spacer(Modifier.height(Space.lg))
    ListBlock {
        if (state.results.isEmpty() && !state.isSearching) {
            item {
                HintText(
                    "输入关键词后点搜索。结果来自 Project Gutenberg（Gutendex）。",
                    modifier = Modifier.padding(Space.lg),
                )
            }
        }
        itemsIndexed(state.results, key = { _, it -> it.id }) { index, item ->
            ListRow(
                title = item.title,
                subtitle = "${item.authors.joinToString(", ")} · 下载量 ${item.downloadCount}",
                titleMaxLines = 2,
                showDivider = index != state.results.lastIndex,
                trailing = {
                    PanelButton(
                        label = "载入",
                        onClick = { onLoad(item) },
                        tone = Tone.Accent,
                        size = ButtonSize.Sm,
                        enabled = !state.isLoadingBook,
                    )
                },
            )
        }
    }
}

/**
 * 支持的 MIME 类型。
 *
 * 最后两项是兜底：不少文件管理器给 `.epub` / `.md` 报的 MIME 并不标准（常见的是
 * `application/octet-stream`），只列标准类型会让这些文件在选择器里变灰、根本选不中。
 * 真正的类型判断在解析层按扩展名做（`parseKindOf`），不依赖这里的 MIME。
 */
private val IMPORT_MIME_TYPES = arrayOf(
    "text/plain",
    "text/markdown",
    "text/html",
    "application/epub+zip",
    "application/octet-stream",
)

/**
 * 文件导入。
 *
 * 用 SAF（`OpenDocument`）而不是申请存储权限：系统选择器把 Uri 授权给本应用，
 * **不需要任何权限** —— manifest 里至今也只有 INTERNET 与 ACCESS_NETWORK_STATE 两条。
 *
 * 选完之后的读文件与解析都在 ViewModel 里（IO 线程），这里只管发起选择。
 */
@Composable
private fun FileTab(
    state: BookshelfViewModel.State,
    onImportFile: (Uri) -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        // 用户取消时回调照样会来，那时 uri 是 null。
        uri?.let(onImportFile)
    }

    PanelButton(
        label = state.busyLabel ?: "选择文件",
        onClick = { launcher.launch(IMPORT_MIME_TYPES) },
        tone = Tone.Accent,
        // 解析期间禁用：`importBook` 的 id 取自时钟，同毫秒的两次保存会互相覆盖。
        // ViewModel 里另有一道重入保护，理由是那里的注释。
        enabled = state.busyLabel == null,
    )
    Spacer(Modifier.height(Space.sm))
    HintText(
        "支持 TXT / Markdown / HTML / EPUB。EPUB 按它自己声明的章节顺序分章，" +
            "其余格式按 CHAPTER I.、Letter 2 这类标题分章，认不出来就按词数均分。",
    )
    Spacer(Modifier.height(Space.sm))
    HintText(
        "导入的书会存进书库，退出再进还在。PDF 暂不支持 —— " +
            "它抽出的文本质量通常很差，扫描件更是完全抽不出文字。",
    )
}

@Composable
private fun PasteTab(
    state: BookshelfViewModel.State,
    onTitle: (String) -> Unit,
    onContent: (String) -> Unit,
) {
    PanelTextField(
        value = state.pasteTitle,
        onValueChange = onTitle,
        placeholder = "标题（可留空）",
    )
    Spacer(Modifier.height(Space.md))
    PanelTextField(
        value = state.pasteContent,
        onValueChange = onContent,
        placeholder = "把正文粘贴到这里…",
        singleLine = false,
        minHeight = 180,
        // 有上界才不会被长正文顶到把别的控件挤出面板；受限之后输入框自己在内部滚动。
        // 「载入阅读器」已经移到面板 footer（见上面），所以这里不再需要它。
        maxHeight = 260,
    )
    Spacer(Modifier.height(Space.sm))
    HintText("语言会自动检测，用来挑朗读音色与显示语言标签。")
}
