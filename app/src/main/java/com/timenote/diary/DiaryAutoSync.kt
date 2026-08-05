package com.timenote.diary

import android.content.Context
import com.timenote.data.TimeNoteDatabase
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 日记自动同步 / 重新汇编（F3.1 增强）。
 *
 * 回答记录变化或用户点「重新汇编」时更新当天日记，规则：
 * - **未手动编辑过**：直接用源数据整篇重写（数据小节 + 小结都会刷新）。
 * - **手动编辑过**：只刷新「娱乐使用」「定时提问」「小结」三个数据小节，
 *   天气、以及用户其他手动内容一律保留，避免汇编把修改冲掉。
 *
 * 小结也属于自动生成的汇总数据，随回答/娱乐变化刷新（真机反馈：已编辑日记的小结不更新）。
 */
object DiaryAutoSync {

    /**
     * 对给定日期集合同步日记。
     * 无日记的日期自动跳过（不主动新建，汇编由用户在日记页触发）。
     */
    suspend fun sync(context: Context, dates: Set<LocalDate>) {
        if (dates.isEmpty()) return
        dates.forEach { refreshDiary(context, it) }
    }

    /** 刷新某天日记（不存在则不动）；返回当天是否有日记 */
    suspend fun refreshDiary(context: Context, date: LocalDate): Boolean {
        val db = TimeNoteDatabase.get(context)
        val diaryDao = db.diaryDao()
        val existing = diaryDao.getByDate(date.toString()) ?: return false
        val fresh = DiaryAssembler.build(context, date)
        val now = System.currentTimeMillis()
        val updated = if (existing.edited) {
            existing.copy(
                content = mergeAutoSections(existing.content, fresh),
                updatedAt = now,
                edited = true,
            )
        } else {
            existing.copy(
                content = fresh,
                updatedAt = now,
                edited = false,
            )
        }
        // 封面默认建议：未设封面时按天气文本 + 回答情绪自动生成（PRD 未决事项）
        val withCover = if (existing.cover == null) {
            val answers = db.answerRecordDao().getOnDay(date)
            updated.copy(cover = DiaryAssembler.suggestCover(updated.content, answers))
        } else {
            updated
        }
        diaryDao.upsert(withCover)
        return true
    }

    /** 手动编辑过的日记：仅用新汇编的数据小节（娱乐使用/定时提问/小结）替换旧小节，其余内容保留 */
    fun mergeAutoSections(existing: String, fresh: String): String {
        var result = existing
        for (heading in DATA_SECTIONS) {
            result = replaceSection(result, heading, sectionBody(fresh, heading))
        }
        return result
    }

    /** epoch millis → 本地日期（回答记录的原始提问日） */
    fun dayOf(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 随数据刷新的小节：小结也是自动生成的汇总，应跟随回答/娱乐数据更新；
     *  天气、正文里用户自定义内容不在列表，保持不变。 */
    private val DATA_SECTIONS = listOf("娱乐使用", "定时提问", "小结")

    /** 提取新汇编里某个 ## 小节的正文（不含小节标题） */
    private fun sectionBody(markdown: String, heading: String): String {
        val lines = markdown.lines()
        val idx = lines.indexOfFirst { it.trim() == "## $heading" }
        if (idx < 0) return ""
        val endIdx = (idx + 1..lines.lastIndex).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        return lines.subList(idx + 1, endIdx).joinToString("\n")
    }

    /** 用新正文替换/插入某个 ## 小节，其余行原样保留 */
    private fun replaceSection(markdown: String, heading: String, body: String): String {
        val marker = "## $heading"
        val lines = markdown.lines()
        val idx = lines.indexOfFirst { it.trim() == marker }
        if (idx < 0) {
            // 小节不存在：插到第一个 ## 小节之前，或追加到末尾
            val endIdx = (0..lines.lastIndex).firstOrNull { lines[it].startsWith("## ") }
            val block = buildString {
                appendLine(marker)
                appendLine()
                append(body)
            }
            return if (endIdx != null) {
                (lines.subList(0, endIdx) + block.lines() + lines.subList(endIdx, lines.size))
                    .joinToString("\n")
            } else {
                "$markdown\n\n$block".trim()
            }
        }
        val endIdx = (idx + 1..lines.lastIndex).firstOrNull { lines[it].startsWith("## ") } ?: lines.size
        val newBody = body.lines()
        return (lines.subList(0, idx) + listOf(marker) + newBody + lines.subList(endIdx, lines.size))
            .joinToString("\n")
    }
}
