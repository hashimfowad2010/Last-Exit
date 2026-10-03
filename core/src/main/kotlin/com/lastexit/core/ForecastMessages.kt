package com.lastexit.core

import java.util.Locale

/**
 * Plain-language copy for every status. Kept here, next to the math, so the words are unit tested
 * and can never contradict the numbers.
 */
class ForecastMessages(
    private val format: AmountFormat,
    private val locale: Locale = Locale.getDefault(),
) {
    private val noun = format.type.limitNoun

    /** Short chip text. Colour is never the only signal: the chip also has an icon and this label. */
    fun chipLabel(f: Forecast): String = when (f.status) {
        Status.SAFE -> if (f.hasEnded) "Done" else "Safe"
        Status.ACT_SOON -> "Act soon"
        Status.LAST_EXIT -> if (f.daysToLastExit == 0) "Last exit today" else "Last exit · ${f.daysToLastExit}d"
        Status.PAST_THE_LINE -> "Past the line"
    }

    /** Big title for the detail hero and notification titles. */
    fun title(f: Forecast): String = when (f.status) {
        Status.SAFE -> when {
            f.hasEnded -> "Finished under the limit"
            f.entryCount == 0 -> "Ready when you are"
            else -> "You're on track"
        }
        Status.ACT_SOON -> if (f.isCappedEarly) "Act soon: early warning" else "Act soon: last exit in ${days(f.daysToLastExit)}"
        Status.LAST_EXIT -> if (f.daysToLastExit == 0) "Last exit is today" else "Last exit in ${days(f.daysToLastExit)}"
        Status.PAST_THE_LINE -> if (f.isOverLimit) "Over the limit" else "Past the point of no return"
    }

    /** The one-line message on each card, e.g. "At this pace you hit your budget on Nov 14. ..." */
    fun headline(f: Forecast): String = when {
        f.status == Status.PAST_THE_LINE && f.isOverLimit ->
            "You're ${format.amount(f.used - f.limit)} over your $noun."
        f.status == Status.PAST_THE_LINE ->
            "Even at your lowest possible pace, you'll exceed the limit by about ${format.amount(f.overshootAtMinPace)}."
        !f.hasStarted -> "Starts ${date(f.startDate, f)}. Nothing to forecast yet."
        f.hasEnded -> "Period ended with ${format.amount(f.remaining)} to spare."
        f.entryCount == 0 -> "No entries yet. Log your first one to get a forecast."
        f.isFinalDay -> "Final day: ${format.amount(f.remaining)} left."
        f.status == Status.SAFE && f.pace <= ForecastEngine.EPSILON ->
            "No recent usage. ${format.amount(f.remaining)} left until ${date(f.deadline, f)}."
        f.status == Status.SAFE ->
            "On track to finish at ${format.amount(f.projectedTotal)} of ${format.amount(f.limit)}."
        f.status == Status.ACT_SOON && f.isCappedEarly ->
            "Early estimate: at this pace you'd hit your $noun on ${date(f.limitHitDate, f)}. " +
                "Log a couple more days before this firms up."
        f.status == Status.ACT_SOON ->
            "At this pace you hit your $noun on ${date(f.limitHitDate, f)}. " +
                "You have ${days(f.daysToLastExit)} to change course."
        f.daysToLastExit == 0 ->
            "Change course today: drop to ${format.pace(f.recovery?.paceIfActToday ?: 0.0)} to stay under."
        else ->
            "At this pace you hit your $noun on ${date(f.limitHitDate, f)}. " +
                "After ${date(f.lastExitDate, f)} it's too late to turn back."
    }

    /** Supporting line under the hero title. Stresses that the warning comes before the crossing. */
    fun subline(f: Forecast): String = when {
        f.status == Status.PAST_THE_LINE && f.isOverLimit ->
            f.crossedOn?.let { "Crossed on ${date(it, f)}. Every extra unit now adds to the overage." }
                ?: "Every extra unit now adds to the overage."
        f.status == Status.PAST_THE_LINE ->
            "The limit isn't crossed yet, but no realistic cut-back saves it. Switch to damage control."
        f.status == Status.SAFE && f.requiredPace != null && f.entryCount > 0 && !f.hasEnded ->
            "Needed pace ${format.pace(f.requiredPace)} · your pace ${format.pace(f.pace)}"
        f.status == Status.SAFE -> "${format.amount(f.remaining.coerceAtLeast(0.0))} left of ${format.amount(f.limit)}"
        else -> "You haven't crossed the limit yet: ${format.amount(f.remaining)} left."
    }

    /** Recovery suggestions (a) act today, (b) wait until the last exit, (c) change nothing. */
    fun recoverySteps(f: Forecast): List<String> {
        if (f.status == Status.PAST_THE_LINE) return damageControl(f)
        val r = f.recovery ?: return emptyList()
        val steps = ArrayList<String>(3)
        val actToday = StringBuilder()
            .append("Act today: cut ${format.amount(r.cutPerDayIfActToday)}/day (${DateText.percent(r.cutFractionIfActToday)})")
            .append(" to ${format.pace(r.paceIfActToday)} for the next ${days(f.remainingDays)}.")
        if (!r.isActTodayRealistic) actToday.append(" That's below what you said is realistic.")
        steps += actToday.toString()
        val wait = r.paceIfWaitUntilLastExit
        if (wait != null && f.lastExitDate != null && f.daysToLastExit != null && f.daysToLastExit > 0) {
            steps += "Wait until the last exit (${date(f.lastExitDate, f)}): you'd have to drop to " +
                "${format.pace(wait)} after that, a ${DateText.percent(1 - wait / f.pace)} cut."
        }
        steps += "Change nothing: you'd need ${format.amount(r.extraLimitNeeded)} more, " +
            "or you'd run out ${days(r.daysShortAtCurrentPace)} early" +
            (f.limitHitDate?.let { " (${date(it, f)})." } ?: ".")
        return steps
    }

    private fun damageControl(f: Forecast): List<String> {
        if (f.isOverLimit) {
            return listOf("Stop where you can: at your current pace you'd end ${format.amount(f.projectedTotal - f.limit)} over.")
        }
        val steps = ArrayList<String>(2)
        steps += "Damage control: drop to your lowest realistic pace (${format.pace(f.minPace)}) now to " +
            "finish about ${format.amount(f.overshootAtMinPace)} over instead of ${format.amount(f.projectedOverBy)}."
        if (f.pace > f.minPace) {
            steps += "Every day you wait adds about ${format.amount(f.pace - f.minPace)} to the overage."
        }
        return steps
    }

    /** Notification body: what happens and the one action that still works. */
    fun notificationBody(f: Forecast): String {
        val action = when {
            f.status == Status.ACT_SOON || (f.status == Status.LAST_EXIT && (f.daysToLastExit ?: 0) > 0) ->
                f.recovery?.let { " Cut to ${format.pace(it.paceIfActToday)} now, or by ${date(f.lastExitDate, f)} at the latest." } ?: ""
            else -> ""
        }
        return headline(f) + action
    }

    /** Full sentence for TalkBack so the status is never conveyed by colour alone. */
    fun accessibilitySummary(name: String, f: Forecast): String =
        "$name. Status: ${spokenStatus(f)}. Used ${format.amount(f.used)} of ${format.amount(f.limit)}. ${headline(f)}"

    /** Chip label written out for screen readers ("Last exit in 2 days" instead of "Last exit · 2d"). */
    fun spokenStatus(f: Forecast): String = when (f.status) {
        Status.LAST_EXIT -> title(f)
        else -> chipLabel(f)
    }

    private fun days(count: Int?): String = DateText.days(count ?: 0)

    private fun date(value: java.time.LocalDate?, f: Forecast): String =
        value?.let { DateText.short(it, f.today, locale) } ?: "the deadline"
}
