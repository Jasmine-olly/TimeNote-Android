package com.timenote.monitor

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

/**
 * 无障碍兜底前台检测（PRD 5.2：实时性强，弥补 UsageStats 延迟）。
 *
 * 通过「活动窗口」判断真实前台应用：
 * 不能直接取最后一次窗口事件的包名（后台窗口的状态变化会干扰），
 * 而是查询 [getWindows] 中 `isActive` 的窗口。
 * 仅读取窗口的包名，不遍历任何窗口内容（隐私红线）。
 */
class TimeNoteAccessibilityService : AccessibilityService() {

    private companion object {
        const val TAG = "TimeNoteAccessibility"

        /** 前台窗口最小面积占比（%）——只有「基本铺满屏幕」才算真前台；
         *  小窗/悬浮层（无论多大，只要没铺满）都保持上次前台，不打断监督 */
        const val MIN_FOREGROUND_PERCENT = 85L
    }

    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        ForegroundState.accessibilityConnected = true
        updateActivePackage()
        Log.d(TAG, "connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }
        updateActivePackage()
    }

    /** 查询当前活动窗口（真实前台应用）的包名。
     *  判定规则：
     *  - 只认「应用窗口」（TYPE_APPLICATION），系统窗口（通知栏/状态栏/输入法）不算；
     *  - 且窗口面积需 ≥ [MIN_FOREGROUND_PERCENT]% 屏幕——小窗/悬浮窗太小，
     *    不算切换前台，保持上次真实前台（小窗不打断监督，避免会话误重置）。 */
    private fun updateActivePackage() {
        val windows = runCatching { getWindows() }.getOrNull() ?: return
        val dm = resources.displayMetrics
        val screenArea = dm.widthPixels.toLong() * dm.heightPixels.toLong()
        val pkg = windows
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .firstOrNull { w ->
                if (!w.isActive) return@firstOrNull false
                val bounds = android.graphics.Rect()
                w.getBoundsInScreen(bounds)
                isForegroundSized(bounds, screenArea)
            }
            ?.root?.packageName?.toString()
        if (pkg != null && pkg != lastPackage) {
            lastPackage = pkg
            ForegroundState.currentPackage = pkg
            Log.d(TAG, "active-window -> $pkg")
        }
    }

    /** 窗口面积是否够大（排除小窗/悬浮层） */
    private fun isForegroundSized(bounds: android.graphics.Rect, screenArea: Long): Boolean {
        if (bounds.width() <= 0 || bounds.height() <= 0) return false
        val area = bounds.width().toLong() * bounds.height().toLong()
        return area * 100 >= screenArea * MIN_FOREGROUND_PERCENT
    }

    override fun onInterrupt() {
        // 无需处理
    }

    override fun onUnbind(intent: Intent?): Boolean {
        ForegroundState.accessibilityConnected = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ForegroundState.accessibilityConnected = false
        super.onDestroy()
    }
}
