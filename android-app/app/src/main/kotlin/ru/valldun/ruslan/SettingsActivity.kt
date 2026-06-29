package ru.valldun.ruslan

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.preference.EditTextPreference
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

            // Telegram users — custom dialog with black buttons (HyperOS fix)
            findPreference<EditTextPreference>("telegram_users")?.setOnPreferenceChangeListener { pref, newValue ->
                val text = newValue as? String ?: ""
                pref.summary = if (text.isEmpty()) "@username или ID через запятую" else text
                true
            }
            findPreference<Preference>("telegram_users")?.setOnPreferenceClickListener {
                val ctx = requireContext()
                val prefs = ctx.getSharedPreferences(ctx.packageName + "_preferences", Context.MODE_PRIVATE)
                val current = prefs.getString("telegram_users", "") ?: ""
                val input = EditText(ctx).apply {
                    setText(current)
                    setSelection(current.length)
                }
                val dialog = AlertDialog.Builder(ctx)
                    .setTitle("Разрешённые пользователи")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ ->
                        val text = input.text.toString().trim()
                        prefs.edit().putString("telegram_users", text).apply()
                        findPreference<EditTextPreference>("telegram_users")?.text = text
                        Toast.makeText(ctx, if (text.isEmpty()) "Разрешены все" else "Сохранено: $text", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.BLACK)
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.BLACK)
                true
            }

            // Navigate to chat settings (telegram button — not needed, kept for compat)
            findPreference<Preference>("telegram")?.setOnPreferenceClickListener {
                Toast.makeText(requireContext(), "Настрой Telegram ниже", Toast.LENGTH_SHORT).show()
                true
            }

            // Navigate to logs
            findPreference<Preference>("logs")?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), LogsActivity::class.java))
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

            // Telegram toggle
            findPreference<SwitchPreferenceCompat>("telegram_enabled")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val token = findPreference<androidx.preference.EditTextPreference>("telegram_token")?.text ?: ""
                if (enabled && token.isNotEmpty()) {
                    try {
                        val py = com.chaquo.python.Python.getInstance()
                        val module = py.getModule("ruslan_proxy")
                        val users = findPreference<androidx.preference.EditTextPreference>("telegram_users")?.text ?: ""
                        module.callAttr("start_telegram_bot", token, users)
                        Logger.i("Settings", "Telegram bot started")
                    } catch (e: Exception) {
                        Logger.e("Settings", "Telegram start failed", e)
                    }
                } else if (!enabled) {
                    try {
                        val py = com.chaquo.python.Python.getInstance()
                        val module = py.getModule("ruslan_proxy")
                        module.callAttr("stop_telegram_bot")
                        Logger.i("Settings", "Telegram bot stopped")
                    } catch (_: Exception) {}
                }
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
