package com.timenote.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时问题计划（PRD F2.1）
 *
 * 「时间 → 问题」配对，支持每周重复与暂停。
 *
 * [weekDays] 用 7 位 bitmask 表示重复星期（bit0=周一 … bit6=周日），例如：
 * - 每日    = 0b01111111
 * - 工作日  = 0b00011111（周一至周五）
 * - 周末    = 0b01100000（周六 + 周日）
 *
 * [intervalDays] ≥ 2 时改用「每隔 N 天」周期模式：[intervalAnchor] 为周期基准日
 * （当天 00:00 的 epoch millis），此后每隔 N 天触发一次（按自然日，忽略星期）。
 */
@Entity(tableName = "question_plans")
data class QuestionPlan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 触发时间：当天第几分钟（0–1439），如 08:30 = 510 */
    val minutesOfDay: Int,

    /** 问题文本 */
    val question: String,

    /** 每周重复星期 bitmask（bit0=周一 … bit6=周日）；intervalDays≥2 时忽略 */
    val weekDays: Int,

    /** 每隔 N 天触发（0 = 按星期模式；≥2 = 间隔模式） */
    val intervalDays: Int = 0,

    /** 间隔模式的周期基准日（当天 00:00 epoch millis）；0 = 未设置 */
    val intervalAnchor: Long = 0,

    /** 是否启用（false = 暂停，PRD F2.1） */
    val enabled: Boolean = true,

    /** 创建时间（epoch millis） */
    val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        /** 每日重复 */
        const val EVERY_DAY = 0b01111111

        /** 工作日（周一至周五）重复 */
        const val WORKDAYS = 0b00011111

        /** 周末（周六 + 周日）重复 */
        const val WEEKENDS = 0b01100000
    }
}
