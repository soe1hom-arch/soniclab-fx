/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.audio

import android.content.Context
import android.util.Log

/**
 * Manages effect registration lifecycle.
 * Now simplified — actual audio session detection and effect attachment
 * is handled by FxOverlayService via AudioSessionDetector + SystemEffectManager.
 *
 * This class only tracks whether the service is active and provides
 * status information for the UI.
 */
class EffectRegistrationManager(private val context: Context) {

    private var active = false

    fun register(): RegistrationResult {
        active = true
        return RegistrationResult(true, "Monitoring audio sessions")
    }

    fun unregister() {
        active = false
    }

    fun isActive(): Boolean = active

    data class RegistrationResult(
        val success: Boolean,
        val message: String
    )

    companion object {
        private const val TAG = "EffectRegistrationMgr"
    }
}
