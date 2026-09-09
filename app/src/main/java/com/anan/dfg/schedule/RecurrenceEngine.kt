package com.anan.dfg.schedule

import com.anan.dfg.data.CheckWindow
import com.anan.dfg.data.IntervalUnit
import com.anan.dfg.data.RecurrenceType
import com.anan.dfg.data.ReminderRule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import kotlin.math.min

/**
 * Expands a reminder rule into the sequence of check windows it implies.
 *
 * A window's end is the reminder deadline: if nothing was logged inside the window,
 * the reminder fires when it closes. An entry outside the window never satisfies it,
 * even when it is on the same day and only minutes away.
 */
object RecurrenceEngine {

    /** Extra days scanned backwards so windows crossing midnight are still found. */
    private const val LOOKBACK_DAYS = 2L

    /** How far ahead the search for the next window will look. */
    private const val MAX_FORWARD_DAYS = 800L

    /** Search cap for INTERVAL rules, so a very old anchor cannot spin forever. */
    private const val MAX_INTERVAL_STEPS = 20_000

    private fun zone(): ZoneId = ZoneId.systemDefault()

    private fun LocalDateTime.toMillis(): Long = atZone(zone()).toInstant().toEpochMilli()

    private fun Long.toLocalDateTime(): LocalDateTime =
        Instant.ofEpochMilli(this).atZone(zone()).toLocalDateTime()

    /** Window length in minutes. An end at or before the start crosses midnight. */
    fun windowMinutes(rule: ReminderRule): Long {
        val raw = rule.windowEndMinute - rule.windowStartMinute
        return if (raw > 0) raw.toLong() else raw + 24L * 60
    }

    /** Whether [date] matches a date-based rule. */
    fun matchesDate(rule: ReminderRule, date: LocalDate): Boolean = when (rule.recurrence) {
        RecurrenceType.ONCE -> parseOnceDate(rule) == date
        RecurrenceType.WEEKLY -> date.dayOfWeek.value in rule.daysOfWeek
        // A day past the end of a short month lands on that month's last day.
        RecurrenceType.MONTHLY ->
            rule.daysOfMonth.any { min(it, date.lengthOfMonth()) == date.dayOfMonth }
        RecurrenceType.INTERVAL -> false
    }

    private fun parseOnceDate(rule: ReminderRule): LocalDate? =
        rule.onceDate?.let {
            try {
                LocalDate.parse(it)
            } catch (e: DateTimeParseException) {
                null
            }
        }

    /** The window that starts on [date] for a date-based rule. */
    private fun windowStartingOn(rule: ReminderRule, date: LocalDate): CheckWindow {
        val start = date.atStartOfDay().plusMinutes(rule.windowStartMinute.toLong())
        val end = start.plusMinutes(windowMinutes(rule))
        return CheckWindow(start.toMillis(), end.toMillis())
    }

    private fun chronoUnit(unit: IntervalUnit): ChronoUnit = when (unit) {
        IntervalUnit.MINUTE -> ChronoUnit.MINUTES
        IntervalUnit.HOUR -> ChronoUnit.HOURS
        IntervalUnit.DAY -> ChronoUnit.DAYS
        IntervalUnit.WEEK -> ChronoUnit.WEEKS
        IntervalUnit.MONTH -> ChronoUnit.MONTHS
    }

    /** Rough period length used only to guess an index; real boundaries use the calendar. */
    private fun approxPeriodMillis(rule: ReminderRule): Long {
        val unit = when (rule.intervalUnit) {
            IntervalUnit.MINUTE -> 60_000L
            IntervalUnit.HOUR -> 3_600_000L
            IntervalUnit.DAY -> 86_400_000L
            IntervalUnit.WEEK -> 604_800_000L
            IntervalUnit.MONTH -> 2_592_000_000L
        }
        return unit * rule.intervalCount.coerceAtLeast(1)
    }

    /** The [k]th period boundary of an INTERVAL rule; k = 0 is the anchor. */
    private fun intervalBoundary(rule: ReminderRule, k: Long): Long {
        val step = k * rule.intervalCount.coerceAtLeast(1)
        return rule.anchorMillis.toLocalDateTime()
            .plus(step, chronoUnit(rule.intervalUnit))
            .toMillis()
    }

    /** Index of the first boundary after [after]; never below 1. */
    private fun firstIntervalIndexAfter(rule: ReminderRule, after: Long): Long {
        val estimate = (after - rule.anchorMillis) / approxPeriodMillis(rule) - 2
        var k = estimate.coerceAtLeast(1L)
        // Walk back if the guess overshot, then forward to the first boundary past it.
        var guard = 0
        while (k > 1 && intervalBoundary(rule, k - 1) > after && guard++ < MAX_INTERVAL_STEPS) {
            k--
        }
        guard = 0
        while (intervalBoundary(rule, k) <= after && guard++ < MAX_INTERVAL_STEPS) {
            k++
        }
        return k
    }

    /**
     * Every window whose end falls in ([afterExclusive], [untilInclusive]], in order.
     */
    fun windowsEndingIn(
        rule: ReminderRule,
        afterExclusive: Long,
        untilInclusive: Long,
    ): List<CheckWindow> {
        if (untilInclusive <= afterExclusive) return emptyList()
        if (rule.recurrence == RecurrenceType.INTERVAL) {
            val result = mutableListOf<CheckWindow>()
            var k = firstIntervalIndexAfter(rule, afterExclusive)
            var end = intervalBoundary(rule, k)
            var guard = 0
            while (end <= untilInclusive && guard++ < MAX_INTERVAL_STEPS) {
                result += CheckWindow(intervalBoundary(rule, k - 1), end)
                k++
                end = intervalBoundary(rule, k)
            }
            return result
        }
        val firstDate = afterExclusive.toLocalDateTime().toLocalDate().minusDays(LOOKBACK_DAYS)
        val lastDate = untilInclusive.toLocalDateTime().toLocalDate()
        val result = mutableListOf<CheckWindow>()
        var date = firstDate
        while (!date.isAfter(lastDate)) {
            if (matchesDate(rule, date)) {
                val window = windowStartingOn(rule, date)
                if (window.endMillis > afterExclusive && window.endMillis <= untilInclusive) {
                    result += window
                }
            }
            date = date.plusDays(1)
        }
        return result.sortedBy { it.endMillis }
    }

    /** The next window ending after [after], or null (a spent one-off rule, say). */
    fun nextWindowEndingAfter(rule: ReminderRule, after: Long): CheckWindow? {
        if (rule.recurrence == RecurrenceType.INTERVAL) {
            val k = firstIntervalIndexAfter(rule, after)
            return CheckWindow(intervalBoundary(rule, k - 1), intervalBoundary(rule, k))
        }
        var date = after.toLocalDateTime().toLocalDate().minusDays(LOOKBACK_DAYS)
        val limit = date.plusDays(MAX_FORWARD_DAYS)
        while (!date.isAfter(limit)) {
            if (matchesDate(rule, date)) {
                val window = windowStartingOn(rule, date)
                if (window.endMillis > after) return window
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** The window containing [at], or null when [at] falls outside every window. */
    fun windowContaining(rule: ReminderRule, at: Long): CheckWindow? {
        if (rule.recurrence == RecurrenceType.INTERVAL) {
            val k = firstIntervalIndexAfter(rule, at)
            val window = CheckWindow(intervalBoundary(rule, k - 1), intervalBoundary(rule, k))
            return window.takeIf { at in it }
        }
        var date = at.toLocalDateTime().toLocalDate().minusDays(LOOKBACK_DAYS)
        val limit = at.toLocalDateTime().toLocalDate()
        while (!date.isAfter(limit)) {
            if (matchesDate(rule, date)) {
                val window = windowStartingOn(rule, date)
                if (at in window) return window
            }
            date = date.plusDays(1)
        }
        return null
    }
}
