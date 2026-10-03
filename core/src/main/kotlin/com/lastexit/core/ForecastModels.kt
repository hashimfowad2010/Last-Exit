package com.lastexit.core

import java.time.LocalDate

/**
 * One logged amount. [oneOff] entries (rent, a yearly fee, a single big commitment) count toward
 * the total used but are excluded from the burn rate, so one big day does not trigger a false alarm.
 */
data class Usage(
    val date: LocalDate,
    val amount: Double,
    val oneOff: Boolean = false,
)

data class ForecastInput(
    val limit: Double,
    val startDate: LocalDate,
    val deadline: LocalDate,
    /** Always passed in, never read from the system clock, so Demo mode can fake time. */
    val today: LocalDate,
    val usages: List<Usage>,
    /** Lowest realistic pace as a fraction of the current pace (0.3 = could cut usage to 30%). */
    val minPaceFactor: Double = ForecastEngine.DEFAULT_MIN_PACE_FACTOR,
    /** "What if?" pace per day. Replaces the measured pace without changing any stored data. */
    val paceOverride: Double? = null,
)

data class Forecast(
    val status: Status,
    val limit: Double,
    /** U: total used up to and including today (future-dated entries are ignored). */
    val used: Double,
    val startDate: LocalDate,
    val deadline: LocalDate,
    val today: LocalDate,
    val totalDays: Int,
    /** Days of the period that have started, including today. */
    val elapsedDays: Int,
    /** R: whole days left after today until the deadline (inclusive). */
    val remainingDays: Int,
    /** Pace measured from the entries, before any what-if override. */
    val measuredPace: Double,
    /** r: pace used for the projection (the override when one is set). */
    val pace: Double,
    /** m: the lowest realistic pace, r * minPaceFactor. */
    val minPace: Double,
    val minPaceFactor: Double,
    val isWhatIf: Boolean,
    /** U + r * R */
    val projectedTotal: Double,
    /** (L - U) / R, or null when no days remain. Zero when the limit is already used up. */
    val requiredPace: Double?,
    /** x: continuous days from today you can keep the current pace. Null when no exit applies. */
    val lastExitDays: Double?,
    /** Whole days left before the last exit (floor of x). 0 means "change course today". */
    val daysToLastExit: Int?,
    val lastExitDate: LocalDate?,
    /** Day on which the current pace reaches the limit, if that happens by the deadline. */
    val limitHitDate: LocalDate?,
    /** Day the logged history first went over the limit, if it already has. */
    val crossedOn: LocalDate?,
    /** U + m * R - L when the line is passed: the overshoot even at the lowest realistic pace. */
    val overshootAtMinPace: Double,
    val redWindowDays: Int,
    val recovery: Recovery?,
    /** True while there is too little history for a confident pace (first days, single entry). */
    val isEarlyEstimate: Boolean,
    /** True when a worse stage was held back to ACT_SOON because there are only 1-2 days of data. */
    val isCappedEarly: Boolean,
    val entryCount: Int,
) {
    val remaining: Double get() = limit - used
    val isOverLimit: Boolean get() = used > limit + ForecastEngine.EPSILON
    val projectedOverBy: Double get() = (projectedTotal - limit).coerceAtLeast(0.0)
    val hasStarted: Boolean get() = elapsedDays > 0
    val hasEnded: Boolean get() = today.isAfter(deadline)
    val isFinalDay: Boolean get() = today == deadline
    val usedFraction: Double get() = used / limit
    val projectedFraction: Double get() = projectedTotal / limit
}

/** Concrete ways back under the limit, computed only when the projection overshoots. */
data class Recovery(
    /** q: daily pace that lands exactly on the limit if you change course today. */
    val paceIfActToday: Double,
    val cutPerDayIfActToday: Double,
    /** Share of the current pace to cut today, 0..1. */
    val cutFractionIfActToday: Double,
    /** False when q is below the lowest realistic pace m. */
    val isActTodayRealistic: Boolean,
    /** Pace needed if you keep going until the last exit date, then change course. */
    val paceIfWaitUntilLastExit: Double?,
    val cutPerDayIfWait: Double?,
    /** P - L: how much more limit you would need to keep the current pace. */
    val extraLimitNeeded: Double,
    /** How many days before the deadline the current pace runs out. */
    val daysShortAtCurrentPace: Int,
)
