package com.taskreminder.ai

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** قاعدة بيانات محلية (SQLite) — لا حساب ولا اتصال بالإنترنت. */
class TaskDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx, "tasks.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE tasks(" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "title TEXT NOT NULL," +
                "hour INTEGER NOT NULL," +
                "minute INTEGER NOT NULL," +
                "repeat_type INTEGER NOT NULL," +
                "days_mask INTEGER NOT NULL," +
                "once_day INTEGER NOT NULL," +
                "enabled INTEGER NOT NULL," +
                "snooze_until INTEGER NOT NULL DEFAULT 0," +
                "created_day INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE completions(" +
                "task_id INTEGER NOT NULL," +
                "day INTEGER NOT NULL," +
                "PRIMARY KEY(task_id, day))"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    private fun Cursor.toTask() = Task(
        id = getLong(0), title = getString(1), hour = getInt(2), minute = getInt(3),
        repeat = getInt(4), daysMask = getInt(5), onceDay = getLong(6),
        enabled = getInt(7) == 1, snoozeUntil = getLong(8), createdDay = getLong(9)
    )

    private fun Task.values() = ContentValues().apply {
        put("title", title); put("hour", hour); put("minute", minute)
        put("repeat_type", repeat); put("days_mask", daysMask); put("once_day", onceDay)
        put("enabled", if (enabled) 1 else 0); put("snooze_until", snoozeUntil)
        put("created_day", createdDay)
    }

    fun all(): List<Task> =
        readableDatabase.rawQuery("SELECT $COLS FROM tasks ORDER BY hour, minute, id", null).use { c ->
            val out = ArrayList<Task>()
            while (c.moveToNext()) out.add(c.toTask())
            out
        }

    fun get(id: Long): Task? =
        readableDatabase.rawQuery("SELECT $COLS FROM tasks WHERE id=?", arrayOf(id.toString())).use { c ->
            if (c.moveToFirst()) c.toTask() else null
        }

    fun insert(t: Task): Long = writableDatabase.insertOrThrow("tasks", null, t.values())

    fun update(t: Task) {
        writableDatabase.update("tasks", t.values(), "id=?", arrayOf(t.id.toString()))
    }

    fun delete(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("tasks", "id=?", arrayOf(id.toString()))
            db.delete("completions", "task_id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun setEnabled(id: Long, enabled: Boolean) {
        val cv = ContentValues().apply {
            put("enabled", if (enabled) 1 else 0)
            put("snooze_until", 0)
        }
        writableDatabase.update("tasks", cv, "id=?", arrayOf(id.toString()))
    }

    fun setSnooze(id: Long, until: Long) {
        val cv = ContentValues().apply { put("snooze_until", until) }
        writableDatabase.update("tasks", cv, "id=?", arrayOf(id.toString()))
    }

    fun markDone(id: Long, day: Long) {
        val cv = ContentValues().apply { put("task_id", id); put("day", day) }
        writableDatabase.insertWithOnConflict("completions", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun unmarkDone(id: Long, day: Long) {
        writableDatabase.delete("completions", "task_id=? AND day=?", arrayOf(id.toString(), day.toString()))
    }

    fun isDone(id: Long, day: Long): Boolean =
        readableDatabase.rawQuery(
            "SELECT 1 FROM completions WHERE task_id=? AND day=?", arrayOf(id.toString(), day.toString())
        ).use { it.moveToFirst() }

    /** معرّفات المهام المنجزة في يوم معيّن. */
    fun doneIds(day: Long): Set<Long> =
        readableDatabase.rawQuery("SELECT task_id FROM completions WHERE day=?", arrayOf(day.toString())).use { c ->
            val s = HashSet<Long>()
            while (c.moveToNext()) s.add(c.getLong(0))
            s
        }

    fun allCompletions(): Set<Pair<Long, Long>> =
        readableDatabase.rawQuery("SELECT task_id, day FROM completions", null).use { c ->
            val s = HashSet<Pair<Long, Long>>()
            while (c.moveToNext()) s.add(Pair(c.getLong(0), c.getLong(1)))
            s
        }

    companion object {
        private const val COLS = "id,title,hour,minute,repeat_type,days_mask,once_day,enabled,snooze_until,created_day"

        @Volatile
        private var inst: TaskDb? = null

        fun get(c: Context): TaskDb =
            inst ?: synchronized(this) {
                inst ?: TaskDb(c.applicationContext).also { inst = it }
            }
    }
}
