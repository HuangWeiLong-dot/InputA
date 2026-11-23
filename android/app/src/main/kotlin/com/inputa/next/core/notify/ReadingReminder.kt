package com.inputa.next.core.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.inputa.next.MainActivity
import com.inputa.next.R
import com.inputa.next.core.data.local.SettingsStore
import com.inputa.next.core.design.AppLanguage
import com.inputa.next.core.design.translate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 每日阅读提醒。用 AlarmManager 在选定时间发一条本地通知，
 * 通知发出后由接收器重新排下一天，不依赖服务端或 WorkManager。
 */
@Singleton
class ReadingReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsStore,
) {
    fun schedule(hour: Int, minute: Int) {
        settings.setReminderTime(hour, minute)
        settings.setReminderEnabled(true)
        scheduleNext()
    }

    fun reschedule(hour: Int, minute: Int) {
        settings.setReminderTime(hour, minute)
        if (settings.reminderEnabled.value) scheduleNext()
    }

    fun cancel() {
        settings.setReminderEnabled(false)
        alarmManager()?.cancel(alarmIntent())
    }

    /** App 启动或时间变更后调用；关闭提醒时什么都不做。 */
    fun scheduleNext() {
        if (!settings.reminderEnabled.value) return
        val manager = alarmManager() ?: return
        val pending = alarmIntent()
        manager.cancel(pending)
        manager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextTrigger(settings.reminderHour.value, settings.reminderMinute.value),
            pending,
        )
    }

    fun notifyNow() {
        createChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val language = AppLanguage.fromCode(settings.appLanguage.value)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(language.translate("Time to read"))
            .setContentText(language.translate("A few minutes now keeps your streak alive."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Daily reading reminder",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "A daily nudge to keep your reading streak."
            },
        )
    }

    private fun nextTrigger(hour: Int, minute: Int): Long {
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReadingReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun alarmManager(): AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    private companion object {
        const val CHANNEL_ID = "daily_reading_reminder"
        const val NOTIFICATION_ID = 1001
        const val REQUEST_ALARM = 2001
        const val REQUEST_OPEN = 2002
    }
}
