package com.lastexit.app.data

import com.lastexit.core.AmountFormat
import com.lastexit.core.Forecast
import com.lastexit.core.ForecastEngine
import com.lastexit.core.ForecastInput
import com.lastexit.core.ForecastMessages
import com.lastexit.core.ForecastSeries
import com.lastexit.core.Status
import com.lastexit.core.TrackerType
import com.lastexit.core.Usage
import java.time.LocalDate

data class Tracker(
    val id: Long,
    val name: String,
    val type: TrackerType,
    val unit: String,
    val limit: Double,
    val startDate: LocalDate,
    val deadline: LocalDate,
    val minPaceFactor: Double,
    val lastNotifiedStatus: Status = Status.SAFE,
    val createdAt: Long = System.currentTimeMillis(),
)

data class Entry(
    val id: Long,
    val trackerId: Long,
    val date: LocalDate,
    val amount: Double,
    val note: String,
    val oneOff: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A tracker with all of its entries, newest first. */
data class TrackerRecord(val tracker: Tracker, val entries: List<Entry>) {
    fun forecastInput(
        today: LocalDate,
        paceOverride: Double? = null,
        minPaceFactor: Double = tracker.minPaceFactor,
        extra: Entry? = null,
    ): ForecastInput = ForecastInput(
        limit = tracker.limit,
        startDate = tracker.startDate,
        deadline = tracker.deadline,
        today = today,
        usages = (if (extra != null) entries + extra else entries).map { Usage(it.date, it.amount, it.oneOff) },
        minPaceFactor = minPaceFactor,
        paceOverride = paceOverride,
    )
}

/** Everything a screen needs to draw one tracker: the record, its forecast, and matching copy. */
class TrackerSnapshot(
    val record: TrackerRecord,
    val input: ForecastInput,
    val forecast: Forecast,
) {
    val tracker: Tracker get() = record.tracker
    val format = AmountFormat(record.tracker.type, record.tracker.unit)
    val messages = ForecastMessages(format)
    val series: ForecastSeries by lazy { ForecastSeries.from(input, forecast) }

    companion object {
        fun of(
            record: TrackerRecord,
            today: LocalDate,
            paceOverride: Double? = null,
            minPaceFactor: Double = record.tracker.minPaceFactor,
            extra: Entry? = null,
        ): TrackerSnapshot {
            val input = record.forecastInput(today, paceOverride, minPaceFactor, extra)
            return TrackerSnapshot(record, input, ForecastEngine.forecast(input))
        }
    }
}
