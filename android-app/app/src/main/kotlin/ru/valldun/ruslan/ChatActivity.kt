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

    // Gateway connection config — reads from .env or defaults
    private var gatewayUrl = "http://127.0.0.1:9123"
    private var gatewayToken = ""

    companion object {
        private const val TAG = "ChatActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadGatewayConfig()
        setupUI()
        addWelcomeMessage()
    }

    private fun loadGatewayConfig() {
        // Try to read gateway URL from the Termux home .env
        try {
            val envFile = java.io.File(
                "${TermuxBootstrap.getPrefixPath(this)}/home/.env"
            )
            if (envFile.exists()) {
                envFile.readLines().forEach { line ->
                    if (line.startsWith("GATEWAY_URL="))
                        gatewayUrl = line.substringAfter("=").trim().trim('"')
                    if (line.startsWith("HERMES_GATEWAY_TOKEN="))
                        gatewayToken = line.substringAfter("=").trim().trim('"')
                    if (line.startsWith("API_KEY=") && gatewayToken.isEmpty())
                        gatewayToken = line.substringAfter("=").trim().trim('"')
                }
            }
        } catch (e: Exception) {
            // Use defaults
        }
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener { finish() }

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
        }
    }

    private fun addWelcomeMessage() {
        adapter.addMessage(
            ChatMessage(
                text = "> Руслан Agent v0.17.0\n" +
                        "> Терминал: ${gatewayUrl}\n" +
                        "> Введи команду или вопрос",
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
