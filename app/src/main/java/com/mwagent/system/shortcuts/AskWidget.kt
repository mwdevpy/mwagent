package com.mwagent.system.shortcuts

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.mwagent.MainActivity
import com.mwagent.R

class AskWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.widget_ask).apply {
            setOnClickPendingIntent(R.id.widget_ask, open(context, listen = false))
            setOnClickPendingIntent(R.id.widget_listen, open(context, listen = true))
        }
        manager.updateAppWidget(ids, views)
    }

    private fun open(context: Context, listen: Boolean): PendingIntent = PendingIntent.getActivity(
        context,
        if (listen) LISTEN_REQUEST else ASK_REQUEST,
        MainActivity.newChatIntent(context, listen),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private companion object {
        const val ASK_REQUEST = 10
        const val LISTEN_REQUEST = 11
    }
}
