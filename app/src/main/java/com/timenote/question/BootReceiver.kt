package com.timenote.question

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机重新登记定时提问闹钟（系统重启后 AlarmManager 的全部闹钟会丢失）。
 * 注：国产 ROM 可能默认禁止应用自启动，需用户在设置中允许。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            QuestionScheduler.rescheduleAll(context)
        }
    }
}
