package ru.valldun.ruslan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            GatewayService.ACTION_STOP -> {
                val serviceIntent = Intent(context, GatewayService::class.java).apply {
                    action = GatewayService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
            GatewayService.ACTION_RESTART -> {
                val serviceIntent = Intent(context, GatewayService::class.java).apply {
                    action = GatewayService.ACTION_RESTART
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}