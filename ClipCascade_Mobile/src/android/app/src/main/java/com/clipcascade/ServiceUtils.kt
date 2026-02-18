package com.clipcascade

import android.content.Context
import android.content.Intent
import android.os.Build

object ServiceUtils {
    /**
     * Workaround for starting service from background on Android 8+.
     *
     * https://stackoverflow.com/a/44505719/1837158
     *
     * Adapted from Syncthing Android's background service logic.
     * Source: https://github.com/syncthing/syncthing-android
     */
    fun startServiceCompat(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
