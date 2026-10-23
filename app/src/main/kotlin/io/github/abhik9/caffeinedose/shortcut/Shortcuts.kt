package io.github.abhik9.caffeinedose.shortcut

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.service.quicksettings.TileService
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.ShortcutReaction
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.core.shortcutReaction
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.system.message
import io.github.abhik9.caffeinedose.system.settingsIntent
import io.github.abhik9.caffeinedose.system.startSettings
import io.github.abhik9.caffeinedose.system.title
import io.github.abhik9.caffeinedose.system.toast
import io.github.abhik9.caffeinedose.system.untilText
import io.github.abhik9.caffeinedose.tile.AwakeTileService
import io.github.abhik9.caffeinedose.widget.AwakeWidget

/**
 * A shortcut to the timer: the Quick Settings tile or the home screen widget. Only what differs between them is here,
 * [toggleTimer] does the rest.
 */
interface ShortcutHost {
    /** Shows [timer], or the idle state when `null`. */
    fun render(timer: Timer?)

    /** Starts an activity, throwing like `Context.startActivity()` when it can't. */
    fun launch(intent: Intent)

    /** Runs [action] once the device is unlocked: activities can't be displayed on top of the keyguard. */
    fun whenUnlocked(action: () -> Unit) = action()
}

/** Toggles the timer from a tap on a shortcut, which reacts the same way as every other shortcut. */
fun Context.toggleTimer(host: ShortcutHost) {
    when (val reaction = awakeTimer().toggle().shortcutReaction()) {
        is ShortcutReaction.Show -> host.render(reaction.timer)

        is ShortcutReaction.Resolve -> {
            toast(reaction.requirement.title)
            // Within the fallback handling of startSettings(), also when it runs after unlocking.
            host.whenUnlocked { startSettings(settingsIntent(reaction.requirement), start = host::launch) }
        }

        // Some devices don't let a shortcut start a foreground service: start it from an activity instead.
        ShortcutReaction.StartFromForeground -> host.whenUnlocked { host.launch(StartActivity.intent(this)) }

        is ShortcutReaction.Explain -> {
            toast(reaction.limit.message)
            host.render(awakeTimer().current())
        }
    }
}

/**
 * Shows the current timer on every shortcut: the Quick Settings tile and the home screen widgets. Called after every
 * change of the timer, including when it ends on its own. Never throws.
 */
fun Context.refreshShortcuts() {
    try {
        TileService.requestListeningState(this, ComponentName(this, AwakeTileService::class.java))
    } catch (e: RuntimeException) {
        // Refused on some devices while the app is in the background: the tile refreshes when it becomes visible anyway.
        diagnostics.warn("tile: update refused", e)
    }
    AwakeWidget.updateAll(this)
}

/** What a shortcut says about [timer]: how to start one, or when it ends. */
fun Context.shortcutStateText(timer: Timer?): String = if (timer == null) getString(R.string.shortcut_idle) else untilText(timer)
