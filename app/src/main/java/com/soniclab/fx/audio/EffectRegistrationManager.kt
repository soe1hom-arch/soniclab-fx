package com.soniclab.fx.audio

import android.content.Context
import android.media.audiofx.AudioEffect
import android.util.Log
import com.soniclab.fx.util.RootHelper
import com.soniclab.fx.util.ShizukuHelper

/**
 * Manages the registration of the global audio effect with the system.
 *
 * Tries three strategies in order:
 *  1. Direct AudioEffect API (works on Android 9 and below, or system apps)
 *  2. Shizuku privileged shell (Android 10+ without root)
 *  3. Root shell (rooted devices)
 */
class EffectRegistrationManager(private val context: Context) {

    private var activeMethod: RegistrationMethod? = null
    private var globalEffect: GlobalAudioEffect? = null

    enum class RegistrationMethod {
        DIRECT, SHIZUKU, ROOT, NONE
    }

    data class RegistrationResult(
        val method: RegistrationMethod,
        val success: Boolean,
        val message: String
    )

    /**
     * Try to register the global audio effect using all available methods.
     */
    fun register(dspChain: DspChain): RegistrationResult {
        // Strategy 1: Direct registration
        val directResult = tryDirectRegistration(dspChain)
        if (directResult.success) {
            activeMethod = RegistrationMethod.DIRECT
            return directResult
        }

        // Strategy 2: Shizuku
        if (ShizukuHelper.isAvailable(context)) {
            val shizukuResult = tryShizukuRegistration()
            if (shizukuResult.success) {
                activeMethod = RegistrationMethod.SHIZUKU
                return shizukuResult
            }
        }

        // Strategy 3: Root
        if (RootHelper.isRooted()) {
            val rootResult = tryRootRegistration()
            if (rootResult.success) {
                activeMethod = RegistrationMethod.ROOT
                return rootResult
            }
        }

        activeMethod = RegistrationMethod.NONE
        return RegistrationResult(
            RegistrationMethod.NONE, false,
            "No registration method available. Install Shizuku or root your device."
        )
    }

    fun unregister() {
        globalEffect?.release()
        globalEffect = null
        activeMethod = null
    }

    fun getActiveMethod(): RegistrationMethod = activeMethod ?: RegistrationMethod.NONE

    fun isRegistered(): Boolean = globalEffect != null || activeMethod == RegistrationMethod.SHIZUKU || activeMethod == RegistrationMethod.ROOT

    private fun tryDirectRegistration(dspChain: DspChain): RegistrationResult {
        return try {
            val effect = GlobalAudioEffect { samples, frames, channels ->
                dspChain.process(samples)
            }
            globalEffect = effect
            effect.setEnabled(true)
            RegistrationResult(RegistrationMethod.DIRECT, true, "Direct registration successful")
        } catch (e: Exception) {
            Log.w(TAG, "Direct registration failed: ${e.message}")
            RegistrationResult(RegistrationMethod.DIRECT, false, "Direct: ${e.message}")
        }
    }

    private fun tryShizukuRegistration(): RegistrationResult {
        return try {
            val result = ShizukuHelper.execCommand("cmd audio effect enable session=0")
            if (!result.contains("Error", ignoreCase = true)) {
                RegistrationResult(RegistrationMethod.SHIZUKU, true, "Shizuku registration successful")
            } else {
                RegistrationResult(RegistrationMethod.SHIZUKU, false, "Shizuku: $result")
            }
        } catch (e: Exception) {
            RegistrationResult(RegistrationMethod.SHIZUKU, false, "Shizuku: ${e.message}")
        }
    }

    private fun tryRootRegistration(): RegistrationResult {
        return try {
            val pkg = context.packageName
            RootHelper.applyGlobalFx(pkg)
            RegistrationResult(RegistrationMethod.ROOT, true, "Root registration successful")
        } catch (e: Exception) {
            RegistrationResult(RegistrationMethod.ROOT, false, "Root: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "EffectRegistrationMgr"
    }
}
