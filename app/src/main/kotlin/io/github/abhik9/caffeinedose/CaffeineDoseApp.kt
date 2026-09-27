package io.github.abhik9.caffeinedose

import android.app.Application
import io.github.abhik9.caffeinedose.awake.DoseNotification
import io.github.abhik9.caffeinedose.diagnostics.diagnostics

class CaffeineDoseApp : Application() {

    override fun onCreate() {
        super.onCreate()
        recordCrashes()
        DoseNotification.createChannel(this)
    }

    /** Crashes are recorded in the diagnostics log (when enabled), then handled as usual. */
    private fun recordCrashes() {
        val log = diagnostics
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Never let diagnostics get in the way of the regular crash handling.
            runCatching { log.recordNow { "crash in ${thread.name}: ${error.stackTraceToString()}" } }
            default?.uncaughtException(thread, error)
        }
    }
}
