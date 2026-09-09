package com.anan.dfg.schedule

import com.anan.dfg.data.CheckWindow
import com.anan.dfg.data.ReminderRule
import com.anan.dfg.data.Repository

/** Live state of one item, shown on the list and detail screens. */
data class ItemStatus(
    val hasEnabledRule: Boolean,
    /** The window currently running, if any. */
    val activeWindow: CheckWindow? = null,
    /** Checks already logged inside [activeWindow]. */
    val activeEventTimes: List<Long> = emptyList(),
    /** The window whose deadline comes next. */
    val nextWindow: CheckWindow? = null,
    val lastEventAt: Long? = null,
) {
    val activeSatisfied: Boolean get() = activeEventTimes.isNotEmpty()

    /** The window worth showing: the running one, otherwise the upcoming one. */
    val focusWindow: CheckWindow? get() = activeWindow ?: nextWindow
}

object StatusComputer {

    suspend fun compute(
        repo: Repository,
        itemId: Long,
        rules: List<ReminderRule>,
        now: Long = System.currentTimeMillis(),
    ): ItemStatus {
        val enabled = rules.filter { it.enabled }
        val lastEventAt = repo.lastEventAt(itemId)
        if (enabled.isEmpty()) {
            return ItemStatus(hasEnabledRule = false, lastEventAt = lastEventAt)
        }
        // When several rules overlap, the one closing soonest is the one that matters.
        val active = enabled
            .mapNotNull { RecurrenceEngine.windowContaining(it, now) }
            .minByOrNull { it.endMillis }
        val next = enabled
            .mapNotNull { RecurrenceEngine.nextWindowEndingAfter(it, now) }
            .minByOrNull { it.endMillis }
        return ItemStatus(
            hasEnabledRule = true,
            activeWindow = active,
            activeEventTimes = active?.let { repo.eventTimesInWindow(itemId, it) } ?: emptyList(),
            nextWindow = next,
            lastEventAt = lastEventAt,
        )
    }
}
