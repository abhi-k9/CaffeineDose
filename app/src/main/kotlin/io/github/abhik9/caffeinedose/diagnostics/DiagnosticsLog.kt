package io.github.abhik9.caffeinedose.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import io.github.abhik9.caffeinedose.core.EventLog
import io.github.abhik9.caffeinedose.settings.SettingsStore
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors

/**
 * Diagnostics log, only written while diagnostics are enabled in the settings.
 *
 * - Local: kept in the app's no-backup storage, it only leaves the device when the user exports it.
 * - Bounded: rotated into a single previous file once it reaches [MAX_FILE_BYTES].
 * - Cheap when disabled: messages are not even built.
 */
class DiagnosticsLog private constructor(private val dir: File, private val isEnabled: () -> Boolean) : EventLog {

    companion object {
        private const val TAG = "CaffeineDose"
        private const val MAX_FILE_BYTES = 256 * 1024L

        @Volatile
        private var instance: DiagnosticsLog? = null

        /** Process-wide instance: every component of the app appends to the same log. */
        fun get(context: Context): DiagnosticsLog = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                val settings = SettingsStore.from(app)
                DiagnosticsLog(File(app.noBackupFilesDir, "diagnostics")) { settings.diagnosticsEnabled }
            }.also { instance = it }
        }
    }

    private val current = File(dir, "diagnostics.log")
    private val previous = File(dir, "diagnostics.1.log")
    private val lock = Any()

    /** Writes never block the caller (often the main thread), and stay in order. */
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "diagnostics").apply { isDaemon = true } }

    override fun record(message: () -> String) {
        if (!isEnabled()) return
        val line = line(message)
        writer.execute { append(line) }
    }

    /** Something went wrong but is handled: logged to Logcat, and recorded. */
    fun warn(message: String, error: Throwable? = null) {
        Log.w(TAG, message, error)
        record { if (error == null) message else "$message: $error" }
    }

    /** Records synchronously, e.g. right before the process dies after a crash. */
    fun recordNow(message: () -> String) {
        if (!isEnabled()) return
        append(line(message))
    }

    /** Size of the recorded log, in bytes. */
    fun size(): Long = synchronized(lock) { current.length() + previous.length() }

    /** Writes [header] then the whole log, oldest entries first. */
    @Throws(IOException::class)
    fun export(output: OutputStream, header: String) = synchronized(lock) {
        output.write("$header\n--- Log ---\n".toByteArray())
        for (file in listOf(previous, current)) if (file.isFile) file.inputStream().use { it.copyTo(output) }
        output.flush()
    }

    fun clear() {
        synchronized(lock) {
            previous.delete()
            current.delete()
        }
    }

    private fun line(message: () -> String): String {
        val text = try {
            message()
        } catch (e: RuntimeException) {
            "<message failed: $e>"
        }
        // Wall clock for humans, elapsedRealtime to compare with timer deadlines.
        val now = OffsetDateTime.now().truncatedTo(ChronoUnit.MILLIS)
        return "$now [${SystemClock.elapsedRealtime()}] [${Thread.currentThread().name}] $text"
    }

    private fun append(line: String) {
        synchronized(lock) {
            try {
                dir.mkdirs()
                if (current.length() >= MAX_FILE_BYTES) {
                    previous.delete()
                    current.renameTo(previous)
                }
                current.appendText(line + "\n")
            } catch (e: IOException) {
                Log.w(TAG, "Diagnostics not recorded", e)
            }
        }
    }
}

/** The diagnostics log of this app. */
val Context.diagnostics: DiagnosticsLog get() = DiagnosticsLog.get(this)
