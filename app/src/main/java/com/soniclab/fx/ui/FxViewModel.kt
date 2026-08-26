/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soniclab.fx.audio.DspChain
import com.soniclab.fx.audio.EffectRegistrationManager
import com.soniclab.fx.audio.FxSettings
import com.soniclab.fx.audio.SpatialProcessor
import com.soniclab.fx.service.FxOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FxViewModel(application: Application) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()
    private val dspChain = DspChain()
    private val regManager = EffectRegistrationManager(context)

    private val _settings = MutableStateFlow(FxSettings())
    val settings: StateFlow<FxSettings> = _settings.asStateFlow()

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    data class Status(
        val active: Boolean = false,
        val message: String = "Inactive",
    )

    init {
        _settings.value = FxSettings.load(context)
    }

    fun toggleEnabled() {
        val s = _settings.value.copy(enabled = !_settings.value.enabled)
        applySettings(s)
    }

    fun updateEqBand(band: Int, gainDb: Float) {
        val gains = _settings.value.eqBandGains.copyOf()
        gains[band] = gainDb.coerceIn(-15f, 15f)
        applySettings(_settings.value.copy(eqBandGains = gains))
    }
    fun resetEq() = applySettings(_settings.value.copy(eqBandGains = FloatArray(FxSettings.BAND_COUNT)))
    fun updateBass(gainDb: Float) = applySettings(_settings.value.copy(bassGainDb = gainDb.coerceIn(-12f, 12f)))
    fun updateTreble(gainDb: Float) = applySettings(_settings.value.copy(trebleGainDb = gainDb.coerceIn(-12f, 12f)))
    fun updatePreamp(gainDb: Float) = applySettings(_settings.value.copy(preampGainDb = gainDb.coerceIn(-12f, 12f)))
    fun updateBalance(balance: Float) = applySettings(_settings.value.copy(balance = balance.coerceIn(-1f, 1f)))
    fun updateReverbMix(mix: Float) = applySettings(_settings.value.copy(reverbMix = mix.coerceIn(0f, 1f)))
    fun updateReverbRoom(size: Float) = applySettings(_settings.value.copy(reverbRoomSize = size.coerceIn(0f, 1f)))
    fun toggleEnhance() = applySettings(_settings.value.copy(enhanceEnabled = !_settings.value.enhanceEnabled))
    fun toggleAutoNormalize() = applySettings(_settings.value.copy(autoNormalize = !_settings.value.autoNormalize))

    fun setSpatialMode(mode: Int) {
        applySettings(_settings.value.copy(
            spatialMode = mode,
            spatial3d = mode == SpatialProcessor.MODE_3D || mode == SpatialProcessor.MODE_3D_8D || mode == SpatialProcessor.MODE_SURROUND,
            spatial8d = mode == SpatialProcessor.MODE_8D || mode == SpatialProcessor.MODE_3D_8D,
            surround = mode == SpatialProcessor.MODE_SURROUND,
        ))
    }
    fun updateSpatialWidth(w: Float) = applySettings(_settings.value.copy(spatialWidth = w.coerceIn(0f, 1f)))
    fun updateSpatialRotation(r: Float) = applySettings(_settings.value.copy(spatialRotation = r.coerceIn(4f, 60f)))
    fun updateSpatialPanDepth(d: Float) = applySettings(_settings.value.copy(spatialPanDepth = d.coerceIn(0.1f, 1f)))

    fun registerEffect(activity: MainActivity) {
        viewModelScope.launch {
            _status.value = _status.value.copy(message = "Checking permissions...")
            if (!hasForegroundServicePermission()) {
                _status.value = _status.value.copy(message = "Requesting permission...")
                activity.requestForegroundServicePermission()
                // Wait a bit for permission to be granted
                kotlinx.coroutines.delay(1000)
                if (!hasForegroundServicePermission()) {
                    _status.value = _status.value.copy(active = false, message = "Permission denied")
                    return@launch
                }
            }
            val result = regManager.register()
            _status.value = _status.value.copy(active = result.success, message = result.message)
            if (result.success) startService()
        }
    }
    fun unregisterEffect() {
        regManager.unregister()
        stopService()
        _status.value = Status()
    }

    private fun hasForegroundServicePermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.FOREGROUND_SERVICE) == PackageManager.PERMISSION_GRANTED
        }
        return true // Not required on older Android
    }

    private fun applySettings(s: FxSettings) {
        _settings.value = s
        s.save(context)
        dspChain.updateSettings(s)
        updateServiceSettings(s)
    }

    private fun startService() {
        val intent = Intent(context, FxOverlayService::class.java)
        ContextCompat.startForegroundService(context, intent)
        _status.value = _status.value.copy(active = true)
    }
    private fun stopService() {
        val intent = Intent(context, FxOverlayService::class.java).apply { action = FxOverlayService.ACTION_STOP }
        ContextCompat.startForegroundService(context, intent)
        _status.value = _status.value.copy(active = false)
    }
    private fun updateServiceSettings(s: FxSettings) {
        val intent = Intent(context, FxOverlayService::class.java).apply {
            action = FxOverlayService.ACTION_UPDATE_SETTINGS
            putExtra("enabled", s.enabled)
            putExtra("eqGains", s.eqBandGains)
            putExtra("bass", s.bassGainDb)
            putExtra("treble", s.trebleGainDb)
            putExtra("balance", s.balance)
            putExtra("reverbMix", s.reverbMix)
            putExtra("reverbRoom", s.reverbRoomSize)
            putExtra("preamp", s.preampGainDb)
            putExtra("enhance", s.enhanceEnabled)
            putExtra("autoNorm", s.autoNormalize)
            putExtra("spatialMode", s.spatialMode)
            putExtra("spatial3d", s.spatial3d)
            putExtra("spatial8d", s.spatial8d)
            putExtra("surround", s.surround)
            putExtra("spatialWidth", s.spatialWidth)
            putExtra("spatialRotation", s.spatialRotation)
            putExtra("spatialPanDepth", s.spatialPanDepth)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    override fun onCleared() {
        regManager.unregister()
        super.onCleared()
    }
}
