package io.github.abhik9.caffeinedose.ui

import android.app.Application
import android.app.UiModeManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.S
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.DurationSetting
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.core.Timer
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.settings.ThemeMode
import io.github.abhik9.caffeinedose.settings.UserSettings
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Immutable
data class MainUiState(
    /** The running timer, `null` when there is none. */
    val timer: Timer? = null,
    val settings: UserSettings = UserSettings(),
    /** What prevents a timer from running, `null` when nothing does. */
    val missingRequirement: Requirement? = null,
    /** Material You colors extracted from the wallpaper, since Android 12. */
    val dynamicColorAvailable: Boolean = SDK_INT >= S,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** The timer can change behind our back (notification actions, timeout, tile, automation). */
        val POLL_INTERVAL = 1.seconds
    }

    private val settings = SettingsStore.from(application)
    private val timer = application.awakeTimer()

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

    private fun uiState(userSettings: UserSettings) = MainUiState(
        timer = timer.current(),
        settings = userSettings,
        missingRequirement = timer.missingRequirement(),
    )

    /** Re-reads the timer and the permissions right away, e.g. when coming back from the system settings. */
    fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private inline fun <T> update(operation: () -> T): T = operation().also { refresh() }

    fun start(minutes: Int): StartResult = update { timer.start(minutes.minutes) }

    fun stop() = update { timer.stop() }

    fun extend(): StartResult? = update { timer.extend() }

    fun reduce(): StartResult? = update { timer.reduce() }

    fun setMinutes(setting: DurationSetting, minutes: Int): StartResult? = update {
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
}

private val ThemeMode.nightMode: Int
    get() = when (this) {
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
