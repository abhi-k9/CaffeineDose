package io.github.abhik9.caffeinedose.settings

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.util.Log
import androidx.core.content.edit
import io.github.abhik9.caffeinedose.core.DurationSetting
import io.github.abhik9.caffeinedose.core.TimerSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Night mode of the app, independent of the system one. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Snapshot of all the user settings. */
data class UserSettings(
    val durations: Map<DurationSetting, Int> = DurationSetting.entries.associateWith { it.defaultMinutes },
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val automationEnabled: Boolean = true,
    val stopOnScreenOff: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
) {
    fun minutes(setting: DurationSetting): Int = durations[setting] ?: setting.defaultMinutes
}

/**
 * User settings, stored in [SharedPreferences]: they are read synchronously by broadcast receivers and the tile.
 * Every read is validated, so that a corrupted or tampered file can only fall back to the defaults.
 */
class SettingsStore private constructor(
    private val prefs: SharedPreferences,
    /** Debug builds record diagnostics unless turned off, e.g. for the device test. */
    private val diagnosticsByDefault: Boolean,
) {

    companion object {
        private const val TAG = "SettingsStore"
        private const val FILE = "settings"
        private const val KEY_THEME = "theme"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_AUTOMATION = "automation"
        private const val KEY_STOP_ON_SCREEN_OFF = "stop_on_screen_off"
        private const val KEY_DIAGNOSTICS = "diagnostics"

        private val DurationSetting.key: String
            get() = when (this) {
                DurationSetting.INITIAL -> "timeout_initial_minutes"
                DurationSetting.INCREMENT -> "timeout_increment_minutes"
                DurationSetting.DECREMENT -> "timeout_decrement_minutes"
            }

        fun from(context: Context): SettingsStore {
            val app = context.applicationContext
            val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
            return SettingsStore(app.getSharedPreferences(FILE, Context.MODE_PRIVATE), diagnosticsByDefault = debuggable)
        }
    }

    fun minutes(setting: DurationSetting): Int = setting.sanitize(read(setting.key, setting.defaultMinutes, prefs::getInt))

    fun setMinutes(setting: DurationSetting, minutes: Int) = prefs.edit { putInt(setting.key, setting.sanitize(minutes)) }

    fun timerSettings(): TimerSettings = TimerSettings.ofMinutes(::minutes)

    var themeMode: ThemeMode
        get() = read(KEY_THEME, null, prefs::getString)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
            ?: ThemeMode.SYSTEM
        set(value) = prefs.edit { putString(KEY_THEME, value.name) }

    var dynamicColor: Boolean
        get() = read(KEY_DYNAMIC_COLOR, true, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }

    /** Whether other apps may control the timer, see [io.github.abhik9.caffeinedose.automation.AutomationReceiver]. */
    var automationEnabled: Boolean
        get() = read(KEY_AUTOMATION, true, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_AUTOMATION, value) }

    /** Whether turning the screen off (e.g. with the power button) ends the timer. */
    var stopOnScreenOff: Boolean
        get() = read(KEY_STOP_ON_SCREEN_OFF, true, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_STOP_ON_SCREEN_OFF, value) }

    /** Whether the [io.github.abhik9.caffeinedose.diagnostics.DiagnosticsLog] is recording. */
    var diagnosticsEnabled: Boolean
        get() = read(KEY_DIAGNOSTICS, diagnosticsByDefault, prefs::getBoolean)
        set(value) = prefs.edit { putBoolean(KEY_DIAGNOSTICS, value) }

    fun snapshot() = UserSettings(
        durations = DurationSetting.entries.associateWith(::minutes),
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        automationEnabled = automationEnabled,
        stopOnScreenOff = stopOnScreenOff,
        diagnosticsEnabled = diagnosticsEnabled,
    )

    /** Emits the current settings, then every change. */
    val changes: Flow<UserSettings> = callbackFlow {
        // The listener is referenced until the flow completes: SharedPreferences only keeps weak references.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(snapshot()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        send(snapshot())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /** A value stored with another type throws [ClassCastException]: fall back to the default. */
    private inline fun <T> read(key: String, default: T, getter: (String, T) -> T): T = try {
        getter(key, default)
    } catch (e: ClassCastException) {
        Log.w(TAG, "Invalid value for $key", e)
        default
    }
}
