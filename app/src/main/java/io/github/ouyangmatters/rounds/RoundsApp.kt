package io.github.ouyangmatters.rounds

import android.app.Application
import io.github.ouyangmatters.rounds.schedule.Notifier
import io.github.ouyangmatters.rounds.schedule.ReminderEvaluator
import io.github.ouyangmatters.rounds.schedule.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoundsApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        // Catch up on launch, in case the system dropped the alarm.
        scope.launch {
            ReminderEvaluator.evaluate(this@RoundsApp)
            ReminderScheduler.reschedule(this@RoundsApp)
        }
    }
}
