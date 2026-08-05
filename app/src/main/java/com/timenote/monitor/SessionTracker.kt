package com.timenote.monitor

import android.os.SystemClock

/**
 * 娱乐使用会话状态机（F1.2 / F1.3）
 *
 * 职责：
 * - 维护「当前娱乐会话」的开始时间，供悬浮计时器显示本次连续使用时长
 * - 达到阈值（默认 30/60/90 分钟）时触发提醒
 * - 处理提醒的三种操作：再玩5分钟 / 退出应用 / 关闭提醒
 */
class SessionTracker(
    private val getThresholdsMs: () -> List<Long>,
) {
    /** 当前处于前台的娱乐应用包名；非娱乐场景为 null */
    var activePackage: String? = null
        private set

    /** 会话开始的 elapsedRealtime（毫秒）；无会话时为 0 */
    var sessionStartRealtime: Long = 0L
        private set

    /** 本次连续使用时长（毫秒） */
    val durationMs: Long
        get() = if (sessionStartRealtime > 0L) SystemClock.elapsedRealtime() - sessionStartRealtime else 0L

    /** 提醒弹窗是否应显示 */
    var reminderVisible: Boolean = false
        private set

    private var nextReminderMs = 0L
    private var snoozedUntilRealtime = 0L
    private var remindersDisabled = false
    private var snoozeCount = 0

    /** 前台包名变化：进入/离开娱乐应用时调用 */
    fun onForegroundChanged(pkg: String?, entertainmentPackages: Set<String>) {
        val isEntertainment = pkg != null && pkg in entertainmentPackages
        if (isEntertainment && activePackage != pkg) {
            startNewSession(pkg)
        } else if (!isEntertainment && activePackage != null) {
            // 离开娱乐应用或锁屏：停止并重置（F1.2）
            reset()
        }
    }

    private fun startNewSession(pkg: String) {
        activePackage = pkg
        sessionStartRealtime = SystemClock.elapsedRealtime()
        nextReminderMs = getThresholdsMs().minOrNull() ?: 0L
        snoozedUntilRealtime = 0L
        remindersDisabled = false
        snoozeCount = 0
        reminderVisible = false
    }

    /** 每秒由服务调用，检查是否应触发提醒 */
    fun onTick() {
        if (activePackage == null || reminderVisible || remindersDisabled) return
        if (nextReminderMs <= 0L) return
        if (durationMs >= nextReminderMs && SystemClock.elapsedRealtime() >= snoozedUntilRealtime) {
            reminderVisible = true
        }
    }

    /** 是否还能【再玩5分钟】（未到单次会话上限） */
    val canSnooze: Boolean get() = snoozeCount < MAX_SNOOZE

    /** 【再玩5分钟】：本次阈值顺延 5 分钟；达到上限返回 false */
    fun snooze(): Boolean {
        if (!canSnooze) return false
        reminderVisible = false
        snoozedUntilRealtime = SystemClock.elapsedRealtime() + SNOOZE_MS
        nextReminderMs = durationMs + SNOOZE_MS
        snoozeCount++
        return true
    }

    /** 【关闭提醒】/【退出应用】：跳到下一个更高阈值；若无则本会话不再提醒 */
    fun dismissReminder() {
        reminderVisible = false
        val d = durationMs
        val next = getThresholdsMs().firstOrNull { it > d }
        if (next == null) {
            remindersDisabled = true
        } else {
            nextReminderMs = next
        }
    }

    fun reset() {
        activePackage = null
        sessionStartRealtime = 0L
        reminderVisible = false
        nextReminderMs = 0L
        snoozedUntilRealtime = 0L
        remindersDisabled = false
        snoozeCount = 0
    }

    companion object {
        private const val SNOOZE_MS = 5L * 60 * 1000

        /** 单个会话内【再玩5分钟】的最大次数（PRD 未决事项） */
        private const val MAX_SNOOZE = 3
    }
}
