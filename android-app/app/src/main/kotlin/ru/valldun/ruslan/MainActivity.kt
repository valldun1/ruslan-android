package ru.valldun.ruslan

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import ru.valldun.ruslan.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isGatewayRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.init(this)
        Logger.i("Main", "App started")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if this is first run
        if (isFirstRun()) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        // Request battery optimization exemption (HyperOS fix)
        requestBatteryOptimizationExemption()

        setupUI()
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun setupUI() {
        // Menu button
        binding.btnMenu.setOnClickListener {
            // TODO: Open drawer menu
            Toast.makeText(this, "Меню", Toast.LENGTH_SHORT).show()
        }

        // Notifications button
        binding.btnNotifications.setOnClickListener {
            // TODO: Show notification panel
            Toast.makeText(this, "Уведомления", Toast.LENGTH_SHORT).show()
        }

        // Open Chat button
        binding.btnOpenChat.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }

        // Settings button
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Bottom navigation
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_chat -> {
                    startActivity(Intent(this, ChatActivity::class.java))
                    true
                }
                R.id.nav_scenarios -> {
                    Toast.makeText(this, "Сценарии", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun updateStatus() {
        // Check if gateway is running
        isGatewayRunning = GatewayService.isRunning

        if (isGatewayRunning) {
            binding.statusIndicator.setImageResource(R.drawable.ic_status_active)
            binding.tvStatus.text = getString(R.string.status_active)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
            binding.tvGatewayStatus.text = getString(R.string.gateway_running)
        } else {
            binding.statusIndicator.setImageResource(R.drawable.ic_status_inactive)
            binding.tvStatus.text = getString(R.string.status_inactive)
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvGatewayStatus.text = getString(R.string.gateway_stopped)
        }

        // Update stats
        updateStats()
    }

    private fun updateStats() {
        // Real JVM RAM usage (always available)
        val runtime = Runtime.getRuntime()
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        binding.tvRamUsage.text = "${usedMb}MB / ${totalMb}MB"

        // Model name from config
        val pm = ProviderManager(this)
        val activeProvider = pm.getActiveProvider()
        binding.tvModelName.text = activeProvider?.let {
            "${it.name} / ${it.defaultModel}"
        } ?: "—"
        binding.tvSessionsCount.text = "${pm.getAllProviders().size}"

        // Get proxy status from the in-process flag (no HTTP needed)
        val proxyOk = GatewayService.isRunning
        if (proxyOk) {
            binding.tvUptime.text = formatUptime((System.currentTimeMillis() - GatewayStartTime) / 1000)
        }
    }

    private fun isFirstRun(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            val dialog = AlertDialog.Builder(this)
                .setTitle(R.string.battery_opt_title)
                .setMessage(R.string.battery_opt_message)
                .setPositiveButton(R.string.go_to_settings) { _, _ ->
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                }
                .setNegativeButton(R.string.later, null)
                .show()

            // Force button text to black on HyperOS
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.BLACK)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.BLACK)
        }
    }

    companion object {
        const val PREFS_NAME = "ruslan_prefs"
        const val KEY_FIRST_RUN = "first_run"
        val GatewayStartTime = System.currentTimeMillis()
    }

    private fun formatUptime(sec: Long): String {
        val days = sec / 86400
        val hours = (sec % 86400) / 3600
        val minutes = (sec % 3600) / 60
        return when {
            days > 0 -> "${days}д ${hours}ч"
            hours > 0 -> "${hours}ч ${minutes}м"
            else -> "${minutes}м"
        }
    }
}
