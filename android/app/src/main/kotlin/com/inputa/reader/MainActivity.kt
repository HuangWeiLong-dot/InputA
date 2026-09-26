package com.inputa.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inputa.reader.ui.bookshelf.BookshelfDialog
import com.inputa.reader.ui.bookshelf.BookshelfViewModel
import com.inputa.reader.ui.reader.Overlay
import com.inputa.reader.ui.reader.ReaderScreen
import com.inputa.reader.ui.reader.ReaderViewModel
import com.inputa.reader.ui.settings.SettingsDialog
import com.inputa.reader.ui.settings.SettingsViewModel
import com.inputa.reader.ui.theme.InputaTheme
import com.inputa.reader.ui.theme.LocalThemeTokens
import com.inputa.reader.ui.vocabulary.VocabularyDialog
import com.inputa.reader.ui.vocabulary.VocabularyViewModel
import dagger.hilt.android.AndroidEntryPoint

/**
 * 全应用唯一的 Activity，也是唯一一个屏。
 *
 * 只有一个屏是有意为之：Web 版没有 router，一切叠加层都是阅读器之上的模态
 * （书架、生词本、设置、释义面板）。所以「返回键永远不离开阅读器」在这里成立 ——
 * 将来若加了第二个屏，这个决定要重新审视。
 *
 * 三个覆盖层在这里组装，而不是塞进 `ReaderScreen`：那样它的参数会长到二十几个。
 * 阅读器只管渲染正文与请求「打开某个覆盖层」，具体是什么由这里决定。
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            // 在这里就地取 ViewModel，而不是提前到 `onCreate` 的某个属性里 —— 这是量过的：
            // 提前建它只会让主线程在首次组合之前多做一点事，而取数早在 `InputaApp` 的预热里
            // 就开始了，两种写法冷启动实测持平到略差，所以留最简单的这个。
            val reader: ReaderViewModel = hiltViewModel()
            val state by reader.state.collectAsStateWithLifecycle()

            InputaTheme(theme = state.settings.theme) {
                val tokens = LocalThemeTokens.current
                // 这层 Surface 是**整窗**的底色，刻意保持方角 —— 它不是一张卡片。
                // 界面里其余的面（卡片、面板、底部面板）圆角都来自 `MaterialTheme.shapes`，
                // 所以这里看起来「漏了圆角」是对的，别顺手给它加一个。
                Surface(color = tokens.bgMain, modifier = Modifier.fillMaxSize()) {
                    ReaderScreen(
                        state = state,
                        scrollToPage = reader.scrollToPage,
                        onWordTap = reader::onWordSelected,
                        onPageSettled = reader::onPageSettled,
                        onPrev = reader::prevPage,
                        onNext = reader::nextPage,
                        onDismissWord = reader::clearActiveWord,
                        onRetryLookup = reader::retryLookup,
                        onSetStatus = reader::setStatus,
                        onMarkMastered = reader::markMastered,
                        onRemoveWord = reader::removeWord,
                        onUndoMastery = reader::undoMastery,
                        onDismissMastery = reader::dismissMasteryNotice,
                        onDismissSkipped = reader::dismissSkippedNotice,
                        onOpenOverlay = reader::openOverlay,
                    )

                    Overlays(
                        overlay = state.overlay,
                        reader = reader,
                        onDismiss = reader::closeOverlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun Overlays(
    overlay: Overlay?,
    reader: ReaderViewModel,
    onDismiss: () -> Unit,
) {
    when (overlay) {
        null -> Unit
        Overlay.BookCatalog -> Bookshelf(reader = reader, onDismiss = onDismiss)
        Overlay.Vocabulary -> Vocabulary(reader = reader, onDismiss = onDismiss)
        Overlay.Settings -> Settings(onDismiss = onDismiss)
    }
}

@Composable
private fun Bookshelf(reader: ReaderViewModel, onDismiss: () -> Unit) {
    val viewModel: BookshelfViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 书架自己不去开书 —— 它只负责说「这本好了」。开书要拿章节与分页状态，
    // 那是阅读器的职责。
    LaunchedEffect(viewModel) {
        viewModel.openRequests.collect { id -> reader.openBookById(id) }
    }

    BookshelfDialog(
        state = state,
        onTab = viewModel::setTab,
        onQuery = viewModel::setQuery,
        onSearch = viewModel::search,
        onLoadGutendex = viewModel::loadGutendex,
        onPasteTitle = viewModel::setPasteTitle,
        onPasteContent = viewModel::setPasteContent,
        onSavePasted = viewModel::savePasted,
        onOpen = viewModel::openBook,
        onDelete = viewModel::deleteBook,
        onDismiss = onDismiss,
    )
}

@Composable
/**
 * 词表面板。**要拿阅读器的 ViewModel** —— 点词查释义走的是阅读器那条点词入口
 * （`onWordSelected`），释义面板、等级选择、移出词库于是全都复用现成的那一套，
 * 而不是在词表里再实现一遍。
 */
private fun Vocabulary(reader: ReaderViewModel, onDismiss: () -> Unit) {
    val viewModel: VocabularyViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    VocabularyDialog(
        state = state,
        onFilter = viewModel::setFilter,
        onQuery = viewModel::setQuery,
        onMarkMastered = viewModel::markMastered,
        onMarkAsNew = viewModel::markAsNew,
        onRemove = viewModel::removeWord,
        // 句子传空串：词表里的词没有上下文句子，释义面板会自动不显示那一段。
        onSelectWord = { word -> reader.onWordSelected(word, "") },
        onClearLibrary = viewModel::clearLibrary,
        onDismiss = onDismiss,
    )
}

@Composable
private fun Settings(onDismiss: () -> Unit) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsDialog(
        state = state,
        onEditBaseUrl = viewModel::editBaseUrl,
        onSaveBaseUrl = viewModel::saveBaseUrl,
        onTestConnection = viewModel::testConnection,
        onStepFontSize = viewModel::stepFontSize,
        onTheme = viewModel::setTheme,
        onStepWordsPerPage = viewModel::stepWordsPerPage,
        onBionic = viewModel::setBionic,
        onDismiss = onDismiss,
    )
}
