package io.github.abhik9.caffeinedose.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

    override fun onReceive(context: Context, intent: Intent) {
        val log = context.diagnostics
        if (!SettingsStore.from(context).automationEnabled) {
            log.record { "automation: ignored ${intent.action} (disabled)" }
            return
        }
        // Extras of an external intent are untrusted: unparcelling them can throw.
        val key = Automation.EXTRA_DURATION
        val seconds = try {
            if (intent.hasExtra(key)) intent.getLongExtra(key, 0L).takeIf { it != 0L } ?: intent.getIntExtra(key, 0).toLong() else null
        } catch (e: RuntimeException) {
            log.warn("automation: invalid extras", e)
            null
        }
        val command = Automation.parse(intent.action, seconds)
        log.record { "automation: ${intent.action} -> $command" }
        if (command != null) context.reportBlocked(context.awakeTimer().execute(command))
    }
}
