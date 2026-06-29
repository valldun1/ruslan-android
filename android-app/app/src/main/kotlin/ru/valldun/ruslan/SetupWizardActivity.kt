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

        // Sync provider selection → model step when navigating
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                if (position == 2) {
                    // Going to model step — read provider from step 2 and pass to model step
                    val step2 = adapter.getFragmentAt(1)
                    if (step2 is WizardStep2Fragment) {
                        val provider = step2.getSelectedProvider()
                        val stepModel = adapter.getFragmentAt(2)
                        if (stepModel is WizardStepModelFragment) {
                            stepModel.setProvider(provider)
                        }
                    }
                }
                updateButtonText()
            }
        })

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
        var selectedModel = ""
        var apiKey = ""

        // Try to read Step 2 (provider selection)
        try {
            val step2 = adapter.getFragmentAt(1)
            if (step2 is WizardStep2Fragment) {
                selectedProvider = step2.getSelectedProvider()
            }
        } catch (e: Exception) {
            // Default: deepseek
        }

        // Try to read Step 3 (model selection) — position 2
        try {
            val stepModel = adapter.getFragmentAt(2)
            if (stepModel is WizardStepModelFragment) {
                selectedModel = stepModel.getSelectedModel()
            }
        } catch (e: Exception) {}

        // Try to read Step 4 (API key) — position 3
        try {
            val step3 = adapter.getFragmentAt(3)
            if (step3 is WizardStep3Fragment) {
                apiKey = step3.getApiKey()
            }
        } catch (e: Exception) {}

        // Save provider config
        val providerManager = ProviderManager(this)
        providerManager.initDefaults()

        val builtIn = ProviderConfig.BUILT_IN.find { it.id == selectedProvider }
        val finalModel = if (selectedModel.isNotEmpty()) selectedModel else (builtIn?.defaultModel ?: "")

        val provider = ProviderConfig(
            id = selectedProvider,
            name = builtIn?.name ?: selectedProvider,
            apiKey = apiKey,
            baseUrl = builtIn?.baseUrl ?: "",
            defaultModel = finalModel,
            isActive = true
        )
        providerManager.addOrUpdateProvider(provider)
        providerManager.setActiveProvider(selectedProvider)

        // Write proxy config JSON
        try {
            val configFile = java.io.File(filesDir, "hermes/ruslan-provider.json")
            configFile.parentFile?.mkdirs()
            val json = org.json.JSONObject().apply {
                put("provider", selectedProvider)
                put("apiKey", apiKey)
                put("baseUrl", builtIn?.baseUrl ?: "")
                put("model", finalModel)
            }
            configFile.writeText(json.toString(2))
            // Reload Python proxy config
            try {
                if (com.chaquo.python.Python.isStarted()) {
                    val py = com.chaquo.python.Python.getInstance()
                    val result = py.getModule("ruslan_proxy").callAttr("reload_config").toString()
                    Logger.i("SetupWizard", "Proxy reload: $result")
                }
            } catch (_: Exception) {}
        } catch (e: Exception) {
            Logger.w("SetupWizard", "Config write error: ${e.message}")
        }

        // Mark first run as completed
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(MainActivity.KEY_FIRST_RUN, false).apply()

        // Start main activity with gateway service
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)

        try {
            val serviceIntent = Intent(this, GatewayService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (e: Exception) {}

        finish()
    }
}
