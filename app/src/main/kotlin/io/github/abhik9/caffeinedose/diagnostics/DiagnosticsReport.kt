package io.github.abhik9.caffeinedose.diagnostics

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.BAKLAVA
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.content.pm.PackageInfoCompat
import io.github.abhik9.caffeinedose.awake.DoseNotification
import io.github.abhik9.caffeinedose.awake.ScreenOverlay
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.settings.SettingsStore
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Snapshot of the app and device state, written at the top of an exported diagnostics log.
 * It only contains technical details: no personal data.
 */
internal object DiagnosticsReport {

    /** [android.app.ApplicationExitInfo] reasons, by value: some constants are newer than the minimum SDK. */
    private val EXIT_REASONS = listOf(
        "unknown", "exit self", "signaled", "low memory", "crash", "native crash", "ANR", "initialization failure",
        "permission change", "excessive resource usage", "user requested", "user stopped", "dependency died", "other",
        "freezer", "package state change", "package updated",
    )

    fun header(context: Context): String = buildString {
        val app = context.applicationContext
        val info = packageInfo(app)
        val notifications = app.getSystemService(NotificationManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        val activities = app.getSystemService(ActivityManager::class.java)
        val timer = app.awakeTimer()

        appendLine("CaffeineDose diagnostics")
        appendLine("Generated: ${OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS)} [elapsedRealtime ${SystemClock.elapsedRealtime()}]")
        appendLine("App: ${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)}), ${app.packageName}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (${sdkVersion()}), ${Build.DISPLAY}")
        appendLine("Notifications enabled: ${notifications.areNotificationsEnabled()}")
        val channel = notifications.getNotificationChannel(DoseNotification.CHANNEL_ID)
        appendLine("Timer channel importance: ${channel?.importance ?: "not created"}")
        appendLine("Display over other apps: ${Settings.canDrawOverlays(app)}, known to be needed: ${ScreenOverlay.isNeeded()}")
        appendLine("Ignoring battery optimizations: ${power.isIgnoringBatteryOptimizations(app.packageName)}")
        appendLine("Power save mode: ${power.isPowerSaveMode}")
        if (SDK_INT >= P) {
            appendLine("Background restricted: ${activities.isBackgroundRestricted}")
            val bucket = app.getSystemService(UsageStatsManager::class.java).appStandbyBucket
            appendLine("Standby bucket: ${standbyBucket(bucket)}")
        }
        appendLine("Screen timeout: ${Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)} ms")
        appendLine("Missing requirement: ${timer.missingRequirement() ?: "none"}")
        appendLine("Timer: ${timer.current() ?: "none"}, remaining: ${timer.remaining() ?: "n/a"}")
        appendLine("Settings: ${SettingsStore.from(app).snapshot()}")
        appendLine("Process exits (latest first):")
        if (SDK_INT >= R) appendExits(activities, app.packageName) else appendLine("  unavailable before Android 11")
    }

    /** Why Android ended the app's previous processes: e.g. killed by the system, a crash, or the user. */
    @RequiresApi(R)
    private fun StringBuilder.appendExits(activities: ActivityManager, packageName: String) {
        val exits = activities.getHistoricalProcessExitReasons(packageName, 0, 10)
        if (exits.isEmpty()) appendLine("  none")
        for (exit in exits) {
            val time = OffsetDateTime.ofInstant(Instant.ofEpochMilli(exit.timestamp), ZoneId.systemDefault())
                .truncatedTo(ChronoUnit.SECONDS)
            val reason = EXIT_REASONS.getOrNull(exit.reason) ?: "reason ${exit.reason}"
            appendLine("  $time $reason, status ${exit.status}, importance ${exit.importance}: ${exit.description}")
        }
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(context: Context): PackageInfo = if (SDK_INT >= TIRAMISU) {
        context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }

    private fun sdkVersion(): String = if (SDK_INT >= BAKLAVA) "API ${Build.VERSION.SDK_INT_FULL}" else "API $SDK_INT"

    @RequiresApi(P)
    private fun standbyBucket(bucket: Int): String = when (bucket) {
        UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
        UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working set"
        UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
        UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
        else -> "other"
    } + " ($bucket)"
}
