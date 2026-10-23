package io.github.abhik9.caffeinedose.core

/** The device's power supply. */
interface PowerSource {
    /** Whether a charger is plugged in. */
    fun isPluggedIn(): Boolean

    /** Battery level, in percent; `null` when unknown. */
    fun batteryLevel(): Int?

    fun isBatterySaverOn(): Boolean

    companion object {
        /** Always plugged in: nothing ever limits a timer. */
        val MAINS = object : PowerSource {
            override fun isPluggedIn() = true
            override fun batteryLevel(): Int? = null
            override fun isBatterySaverOn() = false
        }
    }
}

/** Why the power supply doesn't let the screen be kept on, or no longer. */
enum class PowerLimit {
    /** No charger is plugged in, for a timer that lasts until it is unplugged. */
    UNPLUGGED,

    /** The battery is at or below the level chosen in the settings. */
    LOW_BATTERY,

    /** Battery Saver is on, and the settings stop the timer then. */
    BATTERY_SAVER,
}

/** When the battery stops the timer: never, by default. */
data class BatteryLimits(
    /** Battery level at or below which the screen isn't kept on, in percent; `null` when disabled. */
    val stopAtPercent: Int? = null,
    val stopOnBatterySaver: Boolean = false,
) {
    companion object {
        /** The levels that can be chosen, in percent. */
        val PERCENT_RANGE = 5..50
        const val DEFAULT_PERCENT = 15
    }

    /** What prevents keeping the screen on now, `null` when nothing does. Never while plugged in: the battery isn't drained. */
    fun limit(power: PowerSource): PowerLimit? {
        if (power.isPluggedIn()) return null
        val level = power.batteryLevel()
        return when {
            stopAtPercent != null && level != null && level <= stopAtPercent -> PowerLimit.LOW_BATTERY
            stopOnBatterySaver && power.isBatterySaverOn() -> PowerLimit.BATTERY_SAVER
            else -> null
        }
    }
}
