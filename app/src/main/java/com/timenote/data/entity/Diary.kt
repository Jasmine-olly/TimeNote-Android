package com.timenote.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 日记（PRD F3）
 *
 * [content] 以 Markdown 保存自动汇编结果，用户可整体编辑（F3.2），
 * 同时天然适配 V1.2 的 Markdown 导出。
 *
 * [edited] = true 表示用户手动改过正文：回答记录变化时**不自动覆盖**，
 * 需要用户主动「重新汇编」；未编辑过的日记会随回答变化自动同步（F3.1）。
 */
@Entity(
    tableName = "diaries",
    indices = [Index(value = ["date"], unique = true)],
)
data class Diary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 日记日期（yyyy-MM-dd，唯一） */
    val date: String,

    /** 天气（手动填写，F3.1） */
    val weather: String? = null,

    /** 日记正文（Markdown） */
    val content: String,

    /** 用户是否手动编辑过正文（true 时不自动重新汇编） */
    val edited: Boolean = false,

    /** 封面标记（预留字段，V1.1 封面索引使用） */
    val cover: String? = null,

    /** 自动生成时间（epoch millis） */
    val createdAt: Long = System.currentTimeMillis(),

    /** 最后修改时间（epoch millis） */
    val updatedAt: Long = System.currentTimeMillis(),
)
