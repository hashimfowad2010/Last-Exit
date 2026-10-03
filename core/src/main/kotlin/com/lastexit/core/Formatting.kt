package com.lastexit.core

import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formats amounts the way a person would say them: "$1,240", "12.5 h", "3.2 GB", "40 pts". */
class AmountFormat(
    val type: TrackerType,
    unit: String,
    private val locale: Locale = Locale.getDefault(),
) {
    val unit: String = unit.trim().ifEmpty { type.defaultUnit }

    fun number(value: Double): String {
        val magnitude = abs(value)
        val format = NumberFormat.getNumberInstance(locale)
        val isWhole = abs(magnitude - magnitude.roundToLong()) < 0.005
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = when {
            magnitude >= 100 -> 0
            type == TrackerType.MONEY -> if (isWhole) 0 else 2
            magnitude >= 10 -> 1
            else -> 2
        }
        if (type == TrackerType.MONEY && magnitude < 100 && !isWhole) format.minimumFractionDigits = 2
        return format.format(value)
    }

    fun amount(value: Double): String {
        val sign = if (value < -0.004) "-" else ""
        val digits = number(abs(value))
        return when {
            type != TrackerType.MONEY -> "$sign$digits $unit"
            unit.none { it.isLetter() } -> "$sign$unit$digits"
            else -> "$sign$unit $digits"
        }
    }

    fun pace(value: Double): String = "${amount(value)}/day"

    private fun Double.roundToLong(): Long = Math.round(this)
}

object DateText {
    fun short(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val pattern = if (date.year == today.year) "MMM d" else "MMM d, yyyy"
        return DateTimeFormatter.ofPattern(pattern, locale).format(date)
    }

    fun withWeekday(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofPattern("EEE, MMM d", locale).format(date)

    /** "Today", "Yesterday", "Tomorrow" or a short date. */
    fun relative(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        when (ChronoUnit.DAYS.between(today, date)) {
            0L -> "Today"
            -1L -> "Yesterday"
            1L -> "Tomorrow"
            else -> short(date, today, locale)
        }

    fun days(count: Int): String = if (count == 1) "1 day" else "$count days"

    fun percent(fraction: Double): String = "${(fraction * 100).roundToInt()}%"
}
