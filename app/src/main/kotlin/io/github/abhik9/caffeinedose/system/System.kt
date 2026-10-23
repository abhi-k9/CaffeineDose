package io.github.abhik9.caffeinedose.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.net.toUri
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.PowerLimit
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import java.util.Date
import java.util.Locale

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

/** A duration in whole minutes, localized by ICU, e.g. "1 hr, 30 min". */
fun durationText(locale: Locale, minutes: Int): String {
    val format = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> format.format(Measure(rest, MeasureUnit.MINUTE))
        rest == 0 -> format.format(Measure(hours, MeasureUnit.HOUR))
        else -> format.formatMeasures(Measure(hours, MeasureUnit.HOUR), Measure(rest, MeasureUnit.MINUTE))
    }
}

/** When [timer] ends, in the user's time format (12 or 24 hours). */
fun Context.endTime(timer: Timer): String = DateFormat.getTimeFormat(this).format(Date(timer.endsAt))

/** "Until 10:42", or "Until unplugged", for the notification and the shortcuts (Quick Settings tile, home screen widget). */
fun Context.untilText(timer: Timer): String =
    if (timer.untilUnplugged) getString(R.string.until_unplugged) else getString(R.string.until, endTime(timer))

/** Why the power supply doesn't let the screen be kept on. */
@get:StringRes
val PowerLimit.message: Int
    get() = when (this) {
        PowerLimit.UNPLUGGED -> R.string.power_unplugged
        PowerLimit.LOW_BATTERY -> R.string.power_low_battery
        PowerLimit.BATTERY_SAVER -> R.string.power_battery_saver
    }

fun Context.toast(@StringRes message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

/** Tells the user why a timer operation could not run: it was [StartResult.Blocked], declined or refused. */
fun Context.reportBlocked(result: StartResult?) {
    when (result) {
        is StartResult.Blocked -> toast(result.requirement.title)
        is StartResult.Declined -> toast(result.limit.message)
        StartResult.Refused -> toast(R.string.refused_toast)
        else -> Unit
    }
}
