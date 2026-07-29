package com.flowhist.refocus.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Calendar

class SessionDatabase(context: Context) :
    SQLiteOpenHelper(context, "refocus.db", null, DATABASE_VERSION) {

    override fun onCreate(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name TEXT NOT NULL,
                app_label TEXT NOT NULL,
                purpose TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                planned_duration_ms INTEGER NOT NULL,
                actual_duration_ms INTEGER,
                overtime_ms INTEGER NOT NULL DEFAULT 0,
                goal_completed INTEGER,
                score INTEGER,
                outcome TEXT NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL("CREATE INDEX idx_sessions_started_at ON sessions(started_at DESC)")
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun insertStarted(
        packageName: String,
        appLabel: String,
        purpose: String,
        plannedDurationMs: Long,
        startedAt: Long,
    ): Long = writableDatabase.insertOrThrow(
        "sessions",
        null,
        ContentValues().apply {
            put("package_name", packageName)
            put("app_label", appLabel)
            put("purpose", purpose)
            put("started_at", startedAt)
            put("planned_duration_ms", plannedDurationMs)
            put("outcome", "active")
        },
    )

    @Synchronized
    fun complete(
        id: Long,
        endedAt: Long,
        actualDurationMs: Long,
        plannedDurationMs: Long,
        goalCompleted: Boolean,
        score: Int,
    ) {
        writableDatabase.update(
            "sessions",
            ContentValues().apply {
                put("ended_at", endedAt)
                put("actual_duration_ms", actualDurationMs)
                put("overtime_ms", (actualDurationMs - plannedDurationMs).coerceAtLeast(0L))
                put("goal_completed", if (goalCompleted) 1 else 0)
                put("score", score)
                put(
                    "outcome",
                    when {
                        score > 0 -> "success"
                        score < 0 -> "overdue"
                        else -> "not_completed"
                    },
                )
            },
            "id = ?",
            arrayOf(id.toString()),
        )
    }

    @Synchronized
    fun markOverdue(id: Long) {
        writableDatabase.update(
            "sessions",
            ContentValues().apply {
                put("score", -1)
                put("outcome", "overdue")
            },
            "id = ? AND outcome = 'active'",
            arrayOf(id.toString()),
        )
    }

    @Synchronized
    fun closeStale(id: Long) {
        writableDatabase.update(
            "sessions",
            ContentValues().apply {
                put("ended_at", System.currentTimeMillis())
            },
            "id = ? AND ended_at IS NULL",
            arrayOf(id.toString()),
        )
        writableDatabase.execSQL(
            """
            UPDATE sessions
            SET
                score = COALESCE(score, 0),
                outcome = CASE WHEN score = -1 THEN 'overdue' ELSE 'interrupted' END
            WHERE id = ?
            """.trimIndent(),
            arrayOf(id),
        )
    }

    @Synchronized
    fun recent(limit: Int = 100): List<SessionRecord> {
        val result = mutableListOf<SessionRecord>()
        readableDatabase.query(
            "sessions",
            null,
            null,
            null,
            null,
            null,
            "started_at DESC",
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                fun longOrNull(column: String): Long? {
                    val index = cursor.getColumnIndexOrThrow(column)
                    return if (cursor.isNull(index)) null else cursor.getLong(index)
                }
                val completedIndex = cursor.getColumnIndexOrThrow("goal_completed")
                val scoreIndex = cursor.getColumnIndexOrThrow("score")
                result += SessionRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                    appLabel = cursor.getString(cursor.getColumnIndexOrThrow("app_label")),
                    purpose = cursor.getString(cursor.getColumnIndexOrThrow("purpose")),
                    startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                    endedAt = longOrNull("ended_at"),
                    plannedDurationMs =
                        cursor.getLong(cursor.getColumnIndexOrThrow("planned_duration_ms")),
                    actualDurationMs = longOrNull("actual_duration_ms"),
                    overtimeMs = cursor.getLong(cursor.getColumnIndexOrThrow("overtime_ms")),
                    goalCompleted =
                        if (cursor.isNull(completedIndex)) null else cursor.getInt(completedIndex) == 1,
                    score = if (cursor.isNull(scoreIndex)) null else cursor.getInt(scoreIndex),
                    outcome = cursor.getString(cursor.getColumnIndexOrThrow("outcome")),
                )
            }
        }
        return result
    }

    @Synchronized
    fun todaySummary(now: Long = System.currentTimeMillis()): SessionSummary {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        readableDatabase.rawQuery(
            """
            SELECT
                COALESCE(SUM(score), 0),
                COUNT(CASE WHEN score IS NOT NULL THEN 1 END),
                COUNT(CASE WHEN goal_completed = 1 THEN 1 END),
                COUNT(CASE WHEN score = -1 THEN 1 END)
            FROM sessions
            WHERE started_at >= ?
            """.trimIndent(),
            arrayOf(calendar.timeInMillis.toString()),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                return SessionSummary(
                    totalScore = cursor.getInt(0),
                    sessionCount = cursor.getInt(1),
                    completedCount = cursor.getInt(2),
                    failedCount = cursor.getInt(3),
                )
            }
        }
        return SessionSummary()
    }

    private companion object {
        const val DATABASE_VERSION = 1
    }
}
