package ru.valldun.ruslan

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.valldun.ruslan.databinding.ActivityMainBinding
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isGatewayRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        // Ensure Termux prefix is extracted
        TermuxBootstrap.ensurePrefix(this)

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

        // Update stats (placeholder - would read from service)
        updateStats()
    }

    private fun updateStats() {
        // Read real metrics from the proxy health endpoint
        lifecycleScope.launch {
            try {
                val health = fetchHealth()
                if (health != null) {
                    binding.tvSessionsCount.text = health.optString("requests", "0")
                    binding.tvMemoryPercent.text = getString(R.string.see_providers)
                    binding.tvUptime.text = health.optString("uptime", "--")
                }
            } catch (_: Exception) {
                // keep defaults
            }
        }
    }

    private suspend fun fetchHealth(): org.json.JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://127.0.0.1:9123/health")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                org.json.JSONObject(text)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun isFirstRun(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_FIRST_RUN, true)
    }

    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            AlertDialog.Builder(this)
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
        }
    }

    companion object {
        const val PREFS_NAME = "ruslan_prefs"
        const val KEY_FIRST_RUN = "first_run"
    }
}
