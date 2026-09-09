package com.anan.dfg.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** How a check was logged. */
enum class CheckKind { PHOTO, BUTTON }

/** How a reminder rule repeats. */
enum class RecurrenceType {
    /** A single named date. */
    ONCE,

    /** Chosen days of the week. */
    WEEKLY,

    /** Chosen days of the month. */
    MONTHLY,

    /** Every N units of time. */
    INTERVAL,
}

enum class IntervalUnit { MINUTE, HOUR, DAY, WEEK, MONTH }

/** One recurring thing to check. */
@Entity(tableName = "items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    /** Whether a check can be logged with a photo. */
    val allowPhoto: Boolean = true,
    /** Whether a check can be logged with a tap plus an optional note. */
    val allowButton: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val archived: Boolean = false,
)

/**
 * One logged check. Entries survive even if the item later stops allowing that
 * way of logging.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId"), Index("timestamp")],
)
data class CheckEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val kind: CheckKind,
    /** When the check happened, in epoch millis. */
    val timestamp: Long,
    val note: String? = null,
    /** Path relative to filesDir; only set for PHOTO entries. */
    val photoPath: String? = null,
)

/**
 * A reminder rule: every check window needs at least one entry, otherwise a
 * reminder fires when the window closes.
 *
 * The window runs from [windowStartMinute] to [windowEndMinute], both counted in
 * minutes from midnight. An end at or before the start means the window crosses
 * midnight into the next day. [RecurrenceType.INTERVAL] ignores these: the period
 * itself is the window.
 */
@Entity(
    tableName = "rules",
    foreignKeys = [
        ForeignKey(
            entity = ChecklistItem::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class ReminderRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: Long,
    val enabled: Boolean = true,
    val recurrence: RecurrenceType,
    /** ONCE: an ISO-8601 date. */
    val onceDate: String? = null,
    /** WEEKLY: ISO day numbers, 1 (Monday) through 7 (Sunday). */
    val daysOfWeek: Set<Int> = emptySet(),
    /** MONTHLY: 1 through 31; a day past the end of a month lands on its last day. */
    val daysOfMonth: Set<Int> = emptySet(),
    val intervalCount: Int = 1,
    val intervalUnit: IntervalUnit = IntervalUnit.DAY,
    /** Where INTERVAL periods are counted from. */
    val anchorMillis: Long = System.currentTimeMillis(),
    val windowStartMinute: Int = 6 * 60,
    val windowEndMinute: Int = 9 * 60,
    /** Last deadline already evaluated, so a reminder is never sent twice. */
    @ColumnInfo(defaultValue = "0") val lastEvaluatedDeadline: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

/** A span needing at least one check; [endMillis] is also the reminder deadline. */
data class CheckWindow(val startMillis: Long, val endMillis: Long) {
    operator fun contains(millis: Long): Boolean = millis >= startMillis && millis < endMillis
}
