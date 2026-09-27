package io.github.abhik9.caffeinedose.core

/**
 * Keeps the screen awake until the deadline of a timer (a foreground service holding a screen wake lock on Android).
 *
 * It is also the single source of truth of the timer: a timer only exists while the screen is held, and the hold ends
 * with the process that owns it. Nothing has to be persisted, or cleaned up after a crash or a reboot.
 */
interface ScreenKeeper {
    /** Whether a timer can be shown to the user at all (e.g. notifications are enabled). */
    fun isAvailable(): Boolean

    /** @return the running timer, or `null` when the screen is not held. */
    fun current(): Timer?

    /**
     * Keeps the screen awake until [Timer.deadline], replacing the current timer.
     * @return `false` when the system refused it, e.g. when started from the background.
     */
    fun hold(timer: Timer): Boolean

    /** Lets the screen turn off normally again. */
    fun release()
}

/** Something the user must allow before a timer can run, in the order they must be resolved. */
enum class Requirement { NOTIFICATIONS, }
