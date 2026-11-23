package com.tools.shizuku.dashboard

import android.app.Activity
import android.os.Bundle
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var status: TextView
    private lateinit var hzLabel: TextView
    private lateinit var xLabel: TextView
    private lateinit var yLabel: TextView
    private var refreshHz = 300
    private var sensX = 15
    private var sensY = 30
    private val prefs by lazy { getSharedPreferences("original_settings", MODE_PRIVATE) }
    @Volatile private var remoteService: IRemoteService? = null
    private var pendingRemoteAction: (() -> Unit)? = null
    private val remoteArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, RemoteService::class.java.name))
            .daemon(false).tag("tools-shizuku-remote")
    }
    private val remoteConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            remoteService = IRemoteService.Stub.asInterface(service)
            runOnUiThread { pendingRemoteAction?.also { pendingRemoteAction = null; it() } }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remoteService = null
            runOnUiThread { setStatus("Koneksi layanan Shizuku terputus") }
        }
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == 77) runOnUiThread {
            updateStatus()
            Toast.makeText(this, if (grantResult == PackageManager.PERMISSION_GRANTED) "Izin Shizuku diberikan" else "Izin ditolak", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(16, 16, 16)
        window.navigationBarColor = Color.rgb(16, 16, 16)
        Shizuku.addRequestPermissionResultListener(permissionListener)
        buildDashboard()
        updateStatus()
    }

    private fun buildDashboard() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(24))
            setBackgroundColor(Color.rgb(16, 16, 16))
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = text("TOOLS SHIZUKU", 27, YELLOW, true)
        content.addView(title)
        content.addView(text("PERFORMANCE DASHBOARD", 12, Color.LTGRAY, true).also { it.setPadding(0, dp(4), 0, dp(18)) })
        status = text("Status: memeriksa Shizuku...", 14, Color.WHITE, true)
        content.addView(card(status))

        content.addView(section("KONFIGURASI LAYAR"))
        hzLabel = text("Target refresh rate: 300 Hz", 15, Color.WHITE, true)
        content.addView(hzLabel)
        val hzSeek = SeekBar(this).apply {
            max = 240
            progress = 240
            progressTintList = android.content.res.ColorStateList.valueOf(YELLOW)
            thumbTintList = android.content.res.ColorStateList.valueOf(YELLOW)
        }
        content.addView(hzSeek)
        hzSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                refreshHz = progress + 60
                hzLabel.text = "Target refresh rate: $refreshHz Hz"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        content.addView(text("Nilai aktual tetap dibatasi kemampuan panel dan sistem perangkat.", 12, Color.GRAY, false))

        content.addView(section("PRESET GAME"))
        xLabel = text("Sensitivitas horizontal (X): $sensX", 15, Color.WHITE, true)
        content.addView(xLabel)
        content.addView(seek(100, sensX) { sensX = it; xLabel.text = "Sensitivitas horizontal (X): $sensX" })
        yLabel = text("Sensitivitas vertikal (Y): $sensY", 15, Color.WHITE, true)
        content.addView(yLabel)
        content.addView(seek(100, sensY) { sensY = it; yLabel.text = "Sensitivitas vertikal (Y): $sensY" })
        content.addView(text("FPS target: 60 (preset saja; FPS game tidak dapat dikunci universal oleh Android).", 13, Color.LTGRAY, false))
        content.addView(text("Touch sampling target: hingga 300 Hz (informasi; tidak bisa dipaksa jika hardware tidak mendukung).", 13, Color.LTGRAY, false).apply { setPadding(0, dp(7), 0, 0) })

        content.addView(section("ANIMASI SISTEM"))
        content.addView(text("Skala animasi: 0.5x", 15, Color.WHITE, true))
        content.addView(text("Apply mengatur tiga skala animasi Android ke 0.5x.", 12, Color.GRAY, false))

        val grant = button("IZINKAN SHIZUKU") { requestShizukuPermission() }
        val apply = button("APPLY TWEAKS") { applyTweaks() }
        val reset = button("RESET / RESTORE") { restoreSettings() }
        content.addView(grant)
        content.addView(apply)
        content.addView(reset)
        content.addView(text("Catatan: Apply hanya mengubah refresh-rate preference dan skala animasi. Sensitivitas X/Y dan FPS 60 disimpan sebagai preset, bukan mengubah konfigurasi internal game.", 11, Color.GRAY, false).apply { setPadding(0, dp(14), 0, 0) })
        scroll.addView(content)
        root.addView(scroll)
        setContentView(root)
    }

    private fun seek(maxValue: Int, initial: Int, changed: (Int) -> Unit): SeekBar = SeekBar(this).apply {
        max = maxValue
        progress = initial
        progressTintList = android.content.res.ColorStateList.valueOf(YELLOW)
        thumbTintList = android.content.res.ColorStateList.valueOf(YELLOW)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) changed(p) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) { setStatus("Shizuku belum berjalan. Buka aplikasi Shizuku lalu start service."); return }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                setStatus("Izin Shizuku sudah aktif")
            } else {
                Shizuku.requestPermission(77)
            }
        } catch (e: Exception) { setStatus("Shizuku error: ${e.message}") }
    }

    private fun updateStatus() {
        val message = try {
            when {
                !Shizuku.pingBinder() -> "Status: Shizuku tidak aktif"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "Status: Shizuku terhubung • izin diberikan"
                else -> "Status: Shizuku terhubung • izin belum diberikan"
            }
        } catch (_: Exception) { "Status: Shizuku belum siap" }
        status.text = message
    }

    private fun applyTweaks() {
        if (!hasPermission()) return
        setStatus("Apply sedang berjalan...")
        withRemoteService {
        worker.execute {
            try {
                val keys = listOf(
                    "system:peak_refresh_rate", "system:min_refresh_rate",
                    "global:animator_duration_scale", "global:window_animation_scale", "global:transition_animation_scale"
                )
                val editor = prefs.edit()
                keys.forEach { key ->
                    val (table, name) = key.split(":")
                    val old = shell("settings get $table $name").trim()
                    if (!prefs.contains(key)) editor.putString(key, old)
                }
                editor.apply()
                val script = """
                    settings put system peak_refresh_rate $refreshHz
                    settings put system min_refresh_rate 60
                    settings put global animator_duration_scale 0.5
                    settings put global window_animation_scale 0.5
                    settings put global transition_animation_scale 0.5
                """.trimIndent()
                shell(script)
                runOnUiThread { setStatus("Apply selesai. Target $refreshHz Hz; animasi 0.5x. Hardware dapat membatasi hasil.") }
            } catch (e: Exception) { runOnUiThread { setStatus("Apply gagal: ${e.message}") } }
        }
        }
    }

    private fun restoreSettings() {
        if (!hasPermission()) return
        if (prefs.all.isEmpty()) { setStatus("Belum ada backup pengaturan untuk dipulihkan"); return }
        setStatus("Restore sedang berjalan...")
        withRemoteService {
        worker.execute {
            try {
                val commands = mutableListOf<String>()
                prefs.all.forEach { (key, value) ->
                    val parts = key.split(":")
                    val table = parts[0]; val name = parts[1]
                    val old = value?.toString().orEmpty()
                    if (old.isBlank() || old == "null") commands.add("settings delete $table $name")
                    else commands.add("settings put $table $name '${old.replace("'", "")}'")
                }
                shell(commands.joinToString("\n"))
                prefs.edit().clear().apply()
                runOnUiThread { setStatus("Restore selesai. Nilai awal dikembalikan.") }
            } catch (e: Exception) { runOnUiThread { setStatus("Restore gagal: ${e.message}") } }
        }
        }
    }

    private fun withRemoteService(action: () -> Unit) {
        if (remoteService != null) { action(); return }
        try {
            pendingRemoteAction = action
            Shizuku.bindUserService(remoteArgs, remoteConnection)
        } catch (e: Exception) {
            pendingRemoteAction = null
            setStatus("Gagal menghubungkan layanan Shizuku: ${e.message}")
        }
    }

    private fun hasPermission(): Boolean {
        return try {
            if (!Shizuku.pingBinder()) { setStatus("Jalankan Shizuku terlebih dahulu"); false }
            else if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) { setStatus("Berikan izin Shizuku terlebih dahulu"); false }
            else true
        } catch (e: Exception) { setStatus("Shizuku error: ${e.message}"); false }
    }

    private fun shell(command: String): String {
        return remoteService?.exec(command) ?: throw IllegalStateException("Layanan Shizuku belum terhubung")
    }

    private fun section(label: String): TextView = text(label, 13, YELLOW, true).apply {
        setPadding(0, dp(22), 0, dp(10))
    }
    private fun card(view: View): View = LinearLayout(this).apply {
        setPadding(dp(14), dp(13), dp(14), dp(13)); setBackgroundColor(Color.rgb(35, 35, 35)); addView(view)
    }
    private fun button(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false; setTextColor(Color.BLACK); setBackgroundTintList(android.content.res.ColorStateList.valueOf(YELLOW))
        setOnClickListener { action() }; layoutParams = LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(10) }
    }
    private fun text(value: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun setStatus(message: String) { status.text = message }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object { private const val YELLOW = 0xFFFFC928.toInt() }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        try { Shizuku.unbindUserService(remoteArgs, remoteConnection, true) } catch (_: Exception) {}
        worker.shutdown()
        super.onDestroy()
    }
}
