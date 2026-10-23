package io.github.abhik9.caffeinedose.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.shortcut.ShortcutHost
import io.github.abhik9.caffeinedose.shortcut.tapShortcut

/**
 * Taps on the home screen widget, which toggle the timer like the Quick Settings tile, and the refreshes [AwakeWidget]
 * schedules. Not exported: only reachable through the widget's or the alarm's [PendingIntent]. A tap on a widget lets the
 * app start its foreground service from the background.
 */
class WidgetToggleReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_TOGGLE = "io.github.abhik9.caffeinedose.widget.TOGGLE"
        private const val ACTION_REFRESH = "io.github.abhik9.caffeinedose.widget.REFRESH"

        fun pendingIntent(context: Context): PendingIntent = broadcast(context, ACTION_TOGGLE)

        /** Updates every widget with the current timer. */
        fun refreshIntent(context: Context): PendingIntent = broadcast(context, ACTION_REFRESH)

        private fun broadcast(context: Context, action: String) = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetToggleReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        when (intent.action) {
            ACTION_TOGGLE -> {
                app.diagnostics.record { "widget: tap" }
                app.tapShortcut(WidgetHost(app))
            }
            ACTION_REFRESH -> {
                app.diagnostics.record { "widget: refresh" }
                AwakeWidget.updateAll(app)
            }
        }
    }

    /** Home screen widgets are only shown on an unlocked device: there is nothing to wait for. */
    private class WidgetHost(private val context: Context) : ShortcutHost {
        override fun render(timer: Timer?) = AwakeWidget.updateAll(context, timer)

        // Started from a broadcast receiver: there is no task to start it in.
        override fun launch(intent: Intent) = context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
