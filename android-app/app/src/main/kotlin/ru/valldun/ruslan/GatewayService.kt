package ru.valldun.ruslan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * GatewayService — runs the Ruslan proxy as foreground service.
 * Uses Chaquopy (in-process Python) instead of Termux subprocess.
 */
class GatewayService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private var wakeLockHandler: Handler? = null
    private var wakeLockReacquireTask: Runnable? = null
    private var healthCheckHandler: Handler? = null
    private var healthCheckTask: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "Service onCreate")
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Ruslan::GatewayWakeLock"
        )

        // Initialize Chaquopy Python
        if (!Python.isStarted()) {
            Logger.i(TAG, "Starting Chaquopy Python...")
            try {
                Python.start(AndroidPlatform(this))
                Logger.i(TAG, "Chaquopy Python started")
            } catch (e: Exception) {
                Logger.e(TAG, "Chaquopy init failed", e)
                _lastProxyError = "Python init: ${e.message}"
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Logger.i(TAG, "Received STOP action")
                stopGateway()
                return START_NOT_STICKY
            }
            ACTION_RESTART -> {
                Logger.i(TAG, "Received RESTART action")
                stopGateway()
                startGateway()
                return START_STICKY
            }
            else -> {
                if (!isRunning) {
                    Logger.i(TAG, "Starting gateway (no action)")
                    startGateway()
                }
            }
        }
        return START_STICKY
    }

    private fun startGateway() {
        Logger.i(TAG, "startGateway() called")
        isRunning = true
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())

        Thread {
            runProxyInChaquopy()
        }.start()

        // Start health check polling
        startHealthCheck()
    }

    private fun runProxyInChaquopy() {
        Logger.i(TAG, "runProxyInChaquopy() starting...")
        try {
            val py = Python.getInstance()
            val module = py.getModule("ruslan_proxy")
            Logger.d(TAG, "Got Python module: ruslan_proxy")

            // Config directory — app private files
            val configDir = filesDir.resolve("hermes").absolutePath
            Logger.i(TAG, "Calling start_server(9123, $configDir)")

            // Start the proxy server
            val result = module.callAttr("start_server", 9123, configDir).toString()
            Logger.i(TAG, "Proxy start result: $result")

            if (result == "ok") {
                Logger.i(TAG, "=== Ruslan Proxy started on :9123 ===")
                // The server runs in a daemon thread — this call returns immediately.
                // We keep this thread alive to detect if the app is being killed.
                while (isRunning) {
                    Thread.sleep(30_000)
                    // Periodic heartbeat
                    if (!isRunning) break
                }
            } else if (result == "already_running") {
                Logger.i(TAG, "Proxy already running — reusing")
                while (isRunning) {
                    Thread.sleep(30_000)
                    if (!isRunning) break
                }
            } else {
                Logger.e(TAG, "Proxy failed to start: $result")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Python proxy error", e)
            _lastProxyError = e.message
        }
    }

    private fun startHealthCheck() {
        healthCheckHandler = healthCheckHandler ?: Handler(Looper.getMainLooper())
        healthCheckTask = object : Runnable {
            override fun run() {
                if (!isRunning) return
                val healthy = checkProxyHealth()
                if (!healthy) {
                    Logger.w(TAG, "Proxy health check failed — restarting...")
                    restartProxy()
                }
                healthCheckHandler?.postDelayed(this, 30_000) // every 30 seconds
            }
        }
        healthCheckHandler?.postDelayed(healthCheckTask!!, 10_000)
    }

    private fun checkProxyHealth(): Boolean {
        val healthy = try {
            val url = java.net.URL("http://127.0.0.1:9123/health")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.responseCode == 200
        } catch (_: Exception) {
            false
        }
        if (!healthy) Logger.w(TAG, "Health check failed")
        return healthy
    }

    private fun restartProxy() {
        Logger.i(TAG, "Restarting proxy...")
        try {
            if (Python.isStarted()) {
                val py = Python.getInstance()
                val module = py.getModule("ruslan_proxy")
                module.callAttr("stop_server")
                Logger.d(TAG, "Proxy stopped, restarting...")
            }
            Thread.sleep(1000)
            val configDir = filesDir.resolve("hermes").absolutePath
            if (Python.isStarted()) {
                val py = Python.getInstance()
                val module = py.getModule("ruslan_proxy")
                val result = module.callAttr("start_server", 9123, configDir).toString()
                Logger.i(TAG, "Proxy restart result: $result")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Proxy restart failed", e)
        }
    }

    private fun stopGateway() {
        Logger.i(TAG, "stopGateway() called")
        isRunning = false
        // Cancel wake-lock re-acquisition
        wakeLockReacquireTask?.let { wakeLockHandler?.removeCallbacks(it) }
        healthCheckTask?.let { healthCheckHandler?.removeCallbacks(it) }

        // Stop Python proxy
        try {
            if (Python.isStarted()) {
                val py = Python.getInstance()
                val module = py.getModule("ruslan_proxy")
                module.callAttr("stop_server")
                Logger.i(TAG, "Proxy stopped")
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping proxy", e)
        }

        if (wakeLock.isHeld) {
            wakeLock.release()
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (!wakeLock.isHeld) {
            wakeLock.acquire(10 * 60 * 1000L)
        }
        wakeLockHandler = wakeLockHandler ?: Handler(Looper.getMainLooper())
        wakeLockReacquireTask?.let { wakeLockHandler?.removeCallbacks(it) }
        wakeLockReacquireTask = Runnable {
            if (isRunning) {
                if (wakeLock.isHeld) wakeLock.release()
                wakeLock.acquire(10 * 60 * 1000L)
                acquireWakeLock()
            }
        }
        wakeLockHandler?.postDelayed(wakeLockReacquireTask!!, 9 * 60 * 1000L)
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

        @Volatile
        var lastProxyError: String? = null
            private set
            get() {
                val v = _lastProxyError
                if (v != null) return v
                // Also read from Python if possible
                if (Python.isStarted()) {
                    try {
                        val py = Python.getInstance()
                        val mod = py.getModule("ruslan_proxy")
                        val status = mod.callAttr("get_status").asMap()
                        if (status["status"] == "error") {
                            return status["error"]?.toString()
                        }
                    } catch (_: Exception) {}
                }
                return null
            }

        // Internal
        @Volatile
        private var _lastProxyError: String? = null
    }
}
