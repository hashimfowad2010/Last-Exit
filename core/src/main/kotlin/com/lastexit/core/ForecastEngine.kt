package com.lastexit.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Pure, deterministic forecast engine. It has no Android imports and never reads the clock:
 * "today" is part of [ForecastInput], which is what lets Demo mode fast-forward time.
 *
 * Notation used in comments (matches the README):
 *   L = limit, U = used so far, R = whole days left after today, r = current pace per day,
 *   m = r * minPaceFactor (lowest realistic pace).
 */
object ForecastEngine {
    const val DEFAULT_MIN_PACE_FACTOR = 0.3
    const val RECENT_WINDOW_DAYS = 7
    const val RECENT_WEIGHT = 0.7
    const val MIN_RED_WINDOW_DAYS = 3
    const val RED_WINDOW_FRACTION = 0.15
    const val EARLY_ESTIMATE_DAYS = 3
    const val EARLY_ESTIMATE_FRACTION = 0.2
    const val EPSILON = 1e-9

    fun forecast(input: ForecastInput): Forecast {
        require(input.limit > 0.0) { "limit must be positive" }
        require(!input.deadline.isBefore(input.startDate)) { "deadline must not be before start" }

        val factor = input.minPaceFactor.coerceIn(0.0, 1.0)
        val today = input.today
        val totalDays = daysBetween(input.startDate, input.deadline) + 1
        // Days that have begun, today included. Zero before the period starts.
        val elapsed = (daysBetween(input.startDate, today) + 1).coerceIn(0, totalDays)
        // Days strictly after today. Before the start every day of the period is still ahead.
        val remaining = daysBetween(maxOf(today, input.startDate.minusDays(1)), input.deadline).coerceAtLeast(0)

        val counted = input.usages.filter { !it.date.isAfter(today) }
        val used = counted.sumOf { it.amount }
        val measuredPace = burnRate(counted, input.startDate, today, elapsed)
        val pace = (input.paceOverride ?: measuredPace).coerceAtLeast(0.0)
        val minPace = pace * factor
        val projected = used + pace * remaining
        val redWindow = redWindowDays(remaining)
        val overLimit = used > input.limit + EPSILON

        var exitDays: Double? = null
        val rawStatus = when {
            overLimit -> Status.PAST_THE_LINE
            remaining == 0 -> Status.SAFE
            projected <= input.limit + EPSILON -> Status.SAFE
            else -> {
                val x = lastExitDays(input.limit, used, pace, minPace, remaining)
                exitDays = x
                stageForExit(x, redWindow)
            }
        }
        // One or two days of data cannot support "too late": a single big first entry would be
        // extrapolated over the whole period. Until the pace has a few days behind it, warnings
        // stop at ACT_SOON (unless the limit is genuinely already exceeded).
        val capped = !overLimit && elapsed in 1 until earlyDays(totalDays) && rawStatus.isWorseThan(Status.ACT_SOON)
        val status = if (capped) Status.ACT_SOON else rawStatus
        if (capped) exitDays = null // an exit date from 1-2 days of data would be false precision

        val daysToExit = exitDays?.takeIf { it >= -EPSILON && it.isFinite() }?.let { floor(it + EPSILON).toInt() }
        val hitDays = limitHitDays(input.limit, used, pace, remaining)
        val requiredPace = if (remaining > 0) ((input.limit - used) / remaining).coerceAtLeast(0.0) else null
        val recovery = if (status != Status.SAFE && !overLimit && remaining > 0) {
            recovery(input.limit, used, pace, minPace, remaining, projected, daysToExit, hitDays)
        } else {
            null
        }

        return Forecast(
            status = status,
            limit = input.limit,
            used = used,
            startDate = input.startDate,
            deadline = input.deadline,
            today = today,
            totalDays = totalDays,
            elapsedDays = elapsed,
            remainingDays = remaining,
            measuredPace = measuredPace,
            pace = pace,
            minPace = minPace,
            minPaceFactor = factor,
            isWhatIf = input.paceOverride != null,
            projectedTotal = projected,
            requiredPace = requiredPace,
            lastExitDays = exitDays,
            daysToLastExit = daysToExit,
            lastExitDate = daysToExit?.let { today.plusDays(it.toLong()) },
            limitHitDate = hitDays?.let { today.plusDays(it.toLong()) },
            crossedOn = crossedOn(counted, input.limit),
            overshootAtMinPace = if (status == Status.PAST_THE_LINE) {
                (used + minPace * remaining - input.limit).coerceAtLeast(0.0)
            } else {
                0.0
            },
            redWindowDays = redWindow,
            recovery = recovery,
            isEarlyEstimate = elapsed in 1 until EARLY_ESTIMATE_DAYS || counted.size < 2,
            isCappedEarly = capped,
            entryCount = counted.size,
        )
    }

    /**
     * Weighted burn rate: 70% of the average over the last 7 days (or fewer if the period is
     * younger) plus 30% of the average since the start. Recent behaviour dominates, but one quiet
     * or wild week cannot swing the forecast on its own. One-off entries are excluded.
     */
    fun burnRate(usages: List<Usage>, startDate: LocalDate, today: LocalDate, elapsedDays: Int): Double {
        if (elapsedDays <= 0) return 0.0
        val recurring = usages.filter { !it.oneOff && !it.date.isAfter(today) }
        val overall = recurring.sumOf { it.amount } / elapsedDays
        val window = min(RECENT_WINDOW_DAYS, elapsedDays)
        val windowStart = today.minusDays(window - 1L)
        // Entries dated before the start are treated as day one so they cannot fall out of the window early.
        val recent = recurring.filter { !maxOf(it.date, startDate).isBefore(windowStart) }.sumOf { it.amount } / window
        return max(0.0, RECENT_WEIGHT * recent + (1.0 - RECENT_WEIGHT) * overall)
    }

    /**
     * Last exit x, in days from today. Keep pace r for x days, then drop to m for the remaining
     * R - x days, and you land exactly on the limit:
     *     U + r*x + m*(R - x) = L   =>   x = (L - U - m*R) / (r - m)
     * Negative x means even switching to m today overshoots: the point of no return has passed.
     * When r <= m there is no slower pace to switch to, so it is either never needed (+inf) or
     * already too late (-inf).
     */
    fun lastExitDays(limit: Double, used: Double, pace: Double, minPace: Double, remainingDays: Int): Double {
        val headroomAtMinPace = limit - used - minPace * remainingDays
        val gap = pace - minPace
        if (gap <= EPSILON) {
            return if (headroomAtMinPace >= -EPSILON) Double.POSITIVE_INFINITY else Double.NEGATIVE_INFINITY
        }
        return headroomAtMinPace / gap
    }

    /** Days of history needed before red warnings: 3, or 20% of a short period (1 day for 3-5 day periods). */
    fun earlyDays(totalDays: Int): Int = min(EARLY_ESTIMATE_DAYS, ceil(totalDays * EARLY_ESTIMATE_FRACTION).toInt())

    /** The red window is at least 3 days, or 15% of the remaining time for long periods. */
    fun redWindowDays(remainingDays: Int): Int =
        max(MIN_RED_WINDOW_DAYS, floor(RED_WINDOW_FRACTION * remainingDays).toInt())

    /** Stage for a projected overshoot with last exit [x] days away. Boundaries are inclusive of red. */
    fun stageForExit(x: Double, redWindowDays: Int): Status = when {
        x < -EPSILON -> Status.PAST_THE_LINE
        x.isInfinite() -> Status.ACT_SOON
        floor(x + EPSILON).toInt() <= redWindowDays -> Status.LAST_EXIT
        else -> Status.ACT_SOON
    }

    /**
     * Whole days until U + r*days reaches L at the current pace, or null if it does not happen by
     * the deadline. Rounded up: with 2.3 days of headroom the limit is reached on day 3.
     */
    fun limitHitDays(limit: Double, used: Double, pace: Double, remainingDays: Int): Int? {
        if (used > limit + EPSILON || pace <= EPSILON) return null
        val days = ceil((limit - used) / pace - EPSILON).toInt().coerceAtLeast(0)
        return days.takeIf { it <= remainingDays }
    }

    private fun recovery(
        limit: Double,
        used: Double,
        pace: Double,
        minPace: Double,
        remainingDays: Int,
        projected: Double,
        daysToExit: Int?,
        hitDays: Int?,
    ): Recovery {
        // (a) Change course today: spread what is left evenly over the remaining days.
        val actToday = ((limit - used) / remainingDays).coerceAtLeast(0.0)
        // (b) Keep the current pace until the last exit day k, then spread the rest over R - k days.
        // k <= x guarantees this pace is still >= m, i.e. still realistic.
        val waitPace = daysToExit?.takeIf { it < remainingDays }?.let { k ->
            ((limit - used - pace * k) / (remainingDays - k)).coerceAtLeast(0.0)
        }
        return Recovery(
            paceIfActToday = actToday,
            cutPerDayIfActToday = (pace - actToday).coerceAtLeast(0.0),
            cutFractionIfActToday = if (pace > EPSILON) ((pace - actToday) / pace).coerceIn(0.0, 1.0) else 0.0,
            isActTodayRealistic = actToday >= minPace - EPSILON,
            paceIfWaitUntilLastExit = waitPace,
            cutPerDayIfWait = waitPace?.let { (pace - it).coerceAtLeast(0.0) },
            // (c) The trade-off of not changing at all.
            extraLimitNeeded = (projected - limit).coerceAtLeast(0.0),
            daysShortAtCurrentPace = hitDays?.let { remainingDays - it } ?: 0,
        )
    }

    private fun crossedOn(usages: List<Usage>, limit: Double): LocalDate? {
        var total = 0.0
        usages.groupBy { it.date }.toSortedMap().forEach { (date, dayUsages) ->
            total += dayUsages.sumOf { it.amount }
            if (total > limit + EPSILON) return date
        }
        return null
    }

    /** Number of calendar days in a period, both ends included. */
    fun periodDays(start: LocalDate, deadline: LocalDate): Int = daysBetween(start, deadline) + 1

    internal fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
}
