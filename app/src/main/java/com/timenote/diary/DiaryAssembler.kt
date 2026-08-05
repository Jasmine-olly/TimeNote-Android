package com.timenote.diary

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.AnswerRecord
import com.timenote.ui.diary.Covers
import com.timenote.util.PermissionUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日记自动汇编（F3.1）：把当日「娱乐统计 + 定时提问回答」拼成 Markdown 日记。
 *
 * 娱乐统计直接读取系统 UsageStats（按天聚合），无需额外落库。
 */
object DiaryAssembler {

    private val ISO = DateTimeFormatter.ISO_LOCAL_DATE
    private val DISPLAY = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

    suspend fun build(context: Context, date: LocalDate): String {
        val db = TimeNoteDatabase.get(context)
        val answers = db.answerRecordDao().getOnDay(date)
        val usage = usageStatsFor(context, date)

        val sb = StringBuilder()
        sb.appendLine("# ${date.format(DISPLAY)}")
        sb.appendLine()
        sb.appendLine("## 天气")
        sb.appendLine("（未填写，可手动编辑）")
        sb.appendLine()
        sb.appendLine("## 娱乐使用")
        if (usage.isEmpty()) {
            sb.appendLine("- 今日未记录到娱乐应用使用")
        } else {
            val total = usage.values.sum()
            usage.forEach { (label, ms) -> sb.appendLine("- ${label} ${formatDuration(ms)}") }
            sb.appendLine("- **合计** ${formatDuration(total)}")
        }
        sb.appendLine()
        sb.appendLine("## 定时提问")
        if (answers.isEmpty()) {
            sb.appendLine("（今日无定时提问记录）")
        } else {
            answers.forEach { a ->
                sb.appendLine("**${formatTime(a.askedAt)} · ${a.question}**")
                sb.appendLine(
                    if (a.answer.isBlank()) "> （未回答）" else "> ${a.answer}",
                )
                if (a.isSupplementary) {
                    sb.appendLine("- *补答于 ${formatDateTime(a.answeredAt)}*")
                }
                if (a.sceneAppLabel != null || a.sceneAppPackage != null) {
                    sb.appendLine("- *场景：${a.sceneAppLabel ?: a.sceneAppPackage}*")
                }
                sb.appendLine()
            }
        }
        sb.appendLine()
        sb.appendLine("## 小结")
        sb.appendLine(buildSummary(context, date, usage, answers))
        return sb.toString()
    }

    private suspend fun usageStatsFor(context: Context, date: LocalDate): LinkedHashMap<String, Long> {
        val result = LinkedHashMap<String, Long>()
        if (!PermissionUtils.hasUsageAccess(context)) return result

        val db = TimeNoteDatabase.get(context)
        val apps = db.entertainmentAppDao().getAll()
        if (apps.isEmpty()) return result
        val appByPkg = apps.associateBy { it.packageName }

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = dayStart + 86_400_000L
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayStart, dayEnd)

        val byPkg = stats
            .filter { appByPkg.containsKey(it.packageName) }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.maxOf { it.totalTimeInForeground } }
            .filterValues { it > 0 }
            .entries
            .sortedByDescending { it.value }

        byPkg.forEach { (pkg, ms) ->
            val label = appByPkg[pkg]?.label ?: pkg
            result[label] = ms
        }
        return result
    }

    /** 当日小结增强（F3.1）：分档时长 + 最常用应用占比 + 娱乐高峰时段 + 提问/补答情况 */
    private suspend fun buildSummary(
        context: Context,
        date: LocalDate,
        usage: LinkedHashMap<String, Long>,
        answers: List<AnswerRecord>,
    ): String {
        val totalMs = usage.values.sum()
        val answered = answers.count { it.answer.isNotBlank() }
        val supplementary = answers.count { it.isSupplementary }
        val pending = answers.count { it.answer.isBlank() }

        val parts = mutableListOf<String>()
        when {
            totalMs <= 0L -> parts += "今天没有使用娱乐应用，时间都花在了其他地方。"
            else -> {
                val tier = when {
                    totalMs < 30L * 60_000 -> "今天娱乐使用很少（${formatDuration(totalMs)}），时间管理做得很棒。"
                    totalMs < 90L * 60_000 -> "今天娱乐使用了 ${formatDuration(totalMs)}，节奏适中。"
                    else -> "今天娱乐使用了 ${formatDuration(totalMs)}，注意休息，给眼睛和大脑放个假。"
                }
                parts += tier
                // 最常用应用 + 占比（低于 20% 不强调）
                val top = usage.entries.firstOrNull()
                if (top != null && top.value > 0) {
                    val pct = top.value * 100 / totalMs
                    if (pct >= 20) parts += "最常用的是${top.key}（约${pct}%）。"
                }
                // 娱乐高峰时段（半小时以上才提，避免噪声）
                if (totalMs >= 30L * 60_000) {
                    peakUsageHour(context, date)?.let { h -> parts += "${hourLabel(h)}${h}点是娱乐高峰。" }
                }
            }
        }
        when {
            answered > 0 -> {
                val base = "完成了 $answered 条定时提问"
                parts += if (supplementary > 0) "$base，其中 $supplementary 条是补答的。" else "$base。"
            }
            pending > 0 -> parts += "有 $pending 条提问还没回答，记得补答。"
        }
        return parts.joinToString(" ")
    }

    /** 封面默认建议（PRD 未决事项：天气自动 + 心情关联）：
     *  按正文「天气」文本 → 天气 emoji，按回答情绪关键词 → 心情 emoji；无依据返回 null */
    fun suggestCover(content: String, answers: List<AnswerRecord>): String? {
        val weather = parseWeather(content)
        val mood = suggestMood(answers)
        return if (weather == null && mood == null) null else Covers.encode(weather, mood)
    }

    private fun parseWeather(content: String): String? {
        val lines = content.lines()
        val idx = lines.indexOfFirst { it.trim() == "## 天气" }
        if (idx < 0) return null
        val text = lines.drop(idx + 1).firstOrNull { it.isNotBlank() } ?: return null
        return when {
            text.contains("晴") -> "☀️"
            text.contains("雷") -> "⛈️"
            text.contains("雪") -> "❄️"
            text.contains("雨") -> "🌧️"
            text.contains("阴") || text.contains("云") -> "⛅"
            text.contains("雾") -> "🌫️"
            else -> null
        }
    }

    private fun suggestMood(answers: List<AnswerRecord>): String? {
        val text = answers.joinToString(" ") { it.answer }.trim()
        if (text.isEmpty()) return null
        // 顺序重要：负面关键词先于正面，避免「不好」被「好」误判
        return when {
            text.contains("惊喜") || text.contains("激动") || text.contains("超棒") -> "🤩"
            text.contains("兴奋") -> "😄"
            text.contains("难过") || text.contains("低落") || text.contains("伤心") ||
                text.contains("哭") || text.contains("烦") || text.contains("糟糕") ||
                text.contains("不好") || text.contains("很差") -> "😢"
            text.contains("累") || text.contains("困") || text.contains("疲惫") ||
                text.contains("没睡") || text.contains("熬夜") || text.contains("想睡") -> "😴"
            text.contains("开心") || text.contains("高兴") || text.contains("愉快") ||
                text.contains("棒") || text.contains("不错") || text.contains("满意") ||
                text.contains("好") || text.contains("还行") || text.contains("可以") -> "😊"
            else -> null
        }
    }

    /** 娱乐使用最多的小时（0-23）；无使用 / 无事件返回 null */
    private suspend fun peakUsageHour(context: Context, date: LocalDate): Int? {
        if (!PermissionUtils.hasUsageAccess(context)) return null
        val db = TimeNoteDatabase.get(context)
        val pkgSet = db.entertainmentAppDao().getAll().map { it.packageName }.toSet()
        if (pkgSet.isEmpty()) return null

        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val dayStart = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val dayEnd = dayStart + 86_400_000L
        val hourly = LongArray(24)
        val lastResume = HashMap<String, Long>()

        val events = usm.queryEvents(dayStart, dayEnd)
        val e = UsageEvents.Event()
        var anyEvent = false
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.packageName !in pkgSet) continue
            anyEvent = true
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> lastResume[e.packageName] = e.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val resume = lastResume.remove(e.packageName) ?: continue
                    addDuration(hourly, resume, e.timeStamp)
                }
            }
        }
        if (!anyEvent) return null
        // 处理跨天仍未结束的会话
        val now = System.currentTimeMillis()
        lastResume.forEach { (_, start) -> addDuration(hourly, start, minOf(dayEnd, now)) }

        val peak = hourly.indices.maxByOrNull { hourly[it] } ?: return null
        return if (hourly[peak] > 0) peak else null
    }

    /** 把 [start, end) 的时长按小时拆入 hourly 桶（处理跨小时/跨天） */
    private fun addDuration(hourly: LongArray, start: Long, end: Long) {
        if (end <= start) return
        var cur = start
        while (cur < end) {
            val zoned = Instant.ofEpochMilli(cur).atZone(ZoneId.systemDefault())
            val hour = zoned.hour
            val nextBoundary = if (hour == 23) {
                zoned.toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } else {
                zoned.toLocalDate().atTime(hour + 1, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }
            val nextEnd = minOf(end, nextBoundary)
            hourly[hour] += (nextEnd - cur)
            cur = nextEnd
        }
    }

    /** 小时 → 时段标签（用于「X点是娱乐高峰」） */
    private fun hourLabel(h: Int): String = when (h) {
        in 0..4 -> "凌晨"
        in 5..8 -> "早晨"
        in 9..11 -> "上午"
        in 12..13 -> "中午"
        in 14..17 -> "下午"
        else -> "晚上"
    }

    private fun formatDuration(ms: Long): String {
        val totalMin = ms / 60_000
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 && m > 0 -> "${h}小时${m}分钟"
            h > 0 -> "${h}小时"
            else -> "${m}分钟"
        }
    }

    private fun formatTime(millis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm")
            .format(java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

    private fun formatDateTime(millis: Long): String =
        DateTimeFormatter.ofPattern("HH:mm")
            .format(java.time.Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}
