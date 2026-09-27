package io.github.abhik9.caffeinedose.awake

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.NotificationManager.IMPORTANCE_LOW
import android.app.NotificationManager.IMPORTANCE_NONE
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION.SDK_INT_FULL
import android.os.Build.VERSION_CODES.BAKLAVA
import android.os.Build.VERSION_CODES.S
import android.os.Build.VERSION_CODES_FULL
import android.os.SystemClock
import android.text.format.DateFormat
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.DurationSetting
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.ui.MainActivity
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

/**
 * The ongoing notification of [AwakeService], showing the running timer and its actions.
 */
internal object DoseNotification {
    const val ID = 1
    const val CHANNEL_ID = "dose"

    /** Called at every app start: creating an existing channel only updates its texts, never the user's choices. */
    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), IMPORTANCE_LOW).apply {
            description = context.getString(R.string.notification_channel_description)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Whether the notification can be shown: a timer never runs unnoticed. */
    fun isAvailable(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && manager.getNotificationChannel(CHANNEL_ID)?.importance != IMPORTANCE_NONE
    }

    fun build(context: Context, timer: Timer): Notification {
        val settings = SettingsStore.from(context)
        val increment = settings.minutes(DurationSetting.INCREMENT)
        val decrement = settings.minutes(DurationSetting.DECREMENT)
        val canReduce = (timer.deadline - SystemClock.elapsedRealtime()).milliseconds > decrement.minutes
        return Notification.Builder(context, CHANNEL_ID)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setSmallIcon(R.drawable.ic_tile)
            // A title is required for Live Updates: https://developer.android.com/develop/ui/views/notifications/live-update
            .setContentTitle(context.getString(R.string.notification_title))
            .setSubText(context.getString(R.string.until, DateFormat.getTimeFormat(context).format(Date(timer.endsAt))))
            .setShowWhen(true)
            .setWhen(timer.endsAt)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(MainActivity.pendingIntent(context))
            // Dismissing the notification (possible since Android 14) stops the timer.
            .setDeleteIntent(DoseActionReceiver.dismissIntent(context, timer.deadline))
            .addAction(action(context, DoseActionReceiver.ACTION_EXTEND, context.getString(R.string.action_extend, increment)))
            // Hidden rather than ending the timer; it can become stale as time goes by, which AwakeTimer.reduce() handles.
            .apply {
                if (canReduce) {
                    addAction(action(context, DoseActionReceiver.ACTION_REDUCE, context.getString(R.string.action_reduce, decrement)))
                }
            }
            .addAction(action(context, DoseActionReceiver.ACTION_STOP, context.getString(R.string.action_stop)))
            .apply {
                // Shown right away, instead of being deferred by up to 10 seconds.
                if (SDK_INT >= S) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                // Live Updates (promoted ongoing notifications) are only available since Android 16 QPR2.
                if (SDK_INT >= BAKLAVA && SDK_INT_FULL >= VERSION_CODES_FULL.BAKLAVA_1) setRequestPromotedOngoing(true)
            }
            .build()
    }

    private fun action(context: Context, action: String, title: String) = Notification.Action.Builder(
        Icon.createWithResource(context, R.drawable.ic_tile),
        title,
        DoseActionReceiver.pendingIntent(context, action),
    ).build()
}
