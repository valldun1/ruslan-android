package ru.valldun.ruslan

/**
 * Chat message model
 */
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
)

enum class MessageStatus {
    PENDING, SENT, ERROR
}
