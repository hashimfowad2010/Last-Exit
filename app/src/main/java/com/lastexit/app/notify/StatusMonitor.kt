package com.lastexit.app.notify

import com.lastexit.app.data.TrackerSnapshot
import com.lastexit.app.data.TrackerStore
import com.lastexit.core.Status
import java.time.LocalDate

/**
 * Compares each tracker's current status with the last one the user was told about
 * (lastNotifiedStatus). Only a move to a worse stage alerts, so nobody gets the same warning twice.
 * Improvements are recorded silently, so a later relapse alerts again.
 */
class StatusMonitor(private val store: TrackerStore, private val notifier: Notifier) {

    data class Change(val snapshot: TrackerSnapshot, val previous: Status) {
        val current: Status get() = snapshot.forecast.status
        val isWorse: Boolean get() = current.isWorseThan(previous)
    }

    /** Background check (JobScheduler): posts a system notification for every worsened tracker. */
    fun runBackgroundCheck(today: LocalDate): List<Change> {
        val changes = diff(today)
        changes.filter { it.isWorse }.forEach { notifier.postTrackerAlert(it.snapshot) }
        record(changes)
        return changes
    }

    /** Foreground check: the caller shows worsened trackers in-app instead of as notifications. */
    fun reconcileForeground(today: LocalDate): List<Change> {
        val changes = diff(today)
        record(changes)
        return changes.filter { it.isWorse }
    }

    private fun diff(today: LocalDate): List<Change> = store.all().mapNotNull { record ->
        val snapshot = TrackerSnapshot.of(record, today)
        val previous = record.tracker.lastNotifiedStatus
        if (snapshot.forecast.status == previous) null else Change(snapshot, previous)
    }

    private fun record(changes: List<Change>) {
        changes.forEach { store.setLastNotifiedStatus(it.snapshot.tracker.id, it.current) }
    }
}
