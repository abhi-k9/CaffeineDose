package io.github.abhik9.caffeinedose.awake

import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.abhik9.caffeinedose.EXTRA_DEADLINE
import io.github.abhik9.caffeinedose.EXTRA_ENDS_AT
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.tile.requestTileUpdate

/**
 * Keeps the screen on until the deadline of the [AwakeState.timer], with a screen wake lock.
 *
 * `FLAG_KEEP_SCREEN_ON`, the recommended replacement of screen wake locks, only works while one of the app's windows is
 * visible: the deprecated screen wake lock is the only way to keep the screen on over other apps. A foreground service
 * holds it, as a wake lock only lives as long as its process.
 *
 * Started and stopped by the [ServiceScreenKeeper]. It ends the timer itself when:
 * - the deadline is reached,
 * - the screen is turned off (e.g. with the power button), unless disabled in the settings.
 */
class AwakeService : Service() {

    companion object {
        private const val TAG = "AwakeService"
        private const val WAKE_LOCK_TAG = "CaffeineDose:screen"

        fun intent(context: Context) = Intent(context, AwakeService::class.java)

        fun intent(context: Context, timer: Timer): Intent =
            intent(context).putExtra(EXTRA_DEADLINE, timer.deadline).putExtra(EXTRA_ENDS_AT, timer.endsAt)

        private fun Intent.timer(): Timer? {
            val deadline = getLongExtra(EXTRA_DEADLINE, 0L)
            return if (deadline > 0L) Timer(deadline = deadline, endsAt = getLongExtra(EXTRA_ENDS_AT, 0L)) else null
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val check = Runnable(::update)

    private lateinit var wakeLock: PowerManager.WakeLock

    /** The timer shown by the notification, and held by the [wakeLock]. Main thread only. */
    private var held: Timer? = null

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    val timer = held ?: return
                    if (SettingsStore.from(context).stopOnScreenOff) awakeTimer().end(timer.deadline)
                }

                // The handler may have been delayed by deep sleep while the screen was off: catch up.
                Intent.ACTION_SCREEN_ON -> update()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @Suppress("DEPRECATION")
    override fun onCreate() {
        super.onCreate()
        // ON_AFTER_RELEASE: once released, the screen stays on for the usual timeout instead of turning off at once.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE, WAKE_LOCK_TAG)
            .apply { setReferenceCounted(false) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        // System broadcasts are delivered to non exported receivers.
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must be called right away when started with `startForegroundService()`, even if the timer ended since.
        val requested = intent?.timer() ?: AwakeState.timer
        if (requested == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        enterForeground(requested)
        update()
        // Not restarted by the system: the timer is gone with the process.
        return START_NOT_STICKY
    }

    /**
     * Applies the current timer: the notification, the wake lock and the next check. Every signal ends up here, and the
     * remaining time is always computed on the `elapsedRealtime` clock, so a delayed check can't extend the timer.
     */
    private fun update() {
        handler.removeCallbacks(check)
        val timer = AwakeState.timer
        if (timer == null) {
            // Released in the meantime.
            stopSelf()
            return
        }
        // Stops this service through the keeper.
        if (awakeTimer().expireIfDue()) return
        if (timer != held) {
            held = timer
            getSystemService(NotificationManager::class.java).notify(DoseNotification.ID, DoseNotification.build(this, timer))
        }
        val remaining = timer.deadline - SystemClock.elapsedRealtime()
        // The timeout is a safety net: the lock is never held past the deadline, even if this service misses a check.
        // Timeouts don't elapse in deep sleep, hence the check on ACTION_SCREEN_ON.
        wakeLock.acquire(remaining)
        handler.postDelayed(check, remaining)
    }

    private fun enterForeground(timer: Timer) {
        val notification = DoseNotification.build(this, timer)
        held = timer
        try {
            if (SDK_INT >= UPSIDE_DOWN_CAKE) {
                startForeground(DoseNotification.ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(DoseNotification.ID, notification)
            }
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the keeper normally reports it when starting the service.
            Log.w(TAG, "Foreground service not allowed", e)
            awakeTimer().end(timer.deadline)
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(check)
        if (wakeLock.isHeld) wakeLock.release()
        unregisterReceiver(screenReceiver)
        // Stopped by the system rather than by the keeper: don't report a timer that nothing holds anymore. A newer timer
        // is kept, it restarts this service.
        if (held != null && AwakeState.timer == held) {
            AwakeState.timer = null
            requestTileUpdate()
        }
        super.onDestroy()
    }
}
