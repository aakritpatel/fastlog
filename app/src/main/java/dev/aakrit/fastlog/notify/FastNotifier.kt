package dev.aakrit.fastlog.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.aakrit.fastlog.MainActivity
import dev.aakrit.fastlog.R
import dev.aakrit.fastlog.model.ActiveFast
import dev.aakrit.fastlog.model.Protocol

/**
 * Owns the ongoing "you are fasting" notification.
 *
 * Elapsed time is rendered by a Chronometer widget the system ticks, rather than
 * by us re-posting every second. That keeps the indicator live and prominent
 * with no foreground service and no periodic wakeup.
 */
object FastNotifier {

    const val ACTIVE_CHANNEL_ID = "active_fast"
    const val GOAL_CHANNEL_ID = "goal_reached"
    const val ACTIVE_NOTIFICATION_ID = 1001

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val active = NotificationChannel(
            ACTIVE_CHANNEL_ID,
            "Active fast",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows your running fast in the notification shade"
            setShowBadge(false)
            enableVibration(false)
        }

        val goal = NotificationChannel(
            GOAL_CHANNEL_ID,
            "Goal reached",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts you when a fast reaches its goal"
        }

        manager.createNotificationChannel(active)
        manager.createNotificationChannel(goal)
    }

    fun hasPermission(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    fun showActiveFast(
        context: Context,
        fast: ActiveFast,
        nowMs: Long = System.currentTimeMillis(),
    ) {
        ensureChannels(context)
        if (!hasPermission(context)) return

        val protocol = Protocol.resolve(fast.protocolId, fast.goalHours)
        val goalMs = fast.startEpochMs + (fast.goalHours * 3_600_000.0).toLong()
        val elapsedMs = (nowMs - fast.startEpochMs).coerceAtLeast(0)
        val pastGoal = nowMs >= goalMs

        val content = RemoteViews(context.packageName, R.layout.notification_fast).apply {
            // Chronometer counts from elapsedRealtime, not wall clock, so the
            // base has to be shifted back by however long the fast has run.
            setChronometer(
                R.id.fast_elapsed,
                SystemClock.elapsedRealtime() - elapsedMs,
                null,
                true,
            )
            setTextViewText(
                R.id.fast_remaining,
                if (pastGoal) {
                    val over = nowMs - goalMs
                    if (over < 60_000) "goal reached"
                    else "${formatSpan(over)} past your goal"
                } else {
                    "${formatSpan(goalMs - nowMs)} left"
                },
            )
            setTextViewText(
                R.id.fast_plan,
                "${protocol.label} · ${Protocol.formatHours(fast.goalHours)} goal",
            )
        }

        // Tapping the body opens the app as usual.
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        // "End fast" opens the app and finishes the fast there, so the user sees
        // the summary. Android 12+ forbids a notification action from bouncing
        // through a receiver to start an activity, so this targets it directly.
        val endIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_END_FAST)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = Notification.Builder(context, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(Notification.DecoratedCustomViewStyle())
            .setCustomContentView(content)
            .setCustomBigContentView(content)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(
                Notification.Action.Builder(
                    null as android.graphics.drawable.Icon?,
                    "End fast",
                    endIntent,
                ).build()
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify(ACTIVE_NOTIFICATION_ID, notification)
        }
    }

    fun cancel(context: Context) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(ACTIVE_NOTIFICATION_ID)
        }
    }

    /** Compact "3h 12m" / "45m" for the secondary line. */
    private fun formatSpan(ms: Long): String {
        val totalMinutes = ms.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
