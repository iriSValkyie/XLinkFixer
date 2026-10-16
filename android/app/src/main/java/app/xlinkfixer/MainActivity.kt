package app.xlinkfixer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var statusService: TextView
    private lateinit var statusOverlay: TextView
    private lateinit var statusBattery: TextView
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusService = findViewById(R.id.statusService)
        statusOverlay = findViewById(R.id.statusOverlay)
        statusBattery = findViewById(R.id.statusBattery)
        log = findViewById(R.id.log)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnBattery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.btnTest).setOnClickListener {
            // この Activity が前面にいるのでクリップボードは直接読める
            val out = Rewriter.processClipboard(this)
            Toast.makeText(
                this,
                if (out != null) getString(R.string.toast_rewritten, Prefs.target(this))
                else getString(R.string.toast_nothing),
                Toast.LENGTH_SHORT
            ).show()
            refresh()
        }
        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            Prefs.clearLog(this)
            refresh()
        }

        findViewById<Switch>(R.id.swEnabled).apply {
            isChecked = Prefs.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setEnabled(this@MainActivity, v) }
        }
        findViewById<Switch>(R.id.swToast).apply {
            isChecked = Prefs.toast(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setToast(this@MainActivity, v) }
        }
        findViewById<Switch>(R.id.swAggressive).apply {
            isChecked = Prefs.aggressive(this@MainActivity)
            setOnCheckedChangeListener { _, v -> Prefs.setAggressive(this@MainActivity, v) }
        }

        val rg = findViewById<RadioGroup>(R.id.rgTarget)
        rg.check(
            when (Prefs.target(this)) {
                "fxtwitter.com" -> R.id.rbFxtwitter
                "vxtwitter.com" -> R.id.rbVxtwitter
                else -> R.id.rbFixupx
            }
        )
        rg.setOnCheckedChangeListener { _, id ->
            Prefs.setTarget(
                this,
                when (id) {
                    R.id.rbFxtwitter -> "fxtwitter.com"
                    R.id.rbVxtwitter -> "vxtwitter.com"
                    else -> "fixupx.com"
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        statusService.setText(if (isServiceEnabled()) R.string.status_service_on else R.string.status_service_off)
        statusOverlay.setText(if (Settings.canDrawOverlays(this)) R.string.status_overlay_on else R.string.status_overlay_off)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        statusBattery.setText(
            if (pm.isIgnoringBatteryOptimizations(packageName)) R.string.status_battery_on else R.string.status_battery_off
        )
        log.text = Prefs.logText(this)
    }

    private fun isServiceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = "$packageName/${ClipWatcherService::class.java.name}"
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }
}
