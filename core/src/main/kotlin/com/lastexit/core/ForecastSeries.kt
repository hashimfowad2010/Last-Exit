package com.lastexit.core

import kotlin.math.max

/**
 * Everything a chart needs, in "day space": x = 0 is the start of the first day and x = i is the
 * end of day i, so the whole period spans 0..[totalDays] and "now" is the end of today.
 */
data class ForecastSeries(
    val totalDays: Int,
    val todayX: Int,
    val limit: Double,
    /** Cumulative usage at x = 0..todayX. */
    val actual: List<Double>,
    val used: Double,
    /** Straight line from (todayX, used) at the current pace. */
    val projectedEnd: Double,
    /** Straight line from (todayX, used) that lands exactly on the limit. Null if it is already used up. */
    val requiredEnd: Double?,
    /** End of the last exit day, when there is one. */
    val lastExitX: Double?,
    /** Where the current-pace projection crosses the limit (continuous). */
    val limitHitX: Double?,
    /** Path that keeps the current pace until the last exit, then drops to the lowest realistic pace. */
    val exitPathEnd: Double?,
    val minPace: Double,
    val pace: Double,
    val status: Status,
) {
    /** Upper bound for the y axis with a little headroom above the tallest line. */
    val yMax: Double
        get() = max(max(limit, projectedEnd), max(used, actual.maxOrNull() ?: 0.0)) * 1.12

    fun projectedAt(x: Double): Double = used + pace * (x - todayX)

    companion object {
        fun from(input: ForecastInput, forecast: Forecast): ForecastSeries {
            val counted = input.usages.filter { !it.date.isAfter(input.today) }
            val todayX = forecast.elapsedDays
            val actual = ArrayList<Double>(todayX + 1)
            var running = counted.filter { it.date.isBefore(input.startDate) }.sumOf { it.amount }
            actual += running
            for (day in 0 until todayX) {
                val date = input.startDate.plusDays(day.toLong())
                running += counted.filter { it.date == date }.sumOf { it.amount }
                actual += running
            }
            val remaining = forecast.remainingDays
            val hitX = if (forecast.pace > ForecastEngine.EPSILON && forecast.used <= forecast.limit &&
                forecast.projectedTotal > forecast.limit
            ) {
                todayX + (forecast.limit - forecast.used) / forecast.pace
            } else {
                null
            }
            val exitX = forecast.daysToLastExit?.let { (todayX + it).toDouble() }
            val exitPathEnd = forecast.daysToLastExit?.let { k ->
                forecast.used + forecast.pace * k + forecast.minPace * (remaining - k)
            }
            return ForecastSeries(
                totalDays = forecast.totalDays,
                todayX = todayX,
                limit = forecast.limit,
                actual = actual,
                used = forecast.used,
                projectedEnd = forecast.projectedTotal,
                requiredEnd = if (forecast.used <= forecast.limit && remaining > 0) forecast.limit else null,
                lastExitX = exitX,
                limitHitX = hitX,
                exitPathEnd = exitPathEnd,
                minPace = forecast.minPace,
                pace = forecast.pace,
                status = forecast.status,
            )
        }
    }
}
