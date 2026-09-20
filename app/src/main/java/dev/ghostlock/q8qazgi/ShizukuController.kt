package dev.ghostlock.q8qazgi

import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.io.OutputStream
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

internal object ShizukuController {

    const val PERMISSION_REQUEST_CODE = 21330

    fun isRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (_: Throwable) {
        false
    }

    fun isGranted(): Boolean = try {
        isRunning() && Shizuku.checkSelfPermission() == 0
    } catch (_: Throwable) {
        false
    }

    @Throws(Exception::class)
    fun exec(cmd: Array<String>, env: Array<String>?, dir: String?): Process {
        val binder = Shizuku.getBinder() ?: throw IllegalStateException("Shizuku binder is not available")
        val remote = IShizukuService.Stub.asInterface(binder).newProcess(cmd, env, dir)
        return RemoteProcess(remote)
    }

    fun isKernelSuLoaded(): Boolean {
        if (!isGranted()) return false
        return try {
            val cmd = "(grep -qw kernelsu /proc/modules || [ -d /sys/module/kernelsu ])"
            exec(arrayOf("sh", "-c", cmd), null, null).waitFor() == 0
        } catch (_: Throwable) {
            false
        }
    }

    @Throws(Exception::class)
    fun writeFile(path: String, mode: String, input: InputStream) {
        val process = exec(arrayOf("sh", "-c", "cat > '$path' && chmod $mode '$path'"), null, null)
        try {
            val out: OutputStream = process.outputStream
            val buf = ByteArray(65536)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            out.flush()
            out.close()
        } finally {
            input.close()
        }
        val rc = process.waitFor()
        if (rc != 0) throw IllegalStateException("Failed to stage $path (exit $rc)")
    }

    private class RemoteProcess(private val remote: IRemoteProcess) : Process() {

        private var input: InputStream? = null
        private var output: OutputStream? = null
        private var error: InputStream? = null

        @Synchronized
        override fun getInputStream(): InputStream {
            var i = input
            if (i == null) {
                i = ParcelFileDescriptor.AutoCloseInputStream(remote.inputStream)
                input = i
            }
            return i
        }

        @Synchronized
        override fun getOutputStream(): OutputStream {
            var o = output
            if (o == null) {
                o = ParcelFileDescriptor.AutoCloseOutputStream(remote.outputStream)
                output = o
            }
            return o
        }

        @Synchronized
        override fun getErrorStream(): InputStream {
            var e = error
            if (e == null) {
                e = ParcelFileDescriptor.AutoCloseInputStream(remote.errorStream)
                error = e
            }
            return e
        }

        override fun waitFor(): Int = try {
            remote.waitFor()
        } catch (e: Exception) {
            throw InterruptedException(e.message)
        }

        override fun exitValue(): Int = try {
            remote.exitValue()
        } catch (e: Exception) {
            throw IllegalThreadStateException(e.message)
        }

        override fun destroy() {
            try {
                remote.destroy()
            } catch (_: Exception) {
            }
        }
    }
}
