package dev.ghostlock.q8qazgi

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit

object RootChecker {

    private const val MIN_INTERVAL_MS = 8_000L

    @Volatile
    private var lastAttempt = 0L

    @Volatile
    private var cached = false

    @Synchronized
    fun detect(force: Boolean): Boolean {
        if (!force && cached) return true
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastAttempt < MIN_INTERVAL_MS) return cached
        lastAttempt = now

        val result = readProcModules() || sysModule() || grepProcModules() || viaSu()
        Log.i("GhostLock", "rootcheck -> $result")
        cached = result
        return result
    }

    // Direct read (works if SELinux allows untrusted_app to read proc_modules).
    private fun readProcModules(): Boolean {
        return try {
            File("/proc/modules").readText().contains("kernelsu")
        } catch (_: Throwable) {
            false
        }
    }

    private fun sysModule(): Boolean {
        return try {
            File("/sys/module/kernelsu").exists()
        } catch (_: Throwable) {
            false
        }
    }

    private fun grepProcModules(): Boolean {
        return try {
            val process = ProcessBuilder("sh", "-c", "grep -qw kernelsu /proc/modules").start()
            val ok = process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0
            if (!ok) process.destroyForcibly()
            ok
        } catch (_: Throwable) {
            false
        }
    }

    private fun viaSu(): Boolean {
        val process = try {
            ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start()
        } catch (_: Throwable) {
            return false
        }
        val out = StringBuilder()
        val reader = Thread {
            try {
                process.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') }
            } catch (_: Throwable) {
            }
        }
        reader.isDaemon = true
        reader.start()
        return try {
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return false
            }
            process.exitValue() == 0 && out.contains("uid=0")
        } catch (_: Throwable) {
            process.destroyForcibly()
            false
        }
    }
}
