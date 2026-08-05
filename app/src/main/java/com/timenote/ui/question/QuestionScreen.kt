package com.timenote.ui.question

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.timenote.data.entity.AnswerRecord
import com.timenote.data.entity.QuestionPlan
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/** F2 定时提问主界面：待回答 + 问题计划管理 */
@Composable
fun QuestionScreen(vm: QuestionViewModel) {
    val plans by vm.plans.collectAsState()
    val pending by vm.pending.collectAsState()

    var showAdd by rememberSaveable { mutableStateOf(false) }
    var editingPlan by remember { mutableStateOf<QuestionPlan?>(null) }
    var answering by remember { mutableStateOf<AnswerRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ---- 待回答（F2.3）----
        if (pending.isNotEmpty()) {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("待回答", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "到点时没来得及答的问题，点「补答」完成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    pending.forEach { r ->
                        PendingRow(
                            record = r,
                            onAnswer = { answering = r },
                            onDismiss = { vm.dismissPending(r) },
                        )
                    }
                }
            }
        }

        // ---- 问题计划（F2.1）----
        Card {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "问题计划",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = { showAdd = true }) { Text("＋ 新增") }
                }
                Text(
                    "到点自动弹出提问，可设置每周重复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (plans.isEmpty()) {
                    Text("还没有计划，点右上角「新增」", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    plans.forEach { p ->
                        PlanRow(
                            plan = p,
                            onToggle = { vm.togglePlan(p, it) },
                            onEdit = { editingPlan = p },
                            onDelete = { vm.deletePlan(p) },
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        PlanDialog(
            initial = null,
            onSave = { minutes, question, days, intervalDays, intervalAnchor ->
                vm.addPlan(minutes, question, days, intervalDays, intervalAnchor)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    editingPlan?.let { p ->
        PlanDialog(
            initial = p,
            onSave = { minutes, question, days, intervalDays, intervalAnchor ->
                vm.updatePlan(
                    p.copy(
                        minutesOfDay = minutes,
                        question = question,
                        weekDays = days,
                        intervalDays = intervalDays,
                        intervalAnchor = intervalAnchor,
                    ),
                )
                editingPlan = null
            },
            onDismiss = { editingPlan = null },
        )
    }
    answering?.let { r ->
        AnswerPendingDialog(
            record = r,
            onAnswer = { text ->
                vm.answerPending(r, text)
                answering = null
            },
            onDismiss = { answering = null },
        )
    }
}

@Composable
private fun PendingRow(record: AnswerRecord, onAnswer: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(record.question, fontWeight = FontWeight.Medium)
            Text(
                "提问于 ${formatDateTime(record.askedAt)} · 已错过",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onAnswer) { Text("补答") }
        TextButton(onClick = onDismiss) { Text("忽略") }
    }
}

@Composable
private fun PlanRow(
    plan: QuestionPlan,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${formatTime(plan.minutesOfDay)} · ${WeekDays.format(plan.weekDays, plan.intervalDays, plan.intervalAnchor)}",
                fontWeight = FontWeight.Medium,
                color = if (plan.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                plan.question,
                style = MaterialTheme.typography.bodyMedium,
                color = if (plan.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = plan.enabled, onCheckedChange = onToggle)
        TextButton(onClick = onEdit) { Text("改") }
        TextButton(onClick = onDelete) { Text("删") }
    }
}

/** 新增 / 编辑问题计划对话框 */
@Composable
private fun PlanDialog(
    initial: QuestionPlan?,
    onSave: (minutes: Int, question: String, weekDays: Int, intervalDays: Int, intervalAnchor: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    // 时间默认 6:00（24 小时制，时/分两个下拉）
    var hour by rememberSaveable(initial?.id) { mutableStateOf(initial?.let { it.minutesOfDay / 60 } ?: 6) }
    var minute by rememberSaveable(initial?.id) { mutableStateOf(initial?.let { it.minutesOfDay % 60 } ?: 0) }
    var question by rememberSaveable(initial?.id) { mutableStateOf(initial?.question ?: "") }
    var weekDays by rememberSaveable(initial?.id) { mutableStateOf(initial?.weekDays ?: QuestionPlan.EVERY_DAY) }
    val isInterval = (initial?.intervalDays ?: 0) >= 2
    var repeatMode by rememberSaveable(initial?.id) {
        mutableStateOf(if (isInterval) RepeatMode.Interval else RepeatMode.Weekly)
    }
    var intervalN by rememberSaveable(initial?.id) { mutableStateOf(initial?.intervalDays ?: 2) }
    var intervalAnchor by rememberSaveable(initial?.id) { mutableStateOf(initial?.intervalAnchor ?: 0L) }

    val minutes = hour * 60 + minute
    val valid = question.isNotBlank() && (repeatMode == RepeatMode.Interval || weekDays != 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增问题" else "编辑问题") },
        text = {
            Column {
                Text("时间（24 小时制）", style = MaterialTheme.typography.bodySmall)
                TimeDropdowns(
                    hour = hour,
                    minute = minute,
                    onHourChange = { hour = it },
                    onMinuteChange = { minute = it },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("问题内容") },
                )
                Spacer(Modifier.height(12.dp))
                Text("重复", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = repeatMode == RepeatMode.Weekly,
                        onClick = { repeatMode = RepeatMode.Weekly },
                        label = { Text("按星期") },
                    )
                    FilterChip(
                        selected = repeatMode == RepeatMode.Interval,
                        onClick = { repeatMode = RepeatMode.Interval },
                        label = { Text("每隔N天") },
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (repeatMode == RepeatMode.Weekly) {
                    // 预设：每日 / 工作日 / 周末
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = weekDays == QuestionPlan.EVERY_DAY,
                            onClick = { weekDays = QuestionPlan.EVERY_DAY },
                            label = { Text("每日") },
                        )
                        FilterChip(
                            selected = weekDays == QuestionPlan.WORKDAYS,
                            onClick = { weekDays = QuestionPlan.WORKDAYS },
                            label = { Text("工作日") },
                        )
                        FilterChip(
                            selected = weekDays == QuestionPlan.WEEKENDS,
                            onClick = { weekDays = QuestionPlan.WEEKENDS },
                            label = { Text("周末") },
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    // 周一~周三
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WeekDays.DAYS.take(3).forEachIndexed { i, name ->
                            val bit = 1 shl i
                            FilterChip(
                                selected = (weekDays and bit) != 0,
                                onClick = { weekDays = weekDays xor bit },
                                label = { Text("周$name") },
                            )
                        }
                    }
                    // 周四~周日
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WeekDays.DAYS.drop(3).forEachIndexed { offset, name ->
                            val bit = 1 shl (offset + 3)
                            FilterChip(
                                selected = (weekDays and bit) != 0,
                                onClick = { weekDays = weekDays xor bit },
                                label = { Text("周$name") },
                            )
                        }
                    }
                } else {
                    // 每隔 N 天：从基准日（默认今天）起按自然日计数
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每隔", style = MaterialTheme.typography.bodyMedium)
                        NumberDropdown(
                            label = "天",
                            selected = intervalN.coerceIn(2, 30),
                            range = 2..30,
                            onSelected = { intervalN = it },
                            modifier = Modifier.width(96.dp),
                        )
                        Text("触发一次", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "从 ${formatAnchor(intervalAnchor)} 起算（按自然日）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (repeatMode == RepeatMode.Interval) {
                        val anchor = intervalAnchor.takeIf { it > 0 }
                            ?: LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onSave(minutes, question.trim(), weekDays, intervalN.coerceIn(2, 30), anchor)
                    } else {
                        onSave(minutes, question.trim(), weekDays, 0, 0L)
                    }
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 时间选择：时 + 分两个下拉（24 小时制） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDropdowns(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NumberDropdown(
            label = "时",
            selected = hour,
            range = 0..23,
            onSelected = onHourChange,
            modifier = Modifier.weight(1f),
        )
        Text(":", style = MaterialTheme.typography.titleMedium)
        NumberDropdown(
            label = "分",
            selected = minute,
            range = 0..59,
            onSelected = onMinuteChange,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NumberDropdown(
    label: String,
    selected: Int,
    range: IntRange,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = String.format(Locale.getDefault(), "%02d", selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            range.forEach { value ->
                DropdownMenuItem(
                    text = { Text(String.format(Locale.getDefault(), "%02d", value)) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                )
            }
        }
    }
}

/** 补答对话框 */
@Composable
private fun AnswerPendingDialog(record: AnswerRecord, onAnswer: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("补答") },
        text = {
            Column {
                Text(record.question, fontWeight = FontWeight.Medium)
                Text(
                    "原始提问：${formatDateTime(record.askedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("现在补答") },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAnswer(text.trim()) }) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 星期 bitmask 工具：bit0=周一 … bit6=周日；intervalDays≥2 显示「每隔 N 天」 */
object WeekDays {
    val DAYS = listOf("一", "二", "三", "四", "五", "六", "日")

    fun format(bitmask: Int, intervalDays: Int = 0, intervalAnchor: Long = 0): String {
        if (intervalDays >= 2) {
            val anchor = intervalAnchor.takeIf { it > 0 }
                ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
            return "每${intervalDays}天" + (anchor?.let { "（${it.monthValue}月${it.dayOfMonth}日起）" } ?: "")
        }
        val names = DAYS.filterIndexed { i, _ -> (bitmask shr i) and 1 == 1 }
        return when {
            bitmask == QuestionPlan.EVERY_DAY -> "每天"
            bitmask == QuestionPlan.WORKDAYS -> "工作日"
            bitmask == QuestionPlan.WEEKENDS -> "周末"
            names.isEmpty() -> "未选择"
            names.size == 7 -> "每天"
            else -> "周" + names.joinToString("")
        }
    }
}

private enum class RepeatMode { Weekly, Interval }

/** 间隔模式基准日文案：0 = 未设置（保存时取今天） */
private fun formatAnchor(anchorMillis: Long): String =
    if (anchorMillis > 0) SimpleDateFormat("M月d日", Locale.getDefault()).format(Date(anchorMillis)) else "今天"

private fun formatTime(minutesOfDay: Int): String =
    String.format(Locale.getDefault(), "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(millis))
