/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.util

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Root access helper. Executes commands as root via `su` shell.
 * Used to register audio effects on the global output mix when the device is rooted.
 */
object RootHelper {

    private const val TAG = "RootHelper"

    fun isRooted(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun execCommand(command: String): String {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val stdout = BufferedReader(InputStreamReader(process.inputStream)).readText()
            val stderr = BufferedReader(InputStreamReader(process.errorStream)).readText()
            process.waitFor()
            if (stderr.isNotEmpty()) Log.w(TAG, "stderr: $stderr")
            stdout
        } catch (e: Exception) {
            Log.e(TAG, "execCommand failed: ${e.message}", e)
            "Error: ${e.message}"
        }
    }

    /**
     * Apply audio effects globally via system properties / audio policy commands.
     * This is device-specific and may require vendor-specific commands.
     */
    fun applyGlobalFx(packageName: String): Boolean {
        val commands = listOf(
            // Grant audio capture permission
            "pm grant $packageName android.permission.CAPTURE_AUDIO_OUTPUT",
            // Allow effect on global output
            "settings put global audio_effects_enabled 1"
        )
        for (cmd in commands) {
            val result = execCommand(cmd)
            Log.i(TAG, "$cmd -> $result")
        }
        return true
    }

    fun removeGlobalFx(packageName: String): Boolean {
        execCommand("settings put global audio_effects_enabled 0")
        return true
    }
}
