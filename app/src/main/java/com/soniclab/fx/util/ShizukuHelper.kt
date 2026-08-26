/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.util

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku integration for executing privileged shell commands.
 * Used to register audio effects on the global output mix without root.
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private var permissionGranted = false

    fun isAvailable(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            Shizuku.pingBinder()
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun requestPermission(callback: (Boolean) -> Unit) {
        if (!Shizuku.pingBinder()) {
            callback(false)
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            permissionGranted = true
            callback(true)
            return
        }
        Shizuku.addRequestPermissionResultListener(object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                permissionGranted = grantResult == PackageManager.PERMISSION_GRANTED
                callback(permissionGranted)
            }
        })
        Shizuku.requestPermission(1001)
    }

    /**
     * Execute a shell command via Shizuku's privileged process.
     * Falls back to Runtime.exec if Shizuku process creation fails.
     */
    fun execCommand(command: String): String {
        if (!permissionGranted && !Shizuku.pingBinder()) return "Shizuku not available"
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
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

    fun hasPermission(): Boolean = permissionGranted && Shizuku.pingBinder()
}
