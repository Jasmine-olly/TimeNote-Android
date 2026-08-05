package com.timenote.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.timenote.ui.common.HandDrawnLine
import com.timenote.util.Importer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 导出页（V1.2）：Markdown 日记 / JSON 全量备份，用户选位置保存，零联网 */
@Composable
fun ExportScreen(vm: ExportViewModel) {
    val status by vm.status.collectAsState()
    val stamp = rememberStamp()

    val mdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) vm.exportMarkdown(uri)
    }
    val jsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) vm.exportJson(uri)
    }
    val openBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) vm.onPickedBackup(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("导出", style = MaterialTheme.typography.headlineSmall)
        HandDrawnLine(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp),
        )
        Text(
            "所有数据只写入手机本地、由你在系统里选择保存位置，绝不联网上传。" +
                "适合备份、迁移或把日记拿走自行管理。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- 日记 Markdown ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("导出日记（Markdown）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "全部日记合并为一个 .md 文件，正文为可读的 Markdown，便于查看、转发或发布。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { mdLauncher.launch("TimeNote_日记_$stamp.md") }) {
                    Text("导出 Markdown")
                }
            }
        }

        // ---- 全量 JSON ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("导出全部数据（JSON）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "娱乐清单 / 问题计划 / 回答记录 / 日记 的结构化备份，便于未来迁移或程序化分析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { jsonLauncher.launch("TimeNote_备份_$stamp.json") }) {
                    Text("导出 JSON")
                }
            }
        }

        // ---- 导入恢复 ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("导入恢复（JSON 备份）", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "选择之前导出的备份文件恢复数据：合并 = 保留现有、跳过冲突；覆盖 = 清空后导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { openBackupLauncher.launch(arrayOf("application/json")) }) {
                    Text("选择备份文件导入")
                }
            }
        }

        status?.let {
            Spacer(Modifier.height(4.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }

    // ---- 导入确认对话框 ----
    val pending = vm.pendingImport.collectAsState().value
    pending?.let { (uri, p) ->
        AlertDialog(
            onDismissRequest = { vm.cancelImport() },
            title = { Text("导入备份") },
            text = {
                Column {
                    Text(
                        "检测到备份：娱乐 ${p.apps} 条 / 计划 ${p.plans} 条 / 回答 ${p.answers} 条 / 日记 ${p.diaries} 篇",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "合并：保留现有数据，跳过重复。\n覆盖：清空当前全部数据后再导入（不可恢复）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.importPending(Importer.Mode.Merge, uri) }) { Text("合并导入") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { vm.importPending(Importer.Mode.Replace, uri) }) { Text("覆盖导入") }
                    TextButton(onClick = { vm.cancelImport() }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun rememberStamp(): String {
    val date = remember { Date() }
    return SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(date)
}
