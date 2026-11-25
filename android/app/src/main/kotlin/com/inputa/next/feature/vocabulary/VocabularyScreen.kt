package com.inputa.next.feature.vocabulary

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.Meaning
import com.inputa.next.core.data.VocabCard
import com.inputa.next.core.design.LqChip
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqEmptyState
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSearchField
import com.inputa.next.core.design.translate
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.LqType
import com.inputa.next.core.design.RawText
import com.inputa.next.core.util.rememberSpeaker

/** LingQ 7 词库：搜索、状态筛选、词详情、写回状态、进入复习。 */
@Composable
fun VocabularyScreen(
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VocabularyViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cards = viewModel.filtered()
    val speaker = rememberSpeaker(viewModel.language)
    val context = LocalContext.current
    val appLanguage = com.inputa.next.core.design.LocalAppLanguage.current

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.notice) {
        val message = state.notice
        if (message != null) {
            Toast.makeText(context, appLanguage.translate(message), Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = LqSpace.xl, end = LqSpace.xl, top = LqSpace.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vocabulary",
                        color = LqColors.textStrong,
                        fontSize = LqType.headline,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = state.total?.let { "All $it terms" } ?: "All ${state.cards.size} terms",
                        color = LqColors.muted,
                        fontSize = 12.sp,
                    )
                }
                if (state.cards.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(LqRadius.pill))
                            .background(LqColors.blueSoft)
                            .clickable(onClick = onOpenReview)
                            .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(LqIcons.Cards, contentDescription = null, tint = LqColors.blue, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Review", color = LqColors.blue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(LqSpace.md))
            LqSearchField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                placeholder = "Search your vocabulary",
                onSubmit = {},
                modifier = Modifier.padding(horizontal = LqSpace.xl),
            )
            Spacer(Modifier.height(LqSpace.md))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(LqSpace.sm),
                contentPadding = PaddingValues(horizontal = LqSpace.xl),
            ) {
                items(VocabFilter.entries) { filter ->
                    LqChip(
                        label = filter.label,
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                    )
                }
            }
            Spacer(Modifier.height(LqSpace.sm))

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> LqLoading(
                        modifier = Modifier.align(Alignment.TopCenter),
                        label = "Loading vocabulary…",
                    )

                    state.error != null && state.cards.isEmpty() -> LqErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    cards.isEmpty() -> LqEmptyState(
                        title = if (state.cards.isEmpty()) "No Inputa words yet" else "No matching terms",
                        message = if (state.cards.isEmpty()) {
                            "Tap a blue word in the Reader to save it here."
                        } else {
                            "Try a different filter or search term."
                        },
                        icon = LqIcons.Bookmarked,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(
                            start = LqSpace.xl,
                            end = LqSpace.xl,
                            bottom = 120.dp,
                        ),
                    ) {
                        items(cards, key = { it.id }) { card ->
                            VocabRow(
                                card = card,
                                onSpeak = { speaker.speak(card.term) },
                                onClick = { viewModel.select(card) },
                            )
                        }
                    }
                }
            }
        }

        state.selected?.let { card ->
            VocabDetailSheet(
                card = card,
                meanings = state.meanings,
                meaningsLoading = state.meaningsLoading,
                translation = state.translation,
                onDismiss = viewModel::dismissSelection,
                onSpeak = { speaker.speak(card.term) },
                onStatus = viewModel::updateStatus,
                onDelete = viewModel::deleteSelected,
            )
        }
    }
}

@Composable
private fun VocabRow(card: VocabCard, onSpeak: () -> Unit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .clickable(onClick = onClick)
            .padding(vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = levelColor(card.level)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    card.status == 0 -> "✓"
                    card.status == -1 -> "–"
                    else -> card.level?.toString() ?: "–"
                },
                color = tint,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(LqSpace.md))
        Column(modifier = Modifier.weight(1f)) {
            RawText(
                text = card.term,
                color = LqColors.textStrong,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = card.translation?.takeIf { it.isNotBlank() } ?: when (card.status) {
                    0 -> "Known"
                    -1 -> "Ignored"
                    else -> "Inputa level ${card.level ?: "–"}"
                },
                color = LqColors.muted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.tags.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Row {
                    card.tags.take(3).forEach { tag ->
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(LqColors.surfaceHigh)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            RawText(tag, color = LqColors.faint, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(LqSpace.md))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LqColors.surface)
                .clickable(onClick = onSpeak),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LqIcons.Volume, contentDescription = "Pronounce", tint = LqColors.muted, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun VocabDetailSheet(
    card: VocabCard,
    meanings: List<Meaning>,
    meaningsLoading: Boolean,
    translation: String?,
    onDismiss: () -> Unit,
    onSpeak: () -> Unit,
    onStatus: (Int) -> Unit,
    onDelete: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LqColors.scrim)
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = LqRadius.sheet, topEnd = LqRadius.sheet))
                .background(LqColors.surface)
                .clickable(interactionSource = noRipple, indication = null) {}
                .navigationBarsPadding()
                .padding(horizontal = LqSpace.xl)
                .padding(top = LqSpace.md, bottom = LqSpace.xl),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .background(LqColors.surfaceHigh),
            )
            Spacer(Modifier.height(LqSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RawText(
                    text = card.term,
                    color = LqColors.textStrong,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(LqSpace.sm))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(LqColors.blueSoft)
                        .clickable(onClick = onSpeak),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LqIcons.Volume, contentDescription = "Pronounce", tint = LqColors.blue, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(LqColors.surfaceHigh)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LqIcons.X, contentDescription = "Close", tint = LqColors.muted, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(LqSpace.md))
            val entries = meanings.take(4)
            val machine = translation?.takeIf { it.isNotBlank() }
            when {
                meaningsLoading && entries.isEmpty() -> SheetText("Looking up…")
                entries.isEmpty() && machine == null -> SheetText("No translation available")
                else -> Column(verticalArrangement = Arrangement.spacedBy(LqSpace.sm)) {
                    entries.forEach { meaning ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(LqRadius.control))
                                .background(LqColors.bgSoft)
                                .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            meaning.locale?.takeIf { it.isNotBlank() }?.let { locale ->
                                Text(
                                    text = locale.uppercase(),
                                    color = LqColors.faint,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(LqSpace.sm))
                            }
                            RawText(meaning.text, color = LqColors.text, fontSize = 14.sp)
                        }
                    }
                    // 译文单独一行，不会被 meanings 的 take(4) 截掉。
                    if (machine != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(LqRadius.control))
                                .background(LqColors.bgSoft)
                                .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RawText(machine, color = LqColors.text, fontSize = 14.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(LqSpace.lg))
            Text(
                text = "STATUS",
                color = LqColors.faint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
            )
            Spacer(Modifier.height(LqSpace.sm))
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..4).forEach { level ->
                    StatusCircle(level, selected = card.status == level) { onStatus(level) }
                }
                Spacer(Modifier.weight(1f))
                StatusChip(LqIcons.Check, "Known", LqColors.green, selected = card.status == 0) {
                    onStatus(5)
                }
                Spacer(Modifier.width(LqSpace.sm))
                StatusChip(LqIcons.EyeOff, "Ignore", LqColors.muted, selected = card.status == -1) {
                    onStatus(-1)
                }
            }
            Spacer(Modifier.height(LqSpace.lg))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LqRadius.control))
                    .background(LqColors.red.copy(alpha = 0.12f))
                    .clickable(onClick = onDelete)
                    .padding(vertical = LqSpace.md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Delete word", color = LqColors.red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SheetText(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .background(LqColors.bgSoft)
            .padding(LqSpace.md),
    ) {
        Text(text, color = LqColors.faint, fontSize = 14.sp)
    }
}

@Composable
private fun StatusCircle(level: Int, selected: Boolean, onClick: () -> Unit) {
    val tint = statusColor(level)
    Box(
        modifier = Modifier
            .padding(end = LqSpace.sm)
            .size(42.dp)
            .clip(CircleShape)
            .background(if (selected) tint.copy(alpha = 0.28f) else tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = if (selected) 0.9f else 0.3f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(level.toString(), color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun StatusChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(LqRadius.pill))
            .background(if (selected) tint.copy(alpha = 0.22f) else LqColors.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun statusColor(level: Int): Color = when (level) {
    1, 2 -> LqColors.blue
    3 -> LqColors.yellow
    else -> LqColors.green
}

private fun levelColor(level: Int?): Color = when {
    level == null -> LqColors.faint
    level <= 2 -> LqColors.blue
    level <= 4 -> LqColors.yellow
    else -> LqColors.green
}
