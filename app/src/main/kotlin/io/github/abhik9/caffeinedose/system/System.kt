package io.github.abhik9.caffeinedose.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import java.util.Date

/** Where the user can resolve a [Requirement]. */
fun Context.settingsIntent(requirement: Requirement): Intent = when (requirement) {
    Requirement.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
}

/** Where the user allows the app to display over other apps, see [io.github.abhik9.caffeinedose.awake.ScreenOverlay]. */
fun Context.overlaySettingsIntent(): Intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())

/** Last resort: OEM builds may lack, or restrict, specific settings screens. */
private fun Context.appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/**
 * Starts the first of [intents] that can be started, or else the app details settings.
 */
fun Context.startSettings(vararg intents: Intent, start: (Intent) -> Unit = { startActivity(it) }) {
    for (intent in listOf(*intents, appDetailsIntent())) {
        try {
            start(intent)
            return
        } catch (e: ActivityNotFoundException) {
            diagnostics.warn("settings: no activity for $intent", e)
        } catch (e: SecurityException) {
            diagnostics.warn("settings: not allowed to start $intent", e)
        }
    }
}

@get:StringRes
val Requirement.title: Int
    get() = when (this) {
        Requirement.NOTIFICATIONS -> R.string.requirement_notifications_title
    }

@get:StringRes
val Requirement.explanation: Int
    get() = when (this) {
        Requirement.NOTIFICATIONS -> R.string.requirement_notifications_explanation
    }

/** When [timer] ends, in the user's time format (12 or 24 hours). */
fun Context.endTime(timer: Timer): String = DateFormat.getTimeFormat(this).format(Date(timer.endsAt))

/** "Until 10:42", for the notification and the shortcuts (Quick Settings tile, home screen widget). */
fun Context.untilText(timer: Timer): String = getString(R.string.until, endTime(timer))

fun Context.toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/** Tells the user why a timer operation could not run, when it was [StartResult.Blocked] or [StartResult.Refused]. */
fun Context.reportBlocked(result: StartResult?) {
    when (result) {
        is StartResult.Blocked -> toast(result.requirement.title)
        StartResult.Refused -> toast(R.string.refused_toast)
        else -> Unit
    }
}
