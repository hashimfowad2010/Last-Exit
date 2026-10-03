package com.lastexit.core

import java.time.LocalDate
import kotlin.math.floor

/**
 * A believable month of spending against a $1,500 budget. It starts calm, a weekend away pushes
 * the pace up, and the status escalates SAFE -> ACT_SOON -> LAST_EXIT -> PAST_THE_LINE while the
 * limit is still days away from being crossed. Everything is deterministic and in memory, so the
 * demo never touches the user's real trackers.
 */
object DemoScenario {
    const val NAME = "Monthly budget (demo)"
    const val UNIT = "$"
    const val LIMIT = 1500.0
    const val DAYS = 30
    const val MIN_PACE_FACTOR = 0.4

    /** Share of the "act today" pace the disciplined plan aims for after taking the exit. */
    const val EXIT_PLAN_FACTOR = 0.9

    private val spends = doubleArrayOf(
        38.0, 45.5, 31.0, 52.0, 40.0, 36.5, 48.0, 44.0, 39.0, // days 1-9: comfortably under $50/day
        96.0, 128.0, 84.0, 76.0, 82.0, 71.0, 77.0, 74.0, 86.0, 81.0, // days 10-19: a weekend away, then habits stick
        77.0, 90.0, 83.0, 72.0, 95.0, 78.0, 84.0, 88.0, 76.0, 91.0, // days 20-30: the line is crossed
        86.0,
    )

    private val notes = arrayOf(
        "Groceries", "Coffee & lunch", "Fuel", "Groceries", "Pharmacy", "Lunch", "Groceries", "Phone top-up",
        "Coffee & lunch", "Concert tickets", "Weekend trip", "Dinner out", "Groceries", "Takeaway", "New headphones",
        "Dinner with friends", "Groceries", "Taxi rides", "Takeaway", "Groceries", "Birthday gift", "Lunch out",
        "Groceries", "Weekend brunch", "Fuel", "Takeaway", "Clothes", "Groceries", "Dinner out", "Groceries",
    )

    data class DemoEntry(val day: Int, val date: LocalDate, val amount: Double, val note: String)

    /** After taking the exit, every remaining day is held to [dailyPace]. */
    data class ExitPlan(val fromDay: Int, val dailyPace: Double)

    /** The demo month starts on the 1st of the real current month so dates look natural. */
    fun startDate(realToday: LocalDate): LocalDate = realToday.withDayOfMonth(1)

    fun deadline(start: LocalDate): LocalDate = start.plusDays(DAYS - 1L)

    fun dateOf(start: LocalDate, day: Int): LocalDate = start.plusDays(day - 1L)

    fun plannedSpend(day: Int): Double = spends[(day - 1).coerceIn(0, DAYS - 1)]

    /** Entries visible at the end of simulated [day] (1-based). */
    fun entries(start: LocalDate, day: Int, plan: ExitPlan? = null): List<DemoEntry> =
        (1..day.coerceIn(0, DAYS)).map { d ->
            if (plan != null && d >= plan.fromDay) {
                DemoEntry(d, dateOf(start, d), plan.dailyPace, if (d % 2 == 0) "Cooked at home" else "Groceries (planned)")
            } else {
                DemoEntry(d, dateOf(start, d), spends[d - 1], notes[d - 1])
            }
        }

    fun input(start: LocalDate, day: Int, plan: ExitPlan? = null, paceOverride: Double? = null): ForecastInput =
        ForecastInput(
            limit = LIMIT,
            startDate = start,
            deadline = deadline(start),
            today = dateOf(start, day.coerceAtLeast(1)),
            usages = entries(start, day, plan).map { Usage(it.date, it.amount) },
            minPaceFactor = MIN_PACE_FACTOR,
            paceOverride = paceOverride,
        )

    fun forecast(start: LocalDate, day: Int, plan: ExitPlan? = null): Forecast =
        ForecastEngine.forecast(input(start, day, plan))

    /**
     * Taking the exit on [day]: from tomorrow, spend a whole-dollar amount a little below the pace
     * that lands exactly on the limit, so the weighted pace catches up and the status turns green.
     */
    fun exitPlan(start: LocalDate, day: Int): ExitPlan {
        val required = forecast(start, day).requiredPace ?: 0.0
        return ExitPlan(fromDay = day + 1, dailyPace = floor(required * EXIT_PLAN_FACTOR))
    }

    /** First day on which the running total goes over the limit, or null if it never does. */
    fun crossingDay(start: LocalDate, plan: ExitPlan? = null): Int? {
        var total = 0.0
        entries(start, DAYS, plan).forEach { entry ->
            total += entry.amount
            if (total > LIMIT + ForecastEngine.EPSILON) return entry.day
        }
        return null
    }

    /** First day each status appears on the untouched path, for the pitch summary. */
    fun firstDayOf(start: LocalDate, status: Status, plan: ExitPlan? = null): Int? =
        (1..DAYS).firstOrNull { forecast(start, it, plan).status == status }
}
