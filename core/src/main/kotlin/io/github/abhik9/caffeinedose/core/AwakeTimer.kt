package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration

/** Outcome of an operation that (re)starts the timer. */
sealed interface StartResult {
    data class Started(val timer: Timer) : StartResult

    /** The timer has been stopped (a non positive duration was requested). */
    data object Stopped : StartResult

    /** Nothing changed: the timer can't run until [requirement] is resolved. */
    data class Blocked(val requirement: Requirement) : StartResult

    /**
     * Nothing changed: the system refused to keep the screen awake, typically because the request came from the
     * background. It can be retried from the foreground (e.g. from the app).
     */
    data object Refused : StartResult
}

/**
 * All the timer operations, shared by the notification actions, the Quick Settings tile, automation intents, the app
 * screen and the service holding the screen.
 *
 * Stateless and cheap to create: the state lives in the [keeper]. Operations are expected to run on a single thread
 * (the Android main thread), which serializes them.
 */
class AwakeTimer(
    private val keeper: ScreenKeeper,
    private val clock: DeviceClock,
    private val settings: () -> TimerSettings,
    /** Called after every change of the timer, e.g. to refresh the Quick Settings tile. */
    private val onChange: () -> Unit = {},
) {

    fun current(): Timer? = keeper.current()

    fun remaining(): Duration? = current()?.remaining(clock)

    fun missingRequirement(): Requirement? = if (!keeper.isAvailable()) Requirement.NOTIFICATIONS else null

    /**
     * Starts a timer of [duration] (capped to [MAX_TIMER_DURATION]), replacing the running one.
     * A non positive [duration] stops the timer.
     */
    fun start(duration: Duration = settings().initial): StartResult {
        if (!duration.isPositive()) {
            stop()
            return StartResult.Stopped
        }
        missingRequirement()?.let { return StartResult.Blocked(it) }
        val millis = duration.coerceAtMost(MAX_TIMER_DURATION).inWholeMilliseconds
        val timer = Timer(deadline = clock.elapsedMillis() + millis, endsAt = clock.wallMillis() + millis)
        if (!keeper.hold(timer)) return StartResult.Refused
        onChange()
        return StartResult.Started(timer)
    }

    fun stop() {
        keeper.release()
        onChange()
    }

    fun toggle(): StartResult = if (current() == null) {
        start()
    } else {
        stop()
        StartResult.Stopped
    }

    /**
     * Adds [delta] to the remaining time of the running timer.
     * @param mayEnd whether [delta] is allowed to end the timer. Otherwise such a [delta] is ignored, and the timer is
     * only re-displayed (to refresh its available actions).
     * @return `null` when there is no running timer, or it is already ending.
     */
    fun adjust(delta: Duration, mayEnd: Boolean = true): StartResult? {
        val remaining = remaining() ?: return null
        // The deadline has been reached, the timer is being released.
        if (!remaining.isPositive()) return null
        val next = (remaining + delta).coerceAtMost(MAX_TIMER_DURATION)
        return start(if (next.isPositive() || mayEnd) next else remaining)
    }

    fun extend(): StartResult? = adjust(settings().increment)

    /**
     * Displayed actions can become stale as time goes by: a tap on "−" never turns the screen off unexpectedly, only
     * "Stop" does.
     */
    fun reduce(): StartResult? = adjust(-settings().decrement, mayEnd = false)

    /** Re-displays the running timer, e.g. after a settings change. */
    fun refresh(): StartResult? = adjust(Duration.ZERO)

    /**
     * Ends the timer whose deadline is [deadline]: it expired, its notification was dismissed, or the screen was turned
     * off. A stale signal never ends a newer timer.
     * @return whether the timer has been ended.
     */
    fun end(deadline: Long): Boolean {
        if (current()?.deadline != deadline) return false
        stop()
        return true
    }

    /** Ends the running timer once its deadline is reached. @return whether it has been ended. */
    fun expireIfDue(): Boolean {
        val timer = current() ?: return false
        return !timer.remaining(clock).isPositive() && end(timer.deadline)
    }

    fun execute(command: TimerCommand): StartResult? = when (command) {
        is TimerCommand.Start -> start(command.duration ?: settings().initial)
        is TimerCommand.Adjust -> adjust(command.delta)
        TimerCommand.Extend -> extend()
        TimerCommand.Reduce -> reduce()
        TimerCommand.Toggle -> toggle()
        TimerCommand.Stop -> {
            stop()
            StartResult.Stopped
        }
    }
}

/** Operations available to automation tools. */
sealed interface TimerCommand {
    /** Starts a timer of [duration], or of the default duration when `null`. */
    data class Start(val duration: Duration?) : TimerCommand

    data class Adjust(val delta: Duration) : TimerCommand

    data object Extend : TimerCommand

    data object Reduce : TimerCommand

    data object Toggle : TimerCommand

    data object Stop : TimerCommand
}
