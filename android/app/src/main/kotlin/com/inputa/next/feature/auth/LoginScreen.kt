package com.inputa.next.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton

/**
 * P0 登录页。自用测试：直接登录 LingQ 官方账号，不走任何自建服务。
 * 密码只用于这一次 POST，不写日志、不落盘。
 */
@Composable
fun LoginScreen(
    state: LoginViewModel.State,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onToken: (String) -> Unit,
    onTokenMode: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LqColors.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = LqSpace.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(72.dp))
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(LqColors.blueSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LqIcons.Bot,
                contentDescription = null,
                tint = LqColors.green,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(LqSpace.xl))
        Text(
            text = "InputA",
            color = LqColors.textStrong,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(LqSpace.xs))
        Text(
            text = "Sign in to your LingQ account",
            color = LqColors.muted,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(LqSpace.xl))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(LqRadius.control))
                .background(LqColors.surface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ModeChip("Password", selected = !state.tokenMode) { onTokenMode(false) }
            ModeChip("Token", selected = state.tokenMode) { onTokenMode(true) }
        }
        Spacer(Modifier.height(LqSpace.xl))

        if (state.tokenMode) {
            LqField(
                value = state.token,
                onValueChange = onToken,
                label = "LingQ Token",
                imeAction = ImeAction.Done,
            )
            Spacer(Modifier.height(LqSpace.md))
            Text(
                text = "Open LingQ in a desktop browser → F12 → Network → any www.lingq.com/api request → " +
                    "copy everything after \"Authorization: Token\" in the request headers.",
                color = LqColors.muted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LqField(
                value = state.username,
                onValueChange = onUsername,
                label = "Username or email",
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            )
            Spacer(Modifier.height(LqSpace.lg))
            LqField(
                value = state.password,
                onValueChange = onPassword,
                label = "Password",
                password = true,
                imeAction = ImeAction.Done,
            )
        }

        state.error?.let {
            Spacer(Modifier.height(LqSpace.md))
            Text(
                text = it,
                color = LqColors.red,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(LqSpace.xxl))
        PrimaryButton(
            label = when {
                state.loading -> "Signing in…"
                state.tokenMode -> "Sign in with token"
                else -> "Sign in"
            },
            onClick = onSubmit,
            enabled = !state.loading,
        )
        Spacer(Modifier.height(LqSpace.lg))
        Text(
            text = "For personal testing only. Connects directly to LingQ; your password is never stored.",
            color = LqColors.muted,
            fontSize = 11.sp,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(LqRadius.chip))
            .background(if (selected) LqColors.blueSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = LqSpace.xl),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) LqColors.blue else LqColors.muted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun LqField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(Modifier.fillMaxWidth()) {
        Text(text = label, color = LqColors.muted, fontSize = 12.sp)
        Spacer(Modifier.height(LqSpace.sm))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(LqRadius.control))
                .background(LqColors.surface)
                .border(
                    1.dp,
                    if (pressed) LqColors.blue else LqColors.border,
                    RoundedCornerShape(LqRadius.control),
                )
                .padding(horizontal = LqSpace.lg),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = LqColors.text, fontSize = 15.sp),
                cursorBrush = SolidColor(LqColors.blue),
                visualTransformation = if (password) {
                    PasswordVisualTransformation()
                } else {
                    androidx.compose.ui.text.input.VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = imeAction,
                ),
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = if (password) "••••••" else "your@email.com",
                            color = LqColors.muted,
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                },
            )
        }
    }
}
