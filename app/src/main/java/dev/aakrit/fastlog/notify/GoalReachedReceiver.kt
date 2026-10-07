package dev.aakrit.fastlog.notify

import android.app.Notification
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.aakrit.fastlog.R
import dev.aakrit.fastlog.data.FastingRepository
import dev.aakrit.fastlog.model.Protocol

/** Fires when a running fast hits its goal. */
class GoalReachedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GOAL_REACHED) return

        val active = FastingRepository.get(context).state.value.active ?: return

        // Flip the ongoing indicator over to its "past goal" wording.
        FastNotifier.showActiveFast(context, active)

        if (!FastNotifier.hasPermission(context)) return
        FastNotifier.ensureChannels(context)

        val alert = Notification.Builder(context, FastNotifier.GOAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Goal reached")
            .setContentText(
                "You've completed your ${Protocol.formatHours(active.goalHours)} fast."
            )
            .setAutoCancel(true)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(GOAL_NOTIFICATION_ID, alert) }
    }

    companion object {
        const val ACTION_GOAL_REACHED = "dev.aakrit.fastlog.GOAL_REACHED"
        private const val GOAL_NOTIFICATION_ID = 1002
    }
}
