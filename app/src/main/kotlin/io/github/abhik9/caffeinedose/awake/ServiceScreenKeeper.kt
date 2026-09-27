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
        val intent = AwakeService.intent(context, timer)
        return try {
            // Set first: the service reads it once started, and it may be started right away.
            AwakeState.timer = timer
            // A running foreground service keeps the app in the foreground: it can then be updated from anywhere.
            if (previous != null) context.startService(intent) else context.startForegroundService(intent)
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
        AwakeState.timer = null
        context.stopService(AwakeService.intent(context))
    }
}
