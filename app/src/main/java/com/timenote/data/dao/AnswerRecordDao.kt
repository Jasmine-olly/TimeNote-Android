package com.timenote.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.timenote.data.entity.AnswerRecord
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

/** 定时提问作答记录数据访问（PRD F2.2 / F2.3） */
@Dao
interface AnswerRecordDao {

    @Insert
    suspend fun insert(record: AnswerRecord): Long

    @Update
    suspend fun update(record: AnswerRecord)

    @Delete
    suspend fun delete(record: AnswerRecord)

    @Query("SELECT * FROM answer_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): AnswerRecord?

    /** 计划问题文本变更时，同步所有关联作答记录的快照（含已答/待答） */
    @Query("UPDATE answer_records SET question = :newQuestion WHERE questionPlanId = :planId")
    suspend fun syncQuestionText(planId: Long, newQuestion: String)

    /** 某个计划下的全部作答记录（改题同步时用于定位受影响日期，触发日记重新汇编） */
    @Query("SELECT * FROM answer_records WHERE questionPlanId = :planId")
    suspend fun getByPlan(planId: Long): List<AnswerRecord>

    /** 删除某个计划下的全部作答记录（删题时同步清理历史回答 + 待回答） */
    @Query("DELETE FROM answer_records WHERE questionPlanId = :planId")
    suspend fun deleteByPlan(planId: Long)

    /** 待回答列表（answer 为空 = 未作答，F2.3 补答） */
    @Query("SELECT * FROM answer_records WHERE answer = '' ORDER BY askedAt")
    fun observePending(): Flow<List<AnswerRecord>>

    /** 一次性读取待回答列表 */
    @Query("SELECT * FROM answer_records WHERE answer = '' ORDER BY askedAt")
    suspend fun getPending(): List<AnswerRecord>

    /** 查询某时间段内的作答记录（按原始提问时间排序，日记汇编用） */
    @Query("SELECT * FROM answer_records WHERE askedAt BETWEEN :startMillis AND :endMillis ORDER BY askedAt")
    suspend fun getBetween(startMillis: Long, endMillis: Long): List<AnswerRecord>

    /** 查询某一天（本地时区）内的作答记录 */
    suspend fun getOnDay(date: LocalDate): List<AnswerRecord> {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = start + 86_400_000L
        return getBetween(start, end)
    }

    /** 观察全部作答记录 */
    @Query("SELECT * FROM answer_records ORDER BY askedAt")
    fun observeAll(): Flow<List<AnswerRecord>>

    /** 一次性读取全部作答记录（导出用） */
    @Query("SELECT * FROM answer_records ORDER BY askedAt")
    suspend fun getAll(): List<AnswerRecord>

    /** 清空（导入覆盖用） */
    @Query("DELETE FROM answer_records")
    suspend fun deleteAll()
}
