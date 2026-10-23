package io.github.abhik9.caffeinedose.awake

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import io.github.abhik9.caffeinedose.core.PowerSource

/** The device's [PowerSource]: the latest battery state, a sticky broadcast read without registering a receiver. */
internal class SystemPower(private val context: Context) : PowerSource {

    private fun battery(): Intent? = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

    override fun isPluggedIn() = (battery()?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

    override fun batteryLevel(): Int? {
        val battery = battery() ?: return null
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    override fun isBatterySaverOn() = context.getSystemService(PowerManager::class.java).isPowerSaveMode
}
