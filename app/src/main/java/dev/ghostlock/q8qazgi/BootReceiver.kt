package dev.ghostlock.q8qazgi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if ((action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_LOCKED_BOOT_COMPLETED) &&
            AutoRootService.isAutoEnabled(context)
        ) {
            val i = Intent(context, AutoRootService::class.java).setAction(AutoRootService.ACTION_AUTO)
            try {
                context.startForegroundService(i)
            } catch (t: Throwable) {
                // BOOT_COMPLETED normally allows FGS start; ignore if restricted.
            }
        }
    }
}
