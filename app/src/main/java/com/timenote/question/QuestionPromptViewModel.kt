package com.timenote.question

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.AnswerRecord
import com.timenote.diary.DiaryAutoSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 到点提问弹窗的 ViewModel：加载记录 → 提交回答 */
class QuestionPromptViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TimeNoteDatabase.get(application)

    private val _record = MutableStateFlow<AnswerRecord?>(null)
    val record: StateFlow<AnswerRecord?> = _record.asStateFlow()

    fun loadRecord(id: Long) {
        if (id < 0) return
        viewModelScope.launch {
            _record.value = db.answerRecordDao().getById(id)
        }
    }

    /** 提交回答：补答时间记为当前，超过宽限期视为补答（F2.3） */
    fun submit(answer: String, scenePackage: String?, sceneLabel: String?, onDone: () -> Unit) {
        // 整体在 IO 线程执行：日记汇编里的 UsageStats 系统查询不能在主线程跑，否则弹窗卡顿
        viewModelScope.launch(Dispatchers.IO) {
            val r = _record.value ?: return@launch
            val now = System.currentTimeMillis()
            db.answerRecordDao().update(
                r.copy(
                    answer = answer,
                    answeredAt = now,
                    isSupplementary = now - r.askedAt > GRACE_MS,
                    sceneAppPackage = scenePackage,
                    sceneAppLabel = sceneLabel,
                ),
            )
            // 已作答：移除通知兜底，避免点击旧通知再次进入答题页
            QuestionNotification.cancel(getApplication(), r.id)
            // 先关弹窗立即响应；日记同步在后台继续完成
            withContext(Dispatchers.Main) { onDone() }
            DiaryAutoSync.sync(getApplication(), setOf(DiaryAutoSync.dayOf(r.askedAt)))
        }
    }

    /** 稍后再答：移除通知兜底，记录保留在「待回答」，可稍后在 App 内补答 */
    fun dismissPrompt(recordId: Long) {
        QuestionNotification.cancel(getApplication(), recordId)
    }

    companion object {
        /** 超过 5 分钟视为补答 */
        private const val GRACE_MS = 5L * 60 * 1000
    }
}
