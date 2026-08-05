package com.timenote.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timenote.ui.common.HandDrawnLine
import com.timenote.ui.theme.Green
import java.time.LocalDate

/** 统计页（V1.2）：周报 / 月报可视化，纯本地数据 */
@Composable
fun StatsScreen(vm: StatsViewModel) {
    val period by vm.period.collectAsState()
    val report by vm.report.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("统计", style = MaterialTheme.typography.headlineSmall)
        HandDrawnLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )

        // 周期切换
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatsPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick = { vm.setPeriod(p) },
                    label = { Text(p.label) },
                )
            }
        }

        val r = report
        if (r == null) {
            Text("加载中…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        // ---- 总览 ----
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(vertical = 12.dp)) {
                StatTile("娱乐总时长", formatDuration(r.totalUsageMs), Modifier.weight(1f))
                StatTile("日均", formatDuration(r.totalUsageMs / r.period.days), Modifier.weight(1f))
            }
            Row(Modifier.padding(bottom = 12.dp)) {
                StatTile("提问", "${r.questionTotal} 条", Modifier.weight(1f))
                StatTile("回答率", "${r.answerRate}%", Modifier.weight(1f))
            }
        }

        // ---- 每日娱乐时长 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("每日娱乐时长", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (r.totalUsageMs <= 0) "这段时间没有娱乐使用记录" else "点柱子可查看当天时长",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                if (r.totalUsageMs <= 0) {
                    Text("（需勾选娱乐应用并授权使用情况访问）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DailyBarChart(days = r.days, usage = r.dailyUsageMs, today = LocalDate.now())
                }
            }
        }

        // ---- 应用排行 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("娱乐应用排行", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (r.appTotals.isEmpty()) {
                    Text("无娱乐应用数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    AppRankBars(r.appTotals)
                }
            }
        }

        // ---- 提问情况 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("定时提问", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (r.questionTotal == 0) {
                    Text("无提问记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        QuestionTile("按时答", "${r.questionAnswered - r.questionSupplementary}")
                        QuestionTile("补答", "${r.questionSupplementary}")
                        QuestionTile("未答", "${r.questionUnanswered}")
                    }
                    Spacer(Modifier.height(12.dp))
                    // 回答率条形
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("回答率", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            if (r.answerRate > 0) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(r.answerRate / 100f)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Green),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${r.answerRate}%", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** 总览指标块：label + 数值（weight 由调用方在 Row 里传入） */
@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 提问小项：数值 + 标签 */
@Composable
private fun QuestionTile(label: String, value: String) {
    Column {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 每日娱乐时长柱状图：单色绿（今天深绿）、左轴时长刻度、点柱查看当天时长、索引均匀横轴标签 */
@Composable
private fun DailyBarChart(days: List<LocalDate>, usage: List<Long>, today: LocalDate) {
    val max = usage.maxOrNull() ?: 0L
    // 纵轴满刻度取整到 30 分钟，避免柱子顶到边界
    val niceMax = if (max <= 0) 30 * 60_000L else ((max + 30 * 60_000L - 1) / (30 * 60_000L)) * 30 * 60_000L
    var selected by remember(days) { mutableStateOf<Int?>(null) }
    // 横轴标签：周报每天标日号；月报按索引均匀每 5 天标一个（避免跨月日号错位）
    val labels = days.mapIndexed { i, d ->
        when {
            days.size <= 7 -> "${d.dayOfMonth}"
            i % 5 == 0 || i == days.lastIndex -> "${d.dayOfMonth}"
            else -> ""
        }
    }
    val grid = Color(0xFFE1E0D9)
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column {
        Row {
            // ---- 左轴时长刻度（满 / 半 / 0） ----
            Column(
                modifier = Modifier
                    .height(160.dp)
                    .width(64.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                AxisLabel(formatAxis(niceMax))
                AxisLabel(formatAxis(niceMax / 2))
                AxisLabel("0")
            }
            // ---- 图表区（横轴标签在画布内绘制，按槽位精确居中，两位日期不被窄格截断） ----
            Canvas(
                Modifier
                    .weight(1f)
                    .height(160.dp)
                    .pointerInput(days, usage) {
                        detectTapGestures { offset ->
                            val slot = size.width.toFloat() / days.size
                            selected = (offset.x / slot).toInt().coerceIn(0, days.size - 1)
                        }
                    },
            ) {
                val labelH = 14.dp.toPx()
                val areaHeight = size.height - labelH
                // 细网格线：顶（满）/ 中（半）/ 基线（0），与左轴刻度对应
                listOf(0f, areaHeight / 2, areaHeight).forEach { y ->
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                }
                val slot = size.width / days.size
                if (niceMax > 0) {
                    val barWidth = (slot * 0.62f).coerceAtMost(24.dp.toPx())
                    usage.forEachIndexed { i, ms ->
                        val h = areaHeight * (ms.toFloat() / niceMax)
                        if (h > 0) {
                            val left = i * slot + (slot - barWidth) / 2f
                            val color = when {
                                i == selected -> SelectedGreen
                                days[i] == today -> DarkGreen
                                else -> Green
                            }
                            val radius = barWidth / 2f
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(left, areaHeight - h),
                                size = Size(barWidth, h),
                                cornerRadius = CornerRadius(radius),
                            )
                        }
                    }
                }
                // 横轴日期标签
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 10.sp.toPx()
                    color = axisColor.toArgb()
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                labels.forEachIndexed { i, label ->
                    if (label.isNotEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            i * slot + slot / 2f,
                            areaHeight + labelH - 3.dp.toPx(),
                            paint,
                        )
                    }
                }
            }
        }
        // ---- 数值提示：点选显示当天，未点选显示峰值 ----
        Spacer(Modifier.height(4.dp))
        val sel = selected
        val peak = usage.indices.maxByOrNull { usage[it] }
        val info = if (sel != null && sel in usage.indices) {
            "${days[sel].formatDay()} · ${formatDuration(usage[sel])}"
        } else if (peak != null && usage[peak] > 0) {
            "峰值：${days[peak].formatDay()} ${formatDuration(usage[peak])}"
        } else {
            ""
        }
        if (info.isNotEmpty()) {
            Text(
                info,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 左轴刻度文本（右对齐、紧凑格式） */
@Composable
private fun AxisLabel(text: String) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.End,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 轴刻度紧凑时长格式：如 "1时30分" / "45分" */
private fun formatAxis(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分"
    }
}

/** 应用时长排行：标签 + 填充条（单色绿）+ 时长 */
@Composable
private fun AppRankBars(appTotals: List<Pair<String, Long>>) {
    val max = appTotals.maxOfOrNull { it.second } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        appTotals.forEach { (label, ms) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    modifier = Modifier.width(96.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (ms > 0) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(ms.toFloat() / max)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Green),
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration(ms),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun LocalDate.formatDay(): String = "${monthValue}月${dayOfMonth}日"

private fun formatDuration(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> "${h}小时${m}分"
        h > 0 -> "${h}小时"
        else -> "${m}分钟"
    }
}

private val DarkGreen = Color(0xFF5A7456)    // 今天柱（深鼠尾草绿）
private val SelectedGreen = Color(0xFF475E43) // 点选柱（更深）
