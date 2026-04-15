package com.trackit.expense.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * BroadcastReceiver entry point for [TrackItWidget].
 *
 * Extends [GlanceAppWidgetReceiver] (which in turn extends [AppWidgetProvider])
 * so the system can call [onUpdate], [onDeleted], etc. automatically.
 * Glance's base class delegates those lifecycle events to [TrackItWidget.provideGlance].
 *
 * Registered in AndroidManifest.xml with:
 * - ACTION_APPWIDGET_UPDATE intent filter
 * - android:resource="@xml/trackit_widget_info" metadata
 */
class TrackItWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = TrackItWidget()
}
