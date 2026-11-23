package com.inputa.next.core.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** 收到每日提醒后发通知，并把下一次提醒排到明天同一时间。 */
@AndroidEntryPoint
class ReadingReminderReceiver : BroadcastReceiver() {
    @Inject
    lateinit var reminder: ReadingReminder

    override fun onReceive(context: Context, intent: Intent) {
        // 重启或应用更新后只需把下一次闹钟补回来，不立刻发通知。
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            reminder.scheduleNext()
            return
        }
        reminder.notifyNow()
        reminder.scheduleNext()
    }
}
