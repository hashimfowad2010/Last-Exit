package com.lastexit.app.data

import android.os.Handler
import android.util.Log
import com.lastexit.core.Status
import java.util.concurrent.Executor

/**
 * Single source of truth for real trackers. The data set is small, so it is held in memory and
 * every mutation happens on the main thread, then is written through to SQLite on one background
 * thread (writes stay in order). Screens observe it with [addListener].
 */
class TrackerStore(
    private val database: LastExitDatabase,
    private val io: Executor,
    private val main: Handler,
) {
    private var records: List<TrackerRecord> = emptyList()
    private val listeners = LinkedHashSet<() -> Unit>()
    private val loadWaiters = ArrayList<() -> Unit>()
    private var loading = false
    private var maxTrackerId = 0L
    private var maxEntryId = 0L

    var isLoaded: Boolean = false
        private set

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** Runs [action] on the main thread once the database has been read. */
    fun whenLoaded(action: () -> Unit) {
        if (isLoaded) {
            action()
            return
        }
        loadWaiters += action
        if (loading) return
        loading = true
        io.execute {
            val loaded = guarded("load") { database.loadAll() } ?: emptyList()
            main.post {
                records = loaded
                maxTrackerId = loaded.maxOfOrNull { it.tracker.id } ?: 0L
                maxEntryId = loaded.flatMap { it.entries }.maxOfOrNull { it.id } ?: 0L
                isLoaded = true
                loading = false
                val waiters = loadWaiters.toList()
                loadWaiters.clear()
                notifyChanged()
                waiters.forEach { it() }
            }
        }
    }

    /** Runs [action] on the main thread after every write queued so far has reached the disk. */
    fun afterPendingWrites(action: () -> Unit) {
        io.execute { main.post(action) }
    }

    fun all(): List<TrackerRecord> = records

    fun get(id: Long): TrackerRecord? = records.firstOrNull { it.tracker.id == id }

    fun addTracker(draft: Tracker): Long {
        val tracker = draft.copy(id = ++maxTrackerId)
        records = records + TrackerRecord(tracker, emptyList())
        persist { database.insertTracker(tracker) }
        return tracker.id
    }

    fun updateTracker(tracker: Tracker) {
        records = records.map { if (it.tracker.id == tracker.id) it.copy(tracker = tracker) else it }
        persist { database.updateTracker(tracker) }
    }

    fun deleteTracker(id: Long) {
        records = records.filterNot { it.tracker.id == id }
        persist { database.deleteTracker(id) }
    }

    fun setLastNotifiedStatus(id: Long, status: Status) {
        val record = get(id) ?: return
        if (record.tracker.lastNotifiedStatus == status) return
        records = records.map {
            if (it.tracker.id == id) it.copy(tracker = it.tracker.copy(lastNotifiedStatus = status)) else it
        }
        persist(notify = false) { database.updateNotifiedStatus(id, status) }
    }

    fun newEntryId(): Long = ++maxEntryId

    fun addEntry(entry: Entry) {
        records = records.map { record ->
            if (record.tracker.id != entry.trackerId) {
                record
            } else {
                record.copy(entries = (record.entries + entry).sortedWith(NEWEST_FIRST))
            }
        }
        persist { database.insertEntry(entry) }
    }

    /** Removes an entry and returns it so the caller can offer Undo. */
    fun deleteEntry(entryId: Long): Entry? {
        val entry = records.flatMap { it.entries }.firstOrNull { it.id == entryId } ?: return null
        records = records.map { record ->
            if (record.tracker.id != entry.trackerId) record else record.copy(entries = record.entries - entry)
        }
        persist { database.deleteEntry(entryId) }
        return entry
    }

    private fun persist(notify: Boolean = true, write: () -> Unit) {
        io.execute { guarded("write") { write() } }
        if (notify) notifyChanged()
    }

    /** A storage failure (disk full, corrupt file) is logged instead of crashing the app. */
    private fun <T> guarded(what: String, block: () -> T): T? = try {
        block()
    } catch (failure: RuntimeException) {
        Log.e(TAG, "Database $what failed", failure)
        null
    }

    private fun notifyChanged() {
        listeners.toList().forEach { it() }
    }

    private companion object {
        const val TAG = "TrackerStore"

        val NEWEST_FIRST = Comparator<Entry> { a, b ->
            val byDate = b.date.compareTo(a.date)
            if (byDate != 0) byDate else b.createdAt.compareTo(a.createdAt)
        }
    }
}
