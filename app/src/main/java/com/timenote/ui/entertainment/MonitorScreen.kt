package com.timenote.ui.entertainment

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.timenote.ui.theme.Green
import com.timenote.util.PermissionUtils

/** F1 监督主界面：权限 → 服务开关 → 阈值 → 娱乐清单 */
@Composable
fun MonitorScreen(vm: MonitorViewModel) {
    val permissions by vm.permissions.collectAsState()
    val monitoring by vm.monitoring.collectAsState()
    val installedApps by vm.installedApps.collectAsState()
    val selected by vm.selectedPackages.collectAsState()
    val thresholds by vm.thresholdsText.collectAsState()
    val precise by vm.preciseDetection.collectAsState()

    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    // 从系统设置返回时刷新权限状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshPermissions() }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshPermissions() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PermissionCard(
            permissions = permissions,
            onOpenOverlay = { context.startActivity(PermissionUtils.overlaySettingsIntent(context)) },
            onOpenUsage = { context.startActivity(PermissionUtils.usageAccessSettingsIntent()) },
            onOpenExactAlarm = { context.startActivity(PermissionUtils.exactAlarmSettingsIntent(context)) },
            onRequestNotification = {
                notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
        )
        MonitoringCard(monitoring = monitoring, vm = vm)
        PreciseDetectionCard(
            precise = precise,
            accessibilityGranted = permissions.accessibility,
            onChange = vm::setPreciseDetection,
            onOpenAccessibility = { context.startActivity(PermissionUtils.accessibilitySettingsIntent()) },
        )
        ThresholdsCard(thresholds = thresholds, vm = vm)
        EntertainmentListCard(
            apps = installedApps,
            selected = selected,
            query = query,
            onQueryChange = { query = it },
            onToggle = vm::toggleApp,
        )
    }
}

@Composable
private fun PermissionCard(
    permissions: PermissionUtils.State,
    onOpenOverlay: () -> Unit,
    onOpenUsage: () -> Unit,
    onOpenExactAlarm: () -> Unit,
    onRequestNotification: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("权限状态", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            PermissionRow("悬浮窗", permissions.overlay, onOpenOverlay)
            PermissionRow("使用情况访问", permissions.usageAccess, onOpenUsage)
            PermissionRow("精确闹钟（到点准时提问）", permissions.exactAlarm, onOpenExactAlarm)
            PermissionRow("通知", permissions.notification, onRequestNotification)
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f))
        Text(
            if (granted) "已开启" else "未开启",
            color = if (granted) Green else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Medium,
        )
        if (!granted) {
            TextButton(onClick = onClick) { Text("去开启") }
        }
    }
}

@Composable
private fun MonitoringCard(monitoring: Boolean, vm: MonitorViewModel) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("监督服务", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (monitoring) "运行中 · 检测娱乐应用" else "未运行",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = monitoring,
                    onCheckedChange = { on ->
                        if (on) {
                            val ok = vm.startMonitoring()
                            if (!ok) {
                                // 权限不足：提示后停在关闭态（由权限卡片引导）
                                vm.refreshPermissions()
                            }
                        } else {
                            vm.stopMonitoring()
                        }
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "开启后：进入娱乐应用即显示悬浮计时，达到阈值弹出提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PreciseDetectionCard(
    precise: Boolean,
    accessibilityGranted: Boolean,
    onChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("精确检测", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            precise && accessibilityGranted -> "实时 · 无障碍已开启"
                            precise -> "实时 · 需开启无障碍"
                            else -> "延时 · 系统 5 秒检测"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = precise, onCheckedChange = onChange)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "开启：无障碍实时检测前台，进入/切出即时响应。\n关闭：只用系统使用统计，约 5 秒延迟、无需无障碍（时长数据不受影响）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (precise && !accessibilityGranted) {
                TextButton(onClick = onOpenAccessibility) { Text("去开启无障碍（实时检测）") }
            }
        }
    }
}

@Composable
private fun ThresholdsCard(thresholds: String, vm: MonitorViewModel) {
    var text by rememberSaveable { mutableStateOf(thresholds) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("提醒阈值（分钟，逗号分隔）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("如 30,60,90") },
                singleLine = true,
            )
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = { vm.saveThresholds(text) }) { Text("保存阈值") }
        }
    }
}

@Composable
private fun EntertainmentListCard(
    apps: List<AppInfo>,
    selected: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String, String, Boolean) -> Unit,
) {
    val filtered = apps.filter {
        query.isBlank() ||
            it.label.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
    }
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("娱乐应用清单", style = MaterialTheme.typography.titleMedium)
            Text(
                "勾选的应用计入「娱乐使用时长」 · 已勾选 ${selected.size} 个",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索应用") },
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Text("未找到应用", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.heightIn(max = 480.dp)) {
                    items(filtered, key = { it.packageName }) { app ->
                        AppRow(
                            app = app,
                            checked = app.packageName in selected,
                            onToggle = { checked -> onToggle(app.packageName, app.label, checked) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: AppInfo, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.icon?.toBitmap(width = 56, height = 56)?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = app.label,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(app.label, fontWeight = FontWeight.Medium)
            Text(
                app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}
