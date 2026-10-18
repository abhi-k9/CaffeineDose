package io.github.abhik9.caffeinedose.core

/**
 * What a shortcut to the timer, the Quick Settings tile or the home screen widget, does once a tap toggled it. Both
 * react the same way to every outcome.
 */
sealed interface ShortcutReaction {
    /** Shows [timer], or the idle state when `null`. */
    data class Show(val timer: Timer?) : ShortcutReaction

    /** Tells the user that [requirement] is missing, and opens the settings where it can be resolved. */
    data class Resolve(val requirement: Requirement) : ShortcutReaction

    /** The system refused to start from the background: start again from an activity, in the foreground. */
    data object StartFromForeground : ShortcutReaction
}

fun StartResult.shortcutReaction(): ShortcutReaction = when (this) {
    is StartResult.Started -> ShortcutReaction.Show(timer)
    StartResult.Stopped -> ShortcutReaction.Show(null)
    is StartResult.Blocked -> ShortcutReaction.Resolve(requirement)
    StartResult.Refused -> ShortcutReaction.StartFromForeground
}
