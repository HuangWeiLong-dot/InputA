package com.inputa.reader.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.inputa.reader.BuildConfig
import com.inputa.reader.domain.model.AppTheme
import com.inputa.reader.domain.model.FONT_SIZE_RANGE
import com.inputa.reader.domain.repository.BackendHealth
import com.inputa.reader.ui.components.Card
import com.inputa.reader.ui.components.CheckboxRow
import com.inputa.reader.ui.components.HintText
import com.inputa.reader.ui.components.ModalPanel
import com.inputa.reader.ui.components.PanelButton
import com.inputa.reader.ui.components.PanelTextField
import com.inputa.reader.ui.components.PanelTitle
import com.inputa.reader.ui.components.SectionLabel
import com.inputa.reader.ui.components.Segmented
import com.inputa.reader.ui.components.StepperRow
import com.inputa.reader.ui.components.Tone
import com.inputa.reader.ui.icons.InputaIcons
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.theme.Space
import com.inputa.reader.ui.theme.TypeScale

/**
 * 设置面板。
 *
 * 六组设置各自装进一张 [Card]：分组原来只靠「小节标题 + 18dp 空白」分隔，滚起来是一整片
 * 文字；卡片给每组一个边界。卡片之间的间距（[Space.lg]）比卡片内的间距（[Space.md]）大，
 * 这是分组靠版式说话的最省办法。
 */
@Composable
fun SettingsDialog(
    state: SettingsViewModel.State,
    onEditBaseUrl: (String) -> Unit,
    onSaveBaseUrl: () -> Unit,
    onTestConnection: () -> Unit,
    onStepFontSize: (Int) -> Unit,
    onTheme: (AppTheme) -> Unit,
    onStepWordsPerPage: (Int) -> Unit,
    onBionic: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalPanel(onDismiss = onDismiss) {
        PanelTitle(text = "设置", icon = InputaIcons.Settings)
        Spacer(Modifier.height(Space.lg))

        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Card {
                SectionLabel("服务器")
                PanelTextField(
                    value = state.shownBaseUrl,
                    onValueChange = onEditBaseUrl,
                    placeholder = "http://192.168.1.20:8787",
                )
                Spacer(Modifier.height(Space.sm))
                HintText(
                    "词典查询与书籍下载都走这个地址上的后端服务。留空＝用内置的线上后端；" +
                        "开发时改填自己机器：模拟器填 10.0.2.2，真机填开发机的局域网 IP。",
                )
                state.baseUrlError?.let {
                    Spacer(Modifier.height(Space.sm))
                    HintText(it, tone = Tone.Danger)
                }
                Spacer(Modifier.height(Space.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    PanelButton(
                        label = "保存地址",
                        onClick = onSaveBaseUrl,
                        tone = Tone.Accent,
                        icon = InputaIcons.Check,
                    )
                    PanelButton(
                        label = if (state.isTesting) "正在测试…" else "测试连接",
                        onClick = onTestConnection,
                        enabled = !state.isTesting,
                    )
                }
                HealthLine(state.health)
            }

            Spacer(Modifier.height(Space.lg))
            Card {
                SectionLabel("主题")
                Segmented(
                    options = listOf(AppTheme.Light, AppTheme.Sepia, AppTheme.Dark),
                    selected = state.settings.theme,
                    label = { theme ->
                        when (theme) {
                            AppTheme.Light -> "明亮"
                            AppTheme.Sepia -> "羊皮纸"
                            AppTheme.Dark -> "夜间"
                        }
                    },
                    onSelect = onTheme,
                    // 图标与 Web 版一致：太阳 / 咖啡杯 / 月亮。
                    icon = { theme ->
                        when (theme) {
                            AppTheme.Light -> InputaIcons.Sun
                            AppTheme.Sepia -> InputaIcons.Coffee
                            AppTheme.Dark -> InputaIcons.Moon
                        }
                    },
                )
            }

            Spacer(Modifier.height(Space.lg))
            Card {
                SectionLabel("字号")
                StepperRow(
                    value = "${state.settings.fontSizeSp}",
                    onMinus = { onStepFontSize(-2) },
                    onPlus = { onStepFontSize(+2) },
                    minusEnabled = state.settings.fontSizeSp > FONT_SIZE_RANGE.first,
                    plusEnabled = state.settings.fontSizeSp < FONT_SIZE_RANGE.last,
                )

                Spacer(Modifier.height(Space.xl))
                SectionLabel("每页词数")
                StepperRow(
                    value = "${state.settings.wordsPerPage}",
                    onMinus = { onStepWordsPerPage(-20) },
                    onPlus = { onStepWordsPerPage(+20) },
                    minusEnabled = state.settings.wordsPerPage > 50,
                    plusEnabled = state.settings.wordsPerPage < 600,
                )
                Spacer(Modifier.height(Space.sm))
                HintText(
                    "翻页的边界就是「本页未点击的词自动记为已掌握」的边界 —— " +
                        "调大它等于一次处理更多词。默认 220 与 Web 版一致。",
                )
            }

            Spacer(Modifier.height(Space.lg))
            Card {
                SectionLabel("阅读辅助")
                CheckboxRow(
                    label = "仿生阅读",
                    hint = "把每个词的前 40% 加粗，给眼睛一个锚点；中日韩文字整段跳过。",
                    checked = state.settings.bionicEnabled,
                    onToggle = onBionic,
                )
            }
        }

        Spacer(Modifier.height(Space.xl))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // 版本号取自 BuildConfig，而不是再抄一份常量：改 build.gradle.kts 时不会漏。
            Text(
                text = "InputA v${BuildConfig.VERSION_NAME}",
                color = LocalThemeTokens.current.textMuted,
                fontSize = TypeScale.label,
            )
            PanelButton(label = "关闭", onClick = onDismiss, tone = Tone.Accent)
        }
    }
}

@Composable
private fun HealthLine(health: BackendHealth) {
    // 只有当用户真的测过一次（有结论）才显示这一行 —— 打开面板就报「连不上」是打扰。
    if (health.service == null && health.error == null && !health.ok) return

    Spacer(Modifier.height(Space.md))
    val (text, tone) = when {
        !health.ok -> "连不上：${health.error ?: "无响应"}" to Tone.Danger
        health.dictionaryAvailable == false ->
            "后端在跑，但它读不到词典库（data/stardict.db）" to Tone.Highlight
        health.dictionaryAvailable == null -> "后端在跑（老版本，未报告词典库状态）" to Tone.Success
        else -> "后端就绪，离线词典可用" to Tone.Success
    }
    HintText(text, tone = tone)
}
