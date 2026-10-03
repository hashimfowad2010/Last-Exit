package com.lastexit.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoScenarioTest {
    private val start = DemoScenario.startDate(LocalDate.of(2026, 10, 17))

    @Test
    fun demoMonthStartsOnTheFirst() {
        assertEquals(LocalDate.of(2026, 10, 1), start)
        assertEquals(LocalDate.of(2026, 10, 30), DemoScenario.deadline(start))
    }

    @Test
    fun startsGreen() {
        assertEquals(Status.SAFE, DemoScenario.forecast(start, 1).status)
    }

    @Test
    fun escalatesInOrderBeforeTheLimitIsCrossed() {
        val actSoon = DemoScenario.firstDayOf(start, Status.ACT_SOON)!!
        val lastExit = DemoScenario.firstDayOf(start, Status.LAST_EXIT)!!
        val past = DemoScenario.firstDayOf(start, Status.PAST_THE_LINE)!!
        val crossing = DemoScenario.crossingDay(start)!!
        assertTrue("amber before red", actSoon < lastExit)
        assertTrue("red before past the line", lastExit < past)
        assertTrue("past the line before the actual crossing", past < crossing)
        assertTrue("red warning at least 5 days before the crossing", crossing - lastExit >= 5)
    }

    @Test
    fun statusNeverImprovesOnTheUntouchedPath() {
        var worst = Status.SAFE
        for (day in 1..DemoScenario.DAYS) {
            val status = DemoScenario.forecast(start, day).status
            assertTrue("day $day went from $worst to $status", status.severity >= worst.severity)
            worst = status
        }
    }

    @Test
    fun theRedAlertFiresWhileThereIsStillMoneyLeft() {
        val lastExit = DemoScenario.firstDayOf(start, Status.LAST_EXIT)!!
        val f = DemoScenario.forecast(start, lastExit)
        assertTrue(f.used < DemoScenario.LIMIT)
        assertNotNull(f.lastExitDate)
        assertNotNull(f.recovery)
    }

    @Test
    fun takingTheExitOnAnyWarningDayEndsUnderTheLimitAndGreen() {
        for (day in 1..DemoScenario.DAYS) {
            val status = DemoScenario.forecast(start, day).status
            if (status != Status.ACT_SOON && status != Status.LAST_EXIT) continue
            val plan = DemoScenario.exitPlan(start, day)
            assertNull("exit on day $day still crosses", DemoScenario.crossingDay(start, plan))
            val end = DemoScenario.forecast(start, DemoScenario.DAYS, plan)
            assertEquals("exit on day $day", Status.SAFE, end.status)
        }
    }

    @Test
    fun exitPlanIsRealistic() {
        val lastExit = DemoScenario.firstDayOf(start, Status.LAST_EXIT)!!
        val plan = DemoScenario.exitPlan(start, lastExit)
        val f = DemoScenario.forecast(start, lastExit)
        assertTrue("plan ${plan.dailyPace} below realistic minimum ${f.minPace}", plan.dailyPace >= f.minPace)
    }

    @Test
    fun entriesAreRevealedDayByDay() {
        assertEquals(0, DemoScenario.entries(start, 0).size)
        assertEquals(12, DemoScenario.entries(start, 12).size)
        assertEquals(DemoScenario.DAYS, DemoScenario.entries(start, 99).size)
    }
}
