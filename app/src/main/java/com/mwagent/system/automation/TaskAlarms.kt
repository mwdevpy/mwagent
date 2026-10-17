package com.mwagent.system.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri

class TaskAlarms(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun schedule(id: String, at: Long) {
        val trigger = pending(id)
        if (canScheduleExact) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, trigger)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, trigger)
        }
    }

    fun cancel(id: String) = alarms.cancel(pending(id))

    private fun pending(id: String): PendingIntent {
        val intent = Intent(context, AutomationReceiver::class.java)
            .setAction(AutomationReceiver.ACTION_RUN_TASK)
            .setData("$URI_PREFIX$id".toUri())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val URI_PREFIX = "mwagent://task/"

        fun taskId(intent: Intent): String? = intent.dataString?.removePrefix(URI_PREFIX)?.takeIf { it.isNotEmpty() }
    }
}
