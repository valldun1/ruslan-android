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

    private var proxyProcess: Process? = null
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

        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L)
        }

        startForeground(NOTIFICATION_ID, createNotification())

        Thread {
            runProxyLoop()
        }.start()
    }

    private fun runProxyLoop() {
        val prefix = TermuxBootstrap.getPrefixPath(this)
        val proxyScript = "$prefix/scripts/ruslan-proxy.py"
        val homeDir = "$prefix/home"
        val configDir = "$homeDir/.hermes"
        val configFile = "$configDir/ruslan-provider.json"

        while (isRunning) {
            try {
                Log.d(TAG, "Starting Ruslan API proxy...")

                // Ensure config directory exists
                java.io.File(configDir).mkdirs()

                // Write provider config from SharedPreferences
                val pm = ProviderManager(this)
                val envContent = pm.generateEnvContent()
                if (envContent.isNotEmpty()) {
                    // Parse .env format to JSON config
                    val active = pm.getActiveProvider()
                    if (active != null) {
                        val configJson = org.json.JSONObject().apply {
                            put("provider", active.id)
                            put("apiKey", active.apiKey)
                            put("model", active.defaultModel)
                            put("baseUrl", active.baseUrl)
                        }
                        java.io.File(configFile).writeText(configJson.toString(2))
                        Log.d(TAG, "Config written: ${active.id} / ${active.defaultModel}")
                    }
                }

                val env = mutableMapOf(
                    "PATH" to "$prefix/bin:$prefix/usr/bin",
                    "HOME" to homeDir,
                    "TMPDIR" to "$prefix/tmp",
                    "PREFIX" to prefix
                )

                val pb = ProcessBuilder(
                    "$prefix/bin/python3",
                    proxyScript,
                    "9123"
                ).apply {
                    directory(java.io.File(homeDir))
                    environment().putAll(env)
                    redirectErrorStream(true)
                }

                proxyProcess = pb.start()

                val reader = BufferedReader(InputStreamReader(proxyProcess!!.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    Log.d(TAG, "Proxy: $line")
                }

                val exitCode = proxyProcess?.waitFor()
                Log.w(TAG, "Proxy exited with code: $exitCode")

                if (!isRunning) break
                Thread.sleep(5000)

            } catch (e: Exception) {
                Log.e(TAG, "Proxy error", e)
                if (!isRunning) break
                Thread.sleep(5000)
            }
        }
    }

    private fun stopGateway() {
        isRunning = false
        try {
            proxyProcess?.destroy()
            proxyProcess?.waitFor()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping proxy", e)
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
