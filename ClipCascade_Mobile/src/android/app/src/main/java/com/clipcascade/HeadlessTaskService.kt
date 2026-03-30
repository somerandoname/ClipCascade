// android\app\src\main\java\com\clipcascade\HeadlessTaskService.kt
package com.clipcascade

import android.content.Intent
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.jstasks.HeadlessJsTaskConfig



import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

import android.content.pm.ServiceInfo

class HeadlessTaskService : HeadlessJsTaskService() {

    override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
        return intent?.extras?.let {
            HeadlessJsTaskConfig(
                "Restart", // JS task name
                Arguments.fromBundle(it), // Data passed to the task
                5000, // Timeout for the task
                true // Allow task to run in foreground
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel()
            val notification = NotificationCompat.Builder(this, "ClipCascade")
                .setContentTitle("ClipCascade")
                .setContentText("Starting service...")
                .setSmallIcon(resources.getIdentifier("ic_small_icon", "mipmap", packageName))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
            
            /**
             * Adapted from Syncthing-Fork Android's background service logic.
             * Source: https://github.com/researchxxl/syncthing-android
             */
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1001, notification)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }


    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "ClipCascade Monitor"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel("ClipCascade", name, importance)
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
