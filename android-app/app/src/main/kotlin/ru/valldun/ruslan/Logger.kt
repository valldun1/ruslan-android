package ru.valldun.ruslan

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private const val LOG_FILE_NAME = "ruslan.log"
    private var context: Context? = null

    // In-memory ring buffer for LogsActivity
    data class LogEntry(
        val timestamp: String,
        val level: String,
        val tag: String,
        val message: String
    )

    private val ringBuffer = object {
        private val entries = mutableListOf<LogEntry>()
        private val max = 500

        @Synchronized
        fun add(entry: LogEntry) {
            entries.add(entry)
            if (entries.size > max) {
                entries.removeAt(0)
            }
        }

        @Synchronized
        fun getAll(): List<LogEntry> = entries.toList()

        @Synchronized
        fun clear() { entries.clear() }

        @Synchronized
        fun filter(level: String?): List<LogEntry> =
            if (level == null || level == "ALL") entries.toList()
            else entries.filter { it.level == level }
    }

    fun getRingBuffer(): List<LogEntry> = ringBuffer.getAll()
    fun getRingBufferFiltered(level: String?): List<LogEntry> = ringBuffer.filter(level)
    fun clearRingBuffer() = ringBuffer.clear()

    fun init(appContext: Context) {
        context = appContext.applicationContext
        // Write header with version info on first init
        try {
            val ctx = context ?: return
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            if (!logFile.exists() || logFile.length() == 0L) {
                val versionName = try {
                    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
                } catch (e: Exception) { "?" }
                val device = android.os.Build.MODEL
                val androidVer = android.os.Build.VERSION.RELEASE
                FileWriter(logFile, true).use { writer ->
                    writer.append("# Руслан Agent v${versionName} | ${device} | Android ${androidVer}\n")
                }
            }
        } catch (_: Exception) {}
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("DEBUG", tag, message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("INFO", tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        writeToFile("WARN", tag, message + (throwable?.let { ": ${it.message}" } ?: ""))
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        val extra = throwable?.let { ": ${it.message}\n${it.stackTraceToString()}" } ?: ""
        writeToFile("ERROR", tag, message + extra)
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        // Add to ring buffer
        ringBuffer.add(LogEntry(timestamp, level, tag, message))
        // Write to file
        try {
            val ctx = context ?: return
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            FileWriter(logFile, true).use { writer ->
                writer.append("[$timestamp] $level/$tag: $message\n")
            }
            // Ограничиваем размер лога ~5MB
            if (logFile.exists() && logFile.length() > 5_000_000) {
                val shortened = logFile.readText().takeLast(1_000_000)
                logFile.writeText(shortened)
            }
        } catch (ex: Exception) {
            Log.e("RuslanLogger", "Failed to write log file", ex)
        }
    }

    fun getLogFile(): File? {
        val ctx = context ?: return null
        return File(ctx.filesDir, LOG_FILE_NAME)
    }

    fun clearLogs() {
        getLogFile()?.delete()
    }
}
