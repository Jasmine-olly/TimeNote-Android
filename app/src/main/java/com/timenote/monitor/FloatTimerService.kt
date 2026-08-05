package com.timenote.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.timenote.MainActivity
import com.timenote.R
import com.timenote.data.TimeNoteDatabase
import com.timenote.util.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * F1 监督前台服务（PRD F1.2 / F1.3 / F1.4）
 *
 * 职责：
 * - 每秒驱动 [SessionTracker] 状态机
 * - 前台为娱乐应用时显示悬浮计时器，非娱乐时隐藏
 * - 达到阈值弹出提醒弹窗（再玩5分钟 / 退出应用 / 关闭提醒）
 * - 【退出应用】展示 10 秒倒计时的退出引导页
 */
class FloatTimerService : Service() {

    companion object {
        private const val TAG = "FloatTimerService"
        private const val CHANNEL_ID = "timenote_monitor"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 1_000L

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FloatTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatTimerService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager

    private val tracker = SessionTracker { Prefs.getThresholdsMs(this@FloatTimerService) }

    private var timerView: View? = null
    private var timerLp: WindowManager.LayoutParams? = null
    private var reminderView: View? = null
    private var farewellView: View? = null

    /** 锁屏重置会话（PRD F1.2：锁屏后计时停止/重置） */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) tracker.reset()
        }
    }

    /** 娱乐清单缓存（每秒来自数据库过于频繁，每 10 秒刷新） */
    private var entertainmentPackages: Set<String> = emptySet()

    /** 最近一次前台包名（延时检测模式：后台每 5 秒刷新，避免主线程被系统查询阻塞） */
    @Volatile
    private var lastForeground: String? = null
    private var tickCount = 0

    private val ticker = object : Runnable {
        override fun run() {
            try {
                onTick()
            } catch (t: Throwable) {
                // 防止单次异常中断心跳循环（否则检测/计时会悄悄停摆）
                Log.e(TAG, "onTick error", t)
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // START_STICKY：被系统/ROM 后台清理后，由系统自动重建服务继续监督
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        refreshEntertainmentPackages()
        runCatching { registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF)) }
        handler.post(ticker)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        runCatching { unregisterReceiver(screenOffReceiver) }
        handler.removeCallbacks(ticker)
        scope.cancel()
        removeTimerView()
        removeReminderView()
        removeFarewellView()
    }

    // ---------- 每秒心跳 ----------

    private fun onTick() {
        tickCount++
        if (tickCount % 10 == 0) refreshEntertainmentPackages()

        val foreground = currentForeground()
        tracker.onForegroundChanged(foreground, entertainmentPackages)
        tracker.onTick()

        if (tickCount % 5 == 0) {
            Log.d(
                TAG,
                "tick=$tickCount fg=$foreground active=${tracker.activePackage} " +
                    "dur=${tracker.durationMs} remind=${tracker.reminderVisible} " +
                    "acc=${ForegroundState.accessibilityConnected} ent=${entertainmentPackages.size}",
            )
        }

        updateTimerOverlay()

        if (tracker.reminderVisible && reminderView == null) showReminderView()
        if (!tracker.reminderVisible && reminderView != null) removeReminderView()
    }

    /**
     * 当前前台包名：
     * 无障碍为实时主通道；未开启时降级到使用情况统计（每 5 秒查询一次，
     * 中间用最近一次值填充，避免频繁 null 导致会话误重置）。
     */
    private fun currentForeground(): String? =
        if (ForegroundState.accessibilityConnected && Prefs.isPreciseDetection(this)) {
            // 精确检测：无障碍实时（可在监督页关闭）
            ForegroundState.currentPackage
        } else {
            // 延时检测：每 5 秒在后台线程查一次 UsageStats，中间用最近值填充。
            // 不在主线程执行系统查询，否则整机 UI 会被周期性卡顿（回答弹窗等界面点按变慢）。
            if (tickCount % 5 == 0) {
                scope.launch {
                    lastForeground = UsageStatsHelper.getForegroundPackage(this@FloatTimerService)
                }
            }
            lastForeground
        }

    private fun refreshEntertainmentPackages() {
        scope.launch {
            entertainmentPackages = TimeNoteDatabase.get(this@FloatTimerService)
                .entertainmentAppDao()
                .getAll()
                .map { it.packageName }
                .toSet()
        }
    }

    // ---------- 悬浮计时器（F1.2） ----------

    private fun updateTimerOverlay() {
        val active = tracker.activePackage != null
        if (active && timerView == null) addTimerView()
        if (!active && timerView != null) removeTimerView()
        if (active && timerView != null) {
            timerView?.findViewById<TextView>(R.id.timer_text)
                ?.text = "已使用 ${formatDuration(tracker.durationMs)}"
        }
    }

    private fun addTimerView() {
        val view = LayoutInflater.from(this).inflate(R.layout.floating_timer, null)
        val dm = resources.displayMetrics
        val saved = Prefs.getTimerPos(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 未保存过位置时默认放顶部中间附近，显示后精确居中
            x = saved?.first ?: (dm.widthPixels / 2)
            y = saved?.second ?: (48 * dm.density).toInt()
        }

        // 拖动移动位置（PRD 未决事项：悬浮计时器可拖动），松手记忆到 Prefs
        var downRawX = 0f
        var downRawY = 0f
        var downX = 0
        var downY = 0
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = lp.x
                    downY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = (downX + (event.rawX - downRawX)).toInt()
                    lp.y = (downY + (event.rawY - downRawY)).toInt()
                    runCatching { windowManager.updateViewLayout(v, lp) }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    Prefs.saveTimerPos(this, lp.x, lp.y)
                    true
                }
                else -> false
            }
        }

        windowManager.addView(view, lp)
        timerView = view
        timerLp = lp

        // 首次显示：若未保存过位置，把胶囊水平居中
        if (saved == null) {
            view.post {
                val centeredX = ((dm.widthPixels - view.width) / 2).coerceAtLeast(0)
                lp.x = centeredX
                runCatching { windowManager.updateViewLayout(view, lp) }
            }
        }
    }

    private fun removeTimerView() {
        timerView?.let { runCatching { windowManager.removeView(it) } }
        timerView = null
        timerLp = null
    }

    // ---------- 阈值提醒弹窗（F1.3） ----------

    private fun showReminderView() {
        val view = LayoutInflater.from(this).inflate(R.layout.reminder_dialog, null)
        view.findViewById<TextView>(R.id.reminder_msg)
            .text = "已连续使用娱乐应用 ${formatDuration(tracker.durationMs)}"

        val snoozeBtn = view.findViewById<TextView>(R.id.btn_snooze)
        snoozeBtn.setOnClickListener {
            if (tracker.snooze()) removeReminderView()
        }
        // 到达「再玩5分钟」上限：禁用并提示（PRD 未决事项）
        if (!tracker.canSnooze) {
            snoozeBtn.isEnabled = false
            snoozeBtn.text = "再玩5分钟（已用完）"
        }
        view.findViewById<View>(R.id.btn_exit).setOnClickListener {
            tracker.dismissReminder()
            removeReminderView()
            showFarewellView()
        }
        view.findViewById<View>(R.id.btn_close).setOnClickListener {
            tracker.dismissReminder()
            removeReminderView()
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(view, lp)
        reminderView = view
    }

    private fun removeReminderView() {
        reminderView?.let { runCatching { windowManager.removeView(it) } }
        reminderView = null
    }

    // ---------- 退出引导页（F1.4） ----------

    private fun showFarewellView() {
        val view = LayoutInflater.from(this).inflate(R.layout.farewell_dialog, null)
        val countdown = view.findViewById<TextView>(R.id.farewell_countdown)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        windowManager.addView(view, lp)
        farewellView = view

        view.findViewById<View>(R.id.btn_i_am_ready).setOnClickListener { removeFarewellView() }

        var remaining = 10
        val countdownTick = object : Runnable {
            override fun run() {
                remaining--
                if (remaining <= 0) {
                    removeFarewellView()
                    return
                }
                countdown.text = remaining.toString()
                handler.postDelayed(this, 1_000L)
            }
        }
        handler.postDelayed(countdownTick, 1_000L)
    }

    private fun removeFarewellView() {
        farewellView?.let { runCatching { windowManager.removeView(it) } }
        farewellView = null
    }

    // ---------- 工具 ----------

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", m, s)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TimeNote 监督",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = "前台检测与娱乐使用计时" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TimeNote 正在监督")
            .setContentText("检测娱乐应用使用时长，数据仅保存在本机")
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()
    }
}
