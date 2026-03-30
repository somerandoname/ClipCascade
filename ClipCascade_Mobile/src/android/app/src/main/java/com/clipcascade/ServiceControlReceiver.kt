package com.clipcascade

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ServiceControlReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ServiceControlReceiver"
        const val ACTION_START_SERVICE = "com.clipcascade.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.clipcascade.STOP_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received broadcast: $action")

        when (action) {
            ACTION_START_SERVICE -> {
                startService(context)
            }
            ACTION_STOP_SERVICE -> {
                stopService(context)
            }
        }
    }

    private fun startService(context: Context) {
        val headlessTaskIntent = Intent(context, HeadlessTaskService::class.java).apply {
            putExtra("event", "START_SERVICE")
        }
        ServiceUtils.startServiceCompat(context, headlessTaskIntent)
    }

    private fun stopService(context: Context) {
        val headlessTaskIntent = Intent(context, HeadlessTaskService::class.java).apply {
            putExtra("event", "STOP_SERVICE")
        }
        // Even for stopping, if we use startServiceCompat to reach the headless task, 
        // it needs to handle the foreground requirement if started that way.
        ServiceUtils.startServiceCompat(context, headlessTaskIntent)
    }
}
