package com.anan.dfg.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the exact alarm: judges missed windows, then arms the next alarm. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ReminderEvaluator.evaluate(appContext)
                ReminderScheduler.reschedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CHECK = "com.anan.dfg.action.CHECK_DEADLINE"
    }
}

/** Alarms are cleared by a reboot or an app update, so re-arm here. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                Notifier.ensureChannel(appContext)
                ReminderEvaluator.evaluate(appContext)
                ReminderScheduler.reschedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
        )
    }
}

/** Android 12+: re-arm once the user grants the exact-alarm permission. */
class ExactAlarmPermissionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ReminderScheduler.reschedule(appContext)
            } finally {
                pending.finish()
            }
        }
    }
}
