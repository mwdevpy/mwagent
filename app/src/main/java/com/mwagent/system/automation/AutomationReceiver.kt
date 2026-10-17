package com.mwagent.system.automation

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mwagent.App
import kotlinx.coroutines.launch

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val automations = (context.applicationContext as App).container.automations
        val work: suspend () -> Unit = when (intent.action) {
            ACTION_RUN_TASK -> TaskAlarms.taskId(intent)?.let { id -> { automations.fire(id) } } ?: return
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> automations::restore
            else -> return
        }
        val pending = goAsync()
        automations.scope.launch {
            try {
                work()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_RUN_TASK = "com.mwagent.action.RUN_TASK"
    }
}
