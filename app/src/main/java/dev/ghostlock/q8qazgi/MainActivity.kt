package dev.ghostlock.q8qazgi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.CircularProgressIndicator
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private lateinit var statusView: TextView
    private lateinit var runButton: MaterialButton
    private lateinit var grantButton: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var autoSwitch: MaterialSwitch
    private lateinit var copyLogButton: MaterialButton
    private lateinit var shareLogButton: MaterialButton
    private lateinit var clearLogButton: MaterialButton
    private lateinit var targetCard: MaterialCardView
    private lateinit var targetValue: TextView

    private lateinit var heroState: TextView
    private lateinit var heroCaption: TextView
    private lateinit var heroTarget: TextView
    private lateinit var heroShizuku: TextView
    private lateinit var heroKsu: TextView
    private lateinit var statusRing: CircularProgressIndicator

    @Volatile
    private var cancelled = false

    @Volatile
    private var rooted = false

    @Volatile
    private var ksuDetected = false

    private val rootCheckBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile
    private var busy = false

    private var runLog: ExploitRunner.RunLog? = null
    private val ui = Handler(Looper.getMainLooper())

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        ui.post {
            updateStatus()
            refreshRootStatus(force = true)
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        ui.post { updateStatus() }
    }

    private val permListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == ShizukuController.PERMISSION_REQUEST_CODE) {
            if (grantResult == 0) {
                log("[+] Shizuku permission granted — now tap \"RUN EXPLOIT\"")
            } else {
                log("[!] Shizuku permission denied")
            }
            updateStatus()
            refreshRootStatus(force = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ExploitRunner.recoverInterrupted(this)

        logView = findViewById(R.id.logView)
        logScroll = findViewById(R.id.logScroll)
        statusView = findViewById(R.id.statusView)
        runButton = findViewById(R.id.runButton)
        grantButton = findViewById(R.id.grantButton)
        stopButton = findViewById(R.id.stopButton)
        autoSwitch = findViewById(R.id.autoSwitch)
        copyLogButton = findViewById(R.id.copyLogButton)
        shareLogButton = findViewById(R.id.shareLogButton)
        clearLogButton = findViewById(R.id.clearLogButton)
        targetCard = findViewById(R.id.targetCard)
        targetValue = findViewById(R.id.targetValue)

        heroState = findViewById(R.id.heroState)
        heroCaption = findViewById(R.id.heroCaption)
        heroTarget = findViewById(R.id.heroTarget)
        heroShizuku = findViewById(R.id.heroShizuku)
        heroKsu = findViewById(R.id.heroKsu)
        statusRing = findViewById(R.id.statusRing)

        updateTargetValue()

        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        grantButton.setOnClickListener { requestShizukuPermission() }
        runButton.setOnClickListener { onRun() }
        stopButton.setOnClickListener {
            cancelled = true
            log("[*] Stop requested")
        }
        copyLogButton.setOnClickListener { copyLog() }
        shareLogButton.setOnClickListener { shareLog() }
        clearLogButton.setOnClickListener {
            logView.text = ""
            log("[*] Log cleared")
        }
        targetCard.setOnClickListener { showTargetDialog() }

        autoSwitch.isChecked = AutoRootService.isAutoEnabled(this)
        autoSwitch.setOnCheckedChangeListener { _, checked ->
            AutoRootService.setAutoEnabled(this, checked)
            log(if (checked) "[*] Auto-root on boot: ON (re-applies root after each reboot)" else "[*] Auto-root on boot: OFF")
        }

        findViewById<MaterialButton>(R.id.navRefresh).setOnClickListener {
            log("[*] Refreshing status…")
            refreshRootStatus(force = true)
        }
        findViewById<MaterialButton>(R.id.navRun).setOnClickListener { onRun() }
        findViewById<MaterialButton>(R.id.navCopy).setOnClickListener { copyLog() }
        findViewById<MaterialButton>(R.id.navShare).setOnClickListener { shareLog() }

        updateStatus()
        log("[*] GhostLock ready — select target and run.")
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        refreshRootStatus(force = true)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelled = true
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    private fun targetItems(): Array<String> {
        val items = ArrayList<String>()
        items.add(getString(R.string.target_auto))
        Targets.ALL.forEach { items.add(it.label) }
        return items.toTypedArray()
    }

    private fun showTargetDialog() {
        val items = targetItems()
        var checked = 0
        Targets.getOverride()?.let { o ->
            for (i in Targets.ALL.indices) if (Targets.ALL[i].id == o) checked = i + 1
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_target_title)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                Targets.setOverride(if (which == 0) null else Targets.ALL[which - 1].id)
                updateTargetValue()
                log("[*] Target override: " + (Targets.getOverride() ?: "auto"))
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateTargetValue() {
        targetValue.text = if (Targets.getOverride() == null) {
            getString(R.string.target_auto)
        } else {
            Targets.current().label
        }
    }

    private fun updateStatus() {
        ui.post {
            val running = ShizukuController.isRunning()
            val granted = running && ShizukuController.isGranted()
            val detected = Targets.detected()

            val isRooted = rooted || ksuDetected

            statusView.text = when {
                isRooted -> getString(R.string.status_rooted)
                !running -> getString(R.string.status_shizuku_off)
                !granted -> getString(R.string.status_shizuku_noperm)
                else -> getString(R.string.status_shizuku_ready)
            }

            heroTarget.text = detected?.id ?: Targets.current().id
            heroShizuku.text = if (granted) getString(R.string.on) else getString(R.string.off)
            heroKsu.text = if (isRooted) getString(R.string.hero_state_rooted) else getString(R.string.none)

            when {
                busy -> setHero(
                    getString(R.string.hero_state_running),
                    getString(R.string.hero_caption_running),
                    indeterminate = true
                )
                isRooted -> setHero(
                    getString(R.string.hero_state_rooted),
                    getString(R.string.hero_caption_rooted),
                    progress = 100,
                    colorRes = R.color.gl_success
                )
                !running -> {
                    statusView.text = getString(R.string.status_shizuku_off)
                    setHero(
                        getString(R.string.hero_state_setup),
                        getString(R.string.hero_caption_setup),
                        progress = 0,
                        colorRes = R.color.gl_on_surface_variant
                    )
                }
                !granted -> {
                    statusView.text = getString(R.string.status_shizuku_noperm)
                    setHero(
                        getString(R.string.hero_state_ready),
                        getString(R.string.status_shizuku_noperm),
                        progress = 50,
                        colorRes = R.color.gl_warning
                    )
                }
                else -> {
                    statusView.text = getString(R.string.status_shizuku_ready)
                    setHero(
                        getString(R.string.hero_state_ready),
                        getString(R.string.status_shizuku_ready),
                        progress = 100,
                        colorRes = R.color.gl_accent
                    )
                }
            }

            grantButton.isEnabled = running && !granted
            runButton.isEnabled = granted && !busy && !isRooted
            stopButton.isEnabled = busy
        }
    }

    private fun setHero(
        state: String,
        caption: String,
        progress: Int = 0,
        indeterminate: Boolean = false,
        colorRes: Int = R.color.gl_accent
    ) {
        heroState.text = state
        heroCaption.text = caption
        statusRing.isIndeterminate = indeterminate
        statusRing.setIndicatorColor(ContextCompat.getColor(this, colorRes))
        if (!indeterminate) statusRing.setProgressCompat(progress, true)
    }

    private fun refreshRootStatus(force: Boolean = false) {
        if (!rootCheckBusy.compareAndSet(false, true)) return
        Thread({
            val root = if (ShizukuController.isRunning() && ShizukuController.isGranted()) {
                ShizukuController.isKernelSuLoaded() || RootChecker.detect(force)
            } else {
                RootChecker.detect(force)
            }
            ksuDetected = root
            rootCheckBusy.set(false)
            ui.post { updateStatus() }
        }, "root-check").start()
    }

    private fun requestShizukuPermission() {
        if (!ShizukuController.isRunning()) {
            log("[!] Shizuku is not running. Start Shizuku first, then tap Grant.")
            updateStatus()
            return
        }
        if (ShizukuController.isGranted()) {
            log("[*] Shizuku permission already granted")
            updateStatus()
            return
        }
        log("[*] Requesting Shizuku permission — approve the dialog.")
        try {
            Shizuku.requestPermission(ShizukuController.PERMISSION_REQUEST_CODE)
        } catch (t: Throwable) {
            log("[!] requestPermission failed: ${t.message}")
        }
    }

    private fun isRootedNow(): Boolean = rooted || ksuDetected

    private fun onRun() {
        if (!ShizukuController.isRunning() || !ShizukuController.isGranted()) {
            log("[!] Shizuku permission is required. Tap \"GRANT PERMISSION\" first.")
            requestShizukuPermission()
            return
        }
        if (ExploitRunner.isRunning()) {
            log("[!] Another run is already in progress.")
            return
        }
        if (isRootedNow()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.rerun_title)
                .setMessage(R.string.rerun_message)
                .setPositiveButton(R.string.rerun_confirm) { _, _ -> startRun() }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        startRun()
    }

    private fun startRun() {
        cancelled = false
        rooted = false
        setBusy(true)
        logView.text = ""
        runLog = ExploitRunner.RunLog.start(this)
        log("[*] Run log: ${runLog?.path() ?: "(unavailable)"}")
        Thread({ ExploitRunner.run(this, Targets.current(), runListener) }, "exploit").start()
    }

    private val runListener = object : ExploitRunner.Listener {
        override fun onProgress(msg: String) {}

        override fun onLog(msg: String) {
            log(msg)
            runLog?.append(msg)
        }

        override fun isCancelled(): Boolean = cancelled

        override fun onFinished(success: Boolean) {
            runLog?.finish(if (success) "Completed" else "Failed")
            rooted = success
            ui.post {
                setBusy(false)
                updateStatus()
            }
            refreshRootStatus(force = true)
        }
    }

    private fun copyLog() {
        val text = logView.text ?: return
        if (text.isEmpty()) return
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("GhostLock log", text))
        Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
    }

    private fun shareLog() {
        val text = logView.text ?: return
        if (text.isEmpty()) return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "GhostLock log")
            putExtra(Intent.EXTRA_TEXT, text.toString())
        }
        startActivity(Intent.createChooser(send, getString(R.string.log_share)))
    }

    private fun log(message: String) {
        ui.post {
            logView.append("$message\n")
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        ui.post {
            runButton.isEnabled = !value && ShizukuController.isRunning() && ShizukuController.isGranted() && !isRootedNow()
            stopButton.isEnabled = value
            updateStatus()
        }
    }
}
