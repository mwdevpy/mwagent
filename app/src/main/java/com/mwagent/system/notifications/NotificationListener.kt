package com.mwagent.system.notifications

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.mwagent.App
import java.util.concurrent.ConcurrentHashMap

class NotificationListener : NotificationListenerService() {
    private val log get() = (application as App).container.automations.notifications
    private val labels = ConcurrentHashMap<String, String>()

    override fun onListenerConnected() {
        log.connected = true
        runCatching { activeNotifications }.getOrNull()?.forEach { sbn -> read(sbn)?.let { log.add(it, fresh = false) } }
    }

    override fun onListenerDisconnected() {
        log.connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        read(sbn)?.let { log.add(it, fresh = true) }
    }

    private fun read(sbn: StatusBarNotification): PhoneNotification? {
        val notification = sbn.notification ?: return null
        if (sbn.packageName == packageName || sbn.isOngoing || notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty().trim()
        if (title.isEmpty() && text.isEmpty()) return null
        return PhoneNotification(sbn.key, sbn.packageName, label(sbn.packageName), title.take(MAX_TITLE), text.take(MAX_TEXT), sbn.postTime)
    }

    private fun label(pkg: String): String = labels.computeIfAbsent(pkg) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg
        }
    }

    private companion object {
        const val MAX_TITLE = 200
        const val MAX_TEXT = 2_000
    }
}
