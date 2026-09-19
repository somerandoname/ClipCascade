// android\app\src\main\java\com\clipcascade\ScheduleService.kt
package com.clipcascade

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.WorkerParameters
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import kotlinx.coroutines.delay
import android.app.PendingIntent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log


class ScheduleService(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    
    companion object {
        private const val TAG = "ScheduleService"
        private const val NOTIFICATION_CHANNEL_ID = "clipcascade_foreground_service_stopped_running"
        private const val NOTIFICATION_ID = 1
        private const val SUMMARY_ID = 100
        private const val GROUP_KEY = "monitoring_group"

        fun removeNotificationIfPresent(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
            notificationManager.cancel(SUMMARY_ID)
        }

        fun hasNotificationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

    data class ConnectionStatus(
        val isServiceRunning: Boolean,
        val isConnected: Boolean
    )

    // init
    override suspend fun doWork(): Result {

        // show notification if foreground service is not running or websocket is disconnected
        try {
            if(hasNotificationPermission(applicationContext)) {
                val bridgeData = AsyncStorageBridge(applicationContext)
                if(enableForegroundService(bridgeData)) {
                    val status = checkConnectionStatus(bridgeData)
                    if(!status.isConnected) {
                        showNotificationIfNotPresent(isDisconnected = status.isServiceRunning)
                    } else {
                        removeNotificationIfPresent(applicationContext)
                    }
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error running worker", e)
            return Result.failure()
        }
    }


    fun enableForegroundService(bridgeData: AsyncStorageBridge) : Boolean {
        // Get websocket(foreground service) status (enabled/disabled)
        return bridgeData.getValue("wsIsRunning")?.toBoolean() ?: false 
    } 
    
    suspend fun checkConnectionStatus(bridgeData: AsyncStorageBridge) : ConnectionStatus {
        // check if foreground service is running and websocket is connected
        bridgeData.setValue("echo", "ping")
        
        // Trigger Headless JS task to wake up the engine
        val intent = Intent(applicationContext, HeadlessTaskService::class.java)
        val bundle = android.os.Bundle()
        bundle.putString("event", "PING")
        intent.putExtras(bundle)
        applicationContext.startService(intent)

        repeat(35) { // 3500 ms
            delay(100) // Wait for 100 ms
            val echo = bridgeData.getValue("echo")
            if (echo == "connected") {
                return ConnectionStatus(isServiceRunning = true, isConnected = true)
            } else if (echo == "disconnected") {
                return ConnectionStatus(isServiceRunning = true, isConnected = false)
            } else if (echo == "pong") {
                // Fallback: engine is running; check stored connectivity flags
                val wsConnected = bridgeData.getValue("wsConnected")?.toBoolean() ?: false
                val statusMsg = bridgeData.getValue("wsStatusMessage") ?: ""
                val isConnected = wsConnected || statusMsg.contains("Connected")
                return ConnectionStatus(isServiceRunning = true, isConnected = isConnected)
            }
        }
        return ConnectionStatus(isServiceRunning = false, isConnected = false)
    }

    suspend fun foregroundServiceIsActive(bridgeData: AsyncStorageBridge) : Boolean {
        return checkConnectionStatus(bridgeData).isConnected
    }

    private fun showNotificationIfNotPresent(isDisconnected: Boolean = false) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Check if the notification is already shown
        if (!isNotificationActive(notificationManager)) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                action = "com.clipcascade.NOTIFICATION_ACTION"
                putExtra("action", "foreground_service_stopped_running")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            val pendingIntent = PendingIntent.getActivity(
                applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // 1. Create the Group Summary (Placeholder)
            val summaryNotification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_failure) // Should be same as child
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            val title = if (isDisconnected) "ClipCascade Disconnected" else "ClipCascade Service Inactive"
            val text = if (isDisconnected) "ClipCascade connection is lost. Tap to reconnect." else "ClipCascade monitoring is inactive. Tap to restart."

            // 2. Create the actual Alert Notification
            val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_failure)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setGroup(GROUP_KEY)
                .setGroupSummary(false)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(SUMMARY_ID, summaryNotification)
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun isNotificationActive(notificationManager: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNotifications = notificationManager.activeNotifications
            return activeNotifications.any { it.id == NOTIFICATION_ID }
        }
        return false
    }
}