package com.lastexit.core

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A ready-made limit. Dates are computed from "today" so templates stay correct any day of the year. */
data class TrackerTemplate(
    val id: String,
    val title: String,
    val description: String,
    val type: TrackerType,
    val name: String,
    val unit: String,
    val limit: Double,
    /** Length of the period in days, or null for "until the end of the month". */
    val periodDays: Int?,
    val minPaceFactor: Double,
) {
    fun startDate(today: LocalDate): LocalDate = today

    fun deadline(today: LocalDate): LocalDate {
        if (periodDays != null) return today.plusDays(periodDays - 1L)
        // A monthly limit created late in the month would be uselessly short, so roll to next month.
        val endOfMonth = today.with(TemporalAdjusters.lastDayOfMonth())
        return if (ForecastEngine.daysBetween(today, endOfMonth) >= MIN_MONTHLY_DAYS) {
            endOfMonth
        } else {
            today.plusMonths(1).with(TemporalAdjusters.lastDayOfMonth())
        }
    }

    companion object {
        const val MIN_MONTHLY_DAYS = 13
    }
}

object Templates {
    val monthlyBudget = TrackerTemplate(
        id = "budget",
        title = "Monthly Budget",
        description = "Spending against what you can afford this month.",
        type = TrackerType.MONEY,
        name = "Monthly budget",
        unit = TrackerType.MONEY.defaultUnit,
        limit = 2000.0,
        periodDays = null,
        minPaceFactor = 0.4,
    )

    val studyHours = TrackerTemplate(
        id = "study",
        title = "Student Study Hours",
        description = "A weekly cap on study hours so you don't burn out before exams.",
        type = TrackerType.TIME,
        name = "Study hours (burnout cap)",
        unit = "h",
        limit = 30.0,
        periodDays = 7,
        minPaceFactor = 0.3,
    )

    val freelanceHours = TrackerTemplate(
        id = "freelance",
        title = "Freelance Project Hours",
        description = "Hours burned on a fixed-price project before it stops paying.",
        type = TrackerType.TIME,
        name = "Client project hours",
        unit = "h",
        limit = 40.0,
        periodDays = 28,
        minPaceFactor = 0.5,
    )

    val mobileData = TrackerTemplate(
        id = "data",
        title = "Mobile Data Plan",
        description = "Gigabytes used before your plan throttles or charges extra.",
        type = TrackerType.RESOURCE,
        name = "Mobile data",
        unit = "GB",
        limit = 15.0,
        periodDays = null,
        minPaceFactor = 0.2,
    )

    val overloadRisk = TrackerTemplate(
        id = "risk",
        title = "Overload Risk",
        description = "Each commitment adds risk points. Stay under your threshold.",
        type = TrackerType.RISK,
        name = "Overload risk",
        unit = "pts",
        limit = 100.0,
        periodDays = 14,
        minPaceFactor = 0.3,
    )

    val all: List<TrackerTemplate> = listOf(monthlyBudget, studyHours, freelanceHours, mobileData, overloadRisk)

    fun byId(id: String?): TrackerTemplate? = all.firstOrNull { it.id == id }
}
