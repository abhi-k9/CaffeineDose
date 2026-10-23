package io.github.abhik9.caffeinedose.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.shortcut.ShortcutHost
import io.github.abhik9.caffeinedose.shortcut.shortcutStateText
import io.github.abhik9.caffeinedose.shortcut.tapShortcut

/**
 * Quick Settings tile: tap to keep the screen on for the default duration, or to stop the running timer (or for the next
 * step of "Tap again for longer", see [tapShortcut]). It shows until when the screen is kept on.
 */
class AwakeTileService :
    TileService(),
    ShortcutHost {

    override fun onStartListening() = render(awakeTimer().current())

    override fun onClick() {
        diagnostics.record { "tile: click, locked=$isLocked" }
        tapShortcut(this)
    }

    override fun render(timer: Timer?) {
        val tile = qsTile ?: return
        tile.state = if (timer == null) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        if (SDK_INT >= Q) tile.subtitle = shortcutStateText(timer)
        tile.updateTile()
    }

    override fun whenUnlocked(action: () -> Unit) = if (isLocked) unlockAndRun { action() } else action()

    /** Starts the activity, and collapses the Quick Settings panel to show it. */
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun launch(intent: Intent) {
        // Started from a service: there is no task to start it in.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (SDK_INT >= UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
