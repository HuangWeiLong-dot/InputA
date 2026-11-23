package com.inputa.next.feature.common

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inputa.next.core.design.AvatarImage
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton

/** 点击头像打开的编辑面板：本机改名 + 从相册选头像。 */
@Composable
fun ProfileSheet(
    username: String,
    avatarPath: String?,
    onPickAvatar: (Uri) -> Unit,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    bottomPadding: Dp = 104.dp,
) {
    var name by rememberSaveable(username) { mutableStateOf(username) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPickAvatar)
    }
    val noRipple = remember { MutableInteractionSource() }

    BackHandler(onBack = onDismiss)

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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LqSpace.xl)
                .padding(top = LqSpace.md, bottom = bottomPadding),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .background(LqColors.surfaceHigh),
                )
            }
            Spacer(Modifier.height(LqSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Edit Profile",
                        color = LqColors.textStrong,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Shown on your home screen",
                        color = LqColors.muted,
                        fontSize = 12.sp,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(LqColors.surfaceHigh)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(LqIcons.X, contentDescription = "Close", tint = LqColors.muted, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(LqSpace.xl))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clickable {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(LqColors.surfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        AvatarImage(
                            path = avatarPath,
                            fallback = name,
                            modifier = Modifier.fillMaxSize(),
                            fontSize = 34.sp,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(2.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(LqColors.blueStrong)
                            .border(2.dp, LqColors.surface, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            LqIcons.Camera,
                            contentDescription = "Change photo",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(LqSpace.sm))
            Text(
                text = "Tap the photo to choose a new one",
                color = LqColors.muted,
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(LqSpace.xl))
            Text(text = "Display name", color = LqColors.muted, fontSize = 12.sp)
            Spacer(Modifier.height(LqSpace.sm))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(LqRadius.control))
                    .background(LqColors.bg)
                    .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.control))
                    .padding(horizontal = LqSpace.lg),
                contentAlignment = Alignment.CenterStart,
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    singleLine = true,
                    textStyle = TextStyle(color = LqColors.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(LqColors.blue),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (name.isEmpty()) {
                            Text("Your name", color = LqColors.muted, fontSize = 15.sp)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(LqSpace.xl))
            PrimaryButton(
                label = "Save",
                onClick = { onSave(name) },
                enabled = name.isNotBlank(),
            )
        }
    }
}
