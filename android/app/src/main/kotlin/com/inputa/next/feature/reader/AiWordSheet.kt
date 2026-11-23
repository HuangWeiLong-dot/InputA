package com.inputa.next.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.next.core.design.LqChip
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.SecondaryButton
import com.inputa.next.core.design.Text

private val AiLanguageOptions = listOf(
    "zh" to "中文",
    "en" to "English",
    "ja" to "日本語",
    "ko" to "한국어",
)

private val AiSectionRegex = Regex(
    "\\[(MEANING|GRAMMAR|USAGE|EXAMPLES)\\]",
    RegexOption.IGNORE_CASE,
)

/** AI 单词精讲面板：语言选择、本地缓存、分区块展示、重新生成。 */
@Composable
internal fun AiWordSheet(
    state: ReaderViewModel.AiWordState,
    onSelectLanguage: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    onOpenAiSettings: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LqColors.scrim)
            .clickable(interactionSource = noRipple, indication = null, onClick = onClose),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(topStart = LqRadius.sheet, topEnd = LqRadius.sheet))
                .background(LqColors.surface)
                .clickable(interactionSource = noRipple, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = LqSpace.xl)
                .padding(top = LqSpace.md, bottom = LqSpace.lg),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(width = 38.dp, height = 4.dp)
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .background(LqColors.surfaceHigh),
                )
            }
            Spacer(Modifier.height(LqSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI explanation",
                        color = LqColors.textStrong,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    RawText(
                        text = state.word,
                        color = LqColors.blue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LqColors.surfaceHigh)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        LqIcons.X,
                        contentDescription = "Close",
                        tint = LqColors.muted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.md))
            Text("Explanation language", color = LqColors.muted, fontSize = 11.sp)
            Spacer(Modifier.height(LqSpace.sm))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
                items(AiLanguageOptions.size) { index ->
                    val (code, label) = AiLanguageOptions[index]
                    LqChip(
                        label = label,
                        selected = state.outputLanguage == code,
                        onClick = { onSelectLanguage(code) },
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.md))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(LqColors.border),
            )
            Spacer(Modifier.height(LqSpace.md))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                when {
                    state.loading && state.text == null -> LqLoading(label = "Generating…")

                    state.text != null -> {
                        val sections = remember(state.text) { parseAiSections(state.text) }
                        if (sections.isEmpty()) {
                            RawText(
                                text = state.text,
                                color = LqColors.text,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                            )
                        } else {
                            sections.forEach { (label, body) ->
                                AiSection(label = label, body = body)
                            }
                        }
                        if (state.loading) {
                            Spacer(Modifier.height(LqSpace.sm))
                            Text("Generating…", color = LqColors.muted, fontSize = 11.sp)
                        }
                        state.error?.let { error ->
                            Spacer(Modifier.height(LqSpace.sm))
                            Text(error, color = LqColors.red, fontSize = 11.sp)
                        }
                        if (state.fromCache) {
                            Spacer(Modifier.height(LqSpace.sm))
                            Text("Cached locally", color = LqColors.faint, fontSize = 10.sp)
                        }
                    }

                    state.error != null -> {
                        Text(state.error, color = LqColors.red, fontSize = 13.sp)
                        if (state.error == "AI is not configured") {
                            Spacer(Modifier.height(LqSpace.lg))
                            PrimaryButton(label = "Open AI settings", onClick = onOpenAiSettings)
                        }
                    }

                    else -> LqLoading(label = "Generating…")
                }
            }

            Spacer(Modifier.height(LqSpace.md))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SecondaryButton(
                    label = "Regenerate",
                    onClick = onRefresh,
                    icon = LqIcons.Refresh,
                )
            }
        }
    }
}

@Composable
private fun AiSection(label: String, body: String) {
    Column(modifier = Modifier.padding(bottom = LqSpace.md)) {
        Text(
            text = label,
            color = LqColors.blue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.height(4.dp))
        RawText(
            text = body,
            color = LqColors.text,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
    }
}

private fun parseAiSections(raw: String): List<Pair<String, String>> {
    val matches = AiSectionRegex.findAll(raw).toList()
    if (matches.isEmpty()) return emptyList()
    return matches.mapIndexedNotNull { index, match ->
        val start = match.range.last + 1
        val end = matches.getOrNull(index + 1)?.range?.first ?: raw.length
        val body = raw.substring(start, end).trim()
        if (body.isEmpty()) {
            null
        } else {
            match.groupValues[1].lowercase().replaceFirstChar { it.uppercase() } to body
        }
    }
}
