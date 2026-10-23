package io.github.abhik9.caffeinedose.shortcut

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.Automation
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.system.durationText

/**
 * The launcher shortcuts (long press on the app icon): keep the screen on for 15 minutes, an hour, while charging, or
 * stop. Dynamic rather than static, which would hard-code the package name of the release build.
 */
internal object LauncherShortcuts {

    /** Published from the app screen: the system limits how often apps in the background may do it. */
    fun publish(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val locale = context.resources.configuration.locales[0]
        val start = listOf(15, 60).map { minutes ->
            val duration = durationText(locale, minutes)
            val intent = StartActivity.intent(context, Automation.ACTION_START, minutes * 60L)
            shortcut(context, "start_$minutes", duration, context.getString(R.string.action_start, duration), intent)
        }
        val whileCharging = shortcut(
            context,
            id = "while_charging",
            shortLabel = context.getString(R.string.cycle_step_while_charging),
            longLabel = context.getString(R.string.action_start_while_charging),
            intent = StartActivity.intent(context, Automation.ACTION_START_WHILE_CHARGING),
        )
        val stop = shortcut(
            context,
            id = "stop",
            shortLabel = context.getString(R.string.action_stop),
            longLabel = context.getString(R.string.shortcut_stop),
            intent = StartActivity.intent(context, Automation.ACTION_STOP),
        )
        try {
            manager.dynamicShortcuts = (start + whileCharging + stop).mapIndexed { rank, builder -> builder.setRank(rank).build() }
        } catch (e: RuntimeException) {
            // IllegalStateException while the user is locked, for instance: the next app start publishes them.
            context.diagnostics.warn("shortcuts: not published", e)
        }
    }

    private fun shortcut(context: Context, id: String, shortLabel: String, longLabel: String, intent: Intent) =
        ShortcutInfo.Builder(context, id)
            .setShortLabel(shortLabel)
            .setLongLabel(longLabel)
            .setIcon(Icon.createWithResource(context, R.drawable.ic_shortcut))
            .setIntent(intent)
}
