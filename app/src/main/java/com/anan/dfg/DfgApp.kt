package com.anan.dfg

import android.app.Application
import com.anan.dfg.schedule.Notifier
import com.anan.dfg.schedule.ReminderEvaluator
import com.anan.dfg.schedule.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DfgApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        // Catch up on launch, in case the system dropped the alarm.
        scope.launch {
            ReminderEvaluator.evaluate(this@DfgApp)
            ReminderScheduler.reschedule(this@DfgApp)
        }
    }
}
