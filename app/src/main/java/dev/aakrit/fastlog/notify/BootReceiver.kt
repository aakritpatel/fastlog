package dev.aakrit.fastlog.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.aakrit.fastlog.data.FastingRepository

/**
 * Notifications and alarms do not survive a reboot, but the fast itself does:
 * it is stored on disk as an absolute start timestamp. This re-creates the
 * indicator and the goal alarm from that stored state.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object { const val TAG = "FastLogBoot" }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val app = context.applicationContext
        val active = FastingRepository.get(app).state.value.active
        if (active == null) return

        FastNotifier.showActiveFast(app, active)
        GoalAlarmScheduler.schedule(app, active)
        Log.i(TAG, "restored fasting indicator after boot")
    }
}
