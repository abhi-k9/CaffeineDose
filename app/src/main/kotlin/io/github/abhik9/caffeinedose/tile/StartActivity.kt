package io.github.abhik9.caffeinedose.tile

import android.app.Activity
import android.content.Context
import android.content.Intent
import io.github.abhik9.caffeinedose.awakeTimer
import io.github.abhik9.caffeinedose.core.StartResult
import io.github.abhik9.caffeinedose.system.reportBlocked
import io.github.abhik9.caffeinedose.system.settingsIntent
import io.github.abhik9.caffeinedose.system.startSettings

/**
 * Invisible activity starting the default timer, for the [AwakeTileService] when it is not allowed to start a
 * foreground service itself: a visible activity always is. Not exported, so other apps can't bypass the automation
 * setting through it.
 */
class StartActivity : Activity() {

    companion object {
        fun intent(context: Context): Intent = Intent(context, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onResume() {
        super.onResume()
        // Started once the activity is in the foreground.
        val result = awakeTimer().start()
        if (result is StartResult.Blocked) startSettings(settingsIntent(result.requirement))
        reportBlocked(result)
        finish()
    }
}
