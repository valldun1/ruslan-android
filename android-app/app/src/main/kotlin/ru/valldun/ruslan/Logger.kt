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

    fun init(appContext: Context) {
        context = appContext.applicationContext
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
        try {
            val ctx = context ?: return
            val logFile = File(ctx.filesDir, LOG_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
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
