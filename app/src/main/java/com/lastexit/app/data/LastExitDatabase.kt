package com.lastexit.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.lastexit.core.Status
import com.lastexit.core.TrackerType
import java.time.LocalDate

/**
 * Plain SQLite storage (two tables, foreign key with cascade delete). Only real trackers live here;
 * Demo mode is entirely in memory and never writes to this database.
 */
class LastExitDatabase(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE trackers (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL,
                type TEXT NOT NULL,
                unit TEXT NOT NULL,
                limit_value REAL NOT NULL,
                start_date INTEGER NOT NULL,
                deadline INTEGER NOT NULL,
                min_pace_factor REAL NOT NULL DEFAULT 0.3,
                last_notified_status TEXT NOT NULL DEFAULT 'SAFE',
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE entries (
                id INTEGER PRIMARY KEY,
                tracker_id INTEGER NOT NULL REFERENCES trackers(id) ON DELETE CASCADE,
                date INTEGER NOT NULL,
                amount REAL NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                one_off INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX index_entries_tracker_id ON entries(tracker_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the only schema so far; future migrations go here.
    }

    fun loadAll(): List<TrackerRecord> {
        val db = readableDatabase
        val entries = HashMap<Long, MutableList<Entry>>()
        db.rawQuery("SELECT * FROM entries ORDER BY date DESC, created_at DESC", null).use { c ->
            while (c.moveToNext()) {
                val entry = c.toEntry()
                entries.getOrPut(entry.trackerId) { ArrayList() }.add(entry)
            }
        }
        val records = ArrayList<TrackerRecord>()
        db.rawQuery("SELECT * FROM trackers ORDER BY created_at ASC", null).use { c ->
            while (c.moveToNext()) {
                val tracker = c.toTracker()
                records += TrackerRecord(tracker, entries[tracker.id].orEmpty())
            }
        }
        return records
    }

    fun insertTracker(tracker: Tracker) {
        writableDatabase.insertOrThrow(TRACKERS, null, tracker.toValues())
    }

    fun updateTracker(tracker: Tracker) {
        writableDatabase.update(TRACKERS, tracker.toValues(), "id = ?", arrayOf(tracker.id.toString()))
    }

    fun deleteTracker(id: Long) {
        writableDatabase.delete(TRACKERS, "id = ?", arrayOf(id.toString()))
    }

    fun updateNotifiedStatus(id: Long, status: Status) {
        val values = ContentValues().apply { put("last_notified_status", status.name) }
        writableDatabase.update(TRACKERS, values, "id = ?", arrayOf(id.toString()))
    }

    fun insertEntry(entry: Entry) {
        val values = ContentValues().apply {
            put("id", entry.id)
            put("tracker_id", entry.trackerId)
            put("date", entry.date.toEpochDay())
            put("amount", entry.amount)
            put("note", entry.note)
            put("one_off", if (entry.oneOff) 1 else 0)
            put("created_at", entry.createdAt)
        }
        writableDatabase.insertWithOnConflict(ENTRIES, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteEntry(id: Long) {
        writableDatabase.delete(ENTRIES, "id = ?", arrayOf(id.toString()))
    }

    private fun Tracker.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("type", type.name)
        put("unit", unit)
        put("limit_value", limit)
        put("start_date", startDate.toEpochDay())
        put("deadline", deadline.toEpochDay())
        put("min_pace_factor", minPaceFactor)
        put("last_notified_status", lastNotifiedStatus.name)
        put("created_at", createdAt)
    }

    private fun Cursor.toTracker() = Tracker(
        id = long("id"),
        name = string("name"),
        type = TrackerType.fromName(string("type")),
        unit = string("unit"),
        limit = double("limit_value"),
        startDate = LocalDate.ofEpochDay(long("start_date")),
        deadline = LocalDate.ofEpochDay(long("deadline")),
        minPaceFactor = double("min_pace_factor"),
        lastNotifiedStatus = Status.fromName(string("last_notified_status")),
        createdAt = long("created_at"),
    )

    private fun Cursor.toEntry() = Entry(
        id = long("id"),
        trackerId = long("tracker_id"),
        date = LocalDate.ofEpochDay(long("date")),
        amount = double("amount"),
        note = string("note"),
        oneOff = long("one_off") != 0L,
        createdAt = long("created_at"),
    )

    private fun Cursor.long(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.double(column: String): Double = getDouble(getColumnIndexOrThrow(column))
    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column)) ?: ""

    companion object {
        const val NAME = "last_exit.db"
        const val VERSION = 1
        private const val TRACKERS = "trackers"
        private const val ENTRIES = "entries"
    }
}
