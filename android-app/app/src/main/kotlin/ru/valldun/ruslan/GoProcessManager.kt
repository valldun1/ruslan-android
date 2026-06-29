package ru.valldun.ruslan

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * GoProcessManager manages the Ruslan Go agent binary lifecycle.
 * Extracts the binary from assets, starts/stops the process.
 */
class GoProcessManager(private val context: Context) {

    companion object {
        const val TAG = "GoProcessManager"
        const val PORT = 9123
        private const val BINARY_NAME = "ruslan-android"
        private const val ASSETS_BASE = "ruslan"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var processPid: Int? = null
            private set
    }

    private var process: Process? = null
    private var stdoutThread: Thread? = null
    private var stderrThread: Thread? = null

    /**
     * Start the Go binary on the given port.
     * Blocks until the process is started.
     */
    fun start(port: Int = PORT): Boolean {
        if (isRunning) {
            Log.w(TAG, "Already running")
            return true
        }

        try {
            val binaryPath = extractBinary()
            if (binaryPath == null) {
                Logger.e(TAG, "Failed to extract binary")
                return false
            }

            val configDir = context.filesDir.resolve("ruslan-config")
            configDir.mkdirs()
            val configFile = configDir.resolve("config.yaml")

            // Write config from preferences
            writeConfig(configFile)

            val pb = ProcessBuilder(
                binaryPath.absolutePath,
                "-config", configFile.absolutePath
            )
            pb.directory(context.filesDir)
            pb.environment()["HOME"] = context.filesDir.absolutePath
            // Suppress Go GC logs on older Android
            pb.environment()["GOGC"] = "100"

            process = pb.start()
            processPid = getPid(process!!)

            // Read stdout in background thread
            stdoutThread = Thread {
                try {
                    process!!.inputStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            Logger.d(TAG, "[go] $line")
                        }
                    }
                } catch (e: Exception) {
                    Logger.d(TAG, "stdout reader done: ${e.message}")
                }
            }.apply {
                isDaemon = true
                start()
            }

            // Read stderr in background thread
            stderrThread = Thread {
                try {
                    process!!.errorStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            if (line.contains("error", ignoreCase = true) ||
                                line.contains("panic", ignoreCase = true)) {
                                Logger.e(TAG, "[go-err] $line")
                            } else {
                                Logger.d(TAG, "[go] $line")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Logger.d(TAG, "stderr reader done: ${e.message}")
                }
            }.apply {
                isDaemon = true
                start()
            }

            // Wait a bit for the server to start
            Thread.sleep(2000)

            if (process!!.isAlive) {
                isRunning = true
                Logger.i(TAG, "Go agent started (pid=$processPid)")
                return true
            } else {
                val exitCode = process!!.exitValue()
                Logger.e(TAG, "Go agent exited immediately with code $exitCode")
                return false
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start Go agent", e)
            return false
        }
    }

    /**
     * Stop the Go binary gracefully.
     */
    fun stop() {
        isRunning = false
        try {
            process?.let { p ->
                if (p.isAlive) {
                    p.destroy()
                    // Wait up to 3 seconds for graceful shutdown
                    p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
                    if (p.isAlive) {
                        p.destroyForcibly()
                        p.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping Go agent", e)
        }
        process = null
        processPid = null
        Logger.i(TAG, "Go agent stopped")
    }

    /**
     * Check if the Go agent process is alive.
     */
    fun isAlive(): Boolean {
        return process?.isAlive == true
    }

    /**
     * Check if the HTTP server is responding.
     */
    fun healthCheck(): Boolean {
        return try {
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("127.0.0.1", PORT), 1000)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- Private ---

    /**
     * Extract the correct architecture binary from assets to filesDir.
     */
    private fun extractBinary(): File? {
        val arch = determineArch()
        if (arch == null) {
            Logger.e(TAG, "Unsupported CPU architecture")
            return null
        }

        val assetPath = "$ASSETS_BASE/$arch/$BINARY_NAME"
        val destFile = context.filesDir.resolve("bin/$BINARY_NAME")

        try {
            // Remove old binary if it exists
            if (destFile.exists()) {
                destFile.delete()
            }
            destFile.parentFile?.mkdirs()

            // Copy from assets
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Make executable
            destFile.setExecutable(true)
            Logger.i(TAG, "Binary extracted: $assetPath → $destFile (${destFile.length()} bytes)")
            return destFile
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to extract binary from assets/$assetPath", e)
            return null
        }
    }

    /**
     * Determine the target architecture directory in assets.
     */
    private fun determineArch(): String? {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: return null
        return when {
            abi.contains("arm64-v8a") -> "arm64-v8a"
            abi.contains("armeabi-v7a") || abi.contains("armeabi") -> "armeabi-v7a"
            abi.contains("x86_64") -> "x86_64"
            abi.contains("x86") -> "x86"
            else -> null
        }
    }

    /**
     * Write config.yaml from current settings.
     */
    private fun writeConfig(configFile: File) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)

        val providerId = prefs.getString("provider", "opencode-go") ?: "opencode-go"
        val apiKey = prefs.getString("api_key", "") ?: ""
        val model = prefs.getString("model", "") ?: ""
        val baseUrl = prefs.getString("base_url", "") ?: ""
        val tgToken = prefs.getString("telegram_token", "") ?: ""
        val tgEnabled = prefs.getBoolean("telegram_enabled", false)
        val tgUsers = prefs.getString("telegram_users", "") ?: ""

        val config = buildString {
            appendLine("server:")
            appendLine("  host: \"127.0.0.1\"")
            appendLine("  port: $PORT")
            appendLine("")
            appendLine("provider:")
            appendLine("  id: \"$providerId\"")
            appendLine("  model: \"$model\"")
            appendLine("  api_key: \"$apiKey\"")
            if (baseUrl.isNotEmpty()) {
                appendLine("  base_url: \"$baseUrl\"")
            }
            appendLine("")
            appendLine("telegram:")
            if (tgEnabled && tgToken.isNotEmpty()) {
                appendLine("  token: \"$tgToken\"")
                if (tgUsers.isNotEmpty()) {
                    appendLine("  allowed_users:")
                    tgUsers.split(",").forEach { user ->
                        appendLine("    - \"${user.trim()}\"")
                    }
                }
            } else {
                appendLine("  token: \"\"")
            }
            appendLine("")
            appendLine("persona:")
            appendLine("  soul_file: \"\"")
            appendLine("")
            appendLine("memory:")
            appendLine("  db_path: \"${context.filesDir.absolutePath}/ruslan.db\"")
            appendLine("  max_context: 20")
            appendLine("")
            appendLine("logging:")
            appendLine("  level: \"info\"")
        }

        configFile.writeText(config)
        Logger.d(TAG, "Config written: $configFile")
    }

    /**
     * Get PID of a Java Process (reflection hack for older API).
     */
    private fun getPid(p: Process): Int? {
        return try {
            val field = p.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(p)
        } catch (e: Exception) {
            try {
                // On some Android versions, there's a different field name
                val field = p.javaClass.getDeclaredField("nativeId")
                field.isAccessible = true
                field.getInt(p)
            } catch (e2: Exception) {
                null
            }
        }
    }
}
