package com.soniclab.fx.util

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku integration for registering audio effects on the global output mix
 * without root.  Shizuku provides an ADB-privileged shell that can execute
 * commands requiring MODIFY_AUDIO_SETTINGS / CAPTURE_AUDIO_OUTPUT.
 *
 * Usage flow:
 *  1. Check Shizuku availability (installed + running)
 *  2. Request permission
 *  3. Use Shizuku.newProcess() to run shell commands that register the effect
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var permissionGranted = false

    interface Callback {
        fun onShizukuReady()
        fun onShizukuDenied()
        fun onShizukuError(error: String)
    }

    fun isAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            Shizuku.pingBinder()
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun requestPermission(callback: Callback) {
        if (!Shizuku.pingBinder()) {
            callback.onShizukuError("Shizuku is not running. Start it via ADB or the Shizuku app.")
            return
        }

        if (Shizuku.isPreV11()) {
            callback.onShizukuError("Shizuku version too old. Update to v11+.")
            return
        }

        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
            callback.onShizukuReady()
            return
        }

        Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    permissionGranted = true
                    callback.onShizukuReady()
                } else {
                    callback.onShizukuDenied()
                }
            }
        })
        Shizuku.requestPermission(1001)
    }

    /**
     * Execute a shell command via Shizuku's privileged shell.
     * Returns the combined stdout+stderr output.
     */
    fun execCommand(command: String): String {
        if (!permissionGranted) return "Shizuku permission not granted"
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (stderr.isNotEmpty()) Log.w(TAG, "stderr: $stderr")
            stdout
        } catch (e: Exception) {
            Log.e(TAG, "execCommand failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Register an AudioEffect on the global output mix via shell commands.
     * This uses `cmd audio` which is available on ADB-privileged shells.
     */
    fun registerGlobalEffect(): Boolean {
        // Check if we can modify audio settings
        val checkResult = execCommand("cmd audio check-permission android.permission.MODIFY_AUDIO_SETTINGS")
        Log.i(TAG, "Permission check: $checkResult")

        // Register effect on session 0 (global output mix)
        val result = execCommand("cmd audio effect enable session=0")
        Log.i(TAG, "Effect registration: $result")
        return !result.contains("Error", ignoreCase = true)
    }

    fun hasPermission(): Boolean = permissionGranted && Shizuku.pingBinder()
}
