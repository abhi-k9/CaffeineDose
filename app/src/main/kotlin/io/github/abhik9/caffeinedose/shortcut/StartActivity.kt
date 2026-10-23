package io.github.abhik9.caffeinedose.shortcut

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.Automation
import io.github.abhik9.caffeinedose.core.AwakeTimer
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.system.reportBlocked
import io.github.abhik9.caffeinedose.system.settingsIntent
import io.github.abhik9.caffeinedose.system.startSettings

/**
 * Invisible activity running a timer operation from the foreground, where it may always start the foreground service:
 * for a shortcut (the Quick Settings tile, the home screen widget) that isn't allowed to itself, and for the launcher
 * shortcuts. Not exported, so other apps can't bypass the automation setting through it.
 */
class StartActivity : Activity() {

    companion object {
        /** Taps the shortcut again, from the foreground: see [AwakeTimer.tap]. */
        fun intent(context: Context): Intent = Intent(context, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Runs an [Automation] action, e.g. from a launcher shortcut: see [LauncherShortcuts]. */
        fun intent(context: Context, action: String, durationSeconds: Long? = null): Intent = intent(context)
            .setAction(action)
            .apply { if (durationSeconds != null) putExtra(Automation.EXTRA_DURATION, durationSeconds) }
    }

    override fun onResume() {
        super.onResume()
        // Started once the activity is in the foreground.
        val action = intent.action
        diagnostics.record { "shortcut: ${action ?: "tap"} from the foreground" }
        val timer = awakeTimer()
        val result = if (action == null) {
            timer.tap()
        } else {
            val seconds = intent.getLongExtra(Automation.EXTRA_DURATION, 0L).takeIf { it != 0L }
            Automation.parse(action, seconds)?.let(timer::execute)
        }
        if (result is StartResult.Blocked) startSettings(settingsIntent(result.requirement))
        reportBlocked(result)
        finish()
    }
}
