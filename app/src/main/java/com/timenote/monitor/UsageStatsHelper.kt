package com.timenote.monitor

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.timenote.util.PermissionUtils

/**
 * 使用情况统计兜底检测（PRD 5.2）。
 *
 * 解析最近 [WINDOW_MS] 内的前台切换事件，模拟出一个「当前前台包名」。
 * 局限：无障碍未开启时的降级通道，解析较耗电，且超过窗口期的长会话可能漏检。
 */
object UsageStatsHelper {

    private const val WINDOW_MS = 6L * 60 * 60 * 1000

    fun getForegroundPackage(context: Context): String? {
        if (!PermissionUtils.hasUsageAccess(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        val events = usm.queryEvents(end - WINDOW_MS, end)
        val event = UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> foreground = event.packageName
                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (foreground == event.packageName) foreground = null
                }
            }
        }
        return foreground
    }
}
