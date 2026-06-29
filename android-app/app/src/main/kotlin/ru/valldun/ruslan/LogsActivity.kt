package ru.valldun.ruslan

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class LogsActivity : AppCompatActivity() {

    private lateinit var rvLogs: RecyclerView
    private lateinit var btnClear: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var tvCount: TextView
    private lateinit var btnBack: View
    private lateinit var adapter: LogAdapter
    private val handler = Handler(Looper.getMainLooper())
    private var filter: String? = "ALL"
    private var isUserScrolledUp = false

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateLogs()
            handler.postDelayed(this, 5000) // refresh every 5s (was 1s)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)

        rvLogs = findViewById(R.id.rvLogs)
        btnClear = findViewById(R.id.btnClear)
        btnShare = findViewById(R.id.btnShare)
        btnBack = findViewById(R.id.btnBack)
        tvCount = findViewById(R.id.tvLogCount)

        adapter = LogAdapter()
        val layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvLogs.layoutManager = layoutManager
        rvLogs.adapter = adapter

        // Detect manual scroll — don't force to bottom
        rvLogs.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(rv: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    val lm = rv.layoutManager as LinearLayoutManager
                    val lastVisible = lm.findLastVisibleItemPosition()
                    val total = adapter.itemCount - 1
                    isUserScrolledUp = lastVisible < total - 2
                }
            }
        })

        btnBack.setOnClickListener { finish() }
        btnClear.setOnClickListener {
            Logger.clearRingBuffer()
            updateLogs()
        }
        btnShare.setOnClickListener { shareLogs() }
    }

    override fun onResume() {
        super.onResume()
        isUserScrolledUp = false
        handler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(updateRunnable)
    }

    private fun updateLogs() {
        val logs = Logger.getRingBufferFiltered(filter)
        adapter.updateLogs(logs)
        tvCount.text = "[${logs.size}]"
        // Only scroll to bottom if user hasn't manually scrolled up
        if (!isUserScrolledUp && logs.isNotEmpty()) {
            rvLogs.scrollToPosition(logs.size - 1)
        }
    }

    private fun shareLogs() {
        val logFile = Logger.getLogFile()
        if (logFile == null || !logFile.exists()) return
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", logFile)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                putExtra(android.content.Intent.EXTRA_SUBJECT, "Ruslan Agent Log")
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "Отправить лог"))
        } catch (_: Exception) { }
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.LogVH>() {
        private val items = mutableListOf<Logger.LogEntry>()

        fun updateLogs(newItems: List<Logger.LogEntry>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_log, parent, false)
            return LogVH(view)
        }

        override fun onBindViewHolder(holder: LogVH, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class LogVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvLine: TextView = itemView.findViewById(R.id.tvLogLine)
            fun bind(entry: Logger.LogEntry) {
                val color = when (entry.level) {
                    "ERROR" -> 0xFFFF4444.toInt()
                    "WARN" -> 0xFFFFAA00.toInt()
                    "INFO" -> 0xFF00FF88.toInt()
                    "DEBUG" -> 0xFF888888.toInt()
                    else -> 0xFFE5E5E5.toInt()
                }
                tvLine.text = "[${entry.timestamp}] ${entry.level}/${entry.tag}: ${entry.message}"
                tvLine.setTextColor(color)
            }
        }
    }
}
