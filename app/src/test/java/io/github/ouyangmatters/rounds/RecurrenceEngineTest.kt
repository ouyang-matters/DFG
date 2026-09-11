package io.github.ouyangmatters.rounds

import io.github.ouyangmatters.rounds.data.IntervalUnit
import io.github.ouyangmatters.rounds.data.RecurrenceType
import io.github.ouyangmatters.rounds.data.ReminderRule
import io.github.ouyangmatters.rounds.schedule.RecurrenceEngine
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecurrenceEngineTest {

    private val zone = ZoneId.of("Asia/Taipei")

    @Before
    fun fixTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Taipei"))
    }

    private fun millis(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun twiceWeeklyMorningRule() = ReminderRule(
        id = 1,
        itemId = 1,
        recurrence = RecurrenceType.WEEKLY,
        // Wednesday and Friday.
        daysOfWeek = setOf(3, 5),
        windowStartMinute = 6 * 60,
        windowEndMinute = 9 * 60,
    )

    // 2026-09-09 is a Wednesday and 2026-09-11 a Friday.
    @Test
    fun `weekly rule window covers only the configured hours`() {
        val rule = twiceWeeklyMorningRule()
        val window = RecurrenceEngine.windowContaining(rule, millis("2026-09-09T07:30"))
        requireNotNull(window)
        assertEquals(millis("2026-09-09T06:00"), window.startMillis)
        assertEquals(millis("2026-09-09T09:00"), window.endMillis)
    }

    /** An extra check at 05:00 must not satisfy a window that runs 06:00-09:00. */
    @Test
    fun `event before the window is outside it`() {
        val rule = twiceWeeklyMorningRule()
        val window = RecurrenceEngine.windowContaining(rule, millis("2026-09-09T07:30"))!!
        assertFalse(millis("2026-09-09T05:00") in window)
        assertTrue(millis("2026-09-09T06:00") in window)
        assertTrue(millis("2026-09-09T08:59") in window)
        // The end instant is excluded: that moment is the deadline itself.
        assertFalse(millis("2026-09-09T09:00") in window)
    }

    @Test
    fun `no window outside the configured hours or days`() {
        val rule = twiceWeeklyMorningRule()
        assertNull(RecurrenceEngine.windowContaining(rule, millis("2026-09-09T05:00")))
        assertNull(RecurrenceEngine.windowContaining(rule, millis("2026-09-09T09:30")))
        // Thursday is not part of the rule.
        assertNull(RecurrenceEngine.windowContaining(rule, millis("2026-09-10T07:00")))
    }

    @Test
    fun `next deadline skips to the next configured weekday`() {
        val rule = twiceWeeklyMorningRule()
        // From Tuesday, the next deadline is Wednesday 09:00.
        assertEquals(
            millis("2026-09-09T09:00"),
            RecurrenceEngine.nextWindowEndingAfter(rule, millis("2026-09-08T22:00"))!!.endMillis,
        )
        // Just past Wednesday 09:00, the next one is Friday 09:00.
        assertEquals(
            millis("2026-09-11T09:00"),
            RecurrenceEngine.nextWindowEndingAfter(rule, millis("2026-09-09T09:00"))!!.endMillis,
        )
    }

    @Test
    fun `windows ending in range lists every missed deadline`() {
        val rule = twiceWeeklyMorningRule()
        val windows = RecurrenceEngine.windowsEndingIn(
            rule,
            afterExclusive = millis("2026-09-07T00:00"),
            untilInclusive = millis("2026-09-19T00:00"),
        )
        assertEquals(
            listOf(
                millis("2026-09-09T09:00"),
                millis("2026-09-11T09:00"),
                millis("2026-09-16T09:00"),
                millis("2026-09-18T09:00"),
            ),
            windows.map { it.endMillis },
        )
    }

    @Test
    fun `window crossing midnight extends into the next day`() {
        val rule = twiceWeeklyMorningRule().copy(
            windowStartMinute = 22 * 60,
            windowEndMinute = 2 * 60,
        )
        val window = RecurrenceEngine.windowContaining(rule, millis("2026-09-10T01:00"))
        requireNotNull(window)
        // Starts Wednesday 22:00 and ends Thursday 02:00.
        assertEquals(millis("2026-09-09T22:00"), window.startMillis)
        assertEquals(millis("2026-09-10T02:00"), window.endMillis)
    }

    @Test
    fun `equal start and end means a full day window`() {
        val rule = twiceWeeklyMorningRule().copy(windowStartMinute = 0, windowEndMinute = 0)
        val window = RecurrenceEngine.windowContaining(rule, millis("2026-09-09T13:00"))!!
        assertEquals(millis("2026-09-09T00:00"), window.startMillis)
        assertEquals(millis("2026-09-10T00:00"), window.endMillis)
    }

    @Test
    fun `once rule fires only on its date`() {
        val rule = ReminderRule(
            id = 2,
            itemId = 1,
            recurrence = RecurrenceType.ONCE,
            onceDate = "2026-09-20",
            windowStartMinute = 18 * 60,
            windowEndMinute = 20 * 60,
        )
        assertEquals(
            millis("2026-09-20T20:00"),
            RecurrenceEngine.nextWindowEndingAfter(rule, millis("2026-09-08T00:00"))!!.endMillis,
        )
        // Nothing left once the date has passed.
        assertNull(RecurrenceEngine.nextWindowEndingAfter(rule, millis("2026-09-21T00:00")))
    }

    @Test
    fun `monthly day 31 falls back to the last day of a short month`() {
        val rule = ReminderRule(
            id = 3,
            itemId = 1,
            recurrence = RecurrenceType.MONTHLY,
            daysOfMonth = setOf(31),
            windowStartMinute = 8 * 60,
            windowEndMinute = 10 * 60,
        )
        assertTrue(RecurrenceEngine.matchesDate(rule, LocalDate.of(2026, 1, 31)))
        // February 2026 has 28 days.
        assertTrue(RecurrenceEngine.matchesDate(rule, LocalDate.of(2026, 2, 28)))
        assertFalse(RecurrenceEngine.matchesDate(rule, LocalDate.of(2026, 2, 27)))
        // 2028 is a leap year.
        assertTrue(RecurrenceEngine.matchesDate(rule, LocalDate.of(2028, 2, 29)))
        assertFalse(RecurrenceEngine.matchesDate(rule, LocalDate.of(2028, 2, 28)))
    }

    @Test
    fun `interval rule uses the whole period as the window`() {
        val rule = ReminderRule(
            id = 4,
            itemId = 1,
            recurrence = RecurrenceType.INTERVAL,
            intervalCount = 6,
            intervalUnit = IntervalUnit.HOUR,
            anchorMillis = millis("2026-09-08T00:00"),
        )
        val window = RecurrenceEngine.windowContaining(rule, millis("2026-09-08T14:00"))
        requireNotNull(window)
        assertEquals(millis("2026-09-08T12:00"), window.startMillis)
        assertEquals(millis("2026-09-08T18:00"), window.endMillis)

        assertEquals(
            millis("2026-09-08T18:00"),
            RecurrenceEngine.nextWindowEndingAfter(rule, millis("2026-09-08T14:00"))!!.endMillis,
        )
    }

    @Test
    fun `interval rule works when the anchor is far in the past`() {
        val rule = ReminderRule(
            id = 5,
            itemId = 1,
            recurrence = RecurrenceType.INTERVAL,
            intervalCount = 1,
            intervalUnit = IntervalUnit.DAY,
            anchorMillis = millis("2020-01-01T07:00"),
        )
        val window = RecurrenceEngine.windowContaining(rule, millis("2026-09-08T09:00"))
        requireNotNull(window)
        assertEquals(millis("2026-09-08T07:00"), window.startMillis)
        assertEquals(millis("2026-09-09T07:00"), window.endMillis)
    }

    @Test
    fun `monthly interval follows the calendar not a fixed 30 days`() {
        val rule = ReminderRule(
            id = 6,
            itemId = 1,
            recurrence = RecurrenceType.INTERVAL,
            intervalCount = 1,
            intervalUnit = IntervalUnit.MONTH,
            anchorMillis = millis("2026-01-31T09:00"),
        )
        val deadlines = RecurrenceEngine.windowsEndingIn(
            rule,
            afterExclusive = millis("2026-01-31T09:00"),
            untilInclusive = millis("2026-04-30T23:59"),
        ).map { it.endMillis }
        // plusMonths clamps 31 Jan to 28 Feb, then 31 Mar and 30 Apr follow.
        assertEquals(
            listOf(
                millis("2026-02-28T09:00"),
                millis("2026-03-31T09:00"),
                millis("2026-04-30T09:00"),
            ),
            deadlines,
        )
    }

    @Test
    fun `interval windows are contiguous so nothing falls between periods`() {
        val rule = ReminderRule(
            id = 7,
            itemId = 1,
            recurrence = RecurrenceType.INTERVAL,
            intervalCount = 90,
            intervalUnit = IntervalUnit.MINUTE,
            anchorMillis = millis("2026-09-08T00:00"),
        )
        val windows = RecurrenceEngine.windowsEndingIn(
            rule,
            afterExclusive = millis("2026-09-08T00:00"),
            untilInclusive = millis("2026-09-08T06:00"),
        )
        assertEquals(4, windows.size)
        windows.zipWithNext().forEach { (a, b) ->
            assertEquals(a.endMillis, b.startMillis)
        }
    }

    @Test
    fun `disabled days produce no windows at all`() {
        val rule = twiceWeeklyMorningRule().copy(daysOfWeek = emptySet())
        assertNull(RecurrenceEngine.nextWindowEndingAfter(rule, millis("2026-09-08T00:00")))
        assertTrue(
            RecurrenceEngine.windowsEndingIn(
                rule,
                millis("2026-09-01T00:00"),
                millis("2026-12-01T00:00"),
            ).isEmpty(),
        )
    }
}
