package ru.valldun.ruslan

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportActionBar?.apply {
            title = "> settings"
            setDisplayHomeAsUpEnabled(true)
        }

        supportFragmentManager
            .beginTransaction()
            .replace(android.R.id.content, SettingsFragment())
            .commit()
    }

    class SettingsFragment : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            // Navigate to provider config
            findPreference<Preference>("providers")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), ProviderConfigActivity::class.java))
                true
            }

            // Navigate to chat settings
            findPreference<Preference>("telegram")?.setOnPreferenceClickListener {
                // TODO: Telegram settings
                true
            }

            // Send log
            findPreference<Preference>("send_log")?.setOnPreferenceClickListener {
                shareLog()
                true
            }

            // Auto-start preference
            findPreference<SwitchPreferenceCompat>("auto_start")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val prefs = requireContext().getSharedPreferences(BootReceiver.PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, enabled).apply()
                true
            }

            // Notifications preference
            findPreference<SwitchPreferenceCompat>("notifications")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                // TODO: wire notification toggle
                true
            }

            // About — show version from package manager
            val versionName = try {
                requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName ?: "?"
            } catch (e: Exception) { "?" }
            findPreference<Preference>("about")?.summary = "Руслан Agent v${versionName}"
        }

        private fun shareLog() {
            val ctx = requireContext()
            val logFile = Logger.getLogFile()
            if (logFile == null || !logFile.exists()) {
                Toast.makeText(ctx, "Лог-файл не найден", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", logFile)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Ruslan Agent Log")
                    val vName = try {
                        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                    } catch (e: Exception) { "?" }
                    putExtra(Intent.EXTRA_TEXT, "Лог-файл Руслан Agent v${vName}\nУстройство: ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(intent, "Отправить лог"))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
