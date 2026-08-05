package com.timenote.question

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.timenote.R

/**
 * 到点提问通知兜底（F2.2）：即使后台弹窗被 ROM 延迟/拦截，也保证问题浮出（点通知可回答）。
 *
 * 通知 id 与回答记录 id 绑定；用户作答或「稍后再答」后由 [cancel] 移除，
 * 避免点击旧通知重复进入答题页。
 */
object QuestionNotification {

    private const val CHANNEL_ID = "timenote_question"

    /** 通知 id：与回答记录 id 一致（Integer 范围内），便于按记录取消 */
    private fun id(recordId: Long) = recordId.toInt()

    fun post(context: Context, recordId: Long, question: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "TimeNote 提问", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "到点提问与补答提醒" },
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            recordId.toInt(),
            Intent(context, QuestionPromptActivity::class.java)
                .putExtra(QuestionPromptActivity.EXTRA_RECORD_ID, recordId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_monitor)
            .setContentTitle("TimeNote 提问")
            .setContentText(question)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id(recordId), notification)
    }

    /** 用户已作答或稍后再答后，移除对应通知 */
    fun cancel(context: Context, recordId: Long) {
        NotificationManagerCompat.from(context).cancel(id(recordId))
    }
}
