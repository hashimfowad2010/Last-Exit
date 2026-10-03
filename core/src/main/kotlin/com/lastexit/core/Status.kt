package com.lastexit.core

/**
 * How close a tracked limit is to its point of no return, ordered from calm to critical.
 * [severity] lets callers ask "did this get worse?" without relying on enum ordinal order.
 */
enum class Status(val severity: Int, val label: String) {
    /** Projected total stays under the limit at the current pace. */
    SAFE(0, "Safe"),

    /** Projected over the limit, but the last exit is still comfortably ahead. */
    ACT_SOON(1, "Act soon"),

    /** The last exit is inside the red window (3 days, or 15% of the remaining time). */
    LAST_EXIT(2, "Last exit"),

    /** Even the most realistic cut-back can no longer keep the total under the limit. */
    PAST_THE_LINE(3, "Past the line");

    fun isWorseThan(other: Status): Boolean = severity > other.severity

    companion object {
        /** Lenient parser for persisted values; unknown or missing values fall back to [SAFE]. */
        fun fromName(name: String?): Status = entries.firstOrNull { it.name == name } ?: SAFE
    }
}
