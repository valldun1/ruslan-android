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
import androidx.preference.PreferenceManager

/**
 * GatewayService — runs the Ruslan Go agent as foreground service.
 * Manages the Go binary process lifecycle.
 */
class GatewayService : Service() {

    private lateinit var wakeLock: PowerManager.WakeLock
    private var wakeLockHandler: Handler? = null
    private var wakeLockReacquireTask: Runnable? = null
    private var healthCheckHandler: Handler? = null
    private var healthCheckTask: Runnable? = null
    private var healthFailCount = 0
    private var goManager: GoProcessManager? = null

    override fun onCreate() {
        super.onCreate()
        Logger.i(TAG, "Service onCreate")
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Ruslan::GatewayWakeLock"
        )
        goManager = GoProcessManager(this)
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

        // Start Go binary in background thread
        Thread {
            val success = goManager?.start() ?: false
            if (success) {
                _proxyReady = true
                Logger.i(TAG, "=== Ruslan Go Agent started on :9123 ===")

                // Keep thread alive to maintain service
                while (isRunning && goManager?.isAlive() == true) {
                    try {
                        Thread.sleep(30_000)
                    } catch (e: InterruptedException) {
                        break
                    }
                }
                if (isRunning && goManager?.isAlive() != true) {
                    Logger.w(TAG, "Go agent died unexpectedly")
                    _proxyReady = false
                }
            } else {
                Logger.e(TAG, "Go agent failed to start")
                _proxyReady = false
            }
        }.apply {
            isDaemon = true
            start()
        }

        // Start health check polling
        startHealthCheck()
    }

    private fun startHealthCheck() {
        Logger.i(TAG, "Starting health checks (first check in 30s, interval 45s)")
        healthCheckHandler = healthCheckHandler ?: Handler(Looper.getMainLooper())
        healthFailCount = 0
        healthCheckTask = object : Runnable {
            override fun run() {
                if (!isRunning) return
                val healthy = goManager?.healthCheck() ?: false
                if (healthy) {
                    healthFailCount = 0
                    Logger.d(TAG, "Health check OK")
                } else {
                    healthFailCount++
                    Logger.w(TAG, "Health check FAIL ($healthFailCount consecutive)")
                    if (healthFailCount >= 3) {
                        Logger.w(TAG, "3 failures — restarting proxy...")
                        restartProxy()
                        healthFailCount = 0
                    }
                }
                healthCheckHandler?.postDelayed(this, 45_000)
            }
        }
        healthCheckHandler?.postDelayed(healthCheckTask!!, 30_000)
    }

    private fun restartProxy() {
        Logger.i(TAG, "Restarting proxy...")
        try {
            goManager?.stop()
            Thread.sleep(2000)
            goManager?.start()
        } catch (e: Exception) {
            Logger.e(TAG, "Proxy restart failed", e)
        }
    }

    private fun stopGateway() {
        Logger.i(TAG, "stopGateway() called")
        isRunning = false
        _proxyReady = false
        // Cancel wake-lock re-acquisition
        wakeLockReacquireTask?.let { wakeLockHandler?.removeCallbacks(it) }
        healthCheckTask?.let { healthCheckHandler?.removeCallbacks(it) }

        // Stop Go agent
        goManager?.stop()

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
            get() = _lastProxyError

        // Internal
        @Volatile
        private var _lastProxyError: String? = null

        @Volatile
        private var _proxyReady = false
    }
}
