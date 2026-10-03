package com.lastexit.core

/** What kind of thing is being pushed toward a limit. Drives wording, units and quick-log chips. */
enum class TrackerType(
    val label: String,
    /** Noun used in sentences such as "you hit your budget on Nov 14". */
    val limitNoun: String,
    val defaultUnit: String,
) {
    MONEY("Money", "budget", "$"),
    TIME("Time", "time budget", "h"),
    RESOURCE("Resource", "allowance", "GB"),
    RISK("Risk", "risk threshold", "pts");

    /** Amounts offered as one-tap chips in the quick-log sheet. */
    val quickAmounts: List<Double>
        get() = when (this) {
            RISK -> listOf(5.0, 10.0, 20.0)
            else -> listOf(1.0, 5.0, 10.0)
        }

    /** Optional labels shown next to the quick amounts (risk is logged as commitment sizes). */
    val quickLabels: List<String>
        get() = when (this) {
            RISK -> listOf("Small", "Medium", "Big")
            else -> listOf("", "", "")
        }

    companion object {
        fun fromName(name: String?): TrackerType = entries.firstOrNull { it.name == name } ?: MONEY
    }
}
