/* SPDX-License-Identifier: Apache-2.0 */

package com.soniclab.fx.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soniclab.fx.audio.FxSettings
import com.soniclab.fx.audio.SpatialProcessor

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                FxScreen(viewModel = viewModel())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FxScreen(viewModel: FxViewModel) {
    val settings by viewModel.settings.collectAsState()
    val status by viewModel.status.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SonicLab FX", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Status & Toggle
            StatusCard(status, settings.enabled, viewModel)

            // === Preamp ===
            SectionTitle("Preamp")
            SliderSection("Gain", settings.preampGainDb, -12f..12f,
                "${"%.1f".format(settings.preampGainDb)} dB") { viewModel.updatePreamp(it) }

            // === Balance ===
            SectionTitle("Balance")
            SliderSection("L ← → R", settings.balance, -1f..1f,
                balanceLabel(settings.balance)) { viewModel.updateBalance(it) }

            // === EQ ===
            SectionTitle("Equalizer")
            for (b in 0 until FxSettings.BAND_COUNT) {
                SliderSection(
                    title = formatFreq(FxSettings.bandFrequency(b)),
                    value = settings.eqBandGains[b],
                    valueRange = -15f..15f,
                    label = "${"%.1f".format(settings.eqBandGains[b])} dB",
                    onValueChange = { viewModel.updateEqBand(b, it) }
                )
            }
            TextButton(onClick = { viewModel.resetEq() }) { Text("Reset EQ") }

            HorizontalDivider()

            // === Tone ===
            SectionTitle("Tone")
            SliderSection("Bass", settings.bassGainDb, -12f..12f,
                "${"%.1f".format(settings.bassGainDb)} dB") { viewModel.updateBass(it) }
            SliderSection("Treble", settings.trebleGainDb, -12f..12f,
                "${"%.1f".format(settings.trebleGainDb)} dB") { viewModel.updateTreble(it) }

            HorizontalDivider()

            // === AI Enhance ===
            SectionTitle("AI Enhance")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Enhance", fontSize = 14.sp)
                    Text("Adaptive loudness & clarity boost", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.enhanceEnabled, onCheckedChange = { viewModel.toggleEnhance() })
            }

            // === Auto-normalize ===
            SectionTitle("Auto-normalize")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Normalize to −14 LUFS", fontSize = 14.sp)
                    Text("Consistent volume across tracks", fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = settings.autoNormalize, onCheckedChange = { viewModel.toggleAutoNormalize() })
            }

            HorizontalDivider()

            // === Spatial ===
            SectionTitle("3D / 8D Spatial")
            val modes = listOf(
                SpatialProcessor.MODE_OFF to "Off",
                SpatialProcessor.MODE_3D to "3D",
                SpatialProcessor.MODE_8D to "8D",
                SpatialProcessor.MODE_3D_8D to "3D + 8D",
                SpatialProcessor.MODE_SURROUND to "Surround",
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        onClick = { viewModel.setSpatialMode(mode) },
                        selected = settings.spatialMode == mode,
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
            if (settings.spatialMode != SpatialProcessor.MODE_OFF) {
                SliderSection("Width", settings.spatialWidth, 0f..1f,
                    "${"%.0f".format(settings.spatialWidth * 100)}%") { viewModel.updateSpatialWidth(it) }
                SliderSection("Rotation", settings.spatialRotation, 4f..60f,
                    "${"%.1f".format(settings.spatialRotation)}s") { viewModel.updateSpatialRotation(it) }
                SliderSection("Pan Depth", settings.spatialPanDepth, 0.1f..1f,
                    "${"%.0f".format(settings.spatialPanDepth * 100)}%") { viewModel.updateSpatialPanDepth(it) }
            }

            HorizontalDivider()

            // === Reverb ===
            SectionTitle("Reverb")
            SliderSection("Mix", settings.reverbMix, 0f..1f,
                "${"%.0f".format(settings.reverbMix * 100)}%") { viewModel.updateReverbMix(it) }
            SliderSection("Room Size", settings.reverbRoomSize, 0f..1f,
                "${"%.0f".format(settings.reverbRoomSize * 100)}%") { viewModel.updateReverbRoom(it) }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
}

@Composable
fun StatusCard(status: FxViewModel.Status, enabled: Boolean, viewModel: FxViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (enabled) "ACTIVE" else "BYPASSED",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(status.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = enabled, onCheckedChange = { viewModel.toggleEnabled() })
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!status.active) {
                    Button(onClick = { viewModel.registerEffect() }) { Text("Start", fontSize = 12.sp) }
                } else {
                    OutlinedButton(onClick = { viewModel.unregisterEffect() }) { Text("Stop", fontSize = 12.sp) }
                }
            }
        }
    }
}

@Composable
fun SliderSection(
    title: String, value: Float, valueRange: ClosedFloatingPointRange<Float>,
    label: String, onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, fontSize = 14.sp)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange, modifier = Modifier.fillMaxWidth())
    }
}

private fun formatFreq(freq: Float): String =
    if (freq >= 1000) "${"%.1f".format(freq / 1000)}kHz" else "${"%.0f".format(freq)}Hz"

private fun balanceLabel(v: Float): String = when {
    v < -0.01f -> "L ${"%.0f".format(v * -100)}%"
    v > 0.01f -> "R ${"%.0f".format(v * 100)}%"
    else -> "Center"
}
