package com.anan.dfg.util

import com.anan.dfg.data.CheckWindow
import com.anan.dfg.data.IntervalUnit
import com.anan.dfg.data.RecurrenceType
import com.anan.dfg.data.ReminderRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared time formatting for the UI and notifications. */
object Format {

    private val WEEKDAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val WEEKDAY_INITIAL = listOf("M", "T", "W", "T", "F", "S", "S")

    private fun local(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun weekdayInitial(isoDay: Int): String = WEEKDAY_INITIAL[isoDay - 1]

    fun weekdayShort(isoDay: Int): String = WEEKDAY_SHORT[isoDay - 1]

    /** Minutes from midnight rendered as 24-hour clock time. */
    fun minuteOfDay(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun time(millis: Long): String = local(millis).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun dateTime(millis: Long): String =
        local(millis).format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH))

    /** Heading used to group the timeline by day. */
    fun dayHeading(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(
                DateTimeFormatter.ofPattern(
                    if (date.year == today.year) "EEEE, d MMM" else "EEEE, d MMM yyyy",
                    Locale.ENGLISH,
                ),
            )
        }
    }

    fun dayStamp(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)).uppercase(Locale.ENGLISH)

    /** A window as a compact range, e.g. "Wed 06:00-09:00". */
    fun window(window: CheckWindow): String {
        val start = local(window.startMillis)
        val end = local(window.endMillis)
        val day = WEEKDAY_SHORT[start.dayOfWeek.value - 1]
        val head = start.format(DateTimeFormatter.ofPattern("HH:mm"))
        val tail = end.format(DateTimeFormatter.ofPattern("HH:mm"))
        return if (start.toLocalDate() == end.toLocalDate()) {
            "$day $head-$tail"
        } else {
            val endDay = WEEKDAY_SHORT[end.dayOfWeek.value - 1]
            "$day $head - $endDay $tail"
        }
    }

    /**
     * Start and end labels for a window track. When the window crosses midnight the
     * two ends can show the same clock time, so the day is spelled out in that case.
     */
    fun windowEndpoints(window: CheckWindow): Pair<String, String> {
        val start = local(window.startMillis)
        val end = local(window.endMillis)
        return if (start.toLocalDate() == end.toLocalDate()) {
            time(window.startMillis) to time(window.endMillis)
        } else {
            time(window.startMillis) to
                (WEEKDAY_SHORT[end.dayOfWeek.value - 1] + " " + time(window.endMillis))
        }
    }

    fun unitName(unit: IntervalUnit, count: Int): String {
        val singular = when (unit) {
            IntervalUnit.MINUTE -> "minute"
            IntervalUnit.HOUR -> "hour"
            IntervalUnit.DAY -> "day"
            IntervalUnit.WEEK -> "week"
            IntervalUnit.MONTH -> "month"
        }
        return if (count == 1) singular else singular + "s"
    }

    fun recurrenceName(type: RecurrenceType): String = when (type) {
        RecurrenceType.ONCE -> "Once"
        RecurrenceType.WEEKLY -> "Weekly"
        RecurrenceType.MONTHLY -> "Monthly"
        RecurrenceType.INTERVAL -> "Every"
    }

    /** One-line description of a rule, e.g. "Mon, Thu · 06:00-09:00". */
    fun rule(rule: ReminderRule): String {
        val span = minuteOfDay(rule.windowStartMinute) + "-" + minuteOfDay(rule.windowEndMinute)
        return when (rule.recurrence) {
            RecurrenceType.ONCE -> (rule.onceDate ?: "No date") + " · " + span
            RecurrenceType.WEEKLY -> {
                val days = rule.daysOfWeek.sorted().joinToString(", ") { WEEKDAY_SHORT[it - 1] }
                if (days.isEmpty()) "Weekly · no days picked" else "$days · $span"
            }
            RecurrenceType.MONTHLY -> {
                val days = rule.daysOfMonth.sorted().joinToString(", ") { ordinal(it) }
                if (days.isEmpty()) "Monthly · no days picked" else "$days · $span"
            }
            RecurrenceType.INTERVAL ->
                "Every " + rule.intervalCount + " " + unitName(rule.intervalUnit, rule.intervalCount)
        }
    }

    fun ordinal(day: Int): String {
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** Compact relative distance to a future moment, e.g. "in 2h 15m". */
    fun countdown(fromMillis: Long, toMillis: Long): String {
        val totalMinutes = ((toMillis - fromMillis) / 60_000).coerceAtLeast(0)
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }

    fun fileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}
