package io.github.abhik9.caffeinedose.awake

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.abhik9.caffeinedose.EXTRA_DEADLINE
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.system.reportBlocked

/**
 * Handles the notification actions. Not exported: only reachable through the app's own [PendingIntent]s.
 */
class DoseActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_EXTEND = "io.github.abhik9.caffeinedose.dose.EXTEND"
        const val ACTION_REDUCE = "io.github.abhik9.caffeinedose.dose.REDUCE"
        const val ACTION_STOP = "io.github.abhik9.caffeinedose.dose.STOP"

        /** The notification has been dismissed by the user, see [dismissIntent]. */
        private const val ACTION_DISMISSED = "io.github.abhik9.caffeinedose.dose.DISMISSED"

        private fun intent(context: Context, action: String) = Intent(context, DoseActionReceiver::class.java).setAction(action)

        fun pendingIntent(context: Context, action: String): PendingIntent =
            PendingIntent.getBroadcast(context, 0, intent(context, action), PendingIntent.FLAG_IMMUTABLE)

        /**
         * There is only ever one such [PendingIntent]: [PendingIntent.FLAG_UPDATE_CURRENT] updates the deadline of the
         * instance already referenced by the posted notification.
         */
        fun dismissIntent(context: Context, deadline: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent(context, ACTION_DISMISSED).putExtra(EXTRA_DEADLINE, deadline),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        val timer = context.awakeTimer()
        when (intent.action) {
            ACTION_EXTEND -> context.reportBlocked(timer.extend())
            ACTION_REDUCE -> context.reportBlocked(timer.reduce())
            ACTION_STOP -> timer.stop()
            // Only ends the timer the dismissed notification was showing, never a newer one.
            ACTION_DISMISSED -> timer.end(intent.getLongExtra(EXTRA_DEADLINE, 0L))
        }
    }
}
