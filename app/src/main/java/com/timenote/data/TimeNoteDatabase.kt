package com.timenote.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.timenote.data.dao.AnswerRecordDao
import com.timenote.data.dao.DiaryDao
import com.timenote.data.dao.EntertainmentAppDao
import com.timenote.data.dao.QuestionPlanDao
import com.timenote.data.entity.AnswerRecord
import com.timenote.data.entity.Diary
import com.timenote.data.entity.EntertainmentApp
import com.timenote.data.entity.QuestionPlan

/**
 * TimeNote 本地数据库（全部数据仅存本机，隐私红线：零联网）
 *
 * 覆盖 PRD 三类数据：娱乐清单（F1）/ 定时提问（F2）/ 日记（F3）。
 */
@Database(
    entities = [
        EntertainmentApp::class,
        QuestionPlan::class,
        AnswerRecord::class,
        Diary::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class TimeNoteDatabase : RoomDatabase() {

    abstract fun entertainmentAppDao(): EntertainmentAppDao
    abstract fun questionPlanDao(): QuestionPlanDao
    abstract fun answerRecordDao(): AnswerRecordDao
    abstract fun diaryDao(): DiaryDao

    companion object {
        const val NAME = "timenote.db"

        /** v1 → v2：answer_records 增加 questionPlanId，并按问题文本回填历史记录 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE answer_records ADD COLUMN questionPlanId INTEGER")
                db.execSQL(
                    "UPDATE answer_records SET questionPlanId = " +
                        "(SELECT qp.id FROM question_plans qp WHERE qp.question = answer_records.question)",
                )
            }
        }

        /** v2 → v3：diaries 增加 edited（是否手动编辑过，防止自动汇编覆盖用户修改） */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE diaries ADD COLUMN edited INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3 → v4：question_plans 增加「每隔 N 天」周期字段（intervalDays / intervalAnchor） */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE question_plans ADD COLUMN intervalDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE question_plans ADD COLUMN intervalAnchor INTEGER NOT NULL DEFAULT 0")
            }
        }

        @Volatile
        private var instance: TimeNoteDatabase? = null

        /** 单例获取数据库（线程安全） */
        fun get(context: Context): TimeNoteDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    TimeNoteDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
    }
}
