package com.mwagent.system.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.mwagent.data.automation.WatchEvent
import com.mwagent.data.db.WatchSource

data class PhoneNotification(
    val key: String,
    val pkg: String,
    val app: String,
    val title: String,
    val text: String,
    val postedAt: Long,
) {
    fun toEvent(): WatchEvent = WatchEvent(
        source = WatchSource.NOTIFICATIONS,
        origin = app,
        names = listOf(app, pkg),
        text = listOf(title, text).filter { it.isNotBlank() }.joinToString(": "),
    )
}

class NotificationLog(private val capacity: Int = CAPACITY) {
    private val items = ArrayDeque<PhoneNotification>()

    @Volatile
    var connected = false

    @Volatile
    var onPosted: (PhoneNotification) -> Unit = {}

    fun add(item: PhoneNotification, fresh: Boolean) {
        val added = synchronized(items) {
            if (items.any { it.key == item.key && it.title == item.title && it.text == item.text }) return
            items.addLast(item)
            while (items.size > capacity) items.removeFirst()
            true
        }
        if (added && fresh) onPosted(item)
    }

    fun recent(): List<PhoneNotification> = synchronized(items) { items.sortedByDescending { it.postedAt } }

    companion object {
        private const val CAPACITY = 400

        fun isEnabled(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
