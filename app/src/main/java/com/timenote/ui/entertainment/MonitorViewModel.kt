package com.timenote.ui.entertainment

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.repository.EntertainmentRepository
import com.timenote.monitor.FloatTimerService
import com.timenote.util.PermissionUtils
import com.timenote.util.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

/** F1 监督页 ViewModel：权限、服务开关、娱乐清单、阈值 */
class MonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TimeNoteDatabase.get(application)
    private val repository = EntertainmentRepository(db.entertainmentAppDao())

    /** 已勾选的娱乐应用包名集合（UI 实时刷新） */
    val selectedPackages: StateFlow<Set<String>> =
        repository.observeApps()
            .map { list -> list.map { it.packageName }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    val permissions = MutableStateFlow(PermissionUtils.State.refresh(application))
    val monitoring = MutableStateFlow(FloatTimerService.isRunning)
    val thresholdsText = MutableStateFlow(
        Prefs.getThresholdsMs(application).map { it / 60_000L }.joinToString(","),
    )
    private val _preciseDetection = MutableStateFlow(Prefs.isPreciseDetection(application))
    val preciseDetection: StateFlow<Boolean> = _preciseDetection.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = loadLauncherApps(getApplication())
        }
    }

    fun refreshPermissions() {
        val app = getApplication<Application>()
        permissions.value = PermissionUtils.State.refresh(app)
        monitoring.value = FloatTimerService.isRunning
    }

    fun toggleApp(packageName: String, label: String, checked: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setAppSelected(packageName, label, checked)
        }
    }

    /** 开启监督：需要悬浮窗 + 使用情况访问权限；返回是否成功 */
    fun startMonitoring(): Boolean {
        val app = getApplication<Application>()
        if (!PermissionUtils.hasOverlayPermission(app)) return false
        if (!PermissionUtils.hasUsageAccess(app)) return false
        FloatTimerService.start(app)
        monitoring.value = true
        return true
    }

    fun stopMonitoring() {
        FloatTimerService.stop(getApplication())
        monitoring.value = false
    }

    fun saveThresholds(raw: String) {
        val minutes = raw.split(',', '，').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }
        if (minutes.isEmpty()) return
        Prefs.saveThresholdsMinutes(getApplication(), minutes)
        thresholdsText.value = minutes.joinToString(",")
    }

    /** 精确检测开关：开=无障碍实时；关=系统 5 秒延时（无需无障碍） */
    fun setPreciseDetection(enabled: Boolean) {
        Prefs.setPreciseDetection(getApplication(), enabled)
        _preciseDetection.value = enabled
    }
}

/** 已安装应用的信息（用于勾选娱乐清单） */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable?,
)

/** 查询所有可启动的应用（含图标），按名称排序 */
fun loadLauncherApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { resolveInfo ->
            AppInfo(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName,
                icon = resolveInfo.loadIcon(pm),
            )
        }
        .distinctBy { it.packageName }
        // 中文按拼音、英文按字母排序
        .sortedWith(compareBy(Collator.getInstance(Locale.CHINA)) { it.label })
}
