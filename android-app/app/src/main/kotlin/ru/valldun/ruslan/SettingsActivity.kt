package ru.valldun.ruslan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat

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

            // Auto-start preference
            findPreference<SwitchPreferenceCompat>("auto_start")?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                val prefs = requireContext().getSharedPreferences(BootReceiver.PREFS_NAME, MODE_PRIVATE)
                prefs.edit().putBoolean(BootReceiver.KEY_AUTO_START, enabled).apply()
                true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
