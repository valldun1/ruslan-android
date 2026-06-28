package ru.valldun.ruslan

import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.valldun.ruslan.databinding.ActivityChatBinding
import java.io.OutputStreamWriter
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: MessageAdapter

    // Gateway connection config — reads from JSON or defaults
    private var gatewayUrl = "http://127.0.0.1:9123"
    private var gatewayToken = ""

    companion object {
        private const val TAG = "ChatActivity"
        private const val PREFS_HISTORY = "chat_history"
        private const val KEY_HISTORY = "messages"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadGatewayConfig()
        setupUI()
        loadHistory()
    }

    private fun loadGatewayConfig() {
        // Chaquopy: config is in app's private files as JSON (ruslan-provider.json)
        try {
            val configFile = java.io.File("${filesDir.absolutePath}/hermes/ruslan-provider.json")
            if (configFile.exists()) {
                val text = configFile.readText()
                val json = org.json.JSONObject(text)
                if (json.has("baseUrl") && !json.isNull("baseUrl")) {
                    gatewayUrl = json.getString("baseUrl").trimEnd('/')
                }
                if (json.has("apiKey") && !json.isNull("apiKey")) {
                    gatewayToken = json.getString("apiKey")
                }
            }
        } catch (e: Exception) {
            // Use defaults
        }
        // Also try .env fallback for backward compat
        if (gatewayToken.isEmpty()) {
            try {
                val envFile = java.io.File("${filesDir.absolutePath}/hermes/.env")
                if (envFile.exists()) {
                    envFile.readLines().forEach { line ->
                        if (line.startsWith("API_KEY="))
                            gatewayToken = line.substringAfter("=").trim().trim('"')
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            saveHistory()
            finish()
        }

        // Setup RecyclerView
        adapter = MessageAdapter()
        binding.rvMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvMessages.adapter = adapter

        // Send on Enter
        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else false
        }

        // Send button
        binding.btnSend.setOnClickListener { sendMessage() }

        // Voice button
        binding.btnVoice.setOnClickListener {
            adapter.addMessage(
                ChatMessage(
                    text = "🎤 [голосовой ввод]",
                    isUser = true
                )
            )
            saveHistory()
        }

        // Long-press on connection status — clear history
        binding.tvConnectionStatus.setOnLongClickListener {
            clearHistory()
            true
        }

        // Menu — clear option on back-hold
        binding.btnBack.setOnLongClickListener {
            clearHistory()
            true
        }
    }

    private fun loadHistory() {
        val prefs = getSharedPreferences(PREFS_HISTORY, MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null)
        if (json != null) {
            try {
                val arr = org.json.JSONArray(json)
                val msgs = mutableListOf<ChatMessage>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    msgs.add(
                        ChatMessage(
                            text = obj.getString("text"),
                            isUser = obj.getBoolean("isUser"),
                            timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                            status = try {
                                MessageStatus.valueOf(obj.optString("status", "SENT"))
                            } catch (_: Exception) { MessageStatus.SENT }
                        )
                    )
                }
                if (msgs.isNotEmpty()) {
                    adapter.updateMessages(msgs)
                    binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                    return
                }
            } catch (_: Exception) {}
        }
        // No history — show welcome
        addWelcomeMessage()
    }

    private fun saveHistory() {
        val msgs = adapter.getMessages()
        if (msgs.isEmpty()) return
        try {
            val arr = org.json.JSONArray()
            for (msg in msgs) {
                val obj = org.json.JSONObject()
                obj.put("text", msg.text)
                obj.put("isUser", msg.isUser)
                obj.put("timestamp", msg.timestamp)
                obj.put("status", msg.status.name)
                arr.put(obj)
            }
            getSharedPreferences(PREFS_HISTORY, MODE_PRIVATE)
                .edit()
                .putString(KEY_HISTORY, arr.toString())
                .apply()
        } catch (_: Exception) {}
    }

    private fun clearHistory() {
        getSharedPreferences(PREFS_HISTORY, MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
        adapter.updateMessages(mutableListOf())
        addWelcomeMessage()
        binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
    }

    private fun addWelcomeMessage() {
        adapter.addMessage(
            ChatMessage(
                text = "> Руслан Agent v0.18.0\n" +
                        "> Терминал: ${gatewayUrl}\n" +
                        "> Введи команду или вопрос\n" +
                        ">\n" +
                        "> 💡 Долгое нажатие на статус — очистить чат",
                isUser = false,
                status = MessageStatus.SENT
            )
        )
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        // Add user message to chat
        val userMessage = ChatMessage(
            text = text,
            isUser = true,
            status = MessageStatus.SENT
        )
        adapter.addMessage(userMessage)
        binding.etMessage.text.clear()

        // Hide keyboard
        binding.etMessage.clearFocus()

        saveHistory()

        // Send to gateway
        binding.tvConnectionStatus.text = "● thinking..."
        binding.tvConnectionStatus.setTextColor(getColor(R.color.warning_yellow))

        lifecycleScope.launch {
            val response = sendToGateway(text)

            if (response != null) {
                adapter.addMessage(
                    ChatMessage(
                        text = response,
                        isUser = false
                    )
                )
                saveHistory()
                binding.tvConnectionStatus.text = "● online"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.success_green))
            } else {
                adapter.addMessage(
                    ChatMessage(
                        text = "⚠ Ошибка: gateway не отвечает\n" +
                                "Проверь что gateway запущен\n" +
                                "или настрой провайдера",
                        isUser = false,
                        status = MessageStatus.ERROR
                    )
                )
                saveHistory()
                binding.tvConnectionStatus.text = "● offline"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.error_red))
            }

            // Scroll to bottom
            binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private suspend fun sendToGateway(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${gatewayUrl}/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $gatewayToken")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000  // longer for LLM responses

            // Escape message for JSON (basic escaping)
            val escaped = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            val body = """{"model":"default","messages":[{"role":"user","content":"$escaped"}],"stream":true}"""

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().readText()
                // Try parsing as standard JSON first
                try {
                    val json = org.json.JSONObject(responseText)
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } catch (_: org.json.JSONException) {
                    // Maybe SSE stream — return raw text (handled by caller)
                    responseText
                }
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
                "⚠ Gateway error ($responseCode): $errorText"
            }
        } catch (e: java.net.ConnectException) {
            null // Connection refused — gateway not running
        } catch (e: java.net.SocketTimeoutException) {
            "⚠ Таймаут: gateway не отвечает"
        } catch (e: Exception) {
            Log.e(TAG, "sendToGateway failed", e)
            null
        }
    }
}
