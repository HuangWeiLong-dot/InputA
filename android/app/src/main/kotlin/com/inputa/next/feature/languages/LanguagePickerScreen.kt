package com.inputa.next.feature.languages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.lessonLevelLabel
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqEmptyState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqSpace
import com.inputa.next.feature.home.HomeViewModel
import com.inputa.next.feature.home.LanguageRow
import kotlin.math.roundToInt

/** 头像点进来的语言切换页；写入本地选择后回到首页重新拉数据。 */
@Composable
fun LanguagePickerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state.languages.isEmpty()) viewModel.load(refresh = true)
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
            Column {
                Text(
                    text = "Learning Languages",
                    color = LqColors.textStrong,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Switch what you're studying",
                    color = LqColors.muted,
                    fontSize = 12.sp,
                )
            }
        }

        LevelSlider(
            level = state.languageLevel,
            onLevel = viewModel::setLanguageLevel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LqSpace.xl)
                .padding(bottom = LqSpace.md),
        )

        when {
            state.languagesLoading && state.languages.isEmpty() -> LqLoading(label = "Loading languages…")

            state.languages.isEmpty() -> LqEmptyState(
                title = "No languages found",
                message = "Choose a language to start reading.",
                icon = LqIcons.Globe,
                actionLabel = "Retry",
                onAction = { viewModel.load(refresh = true) },
            )

            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = LqSpace.xl,
                    end = LqSpace.xl,
                    bottom = LqSpace.xxxl,
                ),
            ) {
                items(state.languages, key = { it.code }) { language ->
                    LanguageRow(
                        language = language,
                        selected = language.code == state.languageCode,
                        onClick = {
                            viewModel.selectLanguage(language.code)
                            onBack()
                        },
                    )
                }
            }
        }
    }
}

/** 当前学习语言的等级滑块：0 = 全部等级，1–6 = LingQ 等级。 */
@Composable
private fun LevelSlider(
    level: Int?,
    onLevel: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sliderValue by remember(level) { mutableStateOf((level ?: 0).toFloat()) }
    val selectedLevel = sliderValue.roundToInt().coerceIn(0, 6).takeIf { it in 1..6 }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Course level",
                color = LqColors.textStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = lessonLevelLabel(selectedLevel),
                color = LqColors.blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = {
                val selected = sliderValue.roundToInt().coerceIn(0, 6)
                onLevel(selected.takeIf { it in 1..6 })
            },
            valueRange = 0f..6f,
            steps = 5,
            colors = SliderDefaults.colors(
                thumbColor = LqColors.blue,
                activeTrackColor = LqColors.blue,
                inactiveTrackColor = LqColors.surfaceHigh,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("All", color = LqColors.faint, fontSize = 10.sp)
            Text("Level 6", color = LqColors.faint, fontSize = 10.sp)
        }
    }
}
