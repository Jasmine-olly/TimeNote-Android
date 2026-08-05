package com.timenote.question

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.AnswerRecord
import com.timenote.diary.DiaryAutoSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 到点提问的闹钟接收器（F2.2 / F2.3）：
 * 1. 先落一条「待回答」记录（即使本次弹窗没答，也保证可补答）
 * 2. 尝试弹出提问界面（持有悬浮窗权限才可在后台弹出，否则留在待回答）
 * 3. 为该计划重设下一次闹钟
 */
class QuestionAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val planId = intent.getLongExtra(QuestionScheduler.EXTRA_PLAN_ID, -1L)
        val askedAt = intent.getLongExtra(QuestionScheduler.EXTRA_ASKED_AT, System.currentTimeMillis())
        if (planId < 0) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = TimeNoteDatabase.get(context)
                val plan = db.questionPlanDao().getById(planId) ?: return@launch
                if (!plan.enabled) return@launch // 已被暂停：不再弹、不再调度

                val recordId = db.answerRecordDao().insert(
                    AnswerRecord(
                        question = plan.question,
                        answer = "",
                        askedAt = askedAt,
                        answeredAt = askedAt,
                        questionPlanId = plan.id,
                    ),
                )

                // 当天已汇编的日记自动补上这条新提问（未手动编辑过才覆盖）
                DiaryAutoSync.sync(context, setOf(DiaryAutoSync.dayOf(askedAt)))

                // 弹出提问界面；若后台弹窗受限，记录保留在「待回答」
                startPrompt(context, recordId)
                // 通知兜底：即使弹窗被 ROM 延迟/拦截，也保证问题浮出（点通知可回答）
                QuestionNotification.post(context, recordId, plan.question)

                // 重设下一次触发，实现每日/每周重复
                QuestionScheduler.schedule(context, plan)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun startPrompt(context: Context, recordId: Long) {
        val intent = Intent(context, QuestionPromptActivity::class.java).apply {
            putExtra(QuestionPromptActivity.EXTRA_RECORD_ID, recordId)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: SecurityException) {
            // 无悬浮窗权限时后台弹窗受限，记录留在待回答列表
        }
    }

}
