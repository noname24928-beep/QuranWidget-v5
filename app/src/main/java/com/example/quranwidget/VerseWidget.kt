package com.example.quranwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews

class VerseWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, mgr, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) refreshAll(context, true)
    }

    companion object {
        const val ACTION_REFRESH = "com.example.quranwidget.REFRESH"

        fun refreshAll(context: Context, force: Boolean) {
            if (force) Quran.current(context, true)
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, VerseWidget::class.java))
            ids.forEach { render(context, mgr, it) }
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            val v = Quran.current(context)
            val views = RemoteViews(context.packageName, R.layout.widget_verse)
            views.setTextViewText(R.id.verse, v.display)
            views.setTextViewText(R.id.ref, v.ref)
            if (v.occasion != null) {
                views.setTextViewText(R.id.occasion, v.occasion)
                views.setViewVisibility(R.id.occasion, View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.occasion, View.GONE)
            }
            val i = Intent(context, VerseWidget::class.java).setAction(ACTION_REFRESH)
            val pi = PendingIntent.getBroadcast(
                context, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.root, pi) // اضغط على الودجت لآية جديدة
            mgr.updateAppWidget(id, views)
        }
    }
}
