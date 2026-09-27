package io.github.abhik9.caffeinedose.awake

import android.content.Context
import android.util.Log
import io.github.abhik9.caffeinedose.core.ScreenKeeper
import io.github.abhik9.caffeinedose.core.Timer

/**
 * The running timer. It lives in memory only, like the wake lock held by [AwakeService] in the same process: when the
 * process dies, both are gone, and there is nothing to clean up.
 */
internal object AwakeState {
    @Volatile
    var timer: Timer? = null

    /**
     * `startForegroundService()` calls whose service has not called `startForeground()` yet. Stopping the service in the
     * meantime crashes the app ("did not then call Service.startForeground()"). Main thread only.
     */
    var pendingForegroundStarts = 0
}

/**
 * Holds the screen with [AwakeService], a foreground service.
 */
internal class ServiceScreenKeeper(private val context: Context) : ScreenKeeper {

    private companion object {
        const val TAG = "ServiceScreenKeeper"
    }

    override fun isAvailable() = DoseNotification.isAvailable(context)

    override fun current(): Timer? = AwakeState.timer

    override fun hold(timer: Timer): Boolean {
        val previous = AwakeState.timer
        Log.i(TAG, "Holding $timer, previous: $previous, pending foreground starts: ${AwakeState.pendingForegroundStarts}")
        return try {
            // Set first: the service reads it once started, and it may be started right away.
            AwakeState.timer = timer
            if (previous != null) {
                // A running foreground service keeps the app in the foreground: it can be updated from anywhere.
                context.startService(AwakeService.intent(context, timer, foreground = false))
            } else {
                context.startForegroundService(AwakeService.intent(context, timer, foreground = true))
                AwakeState.pendingForegroundStarts++
            }
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException since Android 12, or background start restrictions: e.g. an
            // automation intent received while the app is in the background.
            Log.w(TAG, "Not allowed to hold the screen", e)
            AwakeState.timer = previous
            false
        }
    }

    override fun release() {
        // With the caller, to find out what ended a timer.
        Log.i(TAG, "Releasing ${AwakeState.timer}", Throwable("caller"))
        AwakeState.timer = null
        // Otherwise, the service stops itself once it has called startForeground(), as the timer is gone.
        if (AwakeState.pendingForegroundStarts == 0) context.stopService(AwakeService.intent(context))
    }
}
