package com.timenote.util

import android.content.Context
import android.net.Uri
import com.timenote.data.TimeNoteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 数据导出（V1.2）：把本地数据导出为文件，用户主动触发、选位置保存。
 *
 * 隐私红线：只写用户选择的本地位置，**不发起任何网络请求**。
 */
object Exporter {

    /** 全部日记合并为一个 Markdown 文件（便于阅读/发布/转发） */
    suspend fun buildMarkdown(context: Context): String {
        val diaries = TimeNoteDatabase.get(context).diaryDao().getAll()
        val sb = StringBuilder()
        sb.appendLine("# TimeNote 日记导出")
        sb.appendLine()
        sb.appendLine("> 共 ${diaries.size} 篇 · 导出时间 ${nowStamp()}")
        sb.appendLine()
        diaries.forEach { d ->
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
            sb.appendLine(d.content.trim())
        }
        return sb.toString()
    }

    /** 全部数据结构化备份为 JSON（娱乐清单 / 问题计划 / 回答记录 / 日记） */
    suspend fun buildJson(context: Context): String {
        val db = TimeNoteDatabase.get(context)
        val apps = db.entertainmentAppDao().getAll()
        val plans = db.questionPlanDao().getAll()
        val answers = db.answerRecordDao().getAll()
        val diaries = db.diaryDao().getAll()

        val root = JSONObject()
        root.put("app", "TimeNote")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("schemaVersion", 4)

        root.put("entertainmentApps", JSONArray().apply {
            apps.forEach { a ->
                put(JSONObject().apply {
                    put("packageName", a.packageName)
                    put("label", a.label)
                    put("addedAt", a.addedAt)
                })
            }
        })
        root.put("questionPlans", JSONArray().apply {
            plans.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("minutesOfDay", p.minutesOfDay)
                    put("question", p.question)
                    put("weekDays", p.weekDays)
                    put("intervalDays", p.intervalDays)
                    put("intervalAnchor", p.intervalAnchor)
                    put("enabled", p.enabled)
                    put("createdAt", p.createdAt)
                })
            }
        })
        root.put("answerRecords", JSONArray().apply {
            answers.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("question", a.question)
                    put("answer", a.answer)
                    put("questionPlanId", a.questionPlanId ?: JSONObject.NULL)
                    put("askedAt", a.askedAt)
                    put("answeredAt", a.answeredAt)
                    put("isSupplementary", a.isSupplementary)
                    put("sceneAppPackage", a.sceneAppPackage ?: JSONObject.NULL)
                    put("sceneAppLabel", a.sceneAppLabel ?: JSONObject.NULL)
                })
            }
        })
        root.put("diaries", JSONArray().apply {
            diaries.forEach { d ->
                put(JSONObject().apply {
                    put("id", d.id)
                    put("date", d.date)
                    put("weather", d.weather ?: JSONObject.NULL)
                    put("cover", d.cover ?: JSONObject.NULL)
                    put("edited", d.edited)
                    put("content", d.content)
                    put("createdAt", d.createdAt)
                    put("updatedAt", d.updatedAt)
                })
            }
        })

        return root.toString(2)
    }

    /** 把文本写入用户选择的 URI（SAF 返回），成功返回 true */
    fun writeToUri(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
        } != null
    } catch (_: Exception) {
        false
    }

    private fun nowStamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
}
