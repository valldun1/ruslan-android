package ru.valldun.ruslan

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.ArrayAdapter
import ru.valldun.ruslan.databinding.ActivityProvidersBinding

class ProviderConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProvidersBinding
    private lateinit var providerManager: ProviderManager
    private lateinit var adapter: ProviderAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProvidersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        providerManager = ProviderManager(this)
        providerManager.initDefaults()

        setupUI()
        refreshList()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnAddProvider.setOnClickListener {
            showProviderDialog(null)
        }
    }

    private fun refreshList() {
        val providers = providerManager.getAllProviders()
        binding.tvCount.text = "[${providers.size}]"

        if (!::adapter.isInitialized) {
            adapter = ProviderAdapter(providers) { provider ->
                showProviderMenu(provider)
            }
            binding.providerList.layoutManager = LinearLayoutManager(this)
            binding.providerList.adapter = adapter
        } else {
            adapter.updateList(providers)
        }
    }

    private fun showProviderMenu(provider: ProviderConfig) {
        val items = arrayOf(
            if (provider.isActive) "✓ Активен" else "Активировать",
            "Настроить",
            "Удалить"
        )
        AlertDialog.Builder(this)
            .setTitle("${provider.name} (${provider.id})")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> if (!provider.isActive) {
                        providerManager.setActiveProvider(provider.id)
                        refreshList()
                        // Generate .env
                        generateEnv()
                    }
                    1 -> showProviderDialog(provider)
                    2 -> {
                        providerManager.deleteProvider(provider.id)
                        refreshList()
                    }
                }
            }
            .show()
    }

    private fun showProviderDialog(existing: ProviderConfig?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_provider, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etBaseUrl = dialogView.findViewById<EditText>(R.id.etBaseUrl)
        val etApiKey = dialogView.findViewById<EditText>(R.id.etApiKey)
        val spinnerModel = dialogView.findViewById<Spinner>(R.id.spinnerModel)

        // Determine provider ID for model list
        val providerId = existing?.id ?: "deepseek"

        // Populate model spinner
        val models = ProviderConfig.getModelsForProvider(providerId)
        val modelAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerModel.adapter = modelAdapter

        // Select current model
        val currentModel = existing?.defaultModel ?: ""
        val modelIndex = models.indexOf(currentModel).coerceAtLeast(0)
        spinnerModel.setSelection(modelIndex)

        if (existing != null) {
            etName.setText(existing.name)
            etBaseUrl.setText(existing.baseUrl)
            etApiKey.setText(existing.apiKey)
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing != null) "Настроить ${existing.name}" else "Новый провайдер")
            .setView(dialogView)
            .setPositiveButton("Сохранить") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, "Имя обязательно", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val id = existing?.id ?: name.lowercase().replace(" ", "_")
                val provider = ProviderConfig(
                    id = id,
                    name = name,
                    apiKey = etApiKey.text.toString().trim(),
                    baseUrl = etBaseUrl.text.toString().trim(),
                    defaultModel = spinnerModel.selectedItem?.toString() ?: "",
                    isActive = existing?.isActive ?: false
                )
                providerManager.addOrUpdateProvider(provider)
                // Auto-activate if no active provider exists
                if (providerManager.getActiveProvider() == null || existing == null) {
                    providerManager.setActiveProvider(provider.id)
                }
                refreshList()
                generateEnv()
            }
            .setNegativeButton("Отмена", null)
            .show()
            .also { dialog ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                    ContextCompat.getColor(this, R.color.btn_secondary_text)
                )
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(
                    ContextCompat.getColor(this, R.color.btn_secondary_text)
                )
            }
    }

    private fun generateEnv() {
        val active = providerManager.getActiveProvider()
        if (active == null) {
            Toast.makeText(this, "Нет активного провайдера", Toast.LENGTH_SHORT).show()
            return
        }
        // Write proxy config JSON (читается Python прокси)
        val configFile = java.io.File(filesDir, "hermes/ruslan-provider.json")
        try {
            configFile.parentFile?.mkdirs()
            val json = org.json.JSONObject().apply {
                put("provider", active.id)
                put("apiKey", active.apiKey)
                put("model", active.defaultModel)
                put("baseUrl", active.baseUrl)
            }
            configFile.writeText(json.toString(2))
            // Tell Python proxy to reload config
            try {
                if (com.chaquo.python.Python.isStarted()) {
                    val py = com.chaquo.python.Python.getInstance()
                    val result = py.getModule("ruslan_proxy").callAttr("reload_config").toString()
                    Logger.i("ProviderCfg", "Proxy reload: $result")
                }
            } catch (_: Exception) {}
            Toast.makeText(this, "✓ Провайдер ${active.name} сохранён", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // --- Adapter ---
    class ProviderAdapter(
        private var providers: List<ProviderConfig>,
        private val onClick: (ProviderConfig) -> Unit
    ) : RecyclerView.Adapter<ProviderAdapter.ViewHolder>() {

        fun updateList(list: List<ProviderConfig>) {
            providers = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_provider, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val p = providers[position]
            holder.bind(p, onClick)
        }

        override fun getItemCount() = providers.size

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvProviderName)
            private val tvModel: TextView = itemView.findViewById(R.id.tvProviderModel)
            private val tvStatus: TextView = itemView.findViewById(R.id.tvProviderStatus)

            fun bind(provider: ProviderConfig, onClick: (ProviderConfig) -> Unit) {
                tvName.text = provider.name
                tvModel.text = provider.defaultModel.ifEmpty { "—" }
                tvStatus.text = if (provider.isActive) "● АКТИВЕН" else "○"
                tvStatus.setTextColor(
                    itemView.context.getColor(
                        if (provider.isActive) R.color.accent_green
                        else R.color.text_tertiary
                    )
                )
                itemView.setOnClickListener { onClick(provider) }
            }
        }
    }
}
