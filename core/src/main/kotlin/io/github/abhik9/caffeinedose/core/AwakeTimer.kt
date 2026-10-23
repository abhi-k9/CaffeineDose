package io.github.abhik9.caffeinedose.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** Outcome of an operation that (re)starts the timer. */
sealed interface StartResult {
    data class Started(val timer: Timer) : StartResult

    /** The timer has been stopped, e.g. a non positive duration was requested. */
    data object Stopped : StartResult

    /** Nothing changed: the timer can't run until [requirement] is resolved. */
    data class Blocked(val requirement: Requirement) : StartResult

    /** Nothing changed: the power supply doesn't let the screen be kept on, see [BatteryLimits]. */
    data class Declined(val limit: PowerLimit) : StartResult

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
    private val power: PowerSource = PowerSource.MAINS,
    /** Called after every change of the timer, including when it ends: e.g. to refresh the tile and the widgets. */
    private val onChange: () -> Unit = {},
    private val log: EventLog = EventLog.NONE,
) {

    fun current(): Timer? = keeper.current()

    fun remaining(): Duration? = current()?.remaining(clock)

    fun missingRequirement(): Requirement? = if (!keeper.isAvailable()) Requirement.NOTIFICATIONS else null

    /**
     * Starts a timer of [duration] (capped to [MAX_TIMER_DURATION]), replacing the running one.
     * A [duration] under a millisecond (e.g. zero or negative) stops the timer.
     */
    fun start(duration: Duration = settings().initial): StartResult = doStart(duration).also { result ->
        log.record { "start($duration): $result" }
    }

    /**
     * Keeps the screen on until the charger is unplugged, replacing the running timer. Declined when no charger is
     * plugged in. Like any timer, it never lasts more than [MAX_TIMER_DURATION].
     */
    fun startUntilUnplugged(): StartResult = doStart(MAX_TIMER_DURATION, untilUnplugged = true).also { result ->
        log.record { "startUntilUnplugged: $result" }
    }

    private fun doStart(duration: Duration, untilUnplugged: Boolean = false): StartResult {
        // In whole milliseconds, the resolution of the clocks: a shorter timer would have ended already.
        val millis = duration.coerceAtMost(MAX_TIMER_DURATION).inWholeMilliseconds
        if (millis <= 0) {
            stop()
            return StartResult.Stopped
        }
        missingRequirement()?.let { return StartResult.Blocked(it) }
        if (untilUnplugged && !power.isPluggedIn()) return StartResult.Declined(PowerLimit.UNPLUGGED)
        settings().battery.limit(power)?.let { return StartResult.Declined(it) }
        return hold(Timer(deadline = clock.elapsedMillis() + millis, endsAt = clock.wallMillis() + millis, untilUnplugged))
    }

    private fun hold(timer: Timer): StartResult {
        if (!keeper.hold(timer)) return StartResult.Refused
        onChange()
        return StartResult.Started(timer)
    }

    /**
     * Ends the running timer when the power supply no longer lets the screen be kept on, e.g. the battery got low.
     * @return why it has been ended, `null` when it goes on.
     */
    fun checkPower(): PowerLimit? {
        val timer = current() ?: return null
        val unplugged = timer.untilUnplugged && !power.isPluggedIn()
        val limit = (if (unplugged) PowerLimit.UNPLUGGED else settings().battery.limit(power)) ?: return null
        log.record { "power: $limit" }
        stop()
        return limit
    }

    fun stop() {
        log.record { "stop" }
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
     * What a tap on a shortcut (Quick Settings tile, home screen widget) does: toggles the default timer, or steps
     * through [TimerSettings.cycle] when set, see [nextCycleStep].
     */
    fun tap(): StartResult {
        val steps = settings().cycle
        if (steps.isEmpty()) return toggle()
        val running = current()
        val next = nextCycleStep(steps, running, running?.remaining(clock) ?: Duration.ZERO, power.isPluggedIn())
        log.record { "tap: next step $next" }
        return when (next) {
            null -> {
                stop()
                StartResult.Stopped
            }

            is CycleStep.Minutes -> start(next.minutes.minutes)

            CycleStep.WhileCharging -> startUntilUnplugged()
        }
    }

    /**
     * Adds [delta] to the remaining time of the running timer.
     *
     * A timer past its deadline that has not been released yet (its check is late, e.g. after deep sleep) counts as
     * having no time left: it can still be extended, from now.
     * @param mayEnd whether [delta] is allowed to end the timer. Otherwise such a [delta] is ignored, and the timer is
     * only re-displayed (to refresh its available actions).
     * A timer that lasts until the charger is unplugged has no time to adjust: it is only re-displayed.
     * @return `null` when there is no running timer.
     */
    fun adjust(delta: Duration, mayEnd: Boolean = true): StartResult? {
        val current = current() ?: return null
        if (current.untilUnplugged) return hold(current)
        val left = current.remaining(clock)
        log.record { "adjust($delta, mayEnd=$mayEnd): remaining=$left" }
        val remaining = left.coerceAtLeast(Duration.ZERO)
        val next = (remaining + delta).coerceAtMost(MAX_TIMER_DURATION)
        return start(if (next.isPositive() || mayEnd) next else remaining)
    }

    fun extend(): StartResult? = adjust(settings().increment)

    /**
     * Displayed actions can become stale as time goes by: a tap on "−" never ends a timer before its deadline, only
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
        val current = current()
        log.record { "end($deadline) at ${clock.elapsedMillis()}: current=$current" }
        if (current?.deadline != deadline) return false
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
        TimerCommand.StartUntilUnplugged -> startUntilUnplugged()
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

    /** Keeps the screen on until the charger is unplugged. */
    data object StartUntilUnplugged : TimerCommand

    data class Adjust(val delta: Duration) : TimerCommand

    data object Extend : TimerCommand

    data object Reduce : TimerCommand

    data object Toggle : TimerCommand

    data object Stop : TimerCommand
}
