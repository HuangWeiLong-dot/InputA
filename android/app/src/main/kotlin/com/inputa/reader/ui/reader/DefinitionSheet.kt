package com.inputa.reader.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.inputa.reader.domain.dict.DefinitionPresentation
import com.inputa.reader.domain.dict.DefinitionPresenter
import com.inputa.reader.domain.model.WordStatus
import com.inputa.reader.domain.vocab.WordLevels
import com.inputa.reader.ui.components.AlertBox
import com.inputa.reader.ui.components.AppIcon
import com.inputa.reader.ui.components.ButtonSize
import com.inputa.reader.ui.components.Card
import com.inputa.reader.ui.components.Chip
import com.inputa.reader.ui.components.PanelButton
import com.inputa.reader.ui.components.SectionLabel
import com.inputa.reader.ui.components.Tone
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Radius
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 释义面板。
 *
 * 展示逻辑全部在领域层的 [DefinitionPresenter] 里（纯函数、有单测），这里只负责把
 * 算好的东西摆出来。Web 版把这两件事混在一个 453 行的组件里，而那边没有组件渲染
 * 覆盖 —— 所以把它拆出来是这次移植少数几处「覆盖度净增」之一。
 *
 * 面板是**底部抽屉**而不是居中对话框：Web 版在窄屏（`lg` 以下）本来就是底部抽屉
 * （`max-h-[86dvh]`），只在宽屏才右侧停靠。手机上就是一个抽屉。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefinitionSheet(
    word: String,
    status: WordStatus?,
    lookup: WordLookupState,
    sentence: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSetStatus: (WordStatus) -> Unit,
    onMarkMastered: () -> Unit,
    onRemoveWord: () -> Unit,
) {
    val tokens = LocalThemeTokens.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 上限 86% 屏高，与 Web 版的上限（`max-h-[86dvh]`）对齐：面板不该吃掉整个屏幕，
    // 否则读者会失去「我在读哪一段」的上下文。
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.86f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // 拖拽把手与面板上缘的圆角都来自 `MaterialTheme.shapes`（见 InputaTheme）——
        // 这里**不要**再传 `shape =`，那会把这些覆盖掉。原先正是那个显式参数让面板
        // 一直画成直角，看上去像是覆盖没生效。
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = tokens.bgSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xxl)
                .padding(bottom = Space.xxl),
        ) {
            StatusBar(status = status, lookup = lookup)
            Spacer(Modifier.height(Space.lg))

            // 词头与熟练度选择器合成一张卡片：它们是同一个问题（「这个词是什么、我熟不熟」），
            // 而下面的释义是另一个问题。
            Card {
                Text(
                    text = word,
                    color = tokens.textStrong,
                    fontFamily = FontFamily.Serif,
                    fontSize = TypeScale.headword,
                )
                Spacer(Modifier.height(Space.xl))
                LevelPicker(status = status, onPick = onSetStatus)
            }

            Spacer(Modifier.height(Space.xl))
            Body(lookup = lookup, word = word, status = status, onRetry = onRetry)
            if (sentence.isNotBlank()) {
                Spacer(Modifier.height(Space.xl))
                SectionLabel("当前所在句子")
                Text(
                    text = sentence,
                    color = tokens.textMuted,
                    fontSize = TypeScale.body,
                    fontStyle = FontStyle.Italic,
                )
            }
            Spacer(Modifier.height(Space.xl))
            Actions(
                status = status,
                onMarkMastered = onMarkMastered,
                onSetStatus = onSetStatus,
                onRemoveWord = onRemoveWord,
            )
        }
    }
}

@Composable
private fun StatusBar(status: WordStatus?, lookup: WordLookupState) {
    val tokens = LocalThemeTokens.current
    val source = (lookup as? WordLookupState.Loaded)?.source?.label

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // **未标记与已掌握都用中性底色，不用 5 级色阶** —— 正文里未收录的词确实被涂成
        // 生词色（那是「只关于显示」的规则），但徽章上写的是「未标记」这个词，配生词底色
        // 会自相矛盾：读者会以为词库里已经有了记录。Web 版也是这么分的。
        // 这条差别现在就落在 `Tone.Level(null)` 上，见 colorsOf。
        Chip(label = DefinitionPresenter.statusLabel(status), tone = Tone.Level(status))
        if (source != null) {
            Text(text = "来源 $source", color = tokens.textMuted, fontSize = TypeScale.label)
        }
    }
}

/**
 * 5 段熟练度选择器。每一段的底色**就是**正文里那一级的颜色 —— 读者在面板里挑的颜色
 * 与正文里看到的完全一致，不用去猜「3 级长什么样」。
 *
 * 选中的那一档除了加粗描边还画一个对勾：光靠 1px 与 2px 的描边差，在色阶本来就有深浅
 * 的五块颜色里几乎看不出来。
 *
 * 点一下就是一次明确的用户写入（覆盖已有状态）。
 */
@Composable
private fun LevelPicker(status: WordStatus?, onPick: (WordStatus) -> Unit) {
    val tokens = LocalThemeTokens.current

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        WordLevels.LEVELS.forEach { level ->
            val selected = status == level
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(Radius.chipShape)
                        .background(tokens.levelBackground(level), Radius.chipShape)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) tokens.textStrong else tokens.borderColor,
                            shape = Radius.chipShape,
                        )
                        .clickable { onPick(level) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        AppIcon(
                            InputaIcons.Check,
                            contentDescription = null,
                            tint = tokens.textStrong,
                            size = 18.dp,
                        )
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = WordLevels.label(level).orEmpty(),
                    color = if (selected) tokens.textStrong else tokens.textMuted,
                    fontSize = TypeScale.label,
                    fontWeight = if (selected) TypeScale.labelWeight else null,
                )
            }
        }
    }
}

@Composable
private fun Body(lookup: WordLookupState, word: String, status: WordStatus?, onRetry: () -> Unit) {
    when (lookup) {
        WordLookupState.Idle -> Unit
        WordLookupState.Loading -> LoadingBlock()
        WordLookupState.Unavailable -> UnavailableBlock(onRetry)
        WordLookupState.NotFound -> NotFoundBlock()
        is WordLookupState.Loaded -> {
            val presentation = remember(lookup, word, status) {
                DefinitionPresenter.present(lookup.entry, lookup.source, status, clickedWord = word)
            }
            LoadedBlock(presentation)
        }
    }
}

@Composable
private fun LoadingBlock() {
    val tokens = LocalThemeTokens.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            color = tokens.accent,
            strokeWidth = 2.dp,
        )
        Spacer(Modifier.width(Space.lg))
        Column {
            Text(text = "正在查询词典…", color = tokens.textMain, fontSize = TypeScale.bodyLg)
            // 把降级链念出来：读者觉得慢的时候能知道它在依次试哪几个来源。
            Text(
                text = "本地词库 → dictionaryapi.dev → Wiktionary → Datamuse",
                color = tokens.textMuted,
                fontSize = TypeScale.label,
            )
        }
    }
}

/**
 * 「服务不可用」与「没收录」是**两件事**，面板必须分开说。
 *
 * 前者是临时的、重试有意义；后者是确定结论，重试只是浪费时间。这条区分是词典降级链
 * 的核心语义，界面这里是它的出口 —— 而它能不能被正确区分，取决于传输层有没有把
 * 404 与 5xx 分开（见 OkHttpProviderRequester）。
 */
@Composable
private fun UnavailableBlock(onRetry: () -> Unit) {
    val tokens = LocalThemeTokens.current
    AlertBox(tone = Tone.Highlight, icon = InputaIcons.AlertTriangle) {
        Text(text = "词典服务暂时无法访问", color = tokens.highlightText, fontSize = TypeScale.bodyLg)
        Text(
            text = "本地词库、dictionaryapi.dev、Wiktionary 与 Datamuse 都没能给出结果。",
            color = tokens.highlightText,
            fontSize = TypeScale.label,
        )
        // 按钮画在琥珀色的告警面**里面**，所以用 OnHighlight（底色是页面底色）——
        // 透明底会让它整个消失在告警面里。
        PanelButton(
            label = "重新查询",
            onClick = onRetry,
            tone = Tone.OnHighlight,
            size = ButtonSize.Sm,
            icon = InputaIcons.RefreshCw,
        )
    }
}

@Composable
private fun NotFoundBlock() {
    // 与「服务不可用」区分开：那是临时的、重试有意义；这是确定结论，重试只是浪费时间。
    // 所以这里没有动作，底色也是中性的。
    AlertBox(tone = Tone.Neutral) {
        Text(text = "词典均未收录该词", color = LocalThemeTokens.current.textMuted, fontSize = TypeScale.bodyLg)
    }
}

@Composable
private fun LoadedBlock(presentation: DefinitionPresentation) {
    val tokens = LocalThemeTokens.current

    Column(modifier = Modifier.fillMaxWidth()) {
        presentation.phonetic?.takeIf { it.isNotBlank() }?.let {
            Text(text = "/$it/", color = tokens.textMuted, fontSize = TypeScale.bodyLg)
            Spacer(Modifier.height(Space.lg))
        }

        presentation.headword?.let {
            Text(text = "词库词条 $it", color = tokens.accent, fontSize = TypeScale.body)
            Spacer(Modifier.height(Space.sm))
        }
        presentation.lemma?.let { lemma ->
            val suffix = presentation.inflection?.let { part -> "（$part）" }.orEmpty()
            Text(text = "原形 $lemma$suffix", color = tokens.accent, fontSize = TypeScale.body)
            Spacer(Modifier.height(Space.sm))
        }

        if (presentation.examTags.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                presentation.examTags.forEach { tag ->
                    Chip(label = tag, tone = Tone.AccentSoft)
                }
            }
            Spacer(Modifier.height(Space.lg))
        }

        if (presentation.hasTranslation) {
            SectionLabel("中文释义")
            presentation.translationLines.forEach { line ->
                Text(text = line, color = tokens.textMain, fontSize = TypeScale.bodyStrong)
            }
            val meta = buildList {
                presentation.posLine?.let(::add)
                addAll(presentation.metaBits)
            }
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                Text(text = meta.joinToString(" ｜ "), color = tokens.textMuted, fontSize = TypeScale.label)
            }
            Spacer(Modifier.height(Space.lg))
        }

        presentation.formLine?.let { formLine ->
            SectionLabel("词形变化")
            Text(text = formLine, color = tokens.textMain, fontSize = TypeScale.bodyLg)
            Spacer(Modifier.height(Space.lg))
        }

        if (presentation.meanings.isNotEmpty()) {
            // 有中文释义时补一个「英文释义」的分隔标题，否则读者不知道下面换了语言。
            if (presentation.hasTranslation) SectionLabel("英文释义")
            presentation.meanings.forEach { meaning ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 来源没给词性时**不显示徽章** —— 印一个字面的 "unknown" 什么也没告诉读者。
                    meaning.partOfSpeechBadge?.let {
                        Text(text = it, color = tokens.accent, fontSize = TypeScale.body)
                        Spacer(Modifier.width(Space.md))
                    }
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = tokens.borderColor,
                        thickness = 1.dp,
                    )
                }
                Spacer(Modifier.height(Space.sm))
                meaning.definitions.forEachIndexed { index, item ->
                    Text(
                        text = "${index + 1}. ${item.definition}",
                        color = tokens.textMain,
                        fontSize = TypeScale.bodyStrong,
                    )
                    item.example?.let { example ->
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            text = example,
                            color = tokens.textMuted,
                            fontSize = TypeScale.body,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                    Spacer(Modifier.height(Space.sm))
                }
                Spacer(Modifier.height(Space.md))
            }
        }
    }
}

/**
 * 底部的动作。
 *
 * 「标为生词」与「标记为已掌握」是互斥的入口：当前是已掌握时显示前者（把它改回来），
 * 否则显示后者 —— 与 Web 版一致。**移出词库一直在**，它恢复的是「未标记」，
 * 不是删除笔记。
 */
@Composable
private fun Actions(
    status: WordStatus?,
    onMarkMastered: () -> Unit,
    onSetStatus: (WordStatus) -> Unit,
    onRemoveWord: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status == WordStatus.MASTERED) {
            PanelButton(
                label = "标为生词",
                onClick = { onSetStatus(WordStatus.L5) },
                tone = Tone.Accent,
                icon = InputaIcons.BookMarked,
            )
        } else {
            PanelButton(
                label = "标记为已掌握",
                onClick = onMarkMastered,
                tone = Tone.Success,
                icon = InputaIcons.Check,
            )
        }
        PanelButton(
            label = "移出词库",
            onClick = onRemoveWord,
            tone = Tone.Danger,
            icon = InputaIcons.Trash2,
        )
    }
}
