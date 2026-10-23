package io.github.abhik9.caffeinedose.widget

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT
import android.appwidget.AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH
import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.ColorRes
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.core.WidgetLayout
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.shortcut.shortcutStateText
import io.github.abhik9.caffeinedose.system.endTime

/**
 * The home screen widget: like the Quick Settings tile, it shows whether the screen is kept on and until when, and a tap
 * toggles the timer (see [WidgetToggleReceiver]). It adapts to its size with the [WidgetLayout]s.
 *
 * The widgets are updated after every timer operation, and at the deadline of the timer by an alarm: the end of the timer
 * otherwise goes unnoticed if the app process died in the meantime.
 */
internal object AwakeWidget {

    /** Shows [timer] on every widget, or the idle state when `null`. */
    fun updateAll(context: Context, timer: Timer? = context.awakeTimer().current()) {
        val manager = context.getSystemService(AppWidgetManager::class.java) ?: return
        update(context, manager, timer) {
            manager.getAppWidgetIds(ComponentName(context, AwakeWidgetProvider::class.java))
                .also { ids -> scheduleRefresh(context, if (ids.isEmpty()) null else timer) }
        }
    }

    /**
     * Refreshes the widgets within [REFRESH_WINDOW] after the deadline of [timer], or cancels the refresh when `null`.
     * Not exact, which would need a permission, and non-waking: the widgets can't be seen before the device wakes up.
     */
    private fun scheduleRefresh(context: Context, timer: Timer?) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val refresh = WidgetToggleReceiver.refreshIntent(context)
        if (timer == null || timer.untilUnplugged) {
            alarms.cancel(refresh)
        } else {
            // A second after the deadline, so that the timer has ended by then.
            val start = maxOf(timer.deadline, SystemClock.elapsedRealtime()) + 1_000L
            alarms.setWindow(AlarmManager.ELAPSED_REALTIME, start, REFRESH_WINDOW, refresh)
        }
    }

    /** The shortest window the system allows (since Android 12), in milliseconds. */
    private const val REFRESH_WINDOW = 10 * 60 * 1_000L

    /**
     * Shows [timer] on the widgets [ids]. Never throws: widgets are updated after every timer operation, which must not
     * fail because of them.
     */
    fun update(context: Context, manager: AppWidgetManager, timer: Timer?, ids: () -> IntArray) {
        try {
            if (SDK_INT >= S) {
                manager.updateAppWidget(ids(), responsiveViews(context, timer))
            } else {
                for (id in ids()) manager.updateAppWidget(id, views(context, layoutOf(context, manager.getAppWidgetOptions(id)), timer))
            }
        } catch (e: RuntimeException) {
            context.diagnostics.warn("widget: update failed", e)
        }
    }

    /** Every layout, with the size it needs: the launcher shows the one that fits, also while the widget is resized. */
    @RequiresApi(S)
    private fun responsiveViews(context: Context, timer: Timer?) =
        RemoteViews(WidgetLayout.entries.associate { SizeF(it.minWidthDp, it.minHeightDp) to views(context, it, timer) })

    /**
     * Before Android 12, the layout that fits the current size: in portrait, the widget is as wide as its minimum width
     * and as high as its maximum height, and conversely in landscape.
     */
    private fun layoutOf(context: Context, options: Bundle): WidgetLayout {
        val portrait = context.resources.configuration.orientation == ORIENTATION_PORTRAIT
        val width = options.getInt(if (portrait) OPTION_APPWIDGET_MIN_WIDTH else OPTION_APPWIDGET_MAX_WIDTH)
        val height = options.getInt(if (portrait) OPTION_APPWIDGET_MAX_HEIGHT else OPTION_APPWIDGET_MIN_HEIGHT)
        // Not known yet, e.g. right after the widget has been added: its default size.
        if (width == 0 || height == 0) return WidgetLayout.STACKED
        return WidgetLayout.forSize(width.toFloat(), height.toFloat())
    }

    private fun views(context: Context, layout: WidgetLayout, timer: Timer?): RemoteViews {
        val content = if (timer != null) R.color.widget_on_content else R.color.widget_off_content
        val background = if (timer != null) R.drawable.widget_background_on else R.drawable.widget_background_off
        return RemoteViews(context.packageName, layout.resource).apply {
            setInt(android.R.id.background, "setBackgroundResource", background)
            setThemeColor(context, R.id.widget_icon, "setColorFilter", content)
            if (layout.showsTitle) setThemeColor(context, R.id.widget_title, "setTextColor", content)
            if (layout.showsState) {
                setTextViewText(R.id.widget_state, context.shortcutStateText(timer))
                setThemeColor(context, R.id.widget_state, "setTextColor", content)
            }
            val description = when {
                timer == null -> context.getString(R.string.widget_description_off)
                timer.untilUnplugged -> context.getString(R.string.widget_description_unplugged)
                else -> context.getString(R.string.widget_description_on, context.endTime(timer))
            }
            setContentDescription(android.R.id.background, description)
            setOnClickPendingIntent(android.R.id.background, WidgetToggleReceiver.pendingIntent(context))
        }
    }

    private val WidgetLayout.resource: Int
        get() = when (this) {
            WidgetLayout.ICON -> R.layout.widget_icon
            WidgetLayout.STACKED -> R.layout.widget_stacked
            WidgetLayout.ROW -> R.layout.widget_row
        }

    /**
     * Since Android 12, the launcher resolves [color] itself, so that dynamic colors and dark mode follow the system.
     * Before, the app resolves it: its night mode is then the system one.
     */
    private fun RemoteViews.setThemeColor(context: Context, @IdRes viewId: Int, method: String, @ColorRes color: Int) {
        if (SDK_INT >= S) setColor(viewId, method, color) else setInt(viewId, method, context.getColor(color))
    }
}
