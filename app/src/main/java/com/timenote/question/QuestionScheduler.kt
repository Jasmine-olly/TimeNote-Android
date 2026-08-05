package com.timenote.question

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.QuestionPlan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 定时提问调度（F2.2）：为每个启用的问题计划设置下一次触发的精确闹钟。
 *
 * 每次闹钟触发后，接收器会重新调度该计划的下一次触发，实现「每日/工作日/自定义星期」重复。
 */
object QuestionScheduler {

    const val EXTRA_PLAN_ID = "plan_id"
    const val EXTRA_ASKED_AT = "asked_at"

    /** 未获精确闹钟授权时的降级窗口（10 分钟） */
    private const val WINDOW_MS = 10 * 60 * 1000L

    /** 全部计划变更后调用：取消旧闹钟 → 为启用中的计划设置下一次触发 */
    fun rescheduleAll(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            val db = TimeNoteDatabase.get(context)
            db.questionPlanDao().getAll().forEach { cancel(context, it.id) }
            db.questionPlanDao().getEnabled().forEach { schedule(context, it) }
        }
    }

    /** 为单个计划设置下一次触发（非阻塞，仅主线程无关的轻量操作） */
    fun schedule(context: Context, plan: QuestionPlan) {
        if (!plan.enabled) return
        val next = nextOccurrenceMillis(plan, System.currentTimeMillis()) ?: return
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, QuestionAlarmReceiver::class.java).apply {
            putExtra(EXTRA_PLAN_ID, plan.id)
            putExtra(EXTRA_ASKED_AT, next)
        }
        val pi = pendingIntent(context, plan.id, intent)
        if (canScheduleExact(context)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
            } catch (_: SecurityException) {
                // 精确闹钟授权被即时撤销：降级为 10 分钟窗口闹钟
                am.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, pi)
            }
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, next, WINDOW_MS, pi)
        }
    }

    /** 取消某计划的闹钟 */
    fun cancel(context: Context, planId: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, planId, Intent(context, QuestionAlarmReceiver::class.java)))
    }

    /** Android 12+ 精确闹钟需用户授权；否则降级为窗口闹钟 */
    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    private fun pendingIntent(context: Context, planId: Long, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            planId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 计算计划的下一次触发时刻（毫秒）。
     *  [QuestionPlan.intervalDays] ≥ 2 时按「每隔 N 天」（[QuestionPlan.intervalAnchor] 为基准日）；
     *  否则按星期 bitmask（bit0=周一 … bit6=周日）。 */
    fun nextOccurrenceMillis(plan: QuestionPlan, nowMillis: Long): Long? {
        val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), ZoneId.systemDefault())
        val hour = plan.minutesOfDay / 60
        val minute = plan.minutesOfDay % 60

        if (plan.intervalDays >= 2) {
            val anchor = Instant.ofEpochMilli(plan.intervalAnchor.takeIf { it > 0 } ?: plan.createdAt)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val n = plan.intervalDays
            // 从今天起检查 n+7 天足够覆盖下一个满足 (距 anchor 天数 % n == 0) 的日期
            for (i in 0 until (n + 7).toLong()) {
                val date = now.toLocalDate().plusDays(i)
                val daysFromAnchor = ChronoUnit.DAYS.between(anchor, date)
                if (daysFromAnchor < 0) continue // 基准日未到：等过了基准日再触发
                val candidate = date.atTime(hour, minute)
                if (candidate.isAfter(now) && daysFromAnchor % n == 0L) {
                    return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
            return null
        }

        for (i in 0..6) {
            val date = now.toLocalDate().plusDays(i.toLong())
            val candidate = date.atTime(hour, minute)
            if (candidate.isAfter(now) && isWeekdayAllowed(plan.weekDays, date.dayOfWeek)) {
                return candidate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
        }
        return null
    }

    private fun isWeekdayAllowed(weekDays: Int, day: DayOfWeek): Boolean {
        val bit = day.value - 1 // ISO：周一=1 … 周日=7 → 位 0..6
        return (weekDays shr bit) and 1 == 1
    }
}
