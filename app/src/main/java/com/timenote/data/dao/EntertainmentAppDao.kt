package com.timenote.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.timenote.data.entity.EntertainmentApp
import kotlinx.coroutines.flow.Flow

/** 娱乐应用清单数据访问（PRD F1.1） */
@Dao
interface EntertainmentAppDao {

    @Insert
    suspend fun insert(app: EntertainmentApp): Long

    @Delete
    suspend fun delete(app: EntertainmentApp)

    @Query("DELETE FROM entertainment_apps WHERE packageName = :packageName")
    suspend fun deleteByPackage(packageName: String)

    /** 观察全部清单（按时序） */
    @Query("SELECT * FROM entertainment_apps ORDER BY addedAt")
    fun observeAll(): Flow<List<EntertainmentApp>>

    /** 一次性读取全部清单（供前台检测即时查询） */
    @Query("SELECT * FROM entertainment_apps")
    suspend fun getAll(): List<EntertainmentApp>

    @Query("SELECT * FROM entertainment_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun findByPackage(packageName: String): EntertainmentApp?

    @Query("SELECT COUNT(*) FROM entertainment_apps")
    suspend fun count(): Int

    /** 清空（导入覆盖用） */
    @Query("DELETE FROM entertainment_apps")
    suspend fun deleteAll()
}
