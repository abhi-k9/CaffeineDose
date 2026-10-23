package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration

/** A step of "Tap again for longer": what each tap on a shortcut (Quick Settings tile, home screen widget) starts. */
sealed interface CycleStep {
    data class Minutes(val minutes: Int) : CycleStep

    /** Until the charger is unplugged: the longest step. */
    data object WhileCharging : CycleStep

    companion object {
        /** The steps that can be chosen, from the shortest. */
        val CHOICES: List<CycleStep> = listOf(5, 15, 30, 60, 120, 240, 480).map(::Minutes) + WhileCharging
        val DEFAULT: List<CycleStep> = listOf(Minutes(15), Minutes(60), Minutes(240))

        private const val WHILE_CHARGING = "charging"

        /** Steps stored as text, e.g. `15,60,charging`. */
        fun encode(steps: List<CycleStep>): String = steps.joinToString(",") { step ->
            when (step) {
                is Minutes -> step.minutes.toString()
                WhileCharging -> WHILE_CHARGING
            }
        }

        /** Reads [encode]d steps: anything but a [CHOICES] is dropped, and without any, the [DEFAULT] steps are used. */
        fun decode(text: String?): List<CycleStep> = text.orEmpty().split(",")
            .mapNotNull { token -> if (token == WHILE_CHARGING) WhileCharging else token.toIntOrNull()?.let(::Minutes) }
            .filter { it in CHOICES }
            .ordered()
            .ifEmpty { DEFAULT }
    }
}

/** From the shortest step to the longest, without duplicates. */
fun List<CycleStep>.ordered(): List<CycleStep> = distinct().sortedBy { step ->
    when (step) {
        is CycleStep.Minutes -> step.minutes
        CycleStep.WhileCharging -> Int.MAX_VALUE
    }
}

/**
 * What a tap starts when stepping through [steps]: the first step without [running] timer, then the first step longer
 * than its [remaining] time rounded up to the minute. `null` past the longest step: the tap stops the timer.
 *
 * Keeping the screen on while charging is the longest step: it is skipped without a charger once a timer runs, but
 * started (and declined, which tells why) when it is the only one.
 */
fun nextCycleStep(steps: List<CycleStep>, running: Timer?, remaining: Duration, pluggedIn: Boolean): CycleStep? {
    val ordered = steps.ordered()
    if (running == null) return ordered.firstOrNull()
    if (running.untilUnplugged) return null
    val left = ceilMinutes(remaining)
    return ordered.firstOrNull { step ->
        when (step) {
            is CycleStep.Minutes -> step.minutes > left
            CycleStep.WhileCharging -> pluggedIn
        }
    }
}
