package com.lastexit.core

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForecastMessagesTest {
    private val start = LocalDate.of(2026, 11, 1)
    private val today = LocalDate.of(2026, 11, 10)
    private val money = AmountFormat(TrackerType.MONEY, "$", Locale.US)
    private val messages = ForecastMessages(money, Locale.US)

    private fun forecast(limit: Double, factor: Double = 0.5): Forecast {
        val usages = (0 until 10).map { Usage(start.plusDays(it.toLong()), 60.0) }
        return ForecastEngine.forecast(ForecastInput(limit, start, LocalDate.of(2026, 11, 30), today, usages, factor))
    }

    @Test
    fun actSoonHeadlineNamesTheDateAndTheDaysLeft() {
        assertEquals(
            "At this pace you hit your budget on Nov 22. You have 4 days to change course.",
            messages.headline(forecast(1320.0)),
        )
    }

    @Test
    fun pastTheLineHeadlineQuantifiesTheOvershoot() {
        assertEquals(
            "Even at your lowest possible pace, you'll exceed the limit by about $1.",
            messages.headline(forecast(1199.0)),
        )
    }

    @Test
    fun overLimitHeadline() {
        assertEquals("You're $100 over your budget.", messages.headline(forecast(500.0)))
    }

    @Test
    fun lastExitTodayTellsYouTheExactPace() {
        assertEquals("Change course today: drop to $30/day to stay under.", messages.headline(forecast(1200.0)))
    }

    @Test
    fun chipLabelsAlwaysCarryText() {
        assertEquals("Safe", messages.chipLabel(forecast(5000.0)))
        assertEquals("Act soon", messages.chipLabel(forecast(1320.0)))
        assertEquals("Last exit · 3d", messages.chipLabel(forecast(1290.0)))
        assertEquals("Last exit today", messages.chipLabel(forecast(1200.0)))
        assertEquals("Past the line", messages.chipLabel(forecast(1199.0)))
    }

    @Test
    fun recoveryStepsCoverAllThreeOptions() {
        val steps = messages.recoverySteps(forecast(1290.0))
        assertEquals(3, steps.size)
        assertEquals("Act today: cut $25.50/day (43%) to $34.50/day for the next 20 days.", steps[0])
        assertTrue(steps[1].startsWith("Wait until the last exit (Nov 13)"))
        assertEquals("Change nothing: you'd need $510 more, or you'd run out 8 days early (Nov 22).", steps[2])
    }

    @Test
    fun damageControlWhenPastTheLine() {
        val steps = messages.recoverySteps(forecast(1199.0))
        assertTrue(steps[0].startsWith("Damage control"))
    }

    @Test
    fun accessibilitySummarySpellsOutTheStatus() {
        val summary = messages.accessibilitySummary("Groceries", forecast(1290.0))
        assertTrue(summary, summary.contains("Status: Last exit in 3 days."))
        assertFalse(summary.contains("·"))
    }

    @Test
    fun notificationBodyIncludesAnAction() {
        val body = messages.notificationBody(forecast(1320.0))
        assertTrue(body, body.endsWith("Cut to $36/day now, or by Nov 14 at the latest."))
    }

    @Test
    fun earlyWarningSaysItIsAnEstimate() {
        val day1 = LocalDate.of(2026, 10, 3)
        val f = ForecastEngine.forecast(ForecastInput(40.0, day1, day1.plusDays(27), day1, listOf(Usage(day1, 15.0)), 0.5))
        val hours = ForecastMessages(AmountFormat(TrackerType.TIME, "h", Locale.US), Locale.US)
        assertEquals("Act soon: early warning", hours.title(f))
        assertEquals(
            "Early estimate: at this pace you'd hit your time budget on Oct 5. Log a couple more days before this firms up.",
            hours.headline(f),
        )
    }

    @Test
    fun amountFormatsReadNaturally() {
        assertEquals("$1,240", money.amount(1240.4))
        assertEquals("$42.50", money.amount(42.5))
        assertEquals("$42", money.amount(42.0))
        assertEquals("-$5", money.amount(-5.0))
        assertEquals("Rs 1,240", AmountFormat(TrackerType.MONEY, "Rs", Locale.US).amount(1240.0))
        assertEquals("12.5 h", AmountFormat(TrackerType.TIME, "h", Locale.US).amount(12.5))
        assertEquals("3.25 GB", AmountFormat(TrackerType.RESOURCE, "GB", Locale.US).amount(3.25))
        assertEquals("40 pts", AmountFormat(TrackerType.RISK, " ", Locale.US).amount(40.0))
        assertEquals("1.5 h/day", AmountFormat(TrackerType.TIME, "h", Locale.US).pace(1.5))
    }

    @Test
    fun relativeDates() {
        assertEquals("Today", DateText.relative(today, today, Locale.US))
        assertEquals("Yesterday", DateText.relative(today.minusDays(1), today, Locale.US))
        assertEquals("Nov 3", DateText.relative(LocalDate.of(2026, 11, 3), today, Locale.US))
        assertEquals("Jan 3, 2027", DateText.short(LocalDate.of(2027, 1, 3), today, Locale.US))
    }
}
