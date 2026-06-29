package ru.valldun.ruslan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed received")

            // Check if auto-start is enabled
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val autoStart = prefs.getBoolean(KEY_AUTO_START, true)

            if (autoStart) {
                Log.d(TAG, "Auto-starting gateway service")
                // Go бинарник запускается внутри GatewayService автоматически
                val serviceIntent = Intent(context, GatewayService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                Log.d(TAG, "Auto-start disabled, skipping")
            }
        }
    }

    companion object {
        const val TAG = "BootReceiver"
        const val PREFS_NAME = "ruslan_prefs"
        const val KEY_AUTO_START = "auto_start_gateway"
    }
}
