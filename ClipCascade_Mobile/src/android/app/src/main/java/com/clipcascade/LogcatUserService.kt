package com.clipcascade

import android.content.Context
import android.os.RemoteException
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class LogcatUserService(context: Context) : ILogcatService.Stub() {

    private var logcatProcess: Process? = null
    private var monitoringThread: Thread? = null
    @Volatile private var isMonitoring = false

    override fun destroy() {
        stopMonitoring()
        System.exit(0)
    }

    override fun startMonitoring(callback: ILogcatCallback?) {
        if (isMonitoring) return
        isMonitoring = true

        monitoringThread = thread(start = true, isDaemon = true) {
            try {
                val timeStamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(Date())

                // Filter for ClipboardService errors, suppress others
                val cmd = arrayOf("logcat", "-T", timeStamp, "ClipboardService:E", "*:S")
                
                logcatProcess = Runtime.getRuntime().exec(cmd)
                val process = logcatProcess ?: return@thread
                
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = null
                
                // We monitor for the application ID in the logs
                val targetString = "com.clipcascade"

                while (isMonitoring) {
                    line = reader.readLine() ?: break
                    
                    if (line?.contains(targetString) == true) {
                        try {
                            callback?.onLogMatched()
                        } catch (e: RemoteException) {
                            // Binder died, stop monitoring
                            break
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cleanup()
            }
        }
    }

    override fun stopMonitoring() {
        isMonitoring = false
        cleanup()
    }

    private fun cleanup() {
        try {
            logcatProcess?.destroy()
        } catch (_: Exception) {}
        logcatProcess = null
    }
}
