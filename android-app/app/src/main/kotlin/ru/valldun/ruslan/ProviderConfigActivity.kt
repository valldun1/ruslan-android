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
import android.widget.TextView
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
        val etModel = dialogView.findViewById<EditText>(R.id.etModel)

        if (existing != null) {
            etName.setText(existing.name)
            etBaseUrl.setText(existing.baseUrl)
            etApiKey.setText(existing.apiKey)
            etModel.setText(existing.defaultModel)
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
                    defaultModel = etModel.text.toString().trim(),
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
        // Write proxy config (читается Go агентом)
        val configDir = java.io.File(filesDir, "hermes")
        configDir.mkdirs()
        val configFile = java.io.File(configDir, "ruslan-provider.json")
        try {
            configFile.writeText(org.json.JSONObject().apply {
                put("provider", active.id)
                put("apiKey", active.apiKey)
                put("model", active.defaultModel)
                put("baseUrl", active.baseUrl)
            }.toString(2))

            // Restart Go agent with new config
            try {
                val intent = android.content.Intent(this, GatewayService::class.java)
                intent.action = GatewayService.ACTION_RESTART
                startService(intent)
                Logger.i("ProviderCfg", "Go agent restart requested")
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
