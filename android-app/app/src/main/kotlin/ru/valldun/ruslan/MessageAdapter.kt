package ru.valldun.ruslan

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val messages: MutableList<ChatMessage> = mutableListOf()
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvMessageStatus)
        private val messagePadding: View = itemView.findViewById(R.id.messagePadding)

        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(message: ChatMessage) {
            tvMessage.text = message.text

            // Format timestamp
            tvTimestamp.text = timeFormat.format(Date(message.timestamp))

            // Status indicator
            tvStatus.text = when (message.status) {
                MessageStatus.PENDING -> "..."
                MessageStatus.SENT -> ""
                MessageStatus.ERROR -> "[ERROR]"
            }
            tvStatus.visibility = if (message.status == MessageStatus.ERROR) View.VISIBLE else View.GONE

            // Terminal style: user messages aligned right, bot left
            val params = itemView.layoutParams as ViewGroup.MarginLayoutParams
            if (message.isUser) {
                tvMessage.setTextColor(itemView.context.getColor(R.color.accent_green))
                tvMessage.gravity = android.view.Gravity.END
                params.marginStart = 60.dpToPx(itemView.context)
                params.marginEnd = 8.dpToPx(itemView.context)
                messagePadding.visibility = View.VISIBLE
            } else {
                tvMessage.setTextColor(itemView.context.getColor(R.color.text_primary))
                tvMessage.gravity = android.view.Gravity.START
                params.marginStart = 8.dpToPx(itemView.context)
                params.marginEnd = 60.dpToPx(itemView.context)
                messagePadding.visibility = View.GONE
            }
            itemView.layoutParams = params
        }
    }
}

fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
