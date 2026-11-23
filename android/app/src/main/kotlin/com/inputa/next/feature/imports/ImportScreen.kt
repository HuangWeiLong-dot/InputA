package com.inputa.next.feature.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.design.LqChip
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton

/** 导入课程：URL 或纯文本，走 LingQ 自己的 lessons/import/。 */
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onImported: (LessonSummary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.imported) {
        state.imported?.let(onImported)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
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
            Text("Import Lesson", color = LqColors.textStrong, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(horizontal = LqSpace.xl)) {
            Text(
                text = "Paste a URL or some text. LingQ splits and stores the lesson on your account.",
                color = LqColors.muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(LqSpace.xl))

            ImportLabel("Title")
            ImportField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = "Lesson title",
            )
            Spacer(Modifier.height(LqSpace.lg))

            ImportLabel("Source URL (optional)")
            ImportField(
                value = state.url,
                onValueChange = viewModel::setUrl,
                placeholder = "https://example.com/article",
            )
            Spacer(Modifier.height(LqSpace.lg))

            ImportLabel("Text")
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 170.dp)
                    .clip(RoundedCornerShape(LqRadius.control))
                    .background(LqColors.surface)
                    .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.control))
                    .padding(LqSpace.md),
            ) {
                BasicTextField(
                    value = state.text,
                    onValueChange = viewModel::setText,
                    textStyle = TextStyle(color = LqColors.text, fontSize = 14.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(LqColors.blue),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (state.text.isEmpty()) {
                            Text(
                                "Paste the article or any text you want to read…",
                                color = LqColors.faint,
                                fontSize = 14.sp,
                            )
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(LqSpace.lg))

            ImportLabel("Collection (optional)")
            ImportField(
                value = state.collection,
                onValueChange = viewModel::setCollection,
                placeholder = "Collection title",
            )
            Spacer(Modifier.height(LqSpace.lg))

            ImportLabel("Level")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(LqSpace.sm),
                contentPadding = PaddingValues(vertical = LqSpace.xs),
            ) {
                items((1..6).toList()) { level ->
                    LqChip(
                        label = "Level $level",
                        selected = state.level == level,
                        onClick = { viewModel.setLevel(level) },
                    )
                }
            }

            state.error?.let {
                Spacer(Modifier.height(LqSpace.md))
                Text(it, color = LqColors.red, fontSize = 12.sp)
            }

            Spacer(Modifier.height(LqSpace.xl))
            PrimaryButton(
                label = if (state.loading) "Importing…" else "Import Lesson",
                onClick = viewModel::submit,
                loading = state.loading,
                enabled = !state.loading,
            )
            Spacer(Modifier.height(LqSpace.xxxl))
        }
    }
}

@Composable
private fun ImportLabel(text: String) {
    Text(
        text = text,
        color = LqColors.muted,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = LqSpace.sm),
    )
}

@Composable
private fun ImportField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(LqRadius.control))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.control))
            .padding(horizontal = LqSpace.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = LqColors.text, fontSize = 14.sp),
            cursorBrush = SolidColor(LqColors.blue),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = LqColors.faint, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}
