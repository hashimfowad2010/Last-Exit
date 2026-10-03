package com.lastexit.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForecastSeriesTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val today = LocalDate.of(2026, 11, 10)

    private fun series(limit: Double): ForecastSeries {
        val input = ForecastInput(
            limit, start, LocalDate.of(2026, 11, 30), today,
            (0 until 10).map { Usage(start.plusDays(it.toLong()), 60.0) }, 0.5,
        )
        return ForecastSeries.from(input, ForecastEngine.forecast(input))
    }

    @Test
    fun actualLineIsCumulativeUpToToday() {
        val s = series(1290.0)
        assertEquals(30, s.totalDays)
        assertEquals(10, s.todayX)
        assertEquals(11, s.actual.size)
        assertEquals(0.0, s.actual.first(), 0.0)
        assertEquals(600.0, s.actual.last(), 1e-9)
    }

    @Test
    fun markersSitWhereTheMathSays() {
        val s = series(1290.0)
        assertEquals(13.0, s.lastExitX!!, 1e-9) // today + 3 days
        assertEquals(21.5, s.limitHitX!!, 1e-9) // today + 690 / 60
        assertEquals(1800.0, s.projectedEnd, 1e-9)
        assertEquals(1290.0, s.exitPathEnd!!, 1e-9) // switching at the exit lands exactly on the limit
        assertEquals(1290.0, s.projectedAt(21.5), 1e-9)
    }

    @Test
    fun safeTrackersHaveNoMarkers() {
        val s = series(5000.0)
        assertNull(s.lastExitX)
        assertNull(s.limitHitX)
    }
}
