package com.clipcascade

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import rikka.shizuku.Shizuku

class ShizukuLogcatManager(
    private val context: Context,
    private val logMatchCallback: () -> Unit,
    private val onFallback: () -> Unit,
    private val onShizukuAvailable: () -> Unit = {}
) {

    private var userService: ILogcatService? = null
    private var lastAllowFallback: Boolean = true

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service != null && service.pingBinder()) {
                val serviceInterface = ILogcatService.Stub.asInterface(service)
                userService = serviceInterface
                try {
                    serviceInterface.startMonitoring(logcatCallback)
                    onShizukuAvailable()
                } catch (e: RemoteException) {
                    e.printStackTrace()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
        }
    }

    private val logcatCallback = object : ILogcatCallback.Stub() {
        override fun onLogMatched() {
            logMatchCallback()
        }
    }

    // Shizuku Listeners
    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        if (checkShizukuPermission()) {
            bindUserService()
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        userService = null
    }

    private val requestPermissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bindUserService()
        } else {
            if (lastAllowFallback) onFallback()
        }
    }

    init {
        // Register Shizuku listeners
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
    }

    fun start(allowFallback: Boolean = true) {
        lastAllowFallback = allowFallback
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            if (isShizukuInstalled()) {
                if (checkShizukuPermission()) {
                    bindUserService()
                } else {
                    try {
                        Shizuku.requestPermission(0)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        if (allowFallback) onFallback()
                    }
                }
            } else {
                // Fallback to standard flow
                if (allowFallback) onFallback()
            }
        }
    }

    fun stop() {
        if (userService != null) {
            try {
                userService?.stopMonitoring()
            } catch (_: Exception) {}

            try {
                val args = Shizuku.UserServiceArgs(
                    ComponentName(context.packageName, LogcatUserService::class.java.name)
                )
                Shizuku.unbindUserService(args, userServiceConnection, true)
            } catch (_: Exception) {}

            userService = null
        }
    }

    fun destroy() {
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        } catch (_: Exception) {}
        stop()
    }

    private fun bindUserService() {
        if (userService != null) return

        val args = Shizuku.UserServiceArgs(
            ComponentName(context.packageName, LogcatUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("service")
            .debuggable(BuildConfig.DEBUG)
            .version(1)

        try {
            Shizuku.bindUserService(args, userServiceConnection)
        } catch (e: Exception) {
            e.printStackTrace()
            onFallback()
        }
    }

    private fun checkShizukuPermission(): Boolean {
        return try {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            !Shizuku.isPreV11()
        } catch (e: Exception) {
            false
        }
    }
}
