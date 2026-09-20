package dev.ghostlock.q8qazgi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat

class AutoRootService : Service() {

    @Volatile
    private var cancelled = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Preparing...", "waiting for Shizuku"))
        if (!isAutoEnabled(this) || !shouldRunThisBoot(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        markRan()
        cancelled = false
        Thread({ work() }, "autoroot").start()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelled = true
        super.onDestroy()
    }

    private fun work() {
        val runLog = ExploitRunner.RunLog.start(this)
        runLog?.append("[auto-root] boot marker=${bootMarker()}")

        // If KernelSU is already loaded (detectable without Shizuku) -> never re-root.
        if (RootChecker.detect(force = true)) {
            append(runLog, "[auto-root] already rooted — skip")
            notifyStatus("이미 루팅됨 / Already rooted", "KernelSU active — skipping auto-root")
            runLog?.finish("Skipped (already rooted)")
            sleep(1500L)
            stopSelf()
            return
        }

        val deadline = SystemClock.elapsedRealtime() + SHIZUKU_WAIT_MS
        while (SystemClock.elapsedRealtime() < deadline && !cancelled) {
            if (!ShizukuController.isRunning() || !ShizukuController.isGranted()) {
                notifyStatus("루팅 확인 중 / Checking", "waiting for Shizuku (skips if already rooted)")
                sleep(3000L)
            } else {
                if (ShizukuController.isKernelSuLoaded()) {
                    append(runLog, "[auto-root] KernelSU already active (via Shizuku) — skip")
                    notifyStatus("이미 루팅됨 / Already rooted", "KernelSU active — skipping auto-root")
                    runLog?.finish("Skipped (already rooted)")
                    sleep(2000L)
                    stopSelf()
                    return
                }
                append(runLog, "[auto-root] Shizuku ready, running chain")
                notifyStatus("Rooting...", "don't touch the phone")
                val listener = object : ExploitRunner.Listener {
                    override fun onLog(msg: String) = append(runLog, msg)
                    override fun onProgress(msg: String) = notifyStatus("Rooting...", msg)
                    override fun isCancelled(): Boolean = cancelled
                    override fun onFinished(success: Boolean) {
                        runLog?.finish(if (success) "Completed" else "Failed")
                        notifyStatus(
                            if (success) "Rooted — KernelSU active" else "Run failed — reboot and try again",
                            if (success) "late-load succeeded" else "check the app log"
                        )
                    }
                }
                append(runLog, "[auto-root] done success=${ExploitRunner.run(this, Targets.current(), listener)}")
                sleep(4000L)
                stopSelf()
                return
            }
        }
        append(runLog, "[auto-root] Shizuku not ready (start it and grant permission)")
        notifyStatus("Auto-root skipped", "Shizuku not ready (start it and grant permission)")
        runLog?.finish("Failed (no Shizuku)")
        stopSelf()
    }

    private fun append(runLog: ExploitRunner.RunLog?, line: String) {
        runLog?.append(line)
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
        }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "GhostLock", NotificationManager.IMPORTANCE_HIGH))
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun notifyStatus(title: String, text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(title, text))
    }

    companion object {
        const val ACTION_AUTO = "dev.ghostlock.q8qazgi.AUTO_ROOT"
        const val PREFS = "ghostlock"
        const val KEY_AUTO = "auto_root_on_boot"
        const val KEY_LAST_BOOT = "last_boot_marker"
        private const val CHANNEL = "ghostlock"
        private const val NOTIF_ID = 71
        private const val SHIZUKU_WAIT_MS = 300_000L

        private fun bootMarker(): Long = System.currentTimeMillis() - SystemClock.elapsedRealtime()

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, 0)

        fun isAutoEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, false)

        fun setAutoEnabled(context: Context, enabled: Boolean) {
            prefs(context).edit().putBoolean(KEY_AUTO, enabled).apply()
        }

        fun shouldRunThisBoot(context: Context): Boolean =
            prefs(context).getLong(KEY_LAST_BOOT, -1L) != bootMarker()
    }

    private fun markRan() {
        prefs(this).edit().putLong(KEY_LAST_BOOT, bootMarker()).apply()
    }
}
