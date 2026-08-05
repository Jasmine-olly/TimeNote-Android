package com.timenote.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.timenote.data.entity.Diary
import kotlinx.coroutines.flow.Flow

/** 日记数据访问（PRD F3） */
@Dao
interface DiaryDao {

    /** 写入或覆盖当日日记（date 唯一） */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(diary: Diary)

    @Update
    suspend fun update(diary: Diary)

    @Query("SELECT * FROM diaries WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): Diary?

    /** 观察全部日记（新→旧，日历翻阅用） */
    @Query("SELECT * FROM diaries ORDER BY date DESC")
    fun observeAll(): Flow<List<Diary>>

    /** 一次性读取全部日记（导出用） */
    @Query("SELECT * FROM diaries ORDER BY date")
    suspend fun getAll(): List<Diary>

    /** 清空（导入覆盖用） */
    @Query("DELETE FROM diaries")
    suspend fun deleteAll()

    /** 有日记的日期集合（日历小圆点标记用） */
    @Query("SELECT date FROM diaries ORDER BY date")
    suspend fun getDates(): List<String>
}
