package com.inputa.next.feature.review

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.Meaning
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLevelBadge
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqProgressBar
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton
import com.inputa.next.core.design.RawText
import com.inputa.next.core.design.SecondaryButton
import com.inputa.next.core.design.translate

/** 闪卡复习：真实 cards + 状态升降 + review 回写。 */
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LqSpace.xl, vertical = LqSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LqColors.surface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.ArrowLeft, contentDescription = "Back", tint = LqColors.text, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(LqSpace.md))
            Text(
                text = "Review",
                color = LqColors.textStrong,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (!state.done && state.cards.isNotEmpty()) {
                Text(
                    text = "Skip",
                    color = LqColors.blue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .clickable(onClick = viewModel::skip)
                        .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
                )
            }
        }

        when {
            state.loading -> LqLoading(label = "Preparing review…")

            state.done -> DoneState(
                reviewed = state.cards.size,
                onBack = onBack,
                onAgain = viewModel::load,
            )

            state.current != null -> {
                val card = state.current!!
                Column(modifier = Modifier.padding(horizontal = LqSpace.xl)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${state.index + 1} / ${state.cards.size}",
                            color = LqColors.muted,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                        )
                        LqLevelBadge(level = card.level)
                    }
                    Spacer(Modifier.height(LqSpace.sm))
                    LqProgressBar(
                        progress = (state.index + 1).toFloat() / state.cards.size,
                        height = 4.dp,
                    )
                    Spacer(Modifier.height(LqSpace.xl))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(LqColors.surface)
                                .border(1.dp, LqColors.border, RoundedCornerShape(28.dp))
                                .clickable { if (!state.revealed) viewModel.reveal() }
                                .padding(horizontal = LqSpace.xl, vertical = LqSpace.xxxl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RawText(
                                text = card.term,
                                color = LqColors.textStrong,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                            if (card.tags.isNotEmpty()) {
                                Spacer(Modifier.height(LqSpace.sm))
                                Text(
                                    text = card.tags.take(3).joinToString(" · "),
                                    color = LqColors.faint,
                                    fontSize = 11.sp,
                                )
                            }
                            Spacer(Modifier.height(LqSpace.xl))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(LqColors.border),
                            )
                            Spacer(Modifier.height(LqSpace.lg))
                            if (!state.revealed) {
                                Text(
                                    text = "Tap to reveal",
                                    color = LqColors.muted,
                                    fontSize = 13.sp,
                                )
                            } else {
                                val meanings = state.meanings.take(3)
                                val translation = state.translation?.takeIf { it.isNotBlank() }
                                if (meanings.isEmpty() && translation == null) {
                                    Text(
                                        text = if (state.meaningsLoading) "Looking up…" else "No saved meaning",
                                        color = LqColors.faint,
                                        fontSize = 14.sp,
                                    )
                                } else {
                                    meanings.forEach { meaning ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 3.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            RawText(
                                                text = meaning.text,
                                                color = LqColors.text,
                                                fontSize = 17.sp,
                                                textAlign = TextAlign.Center,
                                            )
                                            meaning.locale?.takeIf { it.isNotBlank() }?.let { locale ->
                                                Text(
                                                    text = locale.uppercase(),
                                                    color = LqColors.faint,
                                                    fontSize = 9.sp,
                                                )
                                            }
                                        }
                                    }
                                    // 译文单独一行，不被 meanings 的 take(3) 截掉。
                                    if (translation != null) {
                                        Spacer(Modifier.height(6.dp))
                                        RawText(
                                            text = translation,
                                            color = LqColors.text,
                                            fontSize = 17.sp,
                                            textAlign = TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(LqSpace.lg))
                    if (!state.revealed) {
                        PrimaryButton(label = "Show answer", onClick = viewModel::reveal)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(LqSpace.md)) {
                            Box(modifier = Modifier.weight(1f)) {
                                SecondaryButton(
                                    label = "Don't know",
                                    onClick = { viewModel.grade(known = false) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(LqRadius.pill))
                                        .background(LqColors.greenStrong)
                                        .clickable { viewModel.grade(known = true) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Know",
                                        color = Color(0xFF07130B),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(LqSpace.xl))
                }
            }
        }
    }
}

@Composable
private fun DoneState(reviewed: Int, onBack: () -> Unit, onAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LqSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(LqColors.knownStart, LqColors.knownEnd)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(LqIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(LqSpace.xl))
        Text(
            text = "All caught up!",
            color = LqColors.textStrong,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(LqSpace.xs))
        Text(
            text = if (reviewed > 0) "You reviewed $reviewed Inputa words." else "No Inputa words are due right now.",
            color = LqColors.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(LqSpace.xl))
        PrimaryButton(label = "Back to vocabulary", onClick = onBack)
        Spacer(Modifier.height(LqSpace.sm))
        Text(
            text = "Review again",
            color = LqColors.blue,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(LqRadius.pill))
                .clickable(onClick = onAgain)
                .padding(horizontal = LqSpace.lg, vertical = LqSpace.sm),
        )
    }
}
