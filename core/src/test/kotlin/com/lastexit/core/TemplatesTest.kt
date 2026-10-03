package com.lastexit.core

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatesTest {
    @Test
    fun monthlyTemplatesRunToTheEndOfTheMonth() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(LocalDate.of(2026, 10, 31), Templates.monthlyBudget.deadline(today))
    }

    @Test
    fun monthlyTemplatesRollOverLateInTheMonth() {
        val today = LocalDate.of(2026, 10, 25)
        assertEquals(LocalDate.of(2026, 11, 30), Templates.mobileData.deadline(today))
    }

    @Test
    fun fixedLengthTemplatesCountToday() {
        val today = LocalDate.of(2026, 10, 3)
        assertEquals(LocalDate.of(2026, 10, 9), Templates.studyHours.deadline(today)) // 7 days
    }

    @Test
    fun everyTemplateProducesAValidForecast() {
        val today = LocalDate.of(2026, 2, 27)
        Templates.all.forEach { t ->
            assertTrue(t.limit > 0)
            assertFalse(t.deadline(today).isBefore(t.startDate(today)))
            val f = ForecastEngine.forecast(
                ForecastInput(t.limit, t.startDate(today), t.deadline(today), today, emptyList(), t.minPaceFactor),
            )
            assertEquals(Status.SAFE, f.status)
        }
        assertEquals(5, Templates.all.map { it.id }.toSet().size)
    }
}
