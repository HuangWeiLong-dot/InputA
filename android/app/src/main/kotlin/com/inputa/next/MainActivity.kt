package com.inputa.next

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.next.core.data.CurrentCourse
import com.inputa.next.core.data.CurrentLesson
import com.inputa.next.core.data.LessonSummary
import com.inputa.next.core.data.SessionStore
import com.inputa.next.core.data.local.LastLessonStore
import com.inputa.next.core.data.local.SettingsStore
import com.inputa.next.core.design.AppLanguage
import com.inputa.next.core.design.FrostedBottomBar
import com.inputa.next.core.design.LocalAppLanguage
import com.inputa.next.core.design.LqColors
import com.inputa.next.core.design.NextTab
import com.inputa.next.core.design.NextTheme
import com.inputa.next.core.design.translate
import com.inputa.next.core.notify.ReadingReminder
import com.inputa.next.feature.auth.LoginScreen
import com.inputa.next.feature.auth.LoginViewModel
import com.inputa.next.feature.chat.ChatScreen
import com.inputa.next.feature.course.CourseScreen
import com.inputa.next.feature.explore.ExploreScreen
import com.inputa.next.feature.home.HomeScreen
import com.inputa.next.feature.imports.ImportScreen
import com.inputa.next.feature.imports.ImportViewModel
import com.inputa.next.feature.languages.LanguagePickerScreen
import com.inputa.next.feature.lesson.LessonPreviewScreen
import com.inputa.next.feature.playlist.PlaylistScreen
import com.inputa.next.feature.reader.ReaderScreen
import com.inputa.next.feature.review.ReviewScreen
import com.inputa.next.feature.saved.SavedScreen
import com.inputa.next.feature.settings.InfoKind
import com.inputa.next.feature.settings.InfoScreen
import com.inputa.next.feature.settings.SettingsScreen
import com.inputa.next.feature.stats.StatsScreen
import com.inputa.next.feature.vocabulary.VocabularyScreen
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 单 Activity：登录 → 四 tab 壳 → 课程预览 / 阅读器 / 统计 / 设置。
 * 登录态只来自本机 [SessionStore]；没有自建后端。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var session: SessionStore

    @Inject
    lateinit var lastLessonStore: LastLessonStore

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var currentLesson: CurrentLesson

    @Inject
    lateinit var currentCourse: CurrentCourse

    @Inject
    lateinit var reminder: ReadingReminder

    override fun onCreate(savedInstanceState: Bundle?) {
        // 系统状态栏/导航栏都保持全透明，页面内容真正铺满底部。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }
        super.onCreate(savedInstanceState)
        val alreadyLoggedIn = session.isLoggedIn()
        // 每次启动恢复每日提醒（系统重启后 AlarmManager 会丢），并建好通知渠道。
        reminder.createChannel()
        reminder.scheduleNext()

        setContent {
            NextTheme {
                val appLanguage by settingsStore.appLanguage.collectAsStateWithLifecycle()
                CompositionLocalProvider(
                    LocalAppLanguage provides AppLanguage.fromCode(appLanguage),
                ) {
                val login: LoginViewModel = hiltViewModel()
                val loginState by login.state.collectAsStateWithLifecycle()
                var loggedIn by rememberSaveable { mutableStateOf(alreadyLoggedIn) }
                var tab by rememberSaveable { mutableStateOf(NextTab.Home) }
                var screen by remember { mutableStateOf<Screen>(Screen.Tab) }
                var lastOpenedLesson by remember { mutableStateOf<Pair<Int, Long>?>(null) }

                LaunchedEffect(loginState.loggedIn) {
                    if (loginState.loggedIn) loggedIn = true
                }

                Surface(color = LqColors.bg, modifier = Modifier.fillMaxSize()) {
                    if (!loggedIn) {
                        LoginScreen(
                            state = loginState,
                            onUsername = login::setUsername,
                            onPassword = login::setPassword,
                            onToken = login::setToken,
                            onTokenMode = login::setTokenMode,
                            onSubmit = login::submit,
                        )
                    } else {
                        Shell(
                            tab = tab,
                            screen = screen,
                            onTab = {
                                tab = it
                                screen = Screen.Tab
                            },
                            onScreen = { screen = it },
                            onOpenLesson = { lesson ->
                                // 同一课程 600ms 内的重复点击忽略；不同课程仍然立即打开。
                                val now = System.currentTimeMillis()
                                val last = lastOpenedLesson
                                if (last == null || last.first != lesson.id || now - last.second > 600) {
                                    lastOpenedLesson = lesson.id to now
                                    if (lesson.isCourse) {
                                        currentCourse.select(lesson)
                                        screen = Screen.Course
                                    } else {
                                        currentLesson.select(lesson)
                                        lastLessonStore.save(session.language(), lesson)
                                        screen = Screen.Lesson
                                    }
                                }
                            },
                            onSignedOut = {
                                currentLesson.clear()
                                currentCourse.clear()
                                tab = NextTab.Home
                                screen = Screen.Tab
                                loggedIn = false
                            },
                        )
                    }
                }
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Tab : Screen
    data object Lesson : Screen
    data object Reader : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Review : Screen
    data object Import : Screen
    data object Chat : Screen
    data object Languages : Screen
    data object Course : Screen
    data object Method : Screen
    data object Help : Screen
    data object Saved : Screen
}

@Composable
private fun Shell(
    tab: NextTab,
    screen: Screen,
    onTab: (NextTab) -> Unit,
    onScreen: (Screen) -> Unit,
    onOpenLesson: (LessonSummary) -> Unit,
    onSignedOut: () -> Unit,
) {
    var chatReturn by remember { mutableStateOf<Screen>(Screen.Tab) }
    var languagesReturn by remember { mutableStateOf<Screen>(Screen.Tab) }
    var reviewReturn by remember { mutableStateOf<Screen>(Screen.Tab) }
    var importReturn by remember { mutableStateOf<Screen>(Screen.Tab) }
    val importViewModel: ImportViewModel = hiltViewModel()

    fun openImport(returnTo: Screen) {
        importViewModel.consumeImported()
        importReturn = returnTo
        onScreen(Screen.Import)
    }

    BackHandler(enabled = screen != Screen.Tab) {
        onScreen(
            when (screen) {
                Screen.Reader -> Screen.Lesson
                Screen.Chat -> chatReturn
                Screen.Languages -> languagesReturn
                Screen.Review -> reviewReturn
                Screen.Import -> importReturn
                Screen.Method, Screen.Help -> Screen.Settings
                else -> Screen.Tab
            },
        )
    }

    val contentLayer = rememberGraphicsLayer()
    val blurredLayer = rememberGraphicsLayer()
    val blurRadius = with(LocalDensity.current) { 22.dp.toPx() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 页面内容先录进 layer：底栏拿它做实时模糊背景，内容可以直接透出来。
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
        when (screen) {
            Screen.Tab -> Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    NextTab.Home -> HomeScreen(
                        onOpenLesson = onOpenLesson,
                        onOpenStats = { onScreen(Screen.Stats) },
                        onOpenSettings = { onScreen(Screen.Settings) },
                        onExplore = { onTab(NextTab.Explore) },
                        onOpenVocabulary = { onTab(NextTab.Vocabulary) },
                        onOpenLanguages = {
                            languagesReturn = Screen.Tab
                            onScreen(Screen.Languages)
                        },
                        onOpenSaved = { onScreen(Screen.Saved) },
                    )

                    NextTab.Explore -> ExploreScreen(onOpenLesson = onOpenLesson)
                    NextTab.Playlist -> PlaylistScreen(onExplore = { onTab(NextTab.Explore) })
                    NextTab.Vocabulary -> VocabularyScreen(
                        onOpenReview = {
                            reviewReturn = Screen.Tab
                            onScreen(Screen.Review)
                        },
                    )
                }
            }

            Screen.Lesson -> LessonPreviewScreen(
                onBack = { onScreen(Screen.Tab) },
                onStart = { onScreen(Screen.Reader) },
                onOpenReview = {
                    reviewReturn = Screen.Lesson
                    onScreen(Screen.Review)
                },
                onOpenChat = {
                    chatReturn = Screen.Lesson
                    onScreen(Screen.Chat)
                },
            )

            Screen.Course -> CourseScreen(
                onBack = { onScreen(Screen.Tab) },
                onOpenLesson = onOpenLesson,
            )

            Screen.Reader -> ReaderScreen(
                onBack = { onScreen(Screen.Lesson) },
                onOpenVocabulary = { onTab(NextTab.Vocabulary) },
                onOpenChat = {
                    chatReturn = Screen.Reader
                    onScreen(Screen.Chat)
                },
                onOpenReview = {
                    reviewReturn = Screen.Reader
                    onScreen(Screen.Review)
                },
            )

            Screen.Stats -> StatsScreen(onBack = { onScreen(Screen.Tab) })

            Screen.Review -> ReviewScreen(onBack = { onScreen(reviewReturn) })

            Screen.Saved -> SavedScreen(
                onBack = { onScreen(Screen.Tab) },
                onOpenLesson = onOpenLesson,
            )

            Screen.Languages -> LanguagePickerScreen(onBack = { onScreen(languagesReturn) })

            Screen.Import -> ImportScreen(
                onBack = { onScreen(importReturn) },
                onImported = onOpenLesson,
                viewModel = importViewModel,
            )

            Screen.Chat -> ChatScreen(onBack = { onScreen(chatReturn) })

            Screen.Settings -> SettingsScreen(
                onBack = { onScreen(Screen.Tab) },
                onSignedOut = onSignedOut,
                onOpenStats = { onScreen(Screen.Stats) },
                onOpenImport = { openImport(Screen.Settings) },
                onOpenChat = {
                    chatReturn = Screen.Settings
                    onScreen(Screen.Chat)
                },
                onOpenLanguages = {
                    languagesReturn = Screen.Settings
                    onScreen(Screen.Languages)
                },
                onOpenMethod = { onScreen(Screen.Method) },
                onOpenHelp = { onScreen(Screen.Help) },
            )

            Screen.Method -> InfoScreen(kind = InfoKind.Method, onBack = { onScreen(Screen.Settings) })

            Screen.Help -> InfoScreen(kind = InfoKind.Help, onBack = { onScreen(Screen.Settings) })
        }
        }

        if (screen == Screen.Tab) {
            FrostedBottomBar(
                tab = tab,
                onTab = onTab,
                onCreate = { openImport(Screen.Tab) },
                createContentDescription = LocalAppLanguage.current.translate("Import Lesson"),
                backdrop = blurredLayer,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
