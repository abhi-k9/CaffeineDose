package io.github.abhik9.caffeinedose.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.Automation
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.system.reportBlocked

/**
 * Receives the [Automation] broadcasts. Any app can send them, so:
 * - they can be disabled from the app settings,
 * - they only expose timer operations (no settings, no data),
 * - every input is validated and clamped.
 */
class AutomationReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "AutomationReceiver"

        /**
         * Extras of an external intent are untrusted: unparcelling them can throw.
         * @return the [Automation.EXTRA_DURATION] extra, or `null` when missing or invalid.
         */
        fun Intent.durationSeconds(): Long? = try {
            val key = Automation.EXTRA_DURATION
            if (hasExtra(key)) getLongExtra(key, 0L).takeIf { it != 0L } ?: getIntExtra(key, 0).toLong() else null
        } catch (e: RuntimeException) {
            Log.w(TAG, "Invalid extras", e)
            null
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (!SettingsStore.from(context).automationEnabled) {
            Log.i(TAG, "Automation is disabled, ignoring ${intent.action}")
            context.diagnostics.record { "automation: ignored ${intent.action} (disabled)" }
            return
        }
        val command = Automation.parse(intent.action, intent.durationSeconds())
        context.diagnostics.record { "automation: ${intent.action} -> $command" }
        if (command == null) return
        context.reportBlocked(context.awakeTimer().execute(command))
    }
}
