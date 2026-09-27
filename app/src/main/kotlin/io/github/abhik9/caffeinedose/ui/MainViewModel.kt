package io.github.abhik9.caffeinedose.ui

import android.app.Application
import android.app.UiModeManager
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.awake.ScreenOverlay
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.DurationSetting
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.diagnostics.DiagnosticsReport
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.settings.ThemeMode
import io.github.abhik9.caffeinedose.settings.UserSettings
import io.github.abhik9.caffeinedose.system.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Immutable
data class MainUiState(
    /** The running timer, `null` when there is none. */
    val timer: Timer? = null,
    val settings: UserSettings = UserSettings(),
    /** What prevents a timer from running, `null` when nothing does. */
    val missingRequirement: Requirement? = null,
    /** Whether the app may display over other apps, see [io.github.abhik9.caffeinedose.awake.ScreenOverlay]. */
    val overlayAllowed: Boolean = false,
    /** Whether this device is known to ignore the wake lock, see [ScreenOverlay.isNeeded]. */
    val overlayNeeded: Boolean = false,
    /** Size of the recorded diagnostics log, in bytes. */
    val diagnosticsLogBytes: Long = 0,
    /** Material You colors extracted from the wallpaper, since Android 12. */
    val dynamicColorAvailable: Boolean = SDK_INT >= S,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        const val TAG = "MainViewModel"

        /** The timer can change behind our back (notification actions, timeout, tile, automation). */
        val POLL_INTERVAL = 1.seconds
    }

    private val settings = SettingsStore.from(application)
    private val timer = application.awakeTimer()
    private val diagnostics = application.diagnostics

    /** Permissions last seen, to record their changes (e.g. from the system settings). Written off the main thread. */
    @Volatile
    private var permissions: String? = null

    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val ticks = merge(
        flow {
            while (true) {
                emit(Unit)
                delay(POLL_INTERVAL)
            }
        },
        refreshes,
    )

    val state: StateFlow<MainUiState> = combine(settings.changes, ticks) { current, _ -> uiState(current) }
        // Reading the permissions is a binder call: keep it off the main thread.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5.seconds.inWholeMilliseconds), uiState(settings.snapshot()))

    private fun uiState(userSettings: UserSettings): MainUiState {
        val state = MainUiState(
            timer = timer.current(),
            settings = userSettings,
            missingRequirement = timer.missingRequirement(),
            overlayAllowed = Settings.canDrawOverlays(getApplication<Application>()),
            overlayNeeded = ScreenOverlay.isNeeded(),
            diagnosticsLogBytes = diagnostics.size(),
        )
        val current = "missing requirement=${state.missingRequirement}, display over other apps=${state.overlayAllowed}"
        if (current != permissions) {
            permissions = current
            diagnostics.record { "permissions: $current" }
        }
        return state
    }

    /** Re-reads the timer and the permissions right away, e.g. when coming back from the system settings. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    /** Runs a timer [operation] from the app screen: the timer records what it does, this records where from. */
    private inline fun <T> update(name: String, operation: () -> T): T {
        diagnostics.record { "app: $name" }
        return operation().also { refresh() }
    }

    fun start(minutes: Int): StartResult = update("start $minutes min") { timer.start(minutes.minutes) }

    fun stop() = update("stop") { timer.stop() }

    fun extend(): StartResult? = update("extend") { timer.extend() }

    fun reduce(): StartResult? = update("reduce") { timer.reduce() }

    fun setMinutes(setting: DurationSetting, minutes: Int): StartResult? = update("set $setting to $minutes min") {
        settings.setMinutes(setting, minutes)
        // Refresh the notification actions ("+N", "−N") of a running timer.
        if (setting != DurationSetting.INITIAL) timer.refresh() else null
    }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        // Since Android 12 the system persists a per-app night mode, applied to every window (which are recreated).
        if (SDK_INT >= S) {
            getApplication<Application>().getSystemService(UiModeManager::class.java).setApplicationNightMode(mode.nightMode)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        settings.dynamicColor = enabled
    }

    fun setAutomationEnabled(enabled: Boolean) {
        settings.automationEnabled = enabled
    }

    fun setStopOnScreenOff(enabled: Boolean) {
        settings.stopOnScreenOff = enabled
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        // Recorded while enabled, so that both ends of a recording session are in the log.
        if (enabled) {
            settings.diagnosticsEnabled = true
            diagnostics.record { "diagnostics: enabled" }
        } else {
            diagnostics.record { "diagnostics: disabled" }
            settings.diagnosticsEnabled = false
        }
    }

    fun clearDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            diagnostics.clear()
            refresh()
        }
    }

    /** Writes the device details and the diagnostics log to [destination], a document picked by the user. */
    fun exportDiagnostics(destination: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val exported = withContext(Dispatchers.IO) {
                try {
                    val header = DiagnosticsReport.header(app)
                    checkNotNull(app.contentResolver.openOutputStream(destination, "wt")) { "No output stream" }.use {
                        diagnostics.export(it, header)
                    }
                    true
                } catch (e: IOException) {
                    Log.w(TAG, "Diagnostics export failed", e)
                    false
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Diagnostics export failed", e)
                    false
                }
            }
            app.toast(if (exported) R.string.diagnostics_exported else R.string.diagnostics_export_failed)
        }
    }
}

private val ThemeMode.nightMode: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
