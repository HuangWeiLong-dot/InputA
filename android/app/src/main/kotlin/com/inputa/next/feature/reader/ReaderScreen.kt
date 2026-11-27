package com.inputa.next.feature.reader

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import com.inputa.next.core.design.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.Meaning
import com.inputa.next.core.data.ReaderSentence
import com.inputa.next.core.data.ReaderToken
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.LqErrorState
import com.inputa.next.core.design.LqIcons
import com.inputa.next.core.design.LqLoading
import com.inputa.next.core.design.LqRadius
import com.inputa.next.core.design.translate
import com.inputa.next.core.design.LqSpace
import com.inputa.next.core.design.PrimaryButton
import com.inputa.next.core.design.RawText
import com.inputa.next.core.media.RepeatMode
import com.inputa.next.core.util.Speaker
import com.inputa.next.core.util.rememberSpeaker
import kotlin.math.roundToInt

/**
 * LingQ 7 阅读器：
 * - 正文以 sentence.text 原样渲染，服务端 token 只负责词边界和状态；
 * - Page / Sentence / Scroll 三种模式；
 * - 点词弹窗、真实释义、状态写回、课程音频、完成课程。
 */
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenVocabulary: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fontSize by viewModel.fontSize.collectAsStateWithLifecycle()
    val lineHeight by viewModel.lineHeight.collectAsStateWithLifecycle()
    val paragraphSpacing by viewModel.paragraphSpacing.collectAsStateWithLifecycle()
    val wordSpacing by viewModel.wordSpacing.collectAsStateWithLifecycle()
    val autoTts by viewModel.autoTts.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val speaker = rememberSpeaker(viewModel.language)
    // 中日韩/泰文的字形几乎占满整个字身，色块要按它们量高度，否则字头会被切掉。
    val fullHeightGlyphs = remember(viewModel.language) {
        usesFullHeightGlyphs(viewModel.language)
    }
    val context = LocalContext.current
    val appLanguage = com.inputa.next.core.design.LocalAppLanguage.current
    var showSettings by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }
    var immersive by remember { mutableStateOf(false) }
    var tokenAnchor by remember { mutableStateOf<Rect?>(null) }
    // 播放前只请求一次通知权限：拒绝后不再打扰，音频照常播放，只是通知栏不展示。
    var notificationPermissionAsked by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                appLanguage.translate("Notification permission denied. Playback controls will not show in the notification shade."),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val requestNotificationPermission: () -> Unit = {
        if (!notificationPermissionAsked &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionAsked = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val listState = rememberLazyListState()
    val contentLayer = rememberGraphicsLayer()
    val blurredLayer = rememberGraphicsLayer()
    val blurRadius = with(LocalDensity.current) { 18.dp.toPx() }

    LaunchedEffect(state.notice) {
        val message = state.notice
        if (message != null) {
            Toast.makeText(context, appLanguage.translate(message), Toast.LENGTH_SHORT).show()
            viewModel.consumeNotice()
        }
    }

    LaunchedEffect(state.lesson?.id) {
        // 换课时回到顶部，避免继承上一课的滚动位置。
        listState.scrollToItem(0)
    }

    LaunchedEffect(state.page, state.sentenceIndex, state.mode) {
        viewModel.syncProgress()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopAudio() }
    }

    val scrollProgress by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            if (total <= 0) 0f else (listState.firstVisibleItemIndex + 1f) / total
        }
    }
    val pageCount = viewModel.pageCount()
    val totalSentences = state.sentences.size.coerceAtLeast(1)
    val progress = when (state.mode) {
        ReaderMode.Scroll -> scrollProgress
        ReaderMode.Page -> (state.page + 1).toFloat() / pageCount
        ReaderMode.Sentence -> (state.sentenceIndex + 1).toFloat() / totalSentences
    }
    val audioActive = playerState.entry?.lessonId == state.lesson?.id
    val audioPlaying = audioActive && playerState.playing
    // 音频已经加载出时长后才显示进度条；暂停时保留当前位置。
    val audioProgress = playerState.entry
        ?.takeIf { it.lessonId == state.lesson?.id && playerState.durationMs > 0 }
        ?.let { (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f) }
    val lessonLingqCount = remember(state.sentences, state.wordStates) {
        state.sentences.asSequence()
            .flatMap { it.tokens.asSequence() }
            .map { it.text.lowercase() }
            .distinct()
            .count { state.wordStates[it] is WordState.Lingq }
    }
    // 本课里还没有任何状态的新词；右上角入口点击后进入翻转卡片复习。
    val lessonNewWordCount = remember(state.sentences, state.wordStates) {
        state.sentences.asSequence()
            .flatMap { it.tokens.asSequence() }
            .map { it.text.lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .count { state.wordStates[it] == null }
    }

    Box(modifier = modifier.fillMaxSize().background(LqColors.bg)) {
        // 阅读正文先录进 layer，底栏用它做实时模糊，文字会透出来。
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    blurredLayer.record { drawLayer(contentLayer) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        blurredLayer.renderEffect = BlurEffect(blurRadius, blurRadius, TileMode.Decal)
                    }
                    drawLayer(contentLayer)
                },
        ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!immersive) {
                ReaderTopBar(
                    progress = progress,
                    newWordsCount = lessonNewWordCount,
                    onNewWords = onOpenReview,
                    onBack = onBack,
                    onSettings = { showSettings = true },
                    onFinish = viewModel::finishLesson,
                    onOpenChat = onOpenChat,
                    onFullscreen = { immersive = true },
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    state.loading -> LqLoading(
                        modifier = Modifier.align(Alignment.Center),
                        label = "Loading lesson…",
                    )

                    state.error != null -> LqErrorState(
                        message = state.error.orEmpty(),
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    state.sentences.isEmpty() -> LqErrorState(
                        message = "This lesson has no readable text yet. If it was just imported, LingQ may still be generating it — wait a moment and retry.",
                        onRetry = viewModel::load,
                        modifier = Modifier.align(Alignment.Center),
                    )

                    else -> ReaderBody(
                        mode = state.mode,
                        sentences = state.sentences,
                        page = state.page,
                        sentenceIndex = state.sentenceIndex,
                        wordStates = state.wordStates,
                        selection = state.selection,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        paragraphSpacing = paragraphSpacing,
                        wordSpacing = wordSpacing,
                        fullHeightGlyphs = fullHeightGlyphs,
                        listState = listState,
                        bottomPadding = if (immersive) 24.dp else 110.dp,
                        onPrevious = viewModel::previous,
                        onNext = viewModel::next,
                        onPageSelected = viewModel::setPage,
                        onPageBreaks = viewModel::setPageBreaks,
                        onToken = { sentenceIndex, tokenIndex, anchor ->
                            val token = state.sentences
                                .getOrNull(sentenceIndex)
                                ?.tokens
                                ?.getOrNull(tokenIndex)
                            if (autoTts && token != null) viewModel.pronounce(token.text)
                            tokenAnchor = anchor
                            viewModel.select(sentenceIndex, tokenIndex)
                        },
                    )
                }
            }
        }
        }

        if (!immersive && state.sentences.isNotEmpty()) {
            if (modeMenu) {
                ModeMenuOverlay(
                    mode = state.mode,
                    onSelect = { mode ->
                        viewModel.setMode(mode)
                        modeMenu = false
                    },
                    onDismiss = { modeMenu = false },
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = LqSpace.sm),
            ) {
                if (audioActive) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = LqSpace.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RepeatModeMenu(
                            mode = playerState.repeatMode,
                            onSelect = viewModel::setRepeatMode,
                        )
                        Spacer(Modifier.width(LqSpace.sm))
                        if (audioProgress != null) {
                            ReaderAudioProgress(
                                progress = audioProgress,
                                onSeek = viewModel::seekAudio,
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.width(LqSpace.sm))
                        VolumeMenu(
                            volume = playerState.volume,
                            onVolume = viewModel::setVolume,
                        )
                    }
                }
                ReaderMediaBar(
                    playing = audioPlaying,
                    mode = state.mode,
                    lingqCount = lessonLingqCount,
                    onPlay = {
                        requestNotificationPermission()
                        viewModel.playPauseAudio()
                    },
                    onMode = { modeMenu = true },
                    onCards = onOpenVocabulary,
                    onExpand = { immersive = true },
                    onAi = onOpenChat,
                    backdrop = blurredLayer,
                )
            }
        }

        if (immersive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(LqSpace.md),
            ) {
                CircleButton(
                    icon = LqIcons.ChevronDown,
                    description = "Exit fullscreen",
                    onClick = { immersive = false },
                    background = LqColors.surface.copy(alpha = 0.8f),
                )
            }
        }

        state.selection?.let { selection ->
            val sentence = state.sentences.getOrNull(selection.sentenceIndex)
            val token = sentence?.tokens?.getOrNull(selection.tokenIndex)
            if (sentence != null && token != null) {
                WordPopup(
                    token = token,
                    sentence = sentence,
                    anchor = tokenAnchor,
                    speaker = speaker,
                    onPronounce = { viewModel.pronounce(token.text) },
                    onExplain = { viewModel.explainWord(token, sentence.text) },
                    meanings = state.meanings,
                    meaningsLoading = state.meaningsLoading,
                    currentLevel = wordLevel(token, state.wordStates),
                    onDismiss = {
                        tokenAnchor = null
                        viewModel.dismissSelection()
                    },
                    onState = { wordState ->
                        tokenAnchor = null
                        viewModel.markWord(token, wordState)
                    },
                )
            }
        }

        if (showSettings) {
            ReaderSettingsSheet(
                fontSize = fontSize,
                lineHeight = lineHeight,
                paragraphSpacing = paragraphSpacing,
                wordSpacing = wordSpacing,
                onFontSize = viewModel::setFontSize,
                onLineHeight = viewModel::setLineHeight,
                onParagraphSpacing = viewModel::setParagraphSpacing,
                onWordSpacing = viewModel::setWordSpacing,
                onDismiss = { showSettings = false },
            )
        }

        state.aiWord?.let { aiState ->
            AiWordSheet(
                state = aiState,
                onSelectLanguage = viewModel::setAiOutputLanguage,
                onRefresh = viewModel::refreshAiWord,
                onClose = viewModel::closeAiWord,
                onOpenAiSettings = {
                    viewModel.closeAiWord()
                    onOpenChat()
                },
            )
        }

        if (state.finished) {
            LessonCompleteOverlay(
                onBackToLesson = {
                    viewModel.dismissFinished()
                    onBack()
                },
                onKeepReading = viewModel::dismissFinished,
            )
        }
    }
}

@Composable
private fun ReaderProgress(progress: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.height(16.dp)) {
        val height = 5.dp.toPx()
        val centerY = size.height / 2f
        val radius = CornerRadius(height / 2f)
        drawRoundRect(
            color = LqColors.surfaceHigh,
            topLeft = Offset(0f, centerY - height / 2f),
            size = Size(size.width, height),
            cornerRadius = radius,
        )
        val fill = (size.width * progress.coerceIn(0f, 1f)).coerceAtLeast(height)
        drawRoundRect(
            color = LqColors.green,
            topLeft = Offset(0f, centerY - height / 2f),
            size = Size(fill, height),
            cornerRadius = radius,
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(fill.coerceIn(0f, size.width), centerY),
        )
    }
}

@Composable
private fun ReaderTopBar(
    progress: Float,
    newWordsCount: Int,
    onNewWords: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onFinish: () -> Unit,
    onOpenChat: () -> Unit,
    onFullscreen: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = LqSpace.md, vertical = LqSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(icon = LqIcons.ArrowLeft, description = "Back", onClick = onBack)
        Spacer(Modifier.width(LqSpace.md))
        ReaderProgress(progress = progress, modifier = Modifier.weight(1f))
        if (newWordsCount > 0) {
            Spacer(Modifier.width(LqSpace.sm))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .background(LqColors.blueSoft)
                    .clickable(onClick = onNewWords)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    LqIcons.Sparkles,
                    contentDescription = null,
                    tint = LqColors.blue,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = newWordsCount.toString(),
                    color = LqColors.blue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(2.dp))
                Text("new", color = LqColors.blue, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.width(LqSpace.sm))
        Box {
            CircleButton(
                icon = LqIcons.MoreHorizontal,
                description = "More",
                onClick = { menuOpen = true },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                containerColor = LqColors.surfaceHigh,
            ) {
                ReaderMenuItem(
                    icon = LqIcons.Settings,
                    label = "Reading settings",
                    onClick = {
                        menuOpen = false
                        onSettings()
                    },
                )
                ReaderMenuItem(
                    icon = LqIcons.Cards,
                    label = "Review words",
                    onClick = {
                        menuOpen = false
                        onNewWords()
                    },
                )
                ReaderMenuItem(
                    icon = LqIcons.Bot,
                    label = "AI Tutor",
                    onClick = {
                        menuOpen = false
                        onOpenChat()
                    },
                )
                ReaderMenuItem(
                    icon = LqIcons.Expand,
                    label = "Fullscreen",
                    onClick = {
                        menuOpen = false
                        onFullscreen()
                    },
                )
                ReaderMenuItem(
                    icon = LqIcons.Check,
                    label = "Mark lesson complete",
                    tint = LqColors.green,
                    onClick = {
                        menuOpen = false
                        onFinish()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReaderMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = LqColors.text,
) {
    DropdownMenuItem(
        text = { Text(label, color = tint, fontSize = 14.sp) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        },
        onClick = onClick,
    )
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = LqColors.text,
    background: Color = LqColors.surface,
    size: Int = 38,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size((size * 0.5f).dp))
    }
}

@Composable
private fun ReaderBody(
    mode: ReaderMode,
    sentences: List<ReaderSentence>,
    page: Int,
    sentenceIndex: Int,
    wordStates: Map<String, WordState>,
    selection: WordSelection?,
    fontSize: Float,
    lineHeight: Float,
    paragraphSpacing: Float,
    wordSpacing: Float,
    fullHeightGlyphs: Boolean,
    listState: androidx.compose.foundation.lazy.LazyListState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPageSelected: (Int) -> Unit,
    onPageBreaks: (List<IntRange>) -> Unit,
    onToken: (Int, Int, Rect) -> Unit,
) {
    val drag = remember(mode) { floatArrayOf(0f) }
    // 段间距由设置控制，句子间距取同值，段与段再加一半留白。
    val sentenceGap = paragraphSpacing.dp
    val paragraphGap = (paragraphSpacing * 0.6f).dp
    // Page 模式交给 HorizontalPager 跟手翻页，这里只给 Sentence 模式做滑动切换。
    val swipeModifier = if (mode == ReaderMode.Sentence) {
        Modifier.pointerInput(mode) {
            detectHorizontalDragGestures(
                onDragStart = { drag[0] = 0f },
                onHorizontalDrag = { change, amount ->
                    change.consume()
                    drag[0] += amount
                },
                onDragEnd = {
                    if (drag[0] <= -80f) onNext() else if (drag[0] >= 80f) onPrevious()
                    drag[0] = 0f
                },
                onDragCancel = { drag[0] = 0f },
            )
        }
    } else {
        Modifier
    }

    Box(modifier = Modifier.fillMaxSize().then(swipeModifier)) {
    when (mode) {
        ReaderMode.Scroll -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = LqSpace.xxl,
                end = LqSpace.xxl,
                top = LqSpace.lg,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(sentenceGap),
        ) {
            items(sentences, key = { it.index }) { sentence ->
                SentenceText(
                    sentence = sentence,
                    wordStates = wordStates,
                    selection = selection,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    wordSpacing = wordSpacing,
                    fullHeightGlyphs = fullHeightGlyphs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = if (sentence.paragraphStart) paragraphGap else 0.dp),
                    onToken = { tokenIndex, anchor -> onToken(sentence.index, tokenIndex, anchor) },
                )
            }
        }

        ReaderMode.Page -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val textStyle = LocalTextStyle.current.copy(
                fontSize = fontSize.sp,
                lineHeight = (fontSize * lineHeight).sp,
            )
            val widthPx = with(density) { (maxWidth - LqSpace.xxl * 2).roundToPx() }
            val heightPx = with(density) { (maxHeight - LqSpace.lg - bottomPadding).roundToPx() }
            val gapPx = with(density) { sentenceGap.toPx() }
            val paragraphGapPx = with(density) { paragraphGap.toPx() }
            // 版面参数一变就重新量：分页结果必须和真实绘制高度一致。
            // 切页只跟排版有关；词状态只改颜色，不影响行高，别把它写进 key，
            // 否则每点一个新词都会整页重新量一遍，翻页器跟着闪。
            val breaks = remember(
                sentences, fontSize, lineHeight, paragraphSpacing, wordSpacing, widthPx, heightPx,
            ) {
                measurePageBreaks(
                    sentences = sentences,
                    wordStates = wordStates,
                    measurer = measurer,
                    style = textStyle,
                    widthPx = widthPx,
                    heightPx = heightPx,
                    gapPx = gapPx,
                    paragraphGapPx = paragraphGapPx,
                    wordSpacing = wordSpacing,
                )
            }
            LaunchedEffect(breaks) { onPageBreaks(breaks) }
            val pagerState = rememberPagerState(
                initialPage = page.coerceIn(0, breaks.lastIndex.coerceAtLeast(0)),
                pageCount = { breaks.size.coerceAtLeast(1) },
            )
            // 手势翻页时把当前页同步回 ViewModel；外部页码变化时滚到对应页。
            LaunchedEffect(pagerState, breaks) {
                snapshotFlow { pagerState.currentPage }.collect { index -> onPageSelected(index) }
            }
            LaunchedEffect(page, breaks) {
                val target = page.coerceIn(0, breaks.lastIndex.coerceAtLeast(0))
                if (pagerState.currentPage != target) pagerState.scrollToPage(target)
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { currentPage ->
                val range = breaks.getOrNull(currentPage)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = LqSpace.xxl)
                        .padding(top = LqSpace.lg, bottom = bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(sentenceGap),
                ) {
                    if (range != null) {
                        val end = (range.last + 1).coerceAtMost(sentences.size)
                        sentences.subList(range.first.coerceAtLeast(0), end).forEach { sentence ->
                            SentenceText(
                                sentence = sentence,
                                wordStates = wordStates,
                                selection = selection,
                                fontSize = fontSize,
                                lineHeight = lineHeight,
                                wordSpacing = wordSpacing,
                                fullHeightGlyphs = fullHeightGlyphs,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = if (sentence.paragraphStart) paragraphGap else 0.dp),
                                onToken = { tokenIndex, anchor -> onToken(sentence.index, tokenIndex, anchor) },
                            )
                        }
                    }
                }
            }
        }

        ReaderMode.Sentence -> {
            val sentence = sentences.getOrNull(sentenceIndex)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = LqSpace.xxl)
                    .padding(top = LqSpace.xxl, bottom = bottomPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (sentence != null) {
                    Text(
                        text = "${sentenceIndex + 1} / ${sentences.size}",
                        color = LqColors.faint,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = LqSpace.lg),
                    )
                    SentenceText(
                        sentence = sentence,
                        wordStates = wordStates,
                        selection = selection,
                        fontSize = fontSize + 3f,
                        lineHeight = lineHeight,
                        wordSpacing = wordSpacing,
                        fullHeightGlyphs = fullHeightGlyphs,
                        modifier = Modifier.fillMaxWidth(),
                        onToken = { tokenIndex, anchor -> onToken(sentence.index, tokenIndex, anchor) },
                    )
                }
            }
        }
    }
    }
}

private class RenderedSentence(
    val text: AnnotatedString,
    val tokenRanges: List<IntRange>,
    val highlights: List<TokenHighlight>,
)

/** 词底色 / 等级标记：交给 Text 背后的 Canvas 画圆角块，避免 AnnotatedString 只能画直角。 */
private data class TokenHighlight(
    val range: IntRange,
    /** 填充色；虚线档不填色，这里为 Transparent。 */
    val color: Color,
    /** LingQ 4 档不填色，只画一条中性虚线。 */
    val dashed: Boolean = false,
)

/**
 * LingQ 7 深色词色阶，逐值取自 LingQ 7 截图：
 * - 生词 / 未收录＝深蓝底，正文始终白字；
 * - LingQ 1/2/3＝三档黄底，同样白字，熟练度越高越贴近背景；
 * - LingQ 4＝不填色，只留一条浅灰虚线；
 * - 已掌握 / 已忽略＝不画标记。
 * 正文文字一律白色，不与色块同色 —— 同色会让字和底色糊在一起。
 */
private val WordChipBlue = Color(0xFF223754)
private val WordChipYellows = listOf(
    Color(0xFF675227), // LingQ 1
    Color(0xFF4B3A1C), // LingQ 2
    Color(0xFF2F2412), // LingQ 3
)
/** LingQ 的金色：选中描边、等级按钮的数字化。 */
private val WordGold = Color(0xFFFBB718)
private val WordDashedLine = Color(0xFF9FA0A4)

/** 词的熟练度：只认本机记录，LingQ 接口不再提供词库数据。 */
private fun wordLevel(token: ReaderToken, wordStates: Map<String, WordState>): Int? {
    val local = wordStates[token.text.lowercase()]
    return (local as? WordState.Lingq)?.level?.takeIf { it in 1..4 }
}

/**
 * 正文渲染：逐词拼 AnnotatedString，词状态只影响颜色/圆角色块/虚线，
 * 不改变排版；tokenRanges 供点击命中与选中描边。
 */
private fun renderSentence(
    sentence: ReaderSentence,
    wordStates: Map<String, WordState>,
    wordSpacing: Float,
): RenderedSentence {
    val builder = AnnotatedString.Builder()
    val tokenRanges = MutableList(sentence.tokens.size) { IntRange.EMPTY }
    val highlights = ArrayList<TokenHighlight>()
    var cursor = 0
    // 只给词间空隙加 letterSpacing，词内部字符间距不受影响。
    fun appendGap(text: String) {
        if (text.isEmpty()) return
        if (wordSpacing > 0f) {
            builder.pushStyle(SpanStyle(letterSpacing = wordSpacing.sp))
            builder.append(text)
            builder.pop()
        } else {
            builder.append(text)
        }
    }
    sentence.tokens.forEachIndexed { index, token ->
        val start = token.start.coerceIn(0, sentence.text.length)
        val end = token.end.coerceIn(start, sentence.text.length)
        if (end <= start || start < cursor) return@forEachIndexed
        if (start > cursor) appendGap(sentence.text.substring(cursor, start))
        val local = wordStates[token.text.lowercase()]
        val level = (local as? WordState.Lingq)?.level?.coerceIn(1, 4)
        // 正文文字始终白：蓝/黄只出现在色块上，字与底色同色会糊成一片。
        val known = local == WordState.Known
        val ignored = local == WordState.Ignored
        val fresh = local == null
        val color = if (ignored) LqColors.faint else LqColors.text
        val chip = when {
            fresh -> WordChipBlue
            level != null && level < 4 -> WordChipYellows[level - 1]
            else -> Color.Transparent
        }
        builder.pushStyle(
            SpanStyle(
                color = color,
                fontWeight = if (fresh || level != null || known) FontWeight.Medium else FontWeight.Normal,
            ),
        )
        val startOffset = builder.length
        builder.append(sentence.text.substring(start, end))
        val annotatedEnd = builder.length
        tokenRanges[index] = startOffset until annotatedEnd
        builder.pop()
        if (annotatedEnd > startOffset) {
            when {
                chip != Color.Transparent ->
                    highlights += TokenHighlight(startOffset until annotatedEnd, chip)
                level == 4 ->
                    highlights += TokenHighlight(startOffset until annotatedEnd, Color.Transparent, dashed = true)
            }
        }
        cursor = end
    }
    if (cursor < sentence.text.length) appendGap(sentence.text.substring(cursor))
    return RenderedSentence(builder.toAnnotatedString(), tokenRanges, highlights)
}

/** 一支出色块要画的内容：颜色、是否虚线、逐行矩形。 */
private data class ChipShape(val color: Color, val dashed: Boolean, val rects: List<Rect>)

/** 一个字符区间在某一行的横向范围。色块与选中描边共用，保证两者严丝合缝。 */
private data class LineSpan(
    val line: Int,
    val left: Float,
    val right: Float,
)

/**
 * 把字符区间拆成逐行的横向范围。跨行的词按行拆开，不会框住两行之间的空白；
 * RTL 段落从行边界补齐。
 */
private fun lineSpansOf(result: TextLayoutResult, range: IntRange): List<LineSpan> {
    if (range.isEmpty()) return emptyList()
    val first = result.getBoundingBox(range.first)
    val last = result.getBoundingBox(range.last)
    val startLine = result.getLineForOffset(range.first)
    val endLine = result.getLineForOffset(range.last)
    val spans = ArrayList<LineSpan>(endLine - startLine + 1)
    for (line in startLine..endLine) {
        val rtl = result.getParagraphDirection(line) == ResolvedTextDirection.Rtl
        val left: Float
        val right: Float
        when {
            startLine == endLine -> {
                left = minOf(first.left, last.left)
                right = maxOf(first.right, last.right)
            }

            line == startLine -> {
                left = if (rtl) result.getLineLeft(line) else first.left
                right = if (rtl) first.right else result.getLineRight(line)
            }

            line == endLine -> {
                left = if (rtl) last.left else result.getLineLeft(line)
                right = if (rtl) result.getLineRight(line) else last.right
            }

            else -> {
                left = result.getLineLeft(line)
                right = result.getLineRight(line)
            }
        }
        spans += LineSpan(line, left, right)
    }
    return spans
}

/**
 * 算出字符区间的色块矩形（逐行）。
 *
 * 横向用排版盒（advance）而不是另找字体去量字形：排版盒来自**真正渲染这段文字**的
 * 同一个排版引擎，增删、字号、字重、字体回退全都已经算在里面。之前用 `Paint` 量
 * 字形边界，但 `Paint` 解析出的字体与 Compose 实际使用的系统字体不是同一套
 * （假名字宽差 20% 以上），色块于是整体左移、右边还盖住字。
 * 排版盒左右各加**同样**的留白，观感就是左右等宽。
 */
private fun chipRectsOf(
    result: TextLayoutResult,
    range: IntRange,
    ascentPx: Float,
    descentPx: Float,
    padPx: Float,
): List<Rect> = lineSpansOf(result, range).map { span ->
    val baseline = result.getLineBaseline(span.line)
    Rect(
        left = span.left - padPx,
        top = baseline - ascentPx,
        right = span.right + padPx,
        bottom = baseline + descentPx,
    )
}

/**
 * 按真实排版高度切页：每页都不超过可视区域，翻页整页替换，页内不再滚动。
 * 宽高还没量出来时退回一句一页。
 */
private fun measurePageBreaks(
    sentences: List<ReaderSentence>,
    wordStates: Map<String, WordState>,
    measurer: TextMeasurer,
    style: TextStyle,
    widthPx: Int,
    heightPx: Int,
    gapPx: Float,
    paragraphGapPx: Float,
    wordSpacing: Float,
): List<IntRange> {
    if (sentences.isEmpty()) return listOf(0..-1)
    if (widthPx <= 0 || heightPx <= 0) return sentences.indices.map { it..it }
    val pages = ArrayList<IntRange>()
    var pageStart = 0
    var used = 0f
    sentences.forEachIndexed { index, sentence ->
        val measured = measurer.measure(
            text = renderSentence(sentence, wordStates, wordSpacing).text,
            style = style,
            constraints = Constraints(maxWidth = widthPx),
        )
        val height = measured.size.height.toFloat()
        val gap = if (index == pageStart) {
            if (sentence.paragraphStart) paragraphGapPx else 0f
        } else {
            gapPx + if (sentence.paragraphStart) paragraphGapPx else 0f
        }
        if (index > pageStart && used + gap + height > heightPx) {
            pages += pageStart until index
            pageStart = index
            used = height
        } else {
            used += gap + height
        }
    }
    if (pageStart < sentences.size) pages += pageStart until sentences.size
    return pages
}

/** 字形本身的光学上下沿：相对基线向上 [ascentPx]、向下 [descentPx]，单位 px。 */
private class GlyphBand(val ascentPx: Float, val descentPx: Float)

/** 中日韩/泰文的字形几乎占满字身，需要用满格字来量色块高度。 */
private fun usesFullHeightGlyphs(language: String): Boolean {
    val code = language.lowercase()
    return code.startsWith("zh") || code.startsWith("ja") || code.startsWith("ko") || code.startsWith("th")
}

/**
 * 色块纵向的上下沿，与 LingQ 7 一致：以基线为锚，向上取字体 ascent、向下取 descent。
 *
 * 只量字形会太贴：Android 部分系统字体的 ascent 只到大写字头，色块上缘会顶住字母。
 * 所以这里给字体度量加了下限（拉丁 0.82em、满格文字 0.95em），上限 0.95em，
 * 既保证大写/汉字上方有 LingQ 那种呼吸位，又不会被过大的行盒撑开。
 */
private const val BAND_ASCENT_MIN_LATIN = 0.82f
private const val BAND_ASCENT_MIN_FULL = 0.95f
private const val BAND_ASCENT_MAX = 0.95f
private const val BAND_DESCENT_MIN = 0.24f
private const val BAND_DESCENT_MAX = 0.31f

private fun measureGlyphBand(fullHeight: Boolean, fontSizePx: Float): GlyphBand {
    val metrics = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSizePx
        typeface = Typeface.DEFAULT
    }.fontMetrics
    val ascentMin = fontSizePx * if (fullHeight) BAND_ASCENT_MIN_FULL else BAND_ASCENT_MIN_LATIN
    val ascent = (-metrics.ascent).coerceIn(ascentMin, fontSizePx * BAND_ASCENT_MAX)
    val descent = metrics.descent.coerceIn(
        fontSizePx * BAND_DESCENT_MIN,
        fontSizePx * BAND_DESCENT_MAX,
    )
    return GlyphBand(ascentPx = ascent, descentPx = descent)
}

@Composable
private fun SentenceText(
    sentence: ReaderSentence,
    wordStates: Map<String, WordState>,
    selection: WordSelection?,
    fontSize: Float,
    lineHeight: Float,
    wordSpacing: Float,
    fullHeightGlyphs: Boolean,
    modifier: Modifier = Modifier,
    onToken: (Int, Rect) -> Unit,
) {
    // 只按句子缓存排版结果：点词会改词库状态、重建 AnnotatedString，但字形位置不变。
    // 之前把 wordStates 写进 key，点一下新词就把 layout 清成 null，色块要等下一次
    // onTextLayout 才回来 —— 这就是点击时色块闪一下的原因。
    var layout by remember(sentence) { mutableStateOf<TextLayoutResult?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val rendered = remember(sentence, wordStates, wordSpacing) {
        renderSentence(sentence, wordStates, wordSpacing)
    }
    // 色块高度跟着字形走，与行距、词距无关；圆角随色块大小一起缩放。
    val density = LocalDensity.current
    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val band = remember(fullHeightGlyphs, fontSizePx) {
        measureGlyphBand(fullHeightGlyphs, fontSizePx)
    }
    val cornerPx = remember(band, density) {
        ((band.ascentPx + band.descentPx) * 0.15f).coerceIn(
            with(density) { 2.dp.toPx() },
            with(density) { 5.dp.toPx() },
        )
    }
    // 4 档（LingQ 4）的中性虚线。
    val dashWidthPx = with(density) { 1.dp.toPx() }
    val dashEffect = remember(density) {
        PathEffect.dashPathEffect(
            with(density) { floatArrayOf(2.dp.toPx(), 2.dp.toPx()) },
        )
    }
    // 横向留白：拉丁文外扩一点，字不会被色块边缘切到；中日韩的字身自带留白，
    // 只留一点点把相邻词的色块分开。
    val padX = fontSizePx * if (fullHeightGlyphs) 0.02f else 0.05f
    // 选中词：LingQ 的金色实线框，色块外留 2dp 空隙，与色块明确分开。
    val outlineGapPx = with(density) { 2.dp.toPx() }
    val outlineWidthPx = with(density) { 2.dp.toPx() }
    val selectedRange = if (selection?.sentenceIndex == sentence.index) {
        rendered.tokenRanges.getOrNull(selection.tokenIndex)?.takeUnless { it.isEmpty() }
    } else {
        null
    }
    // 色块几何只在排版/词库状态变化时算一次，绘制时直接画缓存。
    val chipShapes = remember(layout, rendered, band, padX) {
        val result = layout ?: return@remember emptyList<ChipShape>()
        rendered.highlights.mapNotNull { highlight ->
            val rects = chipRectsOf(
                result = result,
                range = highlight.range,
                ascentPx = band.ascentPx,
                descentPx = band.descentPx,
                padPx = padX,
            )
            if (rects.isEmpty()) null else ChipShape(highlight.color, highlight.dashed, rects)
        }
    }
    val selectionRects = remember(layout, selectedRange, band, padX) {
        val result = layout
        if (result == null || selectedRange == null) {
            emptyList()
        } else {
            chipRectsOf(
                result = result,
                range = selectedRange,
                ascentPx = band.ascentPx,
                descentPx = band.descentPx,
                padPx = padX,
            )
        }
    }

    Text(
        text = rendered.text,
        color = LqColors.text,
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeight).sp,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind {
                val radius = CornerRadius(cornerPx)
                chipShapes.forEach { chip ->
                    chip.rects.forEach { rect ->
                        if (chip.dashed) {
                            // 虚线画在色块的下缘：g/y/p 的下伸部也包含在色块里，不会被划掉。
                            drawLine(
                                color = WordDashedLine,
                                start = Offset(rect.left, rect.bottom),
                                end = Offset(rect.right, rect.bottom),
                                strokeWidth = dashWidthPx,
                                pathEffect = dashEffect,
                            )
                        } else {
                            drawRoundRect(
                                color = chip.color,
                                topLeft = rect.topLeft,
                                size = rect.size,
                                cornerRadius = radius,
                            )
                        }
                    }
                }
                // 选中词的实线边框：与色块同源几何，向外让出 2dp 空隙、描 2dp 金色线。
                if (selectionRects.isNotEmpty()) {
                    val inset = outlineGapPx + outlineWidthPx / 2f
                    val grow = Size(inset * 2, inset * 2)
                    selectionRects.forEach { rect ->
                        drawRoundRect(
                            color = WordGold,
                            topLeft = rect.topLeft - Offset(inset, inset),
                            size = Size(rect.width + grow.width, rect.height + grow.height),
                            cornerRadius = CornerRadius(cornerPx + inset),
                            style = Stroke(width = outlineWidthPx),
                        )
                    }
                }
            }
            .pointerInput(rendered.text, sentence.index) {
                detectTapGestures { offset ->
                    val result = layout ?: return@detectTapGestures
                    val charOffset = result.getOffsetForPosition(offset)
                    val index = rendered.tokenRanges.indexOfFirst { charOffset in it }
                    if (index >= 0) {
                        val range = rendered.tokenRanges[index]
                        val anchor = if (range.isEmpty()) {
                            Rect(origin.x, origin.y, origin.x, origin.y)
                        } else {
                            val first = result.getBoundingBox(range.first)
                            val last = result.getBoundingBox(range.last)
                            val line = result.getLineForOffset(range.first)
                            Rect(
                                left = origin.x + minOf(first.left, last.left),
                                top = origin.y + result.getLineTop(line),
                                right = origin.x + maxOf(first.right, last.right),
                                bottom = origin.y + result.getLineBottom(line),
                            )
                        }
                        onToken(index, anchor)
                    }
                }
            },
    )
}

/**
 * 循环模式按钮：关闭 / 单曲循环 / 列表循环，弹出菜单选择。
 * 图标随模式切换，当前项高亮。
 */
@Composable
private fun RepeatModeMenu(
    mode: RepeatMode,
    onSelect: (RepeatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LqColors.surfaceHigh)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when (mode) {
                    RepeatMode.Off -> LqIcons.RepeatOff
                    RepeatMode.One -> LqIcons.RepeatOne
                    RepeatMode.All -> LqIcons.Repeat
                },
                contentDescription = when (mode) {
                    RepeatMode.Off -> "Loop off"
                    RepeatMode.One -> "Repeat one"
                    RepeatMode.All -> "Repeat all"
                },
                tint = if (mode == RepeatMode.Off) LqColors.muted else LqColors.blue,
                modifier = Modifier.size(17.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = LqColors.surfaceHigh,
        ) {
            RepeatModeMenuItem(
                icon = LqIcons.RepeatOff,
                label = "Loop off",
                selected = mode == RepeatMode.Off,
            ) {
                expanded = false
                onSelect(RepeatMode.Off)
            }
            RepeatModeMenuItem(
                icon = LqIcons.RepeatOne,
                label = "Repeat one",
                selected = mode == RepeatMode.One,
            ) {
                expanded = false
                onSelect(RepeatMode.One)
            }
            RepeatModeMenuItem(
                icon = LqIcons.Repeat,
                label = "Repeat all",
                selected = mode == RepeatMode.All,
            ) {
                expanded = false
                onSelect(RepeatMode.All)
            }
        }
    }
}

@Composable
private fun RepeatModeMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = if (selected) LqColors.blue else LqColors.text,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) LqColors.blue else LqColors.text,
                modifier = Modifier.size(17.dp),
            )
        },
        trailingIcon = {
            if (selected) {
                Icon(LqIcons.Check, contentDescription = null, tint = LqColors.blue, modifier = Modifier.size(15.dp))
            }
        },
        onClick = onClick,
    )
}

/** 应用内音量控制：弹出横向滑条，0–100%，与系统媒体音量独立。 */
@Composable
private fun VolumeMenu(
    volume: Float,
    onVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(LqColors.surfaceHigh)
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = LqIcons.Volume,
                contentDescription = "Volume",
                tint = if (volume > 0f) LqColors.text else LqColors.muted,
                modifier = Modifier.size(17.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = LqColors.surfaceHigh,
            modifier = Modifier.width(180.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = LqSpace.md, vertical = LqSpace.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Volume",
                        color = LqColors.text,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${(volume * 100).roundToInt()}%",
                        color = LqColors.muted,
                        fontSize = 12.sp,
                    )
                }
                Slider(
                    value = volume,
                    onValueChange = onVolume,
                    colors = SliderDefaults.colors(
                        activeTrackColor = LqColors.blue,
                        inactiveTrackColor = LqColors.surfaceHigh,
                        thumbColor = Color.White,
                    ),
                )
            }
        }
    }
}

/**
 * 可交互的音频进度条：点击跳转，左右拖动连续 seek。
 * 拖动时显示本地进度，避免被播放器 500ms 一次的 ticker 拉回去。
 */
@Composable
private fun ReaderAudioProgress(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var widthPx by remember { mutableStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(progress) }
    val shown = if (dragging) dragProgress else progress
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragProgress = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragProgress = (dragProgress + amount / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek(dragProgress)
                    },
                    onDragCancel = { dragging = false },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val trackHeight = 3.dp.toPx()
            val centerY = size.height / 2f
            val radius = CornerRadius(trackHeight / 2f)
            drawRoundRect(
                color = LqColors.surfaceHigh,
                topLeft = Offset(0f, centerY - trackHeight / 2f),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )
            val fillWidth = (size.width * shown).coerceIn(0f, size.width)
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = LqColors.green,
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(fillWidth.coerceAtLeast(trackHeight), trackHeight),
                    cornerRadius = radius,
                )
            }
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(fillWidth, centerY),
            )
        }
    }
}

@Composable
private fun ReaderMediaBar(
    playing: Boolean,
    mode: ReaderMode,
    lingqCount: Int,
    onPlay: () -> Unit,
    onMode: () -> Unit,
    onCards: () -> Unit,
    onExpand: () -> Unit,
    onAi: () -> Unit,
    backdrop: GraphicsLayer? = null,
    modifier: Modifier = Modifier,
) {
    val pillShape = RoundedCornerShape(28.dp)
    var playOrigin by remember { mutableStateOf(Offset.Zero) }
    var pillOrigin by remember { mutableStateOf(Offset.Zero) }
    var aiOrigin by remember { mutableStateOf(Offset.Zero) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧播放键独立；中间三枚带文字的玻璃按钮；右侧 A 为 AI 入口。
        Box(
            modifier = Modifier
                .onGloballyPositioned { playOrigin = it.positionInRoot() }
                .size(46.dp)
                .drawBehind { drawCircleBackdrop(backdrop, playOrigin) }
                .clip(CircleShape)
                .background(Color(0xFF101014).copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) LqIcons.Pause else LqIcons.Play,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        Row(
            modifier = Modifier
                .onGloballyPositioned { pillOrigin = it.positionInRoot() }
                .shadow(
                    elevation = 10.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.30f),
                    spotColor = Color.Black.copy(alpha = 0.38f),
                )
                .drawBehind { drawPillBackdrop(backdrop, pillOrigin, 28.dp.toPx()) }
                .clip(pillShape)
                .background(Color(0xFF101014).copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), pillShape)
                .padding(horizontal = 6.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaBarButton(
                icon = when (mode) {
                    ReaderMode.Page -> LqIcons.Grid
                    ReaderMode.Sentence -> LqIcons.List
                    ReaderMode.Scroll -> LqIcons.Layers
                },
                description = mode.label,
                tint = LqColors.blue,
                onClick = onMode,
            )
            Box {
                MediaBarButton(
                    icon = LqIcons.Cards,
                    description = "Vocabulary",
                    onClick = onCards,
                )
                if (lingqCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = 1.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(LqColors.blueStrong),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (lingqCount > 99) "99+" else lingqCount.toString(),
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }
            }
            MediaBarButton(
                icon = LqIcons.Expand,
                description = "Fullscreen",
                onClick = onExpand,
            )
        }

        Box(
            modifier = Modifier
                .onGloballyPositioned { aiOrigin = it.positionInRoot() }
                .size(46.dp)
                .drawBehind { drawCircleBackdrop(backdrop, aiOrigin) }
                .clip(CircleShape)
                .background(Color(0xFF101014).copy(alpha = 0.42f))
                .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
                .clickable(onClick = onAi),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "A",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MediaBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = LqColors.text,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** 把页面模糊层裁成圆形画在按钮底下，形成真正的半透明模糊底。 */
private fun DrawScope.drawCircleBackdrop(layer: GraphicsLayer?, origin: Offset) {
    if (layer == null) return
    val path = Path().apply { addOval(Rect(0f, 0f, size.width, size.height)) }
    clipPath(path) {
        translate(left = -origin.x, top = -origin.y) {
            drawLayer(layer)
        }
    }
}

private fun DrawScope.drawPillBackdrop(layer: GraphicsLayer?, origin: Offset, radiusPx: Float) {
    if (layer == null) return
    val path = Path().apply {
        addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx)))
    }
    clipPath(path) {
        translate(left = -origin.x, top = -origin.y) {
            drawLayer(layer)
        }
    }
}

@Composable
private fun ModeMenuOverlay(
    mode: ReaderMode,
    onSelect: (ReaderMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = noRipple, indication = null, onClick = onDismiss),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 16.dp, bottom = 72.dp)
                .width(250.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(LqColors.surfaceHigh)
                .border(1.dp, LqColors.border, RoundedCornerShape(18.dp))
                .padding(6.dp),
        ) {
            ModeMenuItem(LqIcons.Grid, "Page Mode", mode == ReaderMode.Page) {
                onSelect(ReaderMode.Page)
            }
            ModeMenuItem(LqIcons.List, "Sentence Mode", mode == ReaderMode.Sentence) {
                onSelect(ReaderMode.Sentence)
            }
            ModeMenuItem(LqIcons.Layers, "Scroll Mode", mode == ReaderMode.Scroll) {
                onSelect(ReaderMode.Scroll)
            }
        }
    }
}

@Composable
private fun ModeMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) LqColors.blueSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) LqColors.blue else LqColors.text,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = if (selected) LqColors.blue else LqColors.text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun ReaderSettingsSheet(
    fontSize: Float,
    lineHeight: Float,
    paragraphSpacing: Float,
    wordSpacing: Float,
    onFontSize: (Float) -> Unit,
    onLineHeight: (Float) -> Unit,
    onParagraphSpacing: (Float) -> Unit,
    onWordSpacing: (Float) -> Unit,
    onDismiss: () -> Unit,
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
            SheetHandle()
            Spacer(Modifier.height(LqSpace.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Reading Settings",
                    color = LqColors.textStrong,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                CircleButton(icon = LqIcons.X, description = "Close", onClick = onDismiss, size = 32)
            }
            Spacer(Modifier.height(LqSpace.lg))
            StepperLine(
                label = "Font Size",
                value = fontSize.toInt().toString(),
                onMinus = { onFontSize(fontSize - 1f) },
                onPlus = { onFontSize(fontSize + 1f) },
            )
            Spacer(Modifier.height(LqSpace.sm))
            StepperLine(
                label = "Line Spacing",
                value = String.format("%.1fx", lineHeight),
                onMinus = { onLineHeight(lineHeight - 0.1f) },
                onPlus = { onLineHeight(lineHeight + 0.1f) },
            )
            Spacer(Modifier.height(LqSpace.sm))
            StepperLine(
                label = "Paragraph Spacing",
                value = "${paragraphSpacing.toInt()}dp",
                minusLabel = "−",
                plusLabel = "+",
                onMinus = { onParagraphSpacing(paragraphSpacing - 2f) },
                onPlus = { onParagraphSpacing(paragraphSpacing + 2f) },
            )
            Spacer(Modifier.height(LqSpace.sm))
            StepperLine(
                label = "Word Spacing",
                value = String.format("%.1f", wordSpacing),
                minusLabel = "−",
                plusLabel = "+",
                onMinus = { onWordSpacing(wordSpacing - 0.5f) },
                onPlus = { onWordSpacing(wordSpacing + 0.5f) },
            )
            Spacer(Modifier.height(LqSpace.lg))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(LqRadius.control))
                    .background(LqColors.bgSoft)
                    .padding(LqSpace.md),
            ) {
                Text(
                    text = "The quick brown fox jumps over the lazy dog.",
                    color = LqColors.text,
                    fontSize = fontSize.sp,
                    lineHeight = (fontSize * lineHeight).sp,
                )
            }
            Spacer(Modifier.height(LqSpace.lg))
            PrimaryButton(label = "Done", onClick = onDismiss)
        }
    }
}

@Composable
private fun StepperLine(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusLabel: String = "A-",
    plusLabel: String = "A+",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = LqColors.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
        StepButton(minusLabel, onMinus)
        Text(
            text = value,
            color = LqColors.muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(54.dp),
        )
        StepButton(plusLabel, onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(LqColors.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = LqColors.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SheetHandle() {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(LqRadius.pill))
                .background(LqColors.surfaceHigh),
        )
    }
}

/** 单词弹窗贴着被点的词出现：默认在上方，空间不够就翻到下方。 */
private class WordPopupPositionProvider(private val anchor: Rect) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 12
        val gap = 8
        if (anchor.width <= 0f || anchor.height <= 0f) {
            return IntOffset(
                (windowSize.width - popupContentSize.width) / 2,
                (windowSize.height - popupContentSize.height) / 2,
            )
        }
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val x = (anchor.center.x.roundToInt() - popupContentSize.width / 2).coerceIn(margin, maxX)
        val spaceAbove = anchor.top.roundToInt()
        val spaceBelow = windowSize.height - anchor.bottom.roundToInt()
        val y = if (spaceAbove >= popupContentSize.height + gap || spaceAbove >= spaceBelow) {
            (anchor.top.roundToInt() - popupContentSize.height - gap).coerceAtLeast(margin)
        } else {
            (anchor.bottom.roundToInt() + gap)
                .coerceAtMost((windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin))
        }
        return IntOffset(x, y)
    }
}

@Composable
private fun WordPopup(
    token: ReaderToken,
    sentence: ReaderSentence,
    anchor: Rect?,
    speaker: Speaker,
    onPronounce: () -> Unit,
    onExplain: () -> Unit,
    meanings: List<Meaning>,
    meaningsLoading: Boolean,
    currentLevel: Int?,
    onDismiss: () -> Unit,
    onState: (WordState) -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    val provider = remember(anchor) { WordPopupPositionProvider(anchor ?: Rect.Zero) }
    val popupWidth = (LocalConfiguration.current.screenWidthDp * 0.92f).dp.coerceAtMost(400.dp)
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val scale by animateFloatAsState(if (shown) 1f else 0.94f, tween(140), label = "popup-scale")
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(140), label = "popup-alpha")
    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(popupWidth)
                .heightIn(min = 260.dp, max = 520.dp)
                .animateContentSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .shadow(24.dp, shape)
                .clip(shape)
                .background(LqColors.surface)
                .border(1.dp, LqColors.border, shape)
                .clickable(interactionSource = noRipple, indication = null) {},
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(LqSpace.lg),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RawText(
                        text = token.text,
                        color = LqColors.textStrong,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (currentLevel != null) {
                        Spacer(Modifier.width(LqSpace.sm))
                        LevelBadge(currentLevel)
                    }
                    Spacer(Modifier.width(LqSpace.sm))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(LqRadius.pill))
                            .background(LqColors.blueSoft)
                            .clickable(onClick = onExplain)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            LqIcons.Bot,
                            contentDescription = null,
                            tint = LqColors.blue,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "AI",
                            color = LqColors.blue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(LqColors.surfaceHigh)
                            .clickable(onClick = onPronounce),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            LqIcons.Volume,
                            contentDescription = "Pronounce",
                            tint = LqColors.text,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
                token.transliteration?.takeIf { it.isNotBlank() }?.let {
                    RawText(it, color = LqColors.muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(LqSpace.md))
                MeaningList(meanings = meanings, loading = meaningsLoading, inline = token.translation)
                Spacer(Modifier.height(LqSpace.lg))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(LqRadius.pill))
                        .border(1.dp, LqColors.border, RoundedCornerShape(LqRadius.pill))
                        .clickable { speaker.speak(sentence.text) }
                        .padding(vertical = LqSpace.md),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(LqIcons.Play, contentDescription = null, tint = LqColors.text, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(LqSpace.sm))
                    Text("Play from here", color = LqColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(LqSpace.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusIcon(LqIcons.EyeOff, LqColors.muted, "Ignore") { onState(WordState.Ignored) }
                    Spacer(Modifier.weight(1f))
                    (1..4).forEach { level ->
                        StatusCircle(level, selected = currentLevel == level) {
                            onState(WordState.Lingq(level))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    StatusIcon(LqIcons.Check, LqColors.green, "Known") { onState(WordState.Known) }
                }
            }
        }
    }
}

/** 单词标题旁的熟练度徽章，对应 LingQ 的金色数字圆点。 */
@Composable
private fun LevelBadge(level: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(WordGold),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            color = Color(0xFF2A2010),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MeaningList(meanings: List<Meaning>, loading: Boolean, inline: String?) {
    val entries = buildList {
        addAll(meanings)
        val fallback = inline?.takeIf { it.isNotBlank() }
        if (fallback != null && none { it.text == fallback }) add(Meaning(fallback, null))
    }.take(4)
    when {
        loading && entries.isEmpty() -> MeaningBox("Looking up…")
        entries.isEmpty() -> MeaningBox("No translation available")
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
                    RawText(meaning.text, color = LqColors.text, fontSize = 14.sp, lineHeight = 19.sp)
                }
            }
        }
    }
}

@Composable
private fun MeaningBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(LqRadius.control))
            .background(LqColors.bgSoft)
            .padding(LqSpace.md),
    ) {
        RawText(text, color = LqColors.faint, fontSize = 14.sp)
    }
}

/**
 * LingQ 7 的等级圆钮：1/2/3 档的圆底就是正文色块的黄阶，
 * 4 档没有圆底（正文里它本来就不填色），选中用金色圆框标出。
 */
@Composable
private fun StatusCircle(level: Int, selected: Boolean, onClick: () -> Unit) {
    val fill = WordChipYellows.getOrNull(level - 1) ?: Color.Transparent
    Box(
        modifier = Modifier
            .padding(end = LqSpace.sm)
            .size(38.dp)
            .clip(CircleShape)
            .background(fill)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) WordGold else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = level.toString(),
            color = WordGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun StatusIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(LqColors.surfaceHigh)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun LessonCompleteOverlay(
    onBackToLesson: () -> Unit,
    onKeepReading: () -> Unit,
) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LqColors.scrim)
            .clickable(interactionSource = noRipple, indication = null, onClick = onKeepReading),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(horizontal = 32.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(LqColors.surface)
                .clickable(interactionSource = noRipple, indication = null) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(LqColors.greenSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(LqIcons.Check, contentDescription = null, tint = LqColors.green, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(LqSpace.lg))
            Text("Lesson Complete", color = LqColors.textStrong, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(LqSpace.xs))
            Text(
                text = "Your reading progress and Inputa words are saved on this device.",
                color = LqColors.muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(LqSpace.xl))
            PrimaryButton(label = "Back to lesson", onClick = onBackToLesson)
            Spacer(Modifier.height(LqSpace.md))
            Text(
                text = "Keep reading",
                color = LqColors.blue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(LqRadius.pill))
                    .clickable(onClick = onKeepReading)
                    .padding(horizontal = LqSpace.lg, vertical = LqSpace.xs),
            )
        }
    }
}

