package ru.valldun.ruslan

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ru.valldun.ruslan.databinding.ActivityChatBinding

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener {
            finish()
        }

        // Send button
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString()
            if (message.isNotBlank()) {
                // TODO: Implement actual message sending
                binding.etMessage.text.clear()
            }
        }

        // Voice input button
        binding.btnVoice.setOnClickListener {
            // TODO: Implement voice input
        }

        // Attachment button
        binding.btnAttachment.setOnClickListener {
            // TODO: Implement file attachment
        }
    }
}