package com.inputa.next.feature.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton
import com.inputa.next.core.design.RawText

/** 狐狸聊天：使用用户自己配置的 OpenAI 兼容接口，不再走 LingQ Lynx。 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.sending) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
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
                .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
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
            Column(modifier = Modifier.weight(1f)) {
                Text("AI Tutor", color = LqColors.textStrong, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (state.configured) state.model else "Connect your own AI endpoint",
                    color = LqColors.muted,
                    fontSize = 11.sp,
                )
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LqColors.surface)
                    .clickable(onClick = viewModel::openSetup),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.Settings, contentDescription = "AI settings", tint = LqColors.text, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(LqSpace.xs))
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(LqColors.greenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.Bot, contentDescription = null, tint = LqColors.green, modifier = Modifier.size(20.dp))
            }
        }

        if (state.showSetup) {
            AiSetupPanel(
                state = state,
                onBaseUrl = viewModel::setBaseUrl,
                onApiKey = viewModel::setApiKey,
                onModel = viewModel::setModel,
                onSave = viewModel::saveConfig,
                onCancel = viewModel::closeSetup,
            )
        } else {
            if (state.messages.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LqSpace.xl, vertical = LqSpace.xl),
                ) {
                    Text(
                        text = "Ask your AI anything about your target language.",
                        color = LqColors.text,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(LqSpace.md))
                    Suggestion("Explain a sentence from my lesson") { viewModel.send(it) }
                    Suggestion("Give me three example sentences") { viewModel.send(it) }
                    Suggestion("Correct my writing") { viewModel.send(it) }
                    Suggestion("Quiz me on my Inputa words") { viewModel.send(it) }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = LqSpace.xl, vertical = LqSpace.md),
                verticalArrangement = Arrangement.spacedBy(LqSpace.sm),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
                if (state.error != null) {
                    item {
                        Text(
                            text = state.error.orEmpty(),
                            color = LqColors.red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = LqSpace.sm),
                        )
                    }
                }
            }

            Composer(
                value = state.input,
                sending = state.sending,
                onValueChange = viewModel::setInput,
                onSend = { viewModel.send() },
            )
        }
    }
}

@Composable
private fun AiSetupPanel(
    state: ChatViewModel.State,
    onBaseUrl: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onModel: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LqSpace.xl)
            .padding(top = LqSpace.md, bottom = LqSpace.xxl),
    ) {
        Text(
            text = "Connect your AI",
            color = LqColors.textStrong,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(LqSpace.xs))
        Text(
            text = "Works with any OpenAI-compatible /chat/completions endpoint: OpenAI, DeepSeek, Ollama and one-api are all supported.",
            color = LqColors.muted,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(LqSpace.xl))
        AiField(
            label = "Base URL",
            value = state.baseUrl,
            onValueChange = onBaseUrl,
            placeholder = "https://api.openai.com/v1",
            keyboardType = KeyboardType.Uri,
        )
        Spacer(Modifier.height(LqSpace.md))
        AiField(
            label = "API Key",
            value = state.apiKey,
            onValueChange = onApiKey,
            placeholder = "sk-...",
            password = true,
        )
        Spacer(Modifier.height(LqSpace.md))
        AiField(
            label = "Model",
            value = state.model,
            onValueChange = onModel,
            placeholder = "gpt-4o-mini",
        )
        state.error?.let {
            Spacer(Modifier.height(LqSpace.md))
            Text(it, color = LqColors.red, fontSize = 12.sp)
        }
        Spacer(Modifier.height(LqSpace.xl))
        PrimaryButton(label = "Save AI settings", onClick = onSave)
        if (state.configured) {
            Spacer(Modifier.height(LqSpace.md))
            Text(
                text = "Cancel",
                color = LqColors.muted,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = LqSpace.lg, vertical = LqSpace.xs),
            )
        }
    }
}

@Composable
private fun AiField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = LqColors.muted, fontSize = 12.sp)
        Spacer(Modifier.height(LqSpace.sm))
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
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (password) KeyboardType.Password else keyboardType,
                    imeAction = ImeAction.Next,
                ),
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
}

@Composable
private fun Suggestion(text: String, onClick: (String) -> Unit) {
    Box(
        modifier = Modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .background(LqColors.surface)
            .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.control))
            .clickable { onClick(text) }
            .padding(horizontal = LqSpace.md, vertical = LqSpace.md),
    ) {
        Text(text, color = LqColors.text, fontSize = 13.sp)
    }
}

@Composable
private fun MessageBubble(message: ChatViewModel.Message) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (message.fromUser) 18.dp else 4.dp,
                        bottomEnd = if (message.fromUser) 4.dp else 18.dp,
                    ),
                )
                .background(if (message.fromUser) LqColors.blueStrong else LqColors.surface)
                .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
        ) {
            RawText(
                text = message.text.ifBlank { if (message.fromUser) "" else "…" },
                color = if (message.fromUser) Color.White else LqColors.text,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    sending: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(LqRadius.pill))
                .background(LqColors.surface)
                .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.pill))
                .padding(horizontal = LqSpace.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(color = LqColors.text, fontSize = 14.sp),
                cursorBrush = SolidColor(LqColors.blue),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text("Chat with your AI…", color = LqColors.muted, fontSize = 14.sp)
                    }
                    inner()
                },
            )
        }
        Spacer(Modifier.width(LqSpace.sm))
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (value.isBlank() || sending) LqColors.surfaceHigh else LqColors.blueStrong)
                .clickable(enabled = value.isNotBlank() && !sending, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LqIcons.ArrowRight,
                contentDescription = "Send",
                tint = if (value.isBlank() || sending) LqColors.faint else Color.White,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
