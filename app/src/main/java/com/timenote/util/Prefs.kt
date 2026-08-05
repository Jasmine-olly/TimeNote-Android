package com.timenote.util

import android.content.Context

/** 轻量偏好设置（提醒阈值等），与 Room 持久化数据区分开 */
object Prefs {

    private const val NAME = "timenote_prefs"
    private const val KEY_THRESHOLDS = "threshold_minutes"
    private const val KEY_TIMER_X = "timer_x"
    private const val KEY_TIMER_Y = "timer_y"
    private const val KEY_PRECISE_DETECTION = "precise_detection"

    /** 提醒阈值（毫秒），默认 30 / 60 / 90 分钟（PRD F1.3 建议） */
    fun getThresholdsMs(context: Context): List<Long> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY_THRESHOLDS, "30,60,90") ?: "30,60,90"
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > 0 }
            .map { it * 60_000L }
    }

    fun saveThresholdsMinutes(context: Context, minutes: List<Int>) {
        val clean = minutes.filter { it > 0 }.distinct().sorted()
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THRESHOLDS, clean.joinToString(","))
            .apply()
    }

    /** 精确检测（无障碍实时）开关：关掉则只用系统 5 秒延时检测 */
    fun isPreciseDetection(context: Context): Boolean =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_PRECISE_DETECTION, true)

    fun setPreciseDetection(context: Context, enabled: Boolean) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRECISE_DETECTION, enabled)
            .apply()
    }

    /** 悬浮计时器位置（x/y，左上角偏移）；未保存过返回 null */
    fun getTimerPos(context: Context): Pair<Int, Int>? {
        val sp = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (!sp.contains(KEY_TIMER_X) || !sp.contains(KEY_TIMER_Y)) return null
        return sp.getInt(KEY_TIMER_X, 0) to sp.getInt(KEY_TIMER_Y, 0)
    }

    fun saveTimerPos(context: Context, x: Int, y: Int) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_TIMER_X, x)
            .putInt(KEY_TIMER_Y, y)
            .apply()
    }
}
