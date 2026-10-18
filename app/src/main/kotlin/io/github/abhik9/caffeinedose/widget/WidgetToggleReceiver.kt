package io.github.abhik9.caffeinedose.widget

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.shortcut.ShortcutHost
import io.github.abhik9.caffeinedose.shortcut.toggleTimer

/**
 * Taps on the home screen widget, which toggle the timer like the Quick Settings tile. Not exported: only reachable
 * through the widget's [PendingIntent]. A tap on a widget lets the app start its foreground service from the background.
 */
class WidgetToggleReceiver : BroadcastReceiver() {

    companion object {
        private const val ACTION_TOGGLE = "io.github.abhik9.caffeinedose.widget.TOGGLE"

        fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, WidgetToggleReceiver::class.java).setAction(ACTION_TOGGLE),
            PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TOGGLE) return
        val app = context.applicationContext
        app.diagnostics.record { "widget: tap" }
        app.toggleTimer(WidgetHost(app))
    }

    /** Home screen widgets are only shown on an unlocked device: there is nothing to wait for. */
    private class WidgetHost(private val context: Context) : ShortcutHost {
        override fun render(timer: Timer?) = AwakeWidget.updateAll(context, timer)

        // Started from a broadcast receiver: there is no task to start it in.
        override fun launch(intent: Intent) = context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
