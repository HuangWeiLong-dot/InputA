package com.inputa.next.core.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.inputa.next.MainActivity
import com.inputa.next.R
import com.inputa.next.core.design.AppLanguage
import com.inputa.next.core.design.translate
import com.inputa.next.core.data.local.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 课程音频前台服务：播放期间常驻，保证应用切后台后进程不被回收，
 * 并在通知栏展示标题/状态与播放-暂停、停止控制。
 *
 * 服务本身不持有 MediaPlayer，只观察 [LessonPlayer] 的状态流；
 * 播放、循环与音量逻辑仍在 LessonPlayer 内，通知按钮直接回调它。
 */
@AndroidEntryPoint
class LessonAudioService : Service() {

    @Inject
    lateinit var player: LessonPlayer

    @Inject
    lateinit var settings: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player.toggle()
            ACTION_STOP -> player.stop()
        }
        // 无论状态如何都先 startForeground：本服务只能经 startForegroundService 启动，
        // 不进前台就 stopSelf 也会触发 5s 超时异常。
        val state = player.state.value
        startAsForeground(buildNotification(state))
        if (state.entry == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (observeJob == null) {
            observeJob = scope.launch {
                player.state
                    .map { snapshot ->
                        // 进度按秒进入 key：通知进度条每秒刷新一次，不跟 500ms ticker 抖。
                        listOf(
                            snapshot.entry?.lessonId,
                            snapshot.playing,
                            snapshot.preparing,
                            snapshot.error != null,
                            snapshot.positionMs / 1000,
                        )
                    }
                    .distinctUntilChanged()
                    .collect {
                        val current = player.state.value
                        if (current.entry == null ||
                            (current.error != null && !current.playing && !current.preparing)
                        ) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        } else {
                            notifyManager().notify(NOTIFICATION_ID, buildNotification(current))
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(state: LessonPlayer.State): Notification {
        val language = AppLanguage.fromCode(settings.appLanguage.value)
        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val toggle = PendingIntent.getService(
            this,
            REQUEST_TOGGLE,
            Intent(this, LessonAudioService::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, LessonAudioService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val playing = state.playing || state.preparing
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(state.entry?.title ?: language.translate("Lesson audio"))
            .setContentText(language.translate(if (state.preparing) "Loading audio…" else if (playing) "Playing" else "Paused"))
            .setOngoing(playing)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                language.translate(if (playing) "Pause" else "Play"),
                toggle,
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                language.translate("Stop"),
                stop,
            )
        if (state.durationMs > 0) {
            builder.setProgress(state.durationMs, state.positionMs, false)
        }
        return builder.build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Lesson audio",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Playback controls for the lesson audio."
            },
        )
    }

    private fun notifyManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        const val ACTION_TOGGLE = "com.inputa.next.action.TOGGLE_PLAYBACK"
        const val ACTION_STOP = "com.inputa.next.action.STOP_PLAYBACK"
        private const val CHANNEL_ID = "lesson_audio_playback"
        private const val NOTIFICATION_ID = 2001
        private const val REQUEST_OPEN = 3001
        private const val REQUEST_TOGGLE = 3002
        private const val REQUEST_STOP = 3003
    }
}
