package com.timenote.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 娱乐应用清单（PRD F1.1）
 *
 * 用户手动勾选的「娱乐类 App」；记录存在即启用，移除即删除。
 */
@Entity(
    tableName = "entertainment_apps",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class EntertainmentApp(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 应用包名（唯一） */
    val packageName: String,

    /** 应用显示名称 */
    val label: String,

    /** 加入清单的时间（epoch millis） */
    val addedAt: Long = System.currentTimeMillis(),
)
