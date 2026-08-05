package com.timenote.util

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.timenote.data.TimeNoteDatabase
import com.timenote.data.entity.AnswerRecord
import com.timenote.data.entity.Diary
import com.timenote.data.entity.EntertainmentApp
import com.timenote.data.entity.QuestionPlan
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数据导入恢复（与 [Exporter] 对应的反向操作）：读取导出的 JSON 备份，
 * 以「合并（保留现有、跳过冲突）」或「覆盖（清空后导入）」方式恢复。纯本地。
 */
object Importer {

    enum class Mode { Merge, Replace }

    /** 备份内容预览（导入确认前展示） */
    data class Preview(
        val schemaVersion: Int,
        val apps: Int,
        val plans: Int,
        val answers: Int,
        val diaries: Int,
    )

    data class Result(val ok: Boolean, val message: String)

    private fun readText(context: Context, uri: Uri): String? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (_: Exception) {
        null
    }

    /** 读取并校验备份文件，返回内容预览；非 TimeNote 备份 / 解析失败返回 null */
    suspend fun preview(context: Context, uri: Uri): Preview? {
        val json = readText(context, uri) ?: return null
        return try {
            val root = JSONObject(json)
            if (root.optString("app") != "TimeNote") return null
            Preview(
                schemaVersion = root.optInt("schemaVersion"),
                apps = root.optJSONArray("entertainmentApps")?.length() ?: 0,
                plans = root.optJSONArray("questionPlans")?.length() ?: 0,
                answers = root.optJSONArray("answerRecords")?.length() ?: 0,
                diaries = root.optJSONArray("diaries")?.length() ?: 0,
            )
        } catch (_: Exception) {
            null
        }
    }

    /** 导入恢复 */
    suspend fun restore(context: Context, uri: Uri, mode: Mode): Result {
        val json = readText(context, uri) ?: return Result(false, "读取文件失败")
        val db = TimeNoteDatabase.get(context)
        val result = try {
            db.withTransaction {
                val root = JSONObject(json)
                if (root.optString("app") != "TimeNote") {
                    return@withTransaction Result(false, "不是有效的 TimeNote 备份文件")
                }
                if (mode == Mode.Replace) {
                    db.answerRecordDao().deleteAll()
                    db.diaryDao().deleteAll()
                    db.questionPlanDao().deleteAll()
                    db.entertainmentAppDao().deleteAll()
                }
                val apps = importApps(db, root.optJSONArray("entertainmentApps"), mode)
                val plans = importPlans(db, root.optJSONArray("questionPlans"), mode)
                val answers = importAnswers(db, root.optJSONArray("answerRecords"), mode)
                val diaries = importDiaries(db, root.optJSONArray("diaries"), mode)
                val verb = if (mode == Mode.Replace) "覆盖" else "合并"
                Result(true, "${verb}导入完成：娱乐 $apps / 计划 $plans / 回答 $answers / 日记 $diaries")
            }
        } catch (e: Exception) {
            Result(false, "导入失败：${e.message}")
        }
        // 导入后重排闹钟，让恢复的计划立即生效
        if (result.ok) com.timenote.question.QuestionScheduler.rescheduleAll(context)
        return result
    }

    private suspend fun importApps(db: TimeNoteDatabase, arr: JSONArray?, mode: Mode): Int {
        if (arr == null) return 0
        val dao = db.entertainmentAppDao()
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val pkg = o.optString("packageName")
            if (pkg.isEmpty()) continue
            if (mode == Mode.Merge && dao.findByPackage(pkg) != null) continue
            dao.insert(
                EntertainmentApp(
                    packageName = pkg,
                    label = o.optString("label"),
                    addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                ),
            )
            n++
        }
        return n
    }

    private suspend fun importPlans(db: TimeNoteDatabase, arr: JSONArray?, mode: Mode): Int {
        if (arr == null) return 0
        val dao = db.questionPlanDao()
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("id")
            if (mode == Mode.Merge && dao.getById(id) != null) continue
            dao.insert(
                QuestionPlan(
                    id = id,
                    minutesOfDay = o.optInt("minutesOfDay"),
                    question = o.optString("question"),
                    weekDays = o.optInt("weekDays"),
                    intervalDays = o.optInt("intervalDays"),
                    intervalAnchor = o.optLong("intervalAnchor"),
                    enabled = o.optBoolean("enabled", true),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                ),
            )
            n++
        }
        return n
    }

    private suspend fun importAnswers(db: TimeNoteDatabase, arr: JSONArray?, mode: Mode): Int {
        if (arr == null) return 0
        val dao = db.answerRecordDao()
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val id = o.optLong("id")
            if (mode == Mode.Merge && dao.getById(id) != null) continue
            dao.insert(
                AnswerRecord(
                    id = id,
                    question = o.optString("question"),
                    answer = o.optString("answer"),
                    questionPlanId = o.optString("questionPlanId").toLongOrNull(),
                    askedAt = o.optLong("askedAt"),
                    answeredAt = o.optLong("answeredAt"),
                    isSupplementary = o.optBoolean("isSupplementary"),
                    sceneAppPackage = o.optString("sceneAppPackage").ifEmpty { null },
                    sceneAppLabel = o.optString("sceneAppLabel").ifEmpty { null },
                ),
            )
            n++
        }
        return n
    }

    private suspend fun importDiaries(db: TimeNoteDatabase, arr: JSONArray?, mode: Mode): Int {
        if (arr == null) return 0
        val dao = db.diaryDao()
        var n = 0
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val date = o.optString("date")
            if (date.isEmpty()) continue
            if (mode == Mode.Merge && dao.getByDate(date) != null) continue
            dao.upsert(
                Diary(
                    date = date,
                    weather = o.optString("weather").ifEmpty { null },
                    cover = o.optString("cover").ifEmpty { null },
                    edited = o.optBoolean("edited"),
                    content = o.optString("content"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                ),
            )
            n++
        }
        return n
    }
}
