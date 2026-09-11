package io.github.ouyangmatters.rounds.util

import android.content.res.Resources
import io.github.ouyangmatters.rounds.R
import io.github.ouyangmatters.rounds.data.CheckWindow
import io.github.ouyangmatters.rounds.data.IntervalUnit
import io.github.ouyangmatters.rounds.data.RecurrenceType
import io.github.ouyangmatters.rounds.data.ReminderRule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Shared time formatting for the UI and notifications.
 *
 * Day and month names come from java.time, so they follow the active locale
 * without a translated list of their own.
 */
object Format {

    private const val SEPARATOR = " · "

    private fun localeOf(res: Resources): Locale =
        res.configuration.locales.takeIf { !it.isEmpty }?.get(0) ?: Locale.getDefault()

    private fun local(millis: Long): LocalDateTime =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()

    /** Single letter for the day picker. */
    fun weekdayNarrow(res: Resources, isoDay: Int): String =
        DayOfWeek.of(isoDay).getDisplayName(TextStyle.NARROW, localeOf(res))

    fun weekdayShort(res: Resources, isoDay: Int): String =
        DayOfWeek.of(isoDay).getDisplayName(TextStyle.SHORT, localeOf(res))

    /** Minutes from midnight as 24-hour clock time. */
    fun minuteOfDay(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return "%02d:%02d".format(m / 60, m % 60)
    }

    fun time(millis: Long): String = local(millis).format(DateTimeFormatter.ofPattern("HH:mm"))

    fun dateTime(res: Resources, millis: Long): String {
        val date = local(millis).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(localeOf(res)))
        return date + " " + time(millis)
    }

    /** Heading that groups the timeline by day. */
    fun dayHeading(res: Resources, date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> res.getString(R.string.day_today)
            today.minusDays(1) -> res.getString(R.string.day_yesterday)
            else -> date.format(
                DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(localeOf(res)),
            )
        }
    }

    /** A window as a compact range, for example "Wed 06:00-09:00". */
    fun window(res: Resources, window: CheckWindow): String {
        val start = local(window.startMillis)
        val end = local(window.endMillis)
        val head = weekdayShort(res, start.dayOfWeek.value) + " " + time(window.startMillis)
        return if (start.toLocalDate() == end.toLocalDate()) {
            head + "-" + time(window.endMillis)
        } else {
            head + " - " + weekdayShort(res, end.dayOfWeek.value) + " " + time(window.endMillis)
        }
    }

    /**
     * Start and end labels for a window track. A window crossing midnight can show
     * the same clock time at both ends, so the day is added when that happens.
     */
    fun windowEndpoints(res: Resources, window: CheckWindow): Pair<String, String> {
        val start = local(window.startMillis)
        val end = local(window.endMillis)
        return if (start.toLocalDate() == end.toLocalDate()) {
            time(window.startMillis) to time(window.endMillis)
        } else {
            time(window.startMillis) to
                (weekdayShort(res, end.dayOfWeek.value) + " " + time(window.endMillis))
        }
    }

    /** Just the unit, used by the interval dropdown. */
    fun unitName(res: Resources, unit: IntervalUnit, count: Int): String =
        res.getQuantityString(
            when (unit) {
                IntervalUnit.MINUTE -> R.plurals.unit_minutes
                IntervalUnit.HOUR -> R.plurals.unit_hours
                IntervalUnit.DAY -> R.plurals.unit_days
                IntervalUnit.WEEK -> R.plurals.unit_weeks
                IntervalUnit.MONTH -> R.plurals.unit_months
            },
            count,
        )

    /** The whole phrase, which some languages inflect differently from the bare unit. */
    private fun everyPhrase(res: Resources, unit: IntervalUnit, count: Int): String {
        // "Every 1 day" is wrong in every language here, so a count of one has its own wording.
        if (count == 1) {
            return res.getString(
                when (unit) {
                    IntervalUnit.MINUTE -> R.string.every_1_minute
                    IntervalUnit.HOUR -> R.string.every_1_hour
                    IntervalUnit.DAY -> R.string.every_1_day
                    IntervalUnit.WEEK -> R.string.every_1_week
                    IntervalUnit.MONTH -> R.string.every_1_month
                },
            )
        }
        return res.getQuantityString(
            when (unit) {
                IntervalUnit.MINUTE -> R.plurals.every_minutes
                IntervalUnit.HOUR -> R.plurals.every_hours
                IntervalUnit.DAY -> R.plurals.every_days
                IntervalUnit.WEEK -> R.plurals.every_weeks
                IntervalUnit.MONTH -> R.plurals.every_months
            },
            count,
            count,
        )
    }

    fun recurrenceName(res: Resources, type: RecurrenceType): String = res.getString(
        when (type) {
            RecurrenceType.ONCE -> R.string.recur_once
            RecurrenceType.WEEKLY -> R.string.recur_weekly
            RecurrenceType.MONTHLY -> R.string.recur_monthly
            RecurrenceType.INTERVAL -> R.string.recur_interval
        },
    )

    /** One-line description of a rule. */
    fun rule(res: Resources, rule: ReminderRule): String {
        val span = minuteOfDay(rule.windowStartMinute) + "-" + minuteOfDay(rule.windowEndMinute)
        return when (rule.recurrence) {
            RecurrenceType.ONCE ->
                (rule.onceDate ?: res.getString(R.string.rule_no_date)) + SEPARATOR + span
            RecurrenceType.WEEKLY -> {
                val days = rule.daysOfWeek.sorted().joinToString(", ") { weekdayShort(res, it) }
                if (days.isEmpty()) res.getString(R.string.rule_no_days) else days + SEPARATOR + span
            }
            RecurrenceType.MONTHLY -> {
                val days = rule.daysOfMonth.sorted().joinToString(", ")
                if (days.isEmpty()) res.getString(R.string.rule_no_days) else days + SEPARATOR + span
            }
            RecurrenceType.INTERVAL -> everyPhrase(res, rule.intervalUnit, rule.intervalCount)
        }
    }

    /** Compact distance to a future moment. */
    fun countdown(res: Resources, fromMillis: Long, toMillis: Long): String {
        val totalMinutes = ((toMillis - fromMillis) / 60_000).coerceAtLeast(0)
        val days = (totalMinutes / (60 * 24)).toInt()
        val hours = ((totalMinutes % (60 * 24)) / 60).toInt()
        val minutes = (totalMinutes % 60).toInt()
        return when {
            days > 0 -> res.getString(R.string.countdown_dh, days, hours)
            hours > 0 -> res.getString(R.string.countdown_hm, hours, minutes)
            else -> res.getString(R.string.countdown_m, minutes)
        }
    }

    fun fileSize(res: Resources, bytes: Long): String {
        val locale = localeOf(res)
        return when {
            bytes >= 1024L * 1024 -> "%.1f MB".format(locale, bytes / 1024.0 / 1024.0)
            bytes >= 1024 -> "%.0f KB".format(locale, bytes / 1024.0)
            else -> "%d B".format(locale, bytes)
        }
    }
}
