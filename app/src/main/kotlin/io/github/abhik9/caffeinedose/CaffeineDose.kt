package io.github.abhik9.caffeinedose

import android.content.Context
import android.os.SystemClock
import io.github.abhik9.caffeinedose.awake.ServiceScreenKeeper
import io.github.abhik9.caffeinedose.core.AwakeTimer
import io.github.abhik9.caffeinedose.core.DeviceClock
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.tile.requestTileUpdate

/** Extra holding a [io.github.abhik9.caffeinedose.core.Timer.deadline] in intents. */
internal const val EXTRA_DEADLINE = "io.github.abhik9.caffeinedose.extra.DEADLINE"

/** Extra holding a [io.github.abhik9.caffeinedose.core.Timer.endsAt] in intents. */
internal const val EXTRA_ENDS_AT = "io.github.abhik9.caffeinedose.extra.ENDS_AT"

internal object SystemDeviceClock : DeviceClock {
    override fun wallMillis() = System.currentTimeMillis()
    override fun elapsedMillis() = SystemClock.elapsedRealtime()
}

/**
 * Entry point of all timer operations, wired to their Android implementations.
 * Cheap to create: the state lives in the process holding the screen, and the preferences.
 */
fun Context.awakeTimer(): AwakeTimer {
    val context = applicationContext
    val settings = SettingsStore.from(context)
    return AwakeTimer(
        keeper = ServiceScreenKeeper(context),
        clock = SystemDeviceClock,
        settings = settings::timerSettings,
        onChange = context::requestTileUpdate,
        log = context.diagnostics,
    )
}
