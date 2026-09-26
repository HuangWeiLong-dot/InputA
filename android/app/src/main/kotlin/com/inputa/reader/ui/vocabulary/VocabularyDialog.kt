package com.inputa.reader.ui.vocabulary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.inputa.reader.domain.vocab.VocabularyFilter
import com.inputa.reader.domain.vocab.VocabularyItem
import com.inputa.reader.domain.vocab.WordLevels
import com.inputa.reader.ui.components.AlertBox
import com.inputa.reader.ui.components.ButtonSize
import com.inputa.reader.ui.components.Chip
import com.inputa.reader.ui.components.CloseFooter
import com.inputa.reader.ui.components.HintText
import com.inputa.reader.ui.components.IconButton
import com.inputa.reader.ui.components.ListBlock
import com.inputa.reader.ui.components.ListRow
import com.inputa.reader.ui.components.ModalPanel
import com.inputa.reader.ui.components.PanelButton
import com.inputa.reader.ui.components.PanelTextField
import com.inputa.reader.ui.components.PanelTitle
import com.inputa.reader.ui.components.Segmented
import com.inputa.reader.ui.components.Tone
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 词汇库管理。
 *
 * 相对 Web 版**少了导入/导出** —— 备份导入排在第二阶段。表结构已经为它设计好了
 * （笔记与例句独立成表、导入是替换式的一次事务），所以那一步只是加两个按钮与
 * 一个文件选择器，不需要动数据层。
 *
 * 「清空」是破坏性操作，用两步确认：Web 版用的是 `window.confirm`，这里没有
 * 系统级 confirm，所以点第一下先把话说明白、再点第二下才删。
 */
@Composable
fun VocabularyDialog(
    state: VocabularyViewModel.State,
    onFilter: (VocabularyFilter) -> Unit,
    onQuery: (String) -> Unit,
    onMarkMastered: (String) -> Unit,
    onMarkAsNew: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSelectWord: (String) -> Unit,
    onClearLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalPanel(onDismiss = onDismiss) {
        PanelTitle(
            text = "词汇库管理",
            icon = InputaIcons.BookMarked,
            trailing = "${state.counts.active} 未掌握 / ${state.counts.mastered} 已掌握",
        )
        Spacer(Modifier.height(Space.lg))

        PanelTextField(
            value = state.query,
            onValueChange = onQuery,
            placeholder = "搜索收录的单词",
        )
        Spacer(Modifier.height(Space.lg))

        Segmented(
            options = listOf(VocabularyFilter.ACTIVE, VocabularyFilter.MASTERED, VocabularyFilter.ALL),
            selected = state.filter,
            label = { filter ->
                when (filter) {
                    VocabularyFilter.ACTIVE -> "未掌握 ${state.counts.active}"
                    VocabularyFilter.MASTERED -> "已掌握 ${state.counts.mastered}"
                    VocabularyFilter.ALL -> "全部 ${state.counts.total}"
                }
            },
            onSelect = onFilter,
        )
        Spacer(Modifier.height(Space.lg))

        ClearLibraryButton(onClear = onClearLibrary)
        Spacer(Modifier.height(Space.lg))

        // `weight(1f)`（而不是 `fill = false`）是必须的：后者让这个 Box 按内在高度排版，
        // 于是列表会连同它的边框一起撑出面板的高度上限，被裁在面板边缘上。
        // 加上权重之后它只吃剩余高度，列表自己在框内滚动。
        Box(modifier = Modifier.weight(1f, fill = false)) {
            when {
                state.isEmpty && state.libraryIsEmpty -> EmptyState()
                state.isEmpty -> NoMatches()
                else -> VocabularyList(state.items, onMarkMastered, onMarkAsNew, onRemove, onSelectWord)
            }
        }

        Spacer(Modifier.height(Space.lg))
        CloseFooter(onClose = onDismiss)
    }
}

@Composable
private fun VocabularyList(
    items: List<VocabularyItem>,
    onMarkMastered: (String) -> Unit,
    onMarkAsNew: (String) -> Unit,
    onRemove: (String) -> Unit,
    onSelectWord: (String) -> Unit,
) {
    ListBlock {
        itemsIndexed(items, key = { _, item -> item.word }) { index, item ->
            VocabularyRow(
                item = item,
                showDivider = index != items.lastIndex,
                onSelectWord = onSelectWord,
                onMarkMastered = onMarkMastered,
                onMarkAsNew = onMarkAsNew,
                onRemove = onRemove,
            )
        }
    }
}

@Composable
private fun VocabularyRow(
    item: VocabularyItem,
    showDivider: Boolean,
    onSelectWord: (String) -> Unit,
    onMarkMastered: (String) -> Unit,
    onMarkAsNew: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val mastered = !WordLevels.isLevel(item.status)

    ListRow(
        title = item.word,
        titleFontFamily = FontFamily.Serif,
        titleSize = TypeScale.title,
        showDivider = showDivider,
        // 点词查释义。Web 版的词表没有这个入口（那边只有朗读/改状态/移除），
        // 这里是本端新增的：手机上没有「在正文里找到那个词」的便利，
        // 从词表直接看释义是最短的一条路。
        onClick = { onSelectWord(item.word) },
        trailing = {
            // 熟练度胶囊用的是**正文里同一档底色** —— 界面各处对「3 级长什么样」只有一个答案。
            Chip(
                label = if (mastered) "已掌握" else WordLevels.label(item.status).orEmpty(),
                tone = Tone.Level(item.status),
            )
            PanelButton(
                label = if (mastered) "标为生词" else "已掌握",
                onClick = { if (mastered) onMarkAsNew(item.word) else onMarkMastered(item.word) },
                tone = if (mastered) Tone.Accent else Tone.Success,
                size = ButtonSize.Sm,
            )
            IconButton(
                icon = InputaIcons.Trash2,
                contentDescription = "移出词库：${item.word}",
                onClick = { onRemove(item.word) },
                tone = Tone.DangerGhost,
                size = ButtonSize.Sm,
            )
        },
    )
}

/**
 * 清空词汇库，**两步确认**。
 *
 * Web 版用的是 `window.confirm`；Compose 里没有系统级 confirm（`AlertDialog` 是一条
 * 更重的路径）。这里第二下之前会把后果**写成一句话**再让你按 —— 原先只是按钮文案变长
 * （「确认清空整本词汇库」），而一句变长的按钮读起来仍然只是个按钮。
 */
@Composable
private fun ClearLibraryButton(onClear: () -> Unit) {
    var armed by remember { mutableStateOf(false) }

    if (armed) {
        AlertBox(tone = Tone.Danger, icon = InputaIcons.AlertTriangle) {
            Text(
                text = "会清空整本词汇库，连同笔记与例句，且无法撤销。",
                color = LocalThemeTokens.current.danger,
                fontSize = TypeScale.body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                PanelButton(
                    label = "确认清空",
                    onClick = {
                        armed = false
                        onClear()
                    },
                    tone = Tone.DangerSolid,
                    size = ButtonSize.Sm,
                    icon = InputaIcons.Trash2,
                )
                PanelButton(
                    label = "取消",
                    onClick = { armed = false },
                    size = ButtonSize.Sm,
                )
            }
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
            PanelButton(
                label = "清空词汇库",
                onClick = { armed = true },
                tone = Tone.Danger,
                size = ButtonSize.Sm,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = Space.xxl)) {
        Text(
            text = "词汇库还是空的。",
            color = LocalThemeTokens.current.textMain,
            fontSize = TypeScale.bodyLg,
        )
        Spacer(Modifier.height(Space.sm))
        // 把两条规则讲清楚 —— 这是读者理解整个应用行为的地方。
        HintText(
            "在正文里点一个词，它会被记为 5 级「生词」；翻到下一页时，" +
                "这一页没被点过的词会自动记为「已掌握」。",
        )
    }
}

@Composable
private fun NoMatches() {
    HintText(
        text = "没有符合条件的词。",
        modifier = Modifier.padding(vertical = Space.xxl),
    )
}
