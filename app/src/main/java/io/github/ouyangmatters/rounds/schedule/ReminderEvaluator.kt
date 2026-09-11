package io.github.ouyangmatters.rounds.schedule

import android.content.Context
import android.util.Log
import io.github.ouyangmatters.rounds.data.Repository

/**
 * Walks the windows that have closed but not yet been judged, and notifies for the
 * ones that ended empty.
 *
 * Progress is tracked by "last deadline evaluated" rather than by which alarm just
 * fired, so a reboot, a spell in Doze, or a killed process only delays the check —
 * the next run catches up on everything that was missed.
 */
object ReminderEvaluator {

    /** Older misses are marked judged without notifying, so a long gap can't spam. */
    private const val STALE_MILLIS = 24L * 60 * 60 * 1000

    /** Cap on notifications one rule may raise in a single run. */
    private const val MAX_NOTIFICATIONS_PER_RULE = 3

    suspend fun evaluate(context: Context, now: Long = System.currentTimeMillis()) {
        val repo = Repository.get(context)
        for (rule in repo.enabledRules()) {
            // Windows from before the rule existed are not backfilled.
            val floor = maxOf(rule.lastEvaluatedDeadline, rule.createdAt)
            val windows = RecurrenceEngine.windowsEndingIn(rule, floor, now)
            if (windows.isEmpty()) continue

            var sent = 0
            for (window in windows) {
                if (now - window.endMillis > STALE_MILLIS) continue
                if (sent >= MAX_NOTIFICATIONS_PER_RULE) break
                if (repo.countEventsInWindow(rule.itemId, window) > 0) continue
                val item = repo.getItem(rule.itemId) ?: continue
                if (item.archived) continue
                Notifier.notifyMissed(context, item, rule, window)
                sent++
            }
            repo.markRuleEvaluated(rule.id, windows.last().endMillis)
        }
        Log.d(TAG, "evaluated at $now")
    }

    private const val TAG = "ReminderEvaluator"
}
