package io.github.abhik9.caffeinedose.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.core.StartResult

private const val TAG = "System"

/** Where the user can resolve a [Requirement]. */
fun Context.settingsIntent(requirement: Requirement): Intent = when (requirement) {
    Requirement.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
}

/** Fallback for OEM builds missing a specific settings screen. */
fun Context.appDetailsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

/**
 * Starts [intent], or the app details settings when no activity handles it.
 */
fun Context.startSettings(intent: Intent, start: (Intent) -> Unit = { startActivity(it) }) {
    try {
        start(intent)
    } catch (e: ActivityNotFoundException) {
        Log.w(TAG, "No activity for $intent", e)
        runCatching { start(appDetailsIntent()) }.onFailure { Log.w(TAG, "No app details settings", it) }
    }
}

@get:StringRes
val Requirement.message: Int
    get() = when (this) {
        Requirement.NOTIFICATIONS -> R.string.requirement_notifications_toast
    }

fun Context.toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/** Tells the user why a timer operation could not run, when it was [StartResult.Blocked] or [StartResult.Refused]. */
fun Context.reportBlocked(result: StartResult?) {
    when (result) {
        is StartResult.Blocked -> toast(result.requirement.message)
        StartResult.Refused -> toast(R.string.refused_toast)
        else -> Unit
    }
}
