package com.inputa.reader

import android.app.Application
import com.inputa.reader.domain.repository.BookRepository
import com.inputa.reader.domain.repository.ProgressRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Hilt 的注入根。运行时一切单例（Room 数据库、DataStore、OkHttp、仓储）都挂在这里，
 * 没有别的地方持有全局状态 —— 与 Web 版「store 是模块级单例」等价，但生命周期明确。
 */
@HiltAndroidApp
class InputaApp : Application() {

    @Inject lateinit var books: BookRepository
    @Inject lateinit var progress: ProgressRepository

    /**
     * 预热用的作用域：跟着进程活，不跟着任何界面。
     *
     * 冷启动实测（模拟器 + debug 包）：从 Activity 创建到「首屏可读」约 3.4 秒，其中
     * **内置样书的资产读取与首次 JSON 解析就占 1.1 秒**（首次用到 kotlinx.serialization，
     * 加载与反射那些 serializer 是大头），而它原本排在 Compose 首次组合之后才开始。
     * 这里把同样的工作提前到这个进程刚起来时、用 IO 线程跑，与界面初始化重叠。
     *
     * **只预热、不算数**：失败什么也不做，阅读器那边会照原路再取一次并自己报错。
     */
    private val warmup = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 实验：暂时停掉预热，看它是不是测试 OOM 的来源
        // warmup.launch { runCatching { books.builtinBooks() } }
        // warmup.launch { runCatching { progress.observeMostRecent().first() } }
    }
}
