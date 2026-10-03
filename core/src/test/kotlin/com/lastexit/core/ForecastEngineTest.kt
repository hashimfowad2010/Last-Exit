package com.lastexit.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastEngineTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val deadline = LocalDate.of(2026, 11, 30)
    private val day10 = LocalDate.of(2026, 11, 10)

    /** [days] days of [daily] each, starting on the 1st. */
    private fun steady(daily: Double, days: Int = 10): List<Usage> =
        (0 until days).map { Usage(start.plusDays(it.toLong()), daily) }

    private fun forecast(
        limit: Double,
        usages: List<Usage>,
        today: LocalDate = day10,
        factor: Double = 0.5,
        override: Double? = null,
    ) = ForecastEngine.forecast(ForecastInput(limit, start, deadline, today, usages, factor, override))

    // ---- Edge cases -------------------------------------------------------------------------

    @Test
    fun zeroEntriesIsSafeWithNoExit() {
        val f = forecast(1000.0, emptyList())
        assertEquals(Status.SAFE, f.status)
        assertEquals(0.0, f.used, 0.0)
        assertEquals(0.0, f.pace, 0.0)
        assertEquals(50.0, f.requiredPace!!, 1e-9) // 1000 over 20 remaining days
        assertNull(f.lastExitDate)
        assertNull(f.limitHitDate)
        assertNull(f.recovery)
        assertTrue(f.isEarlyEstimate)
        assertEquals(0, f.entryCount)
    }

    @Test
    fun noDaysRemainingOnTheDeadlineIsSafeWhenUnder() {
        val f = forecast(1000.0, steady(30.0, 30), today = deadline)
        assertEquals(0, f.remainingDays)
        assertEquals(Status.SAFE, f.status)
        assertNull(f.requiredPace)
        assertNull(f.recovery)
        assertTrue(f.isFinalDay)
    }

    @Test
    fun noDaysRemainingAfterTheDeadlineAndOverIsPastTheLine() {
        val f = forecast(1000.0, steady(40.0, 30), today = deadline.plusDays(3))
        assertEquals(0, f.remainingDays)
        assertEquals(30, f.elapsedDays)
        assertEquals(Status.PAST_THE_LINE, f.status)
        assertTrue(f.hasEnded)
        assertEquals(LocalDate.of(2026, 11, 26), f.crossedOn) // 26 * 40 = 1040 > 1000
    }

    @Test
    fun zeroPaceIsSafeEvenCloseToTheLimit() {
        val f = forecast(1000.0, listOf(Usage(start, 990.0, oneOff = true)))
        assertEquals(0.0, f.pace, 0.0)
        assertEquals(990.0, f.projectedTotal, 1e-9)
        assertEquals(Status.SAFE, f.status)
        assertNull(f.limitHitDate)
    }

    @Test
    fun paceAtOrBelowMinimumMeansNoExitExists() {
        // minPaceFactor 1.0: no realistic way to slow down, so an overshoot is already past the line.
        val over = forecast(1000.0, steady(60.0), factor = 1.0)
        assertEquals(Status.PAST_THE_LINE, over.status)
        assertEquals(Double.NEGATIVE_INFINITY, over.lastExitDays!!, 0.0)
        assertNull(over.lastExitDate)
        // Same pace but enough room: safe, and the r <= m branch is never reached.
        val under = forecast(2000.0, steady(60.0), factor = 1.0)
        assertEquals(Status.SAFE, under.status)
        assertEquals(Double.POSITIVE_INFINITY, ForecastEngine.lastExitDays(2000.0, 600.0, 60.0, 60.0, 20), 0.0)
    }

    @Test
    fun negativeAmountsReduceUsageButNeverMakeThePaceNegative() {
        val refunds = listOf(Usage(start, -50.0), Usage(start.plusDays(2), -20.0))
        val f = forecast(1000.0, refunds)
        assertEquals(-70.0, f.used, 1e-9)
        assertEquals(0.0, f.pace, 0.0)
        assertEquals(Status.SAFE, f.status)

        val mixed = forecast(1000.0, steady(60.0) + Usage(day10, -200.0))
        assertEquals(400.0, mixed.used, 1e-9)
        assertTrue(mixed.pace > 0.0)
    }

    @Test
    fun limitAlreadyExceededIsPastTheLineWithoutRecovery() {
        val f = forecast(500.0, steady(60.0))
        assertEquals(Status.PAST_THE_LINE, f.status)
        assertTrue(f.isOverLimit)
        assertEquals(LocalDate.of(2026, 11, 9), f.crossedOn) // 9 * 60 = 540 > 500
        assertNull(f.recovery)
        assertNull(f.limitHitDate)
        assertEquals(0.0, f.requiredPace!!, 0.0)
        assertEquals(600.0 + 30.0 * 20 - 500.0, f.overshootAtMinPace, 1e-9)
    }

    @Test
    fun usedExactlyAtLimitDependsOnWhetherYouCanStop() {
        val atLimit = steady(60.0) // U = 600
        assertEquals(Status.SAFE, forecast(600.0, steady(60.0).map { it.copy(oneOff = true) }).status)
        assertEquals(Status.PAST_THE_LINE, forecast(600.0, atLimit, factor = 0.5).status)
        // If stopping completely is realistic, the exit is today.
        val canStop = forecast(600.0, atLimit, factor = 0.0)
        assertEquals(Status.LAST_EXIT, canStop.status)
        assertEquals(0, canStop.daysToLastExit)
        assertEquals(day10, canStop.limitHitDate)
    }

    @Test
    fun beforeTheStartNothingHasHappenedYet() {
        val f = forecast(1000.0, emptyList(), today = start.minusDays(5))
        assertFalse(f.hasStarted)
        assertEquals(0, f.elapsedDays)
        assertEquals(30, f.remainingDays)
        assertEquals(Status.SAFE, f.status)
    }

    @Test
    fun futureEntriesAreIgnoredAndEarlierEntriesCount() {
        val usages = steady(10.0) + Usage(day10.plusDays(1), 5000.0) + Usage(start.minusDays(2), 100.0)
        val f = forecast(1000.0, usages)
        assertEquals(200.0, f.used, 1e-9)
        assertEquals(11, f.entryCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveLimitIsRejected() {
        forecast(0.0, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun deadlineBeforeStartIsRejected() {
        ForecastEngine.forecast(ForecastInput(100.0, deadline, start, day10, emptyList()))
    }

    @Test
    fun oneBigFirstDayIsAnEarlyWarningNotPastTheLine() {
        // 15 h logged on day 1 of a 28-day, 40 h project: extrapolating one day would say "too late".
        val projectStart = LocalDate.of(2026, 10, 3)
        val input = ForecastInput(40.0, projectStart, projectStart.plusDays(27), projectStart, listOf(Usage(projectStart, 15.0)), 0.5)
        val f = ForecastEngine.forecast(input)
        assertEquals(Status.ACT_SOON, f.status)
        assertTrue(f.isCappedEarly)
        assertNull(f.lastExitDate)
        assertNotNull(f.recovery)
        // From day 3 the pace has enough history and the real stage shows.
        val later = ForecastEngine.forecast(input.copy(today = projectStart.plusDays(2), usages = input.usages + Usage(projectStart.plusDays(1), 15.0)))
        assertFalse(later.isCappedEarly)
        assertEquals(Status.PAST_THE_LINE, later.status)
    }

    @Test
    fun earlyCapNeverHidesARealOverage() {
        val f = forecast(100.0, listOf(Usage(start, 150.0)), today = start)
        assertEquals(Status.PAST_THE_LINE, f.status)
        assertFalse(f.isCappedEarly)
    }

    @Test
    fun earlyWindowScalesWithShortPeriods() {
        assertEquals(1, ForecastEngine.earlyDays(3)) // nothing capped once a day has passed
        assertEquals(2, ForecastEngine.earlyDays(7))
        assertEquals(3, ForecastEngine.earlyDays(30))
    }

    // ---- Burn rate --------------------------------------------------------------------------

    @Test
    fun burnRateWeightsTheLastSevenDays() {
        val usages = (0 until 14).map { Usage(start.plusDays(it.toLong()), if (it < 7) 10.0 else 30.0) }
        val today = start.plusDays(13)
        // recent = 210 / 7 = 30, overall = 280 / 14 = 20, r = 0.7 * 30 + 0.3 * 20 = 27
        assertEquals(27.0, ForecastEngine.burnRate(usages, start, today, 14), 1e-9)
    }

    @Test
    fun burnRateUsesAvailableDaysWhenYoungerThanAWeek() {
        val usages = listOf(Usage(start, 10.0), Usage(start.plusDays(1), 20.0), Usage(start.plusDays(2), 30.0))
        assertEquals(20.0, ForecastEngine.burnRate(usages, start, start.plusDays(2), 3), 1e-9)
    }

    @Test
    fun oneOffEntriesCountAsUsedButNotAsPace() {
        val f = forecast(2000.0, steady(20.0) + Usage(start.plusDays(2), 500.0, oneOff = true))
        assertEquals(700.0, f.used, 1e-9)
        assertEquals(20.0, f.pace, 1e-9)
    }

    // ---- Stage boundaries (U = 600, r = 60, m = 30, R = 20, red window = 3) -----------------

    @Test
    fun projectedExactlyAtLimitIsSafe() {
        val f = forecast(1800.0, steady(60.0)) // P = 600 + 60 * 20 = 1800
        assertEquals(1800.0, f.projectedTotal, 1e-9)
        assertEquals(Status.SAFE, f.status)
    }

    @Test
    fun lastExitExactlyAtRedWindowIsLastExit() {
        val f = forecast(1290.0, steady(60.0)) // x = (1290 - 600 - 600) / 30 = 3
        assertEquals(3.0, f.lastExitDays!!, 1e-9)
        assertEquals(Status.LAST_EXIT, f.status)
        assertEquals(3, f.daysToLastExit)
        assertEquals(LocalDate.of(2026, 11, 13), f.lastExitDate)
        assertEquals(LocalDate.of(2026, 11, 22), f.limitHitDate) // h = 690 / 60 = 11.5 -> day 12
    }

    @Test
    fun lastExitOneDayBeyondRedWindowIsActSoon() {
        val f = forecast(1320.0, steady(60.0)) // x = 4
        assertEquals(Status.ACT_SOON, f.status)
        assertEquals(LocalDate.of(2026, 11, 14), f.lastExitDate)
        assertEquals(LocalDate.of(2026, 11, 22), f.limitHitDate) // h = 12 exactly
    }

    @Test
    fun lastExitTodayIsStillLastExit() {
        val f = forecast(1200.0, steady(60.0)) // x = 0
        assertEquals(Status.LAST_EXIT, f.status)
        assertEquals(0, f.daysToLastExit)
        assertEquals(day10, f.lastExitDate)
    }

    @Test
    fun lastExitJustPassedIsPastTheLine() {
        val f = forecast(1199.0, steady(60.0)) // x = -1/30
        assertEquals(Status.PAST_THE_LINE, f.status)
        assertNull(f.lastExitDate)
        assertEquals(1.0, f.overshootAtMinPace, 1e-9)
        assertFalse(f.isOverLimit)
    }

    @Test
    fun stageForExitBoundaries() {
        assertEquals(Status.LAST_EXIT, ForecastEngine.stageForExit(0.0, 3))
        assertEquals(Status.LAST_EXIT, ForecastEngine.stageForExit(3.0, 3))
        assertEquals(Status.LAST_EXIT, ForecastEngine.stageForExit(3.999, 3))
        assertEquals(Status.ACT_SOON, ForecastEngine.stageForExit(4.0, 3))
        assertEquals(Status.PAST_THE_LINE, ForecastEngine.stageForExit(-0.001, 3))
        assertEquals(Status.ACT_SOON, ForecastEngine.stageForExit(Double.POSITIVE_INFINITY, 3))
    }

    @Test
    fun redWindowGrowsToFifteenPercentOfLongPeriods() {
        assertEquals(3, ForecastEngine.redWindowDays(0))
        assertEquals(3, ForecastEngine.redWindowDays(20)) // 15% of 20 = 3
        assertEquals(4, ForecastEngine.redWindowDays(30)) // 4.5 -> 4
        assertEquals(15, ForecastEngine.redWindowDays(100))
    }

    @Test
    fun longPeriodsUseTheWiderRedWindow() {
        val longStart = LocalDate.of(2026, 1, 1)
        val usages = (0 until 10).map { Usage(longStart.plusDays(it.toLong()), 10.0) }
        val today = longStart.plusDays(9)
        // R = 100, window = 15. U = 100, r = 10, m = 5. L = 100 + 500 + 5 * 14 = 670 -> x = 14.
        val f = ForecastEngine.forecast(ForecastInput(670.0, longStart, today.plusDays(100), today, usages, 0.5))
        assertEquals(14.0, f.lastExitDays!!, 1e-9)
        assertEquals(Status.LAST_EXIT, f.status)
    }

    // ---- Recovery ---------------------------------------------------------------------------

    @Test
    fun recoveryOffersActTodayWaitAndTradeOff() {
        val r = forecast(1290.0, steady(60.0)).recovery!!
        assertEquals(34.5, r.paceIfActToday, 1e-9) // (1290 - 600) / 20
        assertEquals(25.5, r.cutPerDayIfActToday, 1e-9)
        assertEquals(0.425, r.cutFractionIfActToday, 1e-9)
        assertTrue(r.isActTodayRealistic)
        assertEquals(30.0, r.paceIfWaitUntilLastExit!!, 1e-9) // waiting the full x days leaves exactly m
        assertEquals(30.0, r.cutPerDayIfWait!!, 1e-9)
        assertEquals(510.0, r.extraLimitNeeded, 1e-9) // 1800 - 1290
        assertEquals(8, r.daysShortAtCurrentPace) // hits on day 12 of 20
    }

    @Test
    fun recoveryFlagsUnrealisticCuts() {
        val r = forecast(1199.0, steady(60.0)).recovery!!
        assertFalse(r.isActTodayRealistic) // needs 29.95/day, below m = 30
        assertNull(r.paceIfWaitUntilLastExit)
    }

    @Test
    fun minPaceFactorZeroMakesTheLastExitTheLimitHitDay() {
        val f = forecast(1290.0, steady(60.0), factor = 0.0)
        assertEquals(11.5, f.lastExitDays!!, 1e-9) // same as h: you could stop the moment you hit it
        assertEquals(Status.ACT_SOON, f.status)
    }

    // ---- What if? ---------------------------------------------------------------------------

    @Test
    fun paceOverrideChangesTheForecastButNotTheMeasuredPace() {
        val real = forecast(1290.0, steady(60.0))
        val whatIf = forecast(1290.0, steady(60.0), override = 30.0)
        assertEquals(Status.LAST_EXIT, real.status)
        assertEquals(Status.SAFE, whatIf.status) // 600 + 30 * 20 = 1200
        assertEquals(60.0, whatIf.measuredPace, 1e-9)
        assertEquals(30.0, whatIf.pace, 1e-9)
        assertTrue(whatIf.isWhatIf)
        assertFalse(real.isWhatIf)
        val worse = forecast(1290.0, steady(60.0), override = 200.0)
        assertEquals(Status.PAST_THE_LINE, worse.status)
    }

    @Test
    fun statusSeverityOrdering() {
        assertTrue(Status.LAST_EXIT.isWorseThan(Status.ACT_SOON))
        assertFalse(Status.SAFE.isWorseThan(Status.SAFE))
        assertEquals(Status.SAFE, Status.fromName(null))
        assertEquals(Status.ACT_SOON, Status.fromName("ACT_SOON"))
        assertNotNull(Status.fromName("garbage"))
    }
}
