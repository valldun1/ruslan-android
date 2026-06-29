package ru.valldun.ruslan

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import ru.valldun.ruslan.databinding.ActivitySetupWizardBinding

class SetupWizardActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupWizardBinding
    private lateinit var adapter: WizardPagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.i("SetupWizard", "Wizard started")
        binding = ActivitySetupWizardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWizard()
    }

    private fun setupWizard() {
        adapter = WizardPagerAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.btnNext.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < adapter.itemCount - 1) {
                binding.viewPager.currentItem = currentItem + 1
                updateButtonText()
            } else {
                finishWizard()
            }
        }
    }

    private fun updateButtonText() {
        val isLastPage = binding.viewPager.currentItem == adapter.itemCount - 1
        binding.btnNext.text = if (isLastPage) getString(R.string.done) else getString(R.string.next)
    }

    private fun finishWizard() {
        // Collect data from wizard fragments
        var selectedProvider = "deepseek"
        var apiKey = ""

        // Try to read Step 2 (provider selection)
        try {
            val step2 = adapter.getFragmentAt(1) // Fragment at position 1
            if (step2 is WizardStep2Fragment) {
                selectedProvider = step2.getSelectedProvider()
            }
        } catch (e: Exception) {
            // Default: deepseek
        }

        // Try to read Step 3 (API key)
        try {
            val step3 = adapter.getFragmentAt(2)
            if (step3 is WizardStep3Fragment) {
                apiKey = step3.getApiKey()
            }
        } catch (e: Exception) {}

        // Save provider config — merge with built-in to preserve baseUrl/model
        val providerManager = ProviderManager(this)
        providerManager.initDefaults()

        // Look up built-in config to get baseUrl and defaultModel
        val builtIn = ProviderConfig.BUILT_IN.find { it.id == selectedProvider }

        val provider = ProviderConfig(
            id = selectedProvider,
            name = when (selectedProvider) {
                "deepseek" -> "DeepSeek"
                "opencode-go" -> "OpenCode Go"
                "openai" -> "OpenAI"
                "anthropic" -> "Anthropic"
                "openrouter" -> "OpenRouter"
                "google" -> "Google Gemini"
                else -> selectedProvider
            },
            apiKey = apiKey,
            baseUrl = builtIn?.baseUrl ?: "",
            defaultModel = builtIn?.defaultModel ?: "",
            isActive = true
        )
        providerManager.addOrUpdateProvider(provider)
        providerManager.setActiveProvider(selectedProvider)

        // Write proxy config JSON immediately
        try {
            val configFile = java.io.File(filesDir, "hermes/ruslan-provider.json")
            configFile.parentFile?.mkdirs()
            val json = org.json.JSONObject().apply {
                put("provider", selectedProvider)
                put("apiKey", apiKey)
                put("baseUrl", "")
                put("model", "")
            }
            configFile.writeText(json.toString(2))
        } catch (e: Exception) {
            // Non-critical - user can reconfigure in settings
        }

        // Mark first run as completed
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(MainActivity.KEY_FIRST_RUN, false).apply()

        // Start main activity with gateway service
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        // Start gateway service in background
        try {
            val serviceIntent = Intent(this, GatewayService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {
            // Ignore - user can start from settings
        }

        finish()
    }
}
