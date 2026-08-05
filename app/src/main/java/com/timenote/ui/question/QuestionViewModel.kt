package com.timenote.ui.question

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.AnswerRecord
import com.timenote.data.entity.QuestionPlan
import com.timenote.diary.DiaryAutoSync
import com.timenote.question.QuestionScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 定时提问页 ViewModel：计划增删改/暂停 + 待回答补答（F2.1 / F2.3） */
class QuestionViewModel(application: Application) : AndroidViewModel(application) {

    private val db = TimeNoteDatabase.get(application)
    private val planDao = db.questionPlanDao()
    private val recordDao = db.answerRecordDao()

    /** 全部问题计划（按时序） */
    val plans: StateFlow<List<QuestionPlan>> = planDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** 待回答记录（answer 为空） */
    val pending: StateFlow<List<AnswerRecord>> = recordDao.observePending()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addPlan(minutesOfDay: Int, question: String, weekDays: Int, intervalDays: Int = 0, intervalAnchor: Long = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            planDao.insert(
                QuestionPlan(
                    minutesOfDay = minutesOfDay,
                    question = question,
                    weekDays = weekDays,
                    intervalDays = intervalDays,
                    intervalAnchor = intervalAnchor,
                ),
            )
            QuestionScheduler.rescheduleAll(getApplication())
        }
    }

    fun updatePlan(plan: QuestionPlan) {
        viewModelScope.launch(Dispatchers.IO) {
            planDao.update(plan)
            // 问题文本变更同步到历史作答记录，确保日记汇编显示最新问题
            val affectedDays = recordDao.getByPlan(plan.id)
                .map { DiaryAutoSync.dayOf(it.askedAt) }
                .toSet()
            recordDao.syncQuestionText(plan.id, plan.question)
            // 改题后这些天的已汇编日记同步刷新（未手动编辑过才覆盖）
            DiaryAutoSync.sync(getApplication(), affectedDays)
            QuestionScheduler.rescheduleAll(getApplication())
        }
    }

    fun deletePlan(plan: QuestionPlan) {
        viewModelScope.launch(Dispatchers.IO) {
            // 删计划前先定位受影响日期（这些天已汇编的日记需要同步刷新）
            val affectedDays = recordDao.getByPlan(plan.id)
                .map { DiaryAutoSync.dayOf(it.askedAt) }
                .toSet()
            planDao.delete(plan)
            // 该计划的作答记录（含待回答）一并清除，日记里不再出现已删除的问题
            recordDao.deleteByPlan(plan.id)
            // 同步受影响日期的已汇编日记（未手动编辑的整篇重写，编辑过的只刷数据小节）
            DiaryAutoSync.sync(getApplication(), affectedDays)
            QuestionScheduler.rescheduleAll(getApplication())
        }
    }

    fun togglePlan(plan: QuestionPlan, enabled: Boolean) {
        updatePlan(plan.copy(enabled = enabled))
    }

    /** 补答一条待回答记录（标注补答时间，F2.3） */
    fun answerPending(record: AnswerRecord, answer: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            recordDao.update(
                record.copy(
                    answer = answer,
                    answeredAt = now,
                    isSupplementary = true,
                ),
            )
            // 补答完成，当天已汇编的日记自动刷新
            DiaryAutoSync.sync(getApplication(), setOf(DiaryAutoSync.dayOf(record.askedAt)))
        }
    }

    fun dismissPending(record: AnswerRecord) {
        viewModelScope.launch(Dispatchers.IO) {
            val day = DiaryAutoSync.dayOf(record.askedAt)
            recordDao.delete(record)
            // 记录删除后，当天已汇编的日记去掉这条未回答项
            DiaryAutoSync.sync(getApplication(), setOf(day))
        }
    }
}
