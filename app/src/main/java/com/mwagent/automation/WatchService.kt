package com.mwagent.automation

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mwagent.App
import com.mwagent.R
import com.mwagent.system.notifications.AppChannel
import com.mwagent.system.notifications.AppNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val automations get() = (application as App).container.automations

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startInForeground()
        scope.launch {
            automations.watching.combine(automations.telegramAccount) { watching, account -> watching to account }.collectLatest { (watching, account) ->
                when {
                    !watching -> {
                        ServiceCompat.stopForeground(this@WatchService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    account != null -> automations.listen(account)
                    else -> automations.unlisten()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        automations.scope.launch { withContext(NonCancellable) { automations.unlisten() } }
        super.onDestroy()
    }

    private fun startInForeground() {
        val notification = NotificationCompat.Builder(this, AppChannel.WATCHING.id)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_watching_title))
            .setContentText(getString(R.string.notification_watching_text))
            .setContentIntent(AppNotifications.openChatIntent(this, chatId = null, OPEN_REQUEST_CODE))
            .setOngoing(true)
            .setSilent(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    companion object {
        private const val TAG = "WatchService"
        private const val NOTIFICATION_ID = 1003
        private const val OPEN_REQUEST_CODE = 3

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Could not start watching", e)
            } catch (e: SecurityException) {
                Log.w(TAG, "Foreground service is not allowed", e)
            }
        }
    }
}
