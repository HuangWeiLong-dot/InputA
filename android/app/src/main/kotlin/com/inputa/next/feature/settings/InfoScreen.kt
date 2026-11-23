package com.inputa.next.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace

enum class InfoKind {
    Method,
    Help,
}

private data class InfoSection(val title: String, val body: String)

private val MethodSections = listOf(
    InfoSection(
        "Input, not memorisation",
        "You learn by reading and listening to content you mostly understand. " +
            "Keep the material slightly above your level and let context do the work.",
    ),
    InfoSection(
        "Known Words and Inputas",
        "White words are known. Yellow words are Inputas you are still learning, with a level from 1 to 4. " +
            "Blue words are new: tap one to see meanings and save it as an Inputa.",
    ),
    InfoSection(
        "Move words forward",
        "Reviewing an Inputa raises its level. When it reaches the top, mark it known and keep going. " +
            "Ignoring a word hides it from your learning counts.",
    ),
    InfoSection(
        "Small steps every day",
        "A steady daily goal beats occasional long sessions. " +
            "Set a comfortable word target in Settings and let the streak build itself.",
    ),
)

private val HelpSections = listOf(
    InfoSection(
        "Reading",
        "Tap any word for meanings and controls. " +
            "Font size, line spacing and the default reading mode live under Settings.",
    ),
    InfoSection(
        "Words and review",
        "Word status is stored on this device. " +
            "Review sessions draw from your Inputas; you can limit their size or shuffle them in Review Settings.",
    ),
    InfoSection(
        "Audio and AI",
        "Word pronunciation uses LingQ audio with a system voice fallback. " +
            "The AI Tutor uses the provider, key and model you configure in the chat screen.",
    ),
    InfoSection(
        "Account and profile",
        "Sign-in uses your LingQ account. " +
            "The name and photo shown on the home screen are local customisations and do not change your LingQ profile.",
    ),
    InfoSection(
        "Reminders",
        "Daily reading reminders are scheduled on this device. " +
            "You can change the time or turn them off at any time in Settings.",
    ),
)

@Composable
fun InfoScreen(
    kind: InfoKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = if (kind == InfoKind.Method) "The LingQ Method" else "Help & Support"
    val subtitle = if (kind == InfoKind.Method) {
        "How learning works in this app"
    } else {
        "Answers to the common questions"
    }
    val sections = if (kind == InfoKind.Method) MethodSections else HelpSections

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = LqSpace.xl,
            end = LqSpace.xl,
            top = LqSpace.md,
            bottom = LqSpace.xxxl,
        ),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                Column {
                    Text(
                        text = title,
                        color = LqColors.textStrong,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(text = subtitle, color = LqColors.muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(LqSpace.xl))
        }

        items(sections.size) { index ->
            val section = sections[index]
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LqRadius.card))
                    .background(LqColors.surface)
                    .padding(LqSpace.xl),
            ) {
                Text(
                    text = section.title,
                    color = LqColors.textStrong,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(LqSpace.sm))
                Text(text = section.body, color = LqColors.muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(LqSpace.md))
        }
    }
}
