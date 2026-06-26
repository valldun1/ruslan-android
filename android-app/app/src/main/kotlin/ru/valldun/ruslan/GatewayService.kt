package ru.valldun.ruslan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class GatewayService : Service() {

    private var gatewayProcess: Process? = null
    private var logThread: Thread? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Ruslan::GatewayWakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopGateway()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                stopGateway()
                startGateway()
                return START_STICKY
            }
            else -> {
                if (!isRunning) {
                    startGateway()
                }
            }
        }
        return START_STICKY
    }

    private fun startGateway() {
        isRunning = true
        Companion.isRunning = true

        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L) // 10 minutes, will be renewed
        }

        // Start foreground with notification
        startForeground(NOTIFICATION_ID, createNotification())

        // Start gateway in background thread
        Thread {
            runGatewayLoop()
        }.start()
    }

    private fun runGatewayLoop() {
        val prefix = TermuxBootstrap.getPrefixPath(this)
        val homeDir = "$prefix/home"

        while (isRunning) {
            try {
                Log.d(TAG, "Starting Ruslan gateway...")

                val env = mutableMapOf(
                    "PATH" to "$prefix/bin:$prefix/usr/bin",
                    "LD_LIBRARY_PATH" to "$prefix/lib",
                    "HOME" to homeDir,
                    "TMPDIR" to "$prefix/tmp",
                    "PREFIX" to prefix,
                    "TERM" to "xterm-256color"
                )

                // Load .env if exists
                val envFile = "$homeDir/.env"
                val envMap = loadEnvFile(envFile)
                env.putAll(envMap)

                val pb = ProcessBuilder(
                    "$prefix/bin/python3",
                    "-m",
                    "hermes_cli",
                    "gateway",
                    "run",
                    "--accept-hooks",
                    "--replace"
                ).apply {
                    directory(java.io.File(homeDir))
                    environment().putAll(env)
                    redirectErrorStream(true)
                }

                gatewayProcess = pb.start()

                // Read logs
                val reader = BufferedReader(InputStreamReader(gatewayProcess!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Gateway: $line")
                    // TODO: Send logs to UI via Broadcast or LiveData
                }

                val exitCode = gatewayProcess?.waitFor()
                Log.w(TAG, "Gateway exited with code: $exitCode")

                if (!isRunning) break

                // Restart delay
                Log.d(TAG, "Restarting gateway in 5 seconds...")
                Thread.sleep(5000)

            } catch (e: Exception) {
                Log.e(TAG, "Gateway error", e)
                if (!isRunning) break
                Thread.sleep(5000)
            }
        }
    }

    private fun loadEnvFile(path: String): Map<String, String> {
        val env = mutableMapOf<String, String>()
        try {
            val file = java.io.File(path)
            if (!file.exists()) return env

            file.readLines().forEach { line ->
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    var value = parts[1].trim()
                    // Remove quotes if present
                    if ((value.startsWith("\"") && value.endsWith("\"")) ||
                        (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length - 1)
                    }
                    env[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading .env", e)
        }
        return env
    }

    private fun stopGateway() {
        isRunning = false
        Companion.isRunning = false

        try {
            gatewayProcess?.destroy()
            gatewayProcess?.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping gateway", e)
        }

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val restartIntent = Intent(this, NotificationActionReceiver::class.java).apply {
            action = ACTION_RESTART
        }
        val restartPendingIntent = PendingIntent.getBroadcast(
            this, 1, restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_stop, getString(R.string.stop), stopPendingIntent)
            .addAction(R.drawable.ic_restart, getString(R.string.restart), restartPendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopGateway()
    }

    companion object {
        const val TAG = "GatewayService"
        const val CHANNEL_ID = "ruslan_gateway"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "ru.valldun.ruslan.STOP_GATEWAY"
        const val ACTION_RESTART = "ru.valldun.ruslan.RESTART_GATEWAY"

        @Volatile
        var isRunning = false
            private set
    }
}
