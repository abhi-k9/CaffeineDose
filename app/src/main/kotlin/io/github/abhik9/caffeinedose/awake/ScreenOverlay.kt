package io.github.abhik9.caffeinedose.awake

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
import android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
import android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
import android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

/**
 * An invisible 1×1 window over other apps, keeping the screen on with [FLAG_KEEP_SCREEN_ON] while it is shown.
 *
 * Some devices (e.g. Samsung phones with Android 16) ignore the screen wake lock of an app that isn't visible, but the
 * window manager honors [FLAG_KEEP_SCREEN_ON] on any visible window. Showing a window over other apps requires the
 * optional "Display over other apps" permission: without it, nothing is shown. Like the wake lock, the window is gone
 * with the process.
 */
internal class ScreenOverlay(private val context: Context) {

    private companion object {
        const val TAG = "ScreenOverlay"
    }

    private val windows = context.getSystemService(WindowManager::class.java)
    private var view: View? = null

    val isShown: Boolean
        get() = view != null

    /**
     * Shows the window when allowed, hides it otherwise: the permission can be granted or revoked at any time.
     * @return whether the window is shown.
     */
    fun sync(): Boolean {
        if (Settings.canDrawOverlays(context)) show() else hide()
        return isShown
    }

    private fun show() {
        if (view != null) return
        val params = WindowManager.LayoutParams(
            1,
            1,
            TYPE_APPLICATION_OVERLAY,
            // Never takes the focus nor the touches: the apps below are used as usual.
            FLAG_NOT_FOCUSABLE or FLAG_NOT_TOUCHABLE or FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            title = "CaffeineDose"
        }
        // No background: fully transparent.
        val overlay = View(context)
        try {
            windows.addView(overlay, params)
            view = overlay
        } catch (e: RuntimeException) {
            // BadTokenException when the permission has just been revoked, or SecurityException on some devices.
            Log.w(TAG, "Can't show the overlay", e)
        }
    }

    fun hide() {
        val overlay = view ?: return
        view = null
        try {
            windows.removeView(overlay)
        } catch (e: IllegalArgumentException) {
            // Already removed by the system.
            Log.w(TAG, "Can't hide the overlay", e)
        }
    }
}
