package ru.valldun.ruslan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.text.Html
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var gatewayModel = ""
    private var gatewayProvider = ""
    private var speechRecognizer: SpeechRecognizer? = null

    private val recordAudioLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceInput()
        else Toast.makeText(this, "Нужно разрешение на микрофон", Toast.LENGTH_LONG).show()
    }

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
        // Gateway is ALWAYS at 127.0.0.1:9123 (local proxy)
        try {
            val configFile = java.io.File("${filesDir.absolutePath}/hermes/ruslan-provider.json")
            if (configFile.exists()) {
                val text = configFile.readText()
                val json = org.json.JSONObject(text)
                if (json.has("apiKey") && !json.isNull("apiKey")) {
                    gatewayToken = json.getString("apiKey")
                }
                if (json.has("model") && !json.isNull("model")) {
                    gatewayModel = json.getString("model")
                }
                gatewayProvider = json.optString("provider", "?")
                Logger.i(TAG, "config loaded: provider=$gatewayProvider model=$gatewayModel hasKey=${gatewayToken.isNotEmpty()}")
            } else {
                Logger.w(TAG, "no config file at ${configFile.absolutePath}")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "loadGatewayConfig error: ${e.message}")
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
        binding.btnVoice.setOnClickListener { checkMicrophonePermission() }

        setupSpeechRecognizer()

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
                text = "> Руслан Agent v${getVersion()}\n" +
                        "> Терминал: ${gatewayUrl}\n" +
                        "> Введи команду или вопрос\n" +
                        ">\n" +
                        "> 🎤 Нажми микрофон для голосового ввода\n" +
                        "> 💡 Долгое нажатие на статус — очистить чат",
                isUser = false,
                status = MessageStatus.SENT
            )
        )
    }

    private fun getVersion(): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        } catch (e: Exception) { "?" }
    }

    // --- Voice input ---

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            binding.btnVoice.isEnabled = false
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    binding.tvConnectionStatus.text = "● слушаю..."
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.warning_yellow))
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    binding.tvConnectionStatus.text = "● online"
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.success_green))
                }
                override fun onError(error: Int) {
                    binding.tvConnectionStatus.text = "● online"
                    binding.tvConnectionStatus.setTextColor(getColor(R.color.success_green))
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Не распознано"
                        SpeechRecognizer.ERROR_NETWORK -> "Ошибка сети"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет разрешения"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Таймаут"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Не услышал речь"
                        else -> "Ошибка распознавания ($error)"
                    }
                    if (error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(this@ChatActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val text = matches?.firstOrNull()
                    if (!text.isNullOrEmpty()) {
                        binding.etMessage.setText(text)
                        binding.etMessage.setSelection(text.length)
                        sendMessage()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    binding.etMessage.setText(matches?.firstOrNull() ?: "")
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun checkMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startVoiceInput()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) -> {
                Toast.makeText(this, "Разрешение на микрофон нужно для голосового ввода", Toast.LENGTH_LONG).show()
                recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceInput() {
        val sr = speechRecognizer ?: return
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
        }
        sr.startListening(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        Logger.i(TAG, "sendMessage: len=${text.length} model=$gatewayModel provider=$gatewayProvider")

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

            if (response != null && !response.startsWith("⚠")) {
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
                val errText = response ?: run {
                    val proxyErr = GatewayService.lastProxyError
                    if (proxyErr != null) "⚠ Gateway error: $proxyErr\nПроверь настройки провайдера"
                    else "⚠ Gateway не отвечает на :9123\nПроверь что gateway запущен"
                }
                adapter.addMessage(
                    ChatMessage(
                        text = errText,
                        isUser = false,
                        status = MessageStatus.ERROR
                    )
                )
                saveHistory()
                binding.tvConnectionStatus.text = "● offline"
                binding.tvConnectionStatus.setTextColor(getColor(R.color.error_red))
            }

            binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
        }
    }

    private suspend fun sendToGateway(message: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("${gatewayUrl}/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (gatewayToken.isNotEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer $gatewayToken")
            }
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 60000  // longer for LLM responses

            // Build JSON body manually (no libs needed)
            val modelName = if (gatewayModel.isNotEmpty()) gatewayModel else "deepseek-chat"
            val escaped = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
            val body = """{"model":"$modelName","messages":[{"role":"user","content":"$escaped"}],"stream":true}"""

            Logger.i(TAG, "POST $url model=$modelName body_len=${body.length}")

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val responseCode = conn.responseCode
            Logger.i(TAG, "response code=$responseCode")

            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
                Logger.i(TAG, "response body len=${responseText.length}")
                // Try parsing as standard JSON first (non-streaming response)
                val result = try {
                    val json = org.json.JSONObject(responseText)
                    if (json.has("choices")) {
                        json.getJSONArray("choices")
                            .getJSONObject(0)
                            .optJSONObject("message")
                            ?.optString("content", "")
                            ?: ""
                    } else {
                        null
                    }
                } catch (_: org.json.JSONException) {
                    // SSE stream — parse data: lines
                    val content = StringBuilder()
                    responseText.lines().forEach { line ->
                        if (line.startsWith("data: ") && line != "data: [DONE]") {
                            try {
                                val data = org.json.JSONObject(line.removePrefix("data: "))
                                val delta = data.optJSONArray("choices")
                                    ?.optJSONObject(0)
                                    ?.optJSONObject("delta")
                                val text = delta?.optString("content", "")
                                if (!text.isNullOrEmpty()) content.append(text)
                            } catch (_: Exception) {
                                // skip malformed SSE lines
                            }
                        }
                    }
                    if (content.isNotEmpty()) content.toString() else null
                }
                if (result != null) result else responseText
            } else {
                val errorText = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText() ?: "HTTP $responseCode"
                val cleanError = stripHtml(errorText).take(300)
                Logger.w(TAG, "error $responseCode: $cleanError")
                "⚠ Gateway error ($responseCode): $cleanError"
            }
        } catch (e: java.net.ConnectException) {
            Logger.e(TAG, "Connection refused")
            null // Connection refused — gateway not running
        } catch (e: java.net.SocketTimeoutException) {
            Logger.e(TAG, "Socket timeout")
            "⚠ Таймаут: gateway не отвечает"
        } catch (e: Exception) {
            Logger.e(TAG, "sendToGateway failed: ${e.message}")
            null
        }
    }

    private fun stripHtml(raw: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(raw, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else {
            @Suppress("DEPRECATION")
            Html.fromHtml(raw).toString().trim()
        }.replace(Regex("""\s+"""), " ")
    }
}
