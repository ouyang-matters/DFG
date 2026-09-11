package io.github.ouyangmatters.rounds.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.ouyangmatters.rounds.data.Repository
import java.util.concurrent.TimeUnit

/**
 * Keeps exactly one exact alarm, set for the next deadline across all rules, and
 * computes the following one after it fires. An hourly WorkManager job backs it up
 * in case the system delays or drops the alarm.
 */
object ReminderScheduler {

    private const val REQUEST_CODE = 4321
    private const val SAFETY_NET_WORK = "reminder-safety-net"
    private const val TAG = "ReminderScheduler"

    /** A little slack, so an entry logged right on the deadline is already committed. */
    private const val TRIGGER_SLACK_MILLIS = 3_000L

    /**
     * The app deliberately does not declare SCHEDULE_EXACT_ALARM, so on Android 12
     * and newer this is false and reminders use an inexact alarm, which can arrive
     * a few minutes after the window closes. Below Android 12 exact alarms need no
     * permission, so they are still used there.
     */
    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /** Recomputes and arms the next alarm. Call after any change to the rules. */
    suspend fun reschedule(context: Context) {
        val repo = Repository.get(context)
        val now = System.currentTimeMillis()
        val next = repo.enabledRules()
            .mapNotNull { RecurrenceEngine.nextWindowEndingAfter(it, now)?.endMillis }
            .minOrNull()
        if (next == null) {
            cancel(context)
            Log.d(TAG, "no upcoming deadline, alarm cancelled")
        } else {
            setAlarm(context, next + TRIGGER_SLACK_MILLIS)
            Log.d(TAG, "next deadline at $next")
        }
        ensureSafetyNet(context)
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun setAlarm(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context)
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            } else {
                // Inexact delivery: the system may hold this for a few minutes.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
        }
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
    }

    private fun ensureSafetyNet(context: Context) {
        val request = PeriodicWorkRequestBuilder<SafetyNetWorker>(1, TimeUnit.HOURS)
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SAFETY_NET_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

/** Backstop: re-runs the evaluation and re-arms the alarm. */
class SafetyNetWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        ReminderEvaluator.evaluate(applicationContext)
        ReminderScheduler.reschedule(applicationContext)
        return Result.success()
    }
}
