package com.timenote.ui.stats

import android.app.Application
import android.app.usage.UsageStatsManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timenote.data.TimeNoteDatabase
import com.timenote.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 统计周期：周报 = 最近 7 天，月报 = 最近 30 天（滚动到今日） */
enum class StatsPeriod(val days: Int, val label: String) {
    Week(7, "周报"),
    Month(30, "月报"),
}

/** 一份周期报告的计算结果 */
data class StatsReport(
    val period: StatsPeriod,
    val days: List<LocalDate>,
    val dailyUsageMs: List<Long>,          // 与 days 一一对应
    val appTotals: List<Pair<String, Long>>, // label -> ms，按使用时长降序
    val totalUsageMs: Long,
    val questionTotal: Int,
    val questionAnswered: Int,             // 非空回答
    val questionSupplementary: Int,        // 补答
    val questionUnanswered: Int,           // 未答
) {
    val answerRate: Int get() =
        if (questionTotal > 0) questionAnswered * 100 / questionTotal else 0
}

/** 统计页 ViewModel（V1.2）：汇总娱乐使用（UsageStats）+ 定时提问回答，纯本地 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val _period = MutableStateFlow(StatsPeriod.Week)
    val period: StateFlow<StatsPeriod> = _period.asStateFlow()

    private val _report = MutableStateFlow<StatsReport?>(null)
    val report: StateFlow<StatsReport?> = _report.asStateFlow()

    init {
        load(_period.value)
    }

    fun setPeriod(p: StatsPeriod) {
        if (p == _period.value) return
        _period.value = p
        load(p)
    }

    private fun load(period: StatsPeriod) {
        viewModelScope.launch(Dispatchers.IO) {
            val app = getApplication<Application>()
            val db = TimeNoteDatabase.get(app)

            val today = LocalDate.now()
            val days = (period.days - 1 downTo 0).map { today.minusDays(it.toLong()) }
            val dayStart = days.first().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // ---- 娱乐使用：UsageStats 按天聚合 ----
            val apps = db.entertainmentAppDao().getAll()
            val pkgSet = apps.map { it.packageName }.toSet()
            val labelByPkg = apps.associateBy { it.packageName }

            val daily = HashMap<LocalDate, Long>()
            val appTotal = HashMap<String, Long>()

            if (PermissionUtils.hasUsageAccess(app)) {
                val usm = app.getSystemService(android.content.Context.USAGE_STATS_SERVICE) as UsageStatsManager
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)
                    .filter { it.packageName in pkgSet && it.totalTimeInForeground > 0 }
                    .forEach { s ->
                        val date = Instant.ofEpochMilli(s.firstTimeStamp)
                            .atZone(ZoneId.systemDefault()).toLocalDate()
                        if (date in days) {
                            daily[date] = (daily[date] ?: 0L) + s.totalTimeInForeground
                            val label = labelByPkg[s.packageName]?.label ?: s.packageName
                            appTotal[label] = (appTotal[label] ?: 0L) + s.totalTimeInForeground
                        }
                    }
            }

            val dailyUsage = days.map { daily[it] ?: 0L }
            val totalUsage = dailyUsage.sum()
            val appTotals = appTotal.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }

            // ---- 定时提问：回答记录统计 ----
            val answers = db.answerRecordDao().getBetween(dayStart, dayEnd)
            var questionTotal = answers.size
            var answered = answers.count { it.answer.isNotBlank() }
            var supplementary = answers.count { it.isSupplementary }
            var unanswered = answers.count { it.answer.isBlank() }

            _report.value = StatsReport(
                period = period,
                days = days,
                dailyUsageMs = dailyUsage,
                appTotals = appTotals,
                totalUsageMs = totalUsage,
                questionTotal = questionTotal,
                questionAnswered = answered,
                questionSupplementary = supplementary,
                questionUnanswered = unanswered,
            )
        }
    }
}
