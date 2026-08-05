package com.timenote.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 定时提问的作答记录（PRD F2.2 / F2.3）
 *
 * 问题文本以**快照**保存：即使原计划日后被删除，日记的历史回答仍完整。
 * 补答时（[isSupplementary] = true），[askedAt] 为原始到点时间，[answeredAt] 为实际作答时间。
 */
@Entity(
    tableName = "answer_records",
    indices = [Index(value = ["askedAt"])],
)
data class AnswerRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 问题文本快照 */
    val question: String,

    /** 作答内容 */
    val answer: String,

    /** 关联的问题计划 id（计划删除后为 null，快照仍保留） */
    val questionPlanId: Long? = null,

    /** 原始提问时间（epoch millis） */
    val askedAt: Long,

    /** 实际作答时间（epoch millis），补答时晚于 askedAt */
    val answeredAt: Long,

    /** 是否为补答（F2.3） */
    val isSupplementary: Boolean = false,

    /** 作答时正在使用的 App 包名（可选场景信息，F2.2） */
    val sceneAppPackage: String? = null,

    /** 作答时正在使用的 App 显示名称（冗余存储，供日记直接展示） */
    val sceneAppLabel: String? = null,
)
