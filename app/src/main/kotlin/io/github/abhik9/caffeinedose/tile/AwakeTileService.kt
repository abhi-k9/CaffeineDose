package io.github.abhik9.caffeinedose.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateFormat
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.system.settingsIntent
import io.github.abhik9.caffeinedose.system.startSettings
import io.github.abhik9.caffeinedose.system.title
import io.github.abhik9.caffeinedose.system.toast
import java.util.Date

/** Asks the system to refresh the tile. */
fun Context.requestTileUpdate() {
    try {
        TileService.requestListeningState(this, ComponentName(this, AwakeTileService::class.java))
    } catch (e: RuntimeException) {
        // Refused on some devices while the app is in the background: the tile refreshes when it becomes visible anyway.
        diagnostics.warn("tile: update refused", e)
    }
}

/**
 * Quick Settings tile: tap to keep the screen on for the default duration, or to stop the running timer.
 */
class AwakeTileService : TileService() {

    override fun onStartListening() = render(awakeTimer().current())

    override fun onClick() {
        diagnostics.record { "tile: click, locked=$isLocked" }
        when (val result = awakeTimer().toggle()) {
            is StartResult.Started -> render(result.timer)
            StartResult.Stopped -> render(null)
            is StartResult.Blocked -> resolve(result.requirement)
            // Some devices don't let a tile start a foreground service: start it from an activity instead.
            StartResult.Refused -> startActivityAndCollapseCompat(StartActivity.intent(this))
        }
    }

    private fun render(timer: Timer?) {
        val tile = qsTile ?: return
        tile.state = if (timer == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (SDK_INT >= Q) {
            tile.subtitle = if (timer == null) {
                getString(R.string.tile_subtitle)
            } else {
                getString(R.string.until, DateFormat.getTimeFormat(this).format(Date(timer.endsAt)))
            }
        }
        tile.updateTile()
    }

    private fun resolve(requirement: Requirement) {
        toast(requirement.title)
        startSettings(settingsIntent(requirement).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), ::startActivityAndCollapseCompat)
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun startActivityAndCollapseCompat(intent: Intent) {
        // Activities can't be displayed on top of the keyguard: the device must be unlocked first.
        if (isLocked) return unlockAndRun { startActivityAndCollapseCompat(intent) }
        if (SDK_INT >= UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
