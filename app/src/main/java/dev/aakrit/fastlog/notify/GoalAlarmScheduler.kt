package dev.aakrit.fastlog.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dev.aakrit.fastlog.model.ActiveFast

/**
 * Schedules a single nudge at the moment a fast reaches its goal.
 *
 * Deliberately an inexact alarm: exact alarms need the restricted
 * SCHEDULE_EXACT_ALARM permission, and "your goal is up" does not need
 * second-level precision.
 */
object GoalAlarmScheduler {

    private const val REQUEST_CODE = 2001

    private fun intent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, GoalReachedReceiver::class.java)
                .setAction(GoalReachedReceiver.ACTION_GOAL_REACHED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(context: Context, fast: ActiveFast) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val goalAtMs = fast.startEpochMs + (fast.goalHours * 3_600_000.0).toLong()
        // Already past the goal (e.g. restored after a long reboot): nothing to schedule.
        if (goalAtMs <= System.currentTimeMillis()) return
        runCatching {
            manager.set(AlarmManager.RTC_WAKEUP, goalAtMs, intent(context))
        }
    }

    fun cancel(context: Context) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        runCatching { manager.cancel(intent(context)) }
    }
}
