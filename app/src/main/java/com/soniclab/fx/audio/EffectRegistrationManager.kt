package com.soniclab.fx.audio

import android.content.Context
import android.util.Log
import com.soniclab.fx.util.RootHelper
import com.soniclab.fx.util.ShizukuHelper

/**
 * Manages the registration of the global audio effect with the system.
 * Auto-selects the best available method: Direct → Shizuku → Root.
 */
class EffectRegistrationManager(private val context: Context) {

    private var activeMethod: RegistrationMethod? = null

    enum class RegistrationMethod {
        SHIZUKU, ROOT, NONE
    }

    data class RegistrationResult(
        val method: RegistrationMethod,
        val success: Boolean,
        val message: String
    )

    fun register(dspChain: DspChain): RegistrationResult {
        // Strategy 1: Shizuku
        if (ShizukuHelper.isAvailable(context)) {
            val result = tryShizukuRegistration()
            if (result.success) {
                activeMethod = RegistrationMethod.SHIZUKU
                GlobalAudioEffect.setRegistered(true)
                return result
            }
        }

        // Strategy 2: Root
        if (RootHelper.isRooted()) {
            val result = tryRootRegistration()
            if (result.success) {
                activeMethod = RegistrationMethod.ROOT
                GlobalAudioEffect.setRegistered(true)
                return result
            }
        }

        activeMethod = RegistrationMethod.NONE
        return RegistrationResult(
            RegistrationMethod.NONE, false,
            "No registration method available. Install Shizuku or root your device."
        )
    }

    fun unregister() {
        val pkg = context.packageName
        val commands = GlobalAudioEffect.buildUnregistrationCommands(pkg)
        for (cmd in commands) {
            when (activeMethod) {
                RegistrationMethod.SHIZUKU -> ShizukuHelper.execCommand(cmd)
                RegistrationMethod.ROOT -> RootHelper.execCommand(cmd)
                else -> {}
            }
        }
        activeMethod = null
        GlobalAudioEffect.setRegistered(false)
    }

    fun getActiveMethod(): RegistrationMethod = activeMethod ?: RegistrationMethod.NONE
    fun isRegistered(): Boolean = activeMethod != RegistrationMethod.NONE

    private fun tryShizukuRegistration(): RegistrationResult {
        return try {
            val pkg = context.packageName
            val commands = GlobalAudioEffect.buildRegistrationCommands(pkg)
            var allOk = true
            for (cmd in commands) {
                val result = ShizukuHelper.execCommand(cmd)
                if (result.contains("Error", ignoreCase = true)) {
                    Log.w(TAG, "Shizuku command failed: $cmd -> $result")
                    allOk = false
                }
            }
            if (allOk) {
                RegistrationResult(RegistrationMethod.SHIZUKU, true, "Shizuku registration successful")
            } else {
                RegistrationResult(RegistrationMethod.SHIZUKU, false, "Shizuku: some commands failed")
            }
        } catch (e: Exception) {
            RegistrationResult(RegistrationMethod.SHIZUKU, false, "Shizuku: ${e.message}")
        }
    }

    private fun tryRootRegistration(): RegistrationResult {
        return try {
            val pkg = context.packageName
            val commands = GlobalAudioEffect.buildRegistrationCommands(pkg)
            for (cmd in commands) {
                RootHelper.execCommand(cmd)
            }
            RegistrationResult(RegistrationMethod.ROOT, true, "Root registration successful")
        } catch (e: Exception) {
            RegistrationResult(RegistrationMethod.ROOT, false, "Root: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "EffectRegistrationMgr"
    }
}
