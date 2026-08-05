package com.timenote.ui.diary

import androidx.compose.ui.graphics.Color

/**
 * 日记封面（F3.3）：天气 emoji（大字）+ 心情底色，两者可选。
 *
 * 存储复用 [com.timenote.data.entity.Diary.cover] 字段，编码格式 `"天气|心情"`，
 * 例如 `"☀️|😊"`；缺任一侧用空串占位（`"☀️|"`、`"|😊"`）；两者都无则存 null。
 */
object Covers {

    /** 可选天气（大字显示在封面条上） */
    val WEATHER = listOf("☀️", "⛅", "🌧️", "⛈️", "❄️", "🌈", "🌫️")

    /** 可选心情：emoji + 名称 + 封面色 */
    val MOODS = listOf(
        Mood("😊", "开心", Color(0xFFFFE082)),
        Mood("😄", "兴奋", Color(0xFFFFAB91)),
        Mood("🤩", "惊喜", Color(0xFFCE93D8)),
        Mood("😐", "平静", Color(0xFF80CBC4)),
        Mood("😢", "低落", Color(0xFF90CAF9)),
        Mood("😴", "疲惫", Color(0xFFB0BEC5)),
    )

    /** 心情 emoji → 封面色；未知/未设返回 null（用默认底色） */
    fun moodColor(moodEmoji: String?): Color? =
        MOODS.firstOrNull { it.emoji == moodEmoji }?.color

    /** 编码两个可空值 → cover 字段值（两者皆空返回 null） */
    fun encode(weather: String?, mood: String?): String? {
        if (weather == null && mood == null) return null
        return "${weather ?: ""}|${mood ?: ""}"
    }

    /** 解析 cover 字段 → (天气, 心情)；空串/空串返回 null */
    fun parse(raw: String?): Pair<String?, String?> {
        if (raw.isNullOrBlank()) return null to null
        val parts = raw.split("|", limit = 2)
        return parts.getOrNull(0)?.takeIf { it.isNotEmpty() } to
            parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
    }
}

/** 心情选项：emoji、显示名、底色 */
data class Mood(
    val emoji: String,
    val label: String,
    val color: Color,
)
