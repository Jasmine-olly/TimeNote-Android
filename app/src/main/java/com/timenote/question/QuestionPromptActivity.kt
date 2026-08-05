package com.timenote.question

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.timenote.monitor.ForegroundState
import com.timenote.monitor.UsageStatsHelper
import com.timenote.ui.theme.Green
import com.timenote.ui.theme.TimeNoteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 到点提问弹窗（F2.2）：悬浮对话框 Activity，作答自动附带场景信息（当前前台应用）。
 * 后台由闹钟接收器启动；需悬浮窗权限（后台启动豁免），否则记录留在「待回答」。
 */
class QuestionPromptActivity : ComponentActivity() {

    companion object {
        const val EXTRA_RECORD_ID = "record_id"
    }

    private val viewModel: QuestionPromptViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val recordId = intent.getLongExtra(EXTRA_RECORD_ID, -1L)
        viewModel.loadRecord(recordId)

        setContent {
            TimeNoteTheme {
                // 场景信息（当前前台 App）在后台线程获取，避免系统 UsageStats 查询阻塞弹窗首帧
                var scenePackage by remember { mutableStateOf<String?>(null) }
                var sceneLabel by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val (pkg, label) = withContext(Dispatchers.IO) { sceneInfo() }
                    scenePackage = pkg
                    sceneLabel = label
                }
                QuestionPromptContent(
                    viewModel = viewModel,
                    scenePackage = scenePackage,
                    sceneLabel = sceneLabel,
                    onDone = { finish() },
                )
            }
        }
    }

    /** 作答时的场景信息（PRD F2.2：自动附带当前正在使用的 App） */
    private fun sceneInfo(): Pair<String?, String?> {
        val pkg = ForegroundState.currentPackage
            ?: runCatching { UsageStatsHelper.getForegroundPackage(this) }.getOrNull()
        if (pkg == null) return null to null
        val label = runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrNull()
        return pkg to label
    }
}

@Composable
private fun QuestionPromptContent(
    viewModel: QuestionPromptViewModel,
    scenePackage: String?,
    sceneLabel: String?,
    onDone: () -> Unit,
) {
    val record by viewModel.record.collectAsState()
    var answer by remember { mutableStateOf("") }

    // 兜底：极少数情况下通知点击与作答竞争，已作答的问题不再进入答题页
    LaunchedEffect(record) {
        if (record?.answer?.isNotBlank() == true) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "TimeNote 提问",
                    style = MaterialTheme.typography.labelMedium,
                    color = Green,
                )
                Spacer(Modifier.height(8.dp))
                if (record == null) {
                    Text("加载中…")
                } else {
                    record?.let { r ->
                        Text(r.question, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "提问于 ${formatDateTime(r.askedAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "当前正在使用：${sceneLabel ?: scenePackage ?: "未知"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("你的回答") },
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                enabled = answer.isNotBlank(),
                                onClick = {
                                    viewModel.submit(answer.trim(), scenePackage, sceneLabel) { onDone() }
                                },
                            ) { Text("回答") }
                            OutlinedButton(onClick = {
                                viewModel.dismissPrompt(r.id)
                                onDone()
                            }) { Text("稍后再答") }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(millis))
