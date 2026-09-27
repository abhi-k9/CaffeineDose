package io.github.abhik9.caffeinedose.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration.UI_MODE_NIGHT_MASK
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import android.graphics.Color
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.abhik9.caffeinedose.R
import io.github.abhik9.caffeinedose.core.Requirement
import io.github.abhik9.caffeinedose.diagnostics.diagnostics
import io.github.abhik9.caffeinedose.settings.SettingsStore
import io.github.abhik9.caffeinedose.settings.ThemeMode
import io.github.abhik9.caffeinedose.system.overlaySettingsIntent
import io.github.abhik9.caffeinedose.system.settingsIntent
import io.github.abhik9.caffeinedose.system.startSettings
import io.github.abhik9.caffeinedose.system.toast
import io.github.abhik9.caffeinedose.ui.theme.CaffeineDoseTheme
import java.time.LocalDate

/**
 * Keeps the screen on for an exact duration, controls the running timer, and holds the settings.
 * Opened from the launcher, the notification, or by long pressing the Quick Settings tile.
 *
 * Only handles what requires an activity: permission prompts, system settings, the export destination picker and the
 * system bars. Everything else is up to the [MainViewModel].
 */
class MainActivity : ComponentActivity() {

    companion object {
        fun pendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
    }

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(RequestPermission()) { granted ->
        viewModel.refresh()
        diagnostics.record { "permission: notifications granted=$granted" }
        // Denied without any prompt (e.g. after two refusals): fall back to the settings.
        if (!granted) openSettings(Requirement.NOTIFICATIONS)
    }

    private val exportDestination = registerForActivityResult(CreateDocument("text/plain")) { destination ->
        destination?.let(viewModel::exportDiagnostics)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applySystemBars(SettingsStore.from(this).themeMode)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val themeMode = state.settings.themeMode
            // Before Android 12 the activity is not recreated on theme changes: Compose follows alone, not the system bars.
            LaunchedEffect(themeMode) { applySystemBars(themeMode) }
            LaunchedEffect(viewModel) { viewModel.blocked.collect { resolve(it) } }
            CaffeineDoseTheme(themeMode = themeMode, dynamicColor = state.settings.dynamicColor) {
                MainScreen(
                    state = state,
                    actions = viewModel,
                    onResolve = ::resolve,
                    onAllowOverlay = { startSettings(overlaySettingsIntent()) },
                    onExportDiagnostics = ::exportDiagnostics,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permissions may have been changed from the system settings.
        viewModel.refresh()
    }

    /** Edge-to-edge, with system bar icons matching the selected [ThemeMode] rather than the system one. */
    private fun applySystemBars(mode: ThemeMode) {
        val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { resources ->
            when (mode) {
                ThemeMode.SYSTEM -> resources.configuration.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    private fun resolve(requirement: Requirement) {
        val canPrompt =
            requirement == Requirement.NOTIFICATIONS && SDK_INT >= TIRAMISU && checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED
        if (canPrompt) notificationPermission.launch(POST_NOTIFICATIONS) else openSettings(requirement)
    }

    private fun openSettings(requirement: Requirement) = startSettings(settingsIntent(requirement))

    private fun exportDiagnostics() {
        try {
            exportDestination.launch("caffeinedose-diagnostics-${LocalDate.now()}.txt")
        } catch (e: ActivityNotFoundException) {
            diagnostics.warn("diagnostics: no document provider", e)
            toast(R.string.diagnostics_export_failed)
        }
    }
}
