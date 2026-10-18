package io.github.abhik9.caffeinedose.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle
import io.github.abhik9.caffeinedose.awakeTimer

/** Receives the updates the system requests for the home screen widgets, e.g. once added, see [AwakeWidget]. */
class AwakeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        AwakeWidget.update(context, manager, context.awakeTimer().current()) { ids }

    /** Before Android 12, a resized widget needs the layout of its new size. */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) =
        AwakeWidget.update(context, manager, context.awakeTimer().current()) { intArrayOf(id) }
}
