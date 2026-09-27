package io.github.abhik9.caffeinedose.diagnostics

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.P
import android.os.Build.VERSION_CODES.R
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import io.github.abhik9.caffeinedose.awake.AwakeState
import io.github.abhik9.caffeinedose.settings.SettingsStore
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small log of what the app did, kept on the device: it tells why a timer ended on devices that can't be debugged
 * with `adb`, e.g. when the manufacturer's power management stops the app. It never leaves the device unless the user
 * shares the [report].
 */
object Diagnostics {
    private const val TAG = "Diagnostics"
    private const val FILE = "diagnostics.log"

    /** Once the file exceeds [MAX_BYTES], only its last [KEEP_LINES] lines are kept. */
    private const val MAX_BYTES = 256 * 1024
    private const val KEEP_LINES = 1000

    /** Lines of the log in the [report]: it is shared as the text of an intent, whose size is limited. */
    private const val REPORT_LINES = 300

    /** [ActivityManager] exit reasons, by value: some constants are newer than the minimum SDK. */
    private val EXIT_REASONS = listOf(
        "unknown", "exit self", "signaled", "low memory", "crash", "native crash", "ANR", "initialization failure",
        "permission change", "excessive resource usage", "user requested", "user stopped", "dependency died", "other",
        "freezer", "package state change", "package updated",
    )

    // Guarded by this object.
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT)

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    private fun format(millis: Long): String = synchronized(this) { time.format(Date(millis)) }

    /** Logs [message] to logcat and to the diagnostics file. Cheap enough for the main thread. */
    fun log(context: Context, tag: String, message: String) {
        Log.i(tag, message)
        synchronized(this) {
            try {
                val file = file(context)
                file.appendText("${format(System.currentTimeMillis())} $tag: $message\n")
                if (file.length() > MAX_BYTES) {
                    file.writeText(file.readLines().takeLast(KEEP_LINES).joinToString("\n", postfix = "\n"))
                }
            } catch (e: IOException) {
                Log.w(TAG, "Can't write the diagnostics", e)
            }
        }
    }

    fun clear(context: Context) {
        synchronized(this) { file(context).delete() }
    }

    /** The device, the settings that matter, why the app's process last ended, then the latest events. */
    fun report(context: Context): String = buildString {
        appendLine("CaffeineDose ${versionName(context)}")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API $SDK_INT), ${Build.DISPLAY}")
        val power = context.getSystemService(PowerManager::class.java)
        val activities = context.getSystemService(ActivityManager::class.java)
        appendLine("Notifications enabled: ${context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()}")
        appendLine("Battery optimization ignored: ${power.isIgnoringBatteryOptimizations(context.packageName)}")
        if (SDK_INT >= P) appendLine("Background restricted: ${activities.isBackgroundRestricted}")
        appendLine("Screen timeout: ${Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, -1)} ms")
        appendLine("Stop when the screen is turned off: ${SettingsStore.from(context).stopOnScreenOff}")
        appendLine("Running timer: ${AwakeState.timer}")

        appendLine()
        appendLine("Process exits (latest first):")
        if (SDK_INT >= R) {
            val exits = activities.getHistoricalProcessExitReasons(context.packageName, 0, 10)
            if (exits.isEmpty()) appendLine("none")
            for (exit in exits) {
                val reason = EXIT_REASONS.getOrNull(exit.reason) ?: "reason ${exit.reason}"
                appendLine("${format(exit.timestamp)} $reason, status ${exit.status}, importance ${exit.importance}: ${exit.description}")
            }
        } else {
            appendLine("unavailable before Android 11")
        }

        appendLine()
        appendLine("Events (latest last):")
        val lines = synchronized(this@Diagnostics) {
            try {
                file(context).takeIf { it.exists() }?.readLines().orEmpty()
            } catch (e: IOException) {
                listOf("Can't read the diagnostics: $e")
            }
        }
        if (lines.isEmpty()) appendLine("none")
        lines.takeLast(REPORT_LINES).forEach(::appendLine)
    }

    private fun versionName(context: Context): String? = try {
        val info = if (SDK_INT >= TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "Unknown package", e)
        null
    }
}
