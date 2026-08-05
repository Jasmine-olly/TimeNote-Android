package com.timenote.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.timenote.data.entity.QuestionPlan
import kotlinx.coroutines.flow.Flow

/** 定时问题计划数据访问（PRD F2.1） */
@Dao
interface QuestionPlanDao {

    @Insert
    suspend fun insert(plan: QuestionPlan): Long

    @Update
    suspend fun update(plan: QuestionPlan)

    @Delete
    suspend fun delete(plan: QuestionPlan)

    @Query("SELECT * FROM question_plans WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): QuestionPlan?

    /** 一次性读取全部计划（调度器重排闹钟用，含暂停项） */
    @Query("SELECT * FROM question_plans")
    suspend fun getAll(): List<QuestionPlan>

    /** 观察全部计划（按触发时间排序） */
    @Query("SELECT * FROM question_plans ORDER BY minutesOfDay")
    fun observeAll(): Flow<List<QuestionPlan>>

    /** 观察启用中的计划 */
    @Query("SELECT * FROM question_plans WHERE enabled = 1 ORDER BY minutesOfDay")
    fun observeEnabled(): Flow<List<QuestionPlan>>

    /** 一次性读取启用中的计划 */
    @Query("SELECT * FROM question_plans WHERE enabled = 1")
    suspend fun getEnabled(): List<QuestionPlan>

    /** 清空（导入覆盖用） */
    @Query("DELETE FROM question_plans")
    suspend fun deleteAll()
}
