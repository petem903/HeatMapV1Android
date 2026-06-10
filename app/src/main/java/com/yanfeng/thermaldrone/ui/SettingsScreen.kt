package com.yanfeng.thermaldrone.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yanfeng.thermaldrone.AppConfig
import com.yanfeng.thermaldrone.processing.FrameProcessor
import com.yanfeng.thermaldrone.processing.Palette
import com.yanfeng.thermaldrone.util.QrCode
import com.yanfeng.thermaldrone.viewmodel.SettingsViewModel

/** SETTINGS tab: server card + QR, units, palette, denoising, storage, versions. */
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val commandLog by vm.commandLog.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- drone connection ----
        SettingCard("Drone connection") {
            Text(
                "Direct video link — the app opens this stream when you tap CONNECT on the FLY tab. " +
                    "RTSP, RTP and HTTP streams are supported.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            var urlText by remember(settings.streamUrl) { mutableStateOf(settings.streamUrl) }
            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                label = { Text("Stream URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.setStreamUrl(urlText) }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Save") }
                OutlinedButton(
                    onClick = { urlText = AppConfig.DEFAULT_STREAM_URL; vm.setStreamUrl(AppConfig.DEFAULT_STREAM_URL) },
                    modifier = Modifier.sizeIn(minHeight = 48.dp)
                ) { Text("Reset") }
            }
            Text(
                "Temperature calibration — video brightness is mapped onto this range:",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Dark pixels = ${settings.streamTempMinC.toInt()} °C", fontSize = 13.sp)
            Slider(
                value = settings.streamTempMinC,
                onValueChange = { vm.setStreamTempRange(it, settings.streamTempMaxC) },
                valueRange = -40f..100f
            )
            Text("Bright pixels = ${settings.streamTempMaxC.toInt()} °C", fontSize = 13.sp)
            Slider(
                value = settings.streamTempMaxC,
                onValueChange = { vm.setStreamTempRange(settings.streamTempMinC, it) },
                valueRange = 0f..400f
            )
        }

        // ---- ground control server card ----
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Laptop Ground Control", fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                val url = remember { vm.serverUrl }
                val token = remember { vm.sessionToken }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val qr = remember(url, token) {
                        runCatching { QrCode.generate("$url|token=$token", 360) }.getOrNull()
                    }
                    if (qr != null) {
                        Image(qr.asImageBitmap(), "server QR", Modifier.size(150.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(url, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
                        Text("Token: $token", fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("GET /stream  /telemetry  /snapshot  /health", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("POST /command  (Authorization: Bearer $token)", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (commandLog.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Recent commands", fontSize = 12.sp)
                    commandLog.takeLast(5).forEach {
                        Text(it, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---- units ----
        SettingCard("Units") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("°C", fontSize = 14.sp)
                Switch(
                    checked = settings.useFahrenheit,
                    onCheckedChange = { vm.setFahrenheit(it) },
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                Text("°F", fontSize = 14.sp)
            }
        }

        // ---- default palette ----
        SettingCard("Default palette") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Palette.entries.forEach { p ->
                    FilterChip(
                        selected = settings.defaultPalette == p,
                        onClick = { vm.setDefaultPalette(p) },
                        label = { Text(p.displayName) },
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    )
                }
            }
        }

        // ---- denoising defaults ----
        SettingCard("Denoising defaults") {
            Text("Spatial kernel: ${if (settings.denoiseKernel < 3) "off" else "${settings.denoiseKernel}×${settings.denoiseKernel}"}", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1, 3, 5, 7).forEach { k ->
                    FilterChip(
                        selected = settings.denoiseKernel == k,
                        onClick = { vm.setDenoiseKernel(k) },
                        label = { Text(if (k == 1) "Off" else "$k") },
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    )
                }
            }
            Text("Temporal averaging: ${settings.temporalFrames} frame(s)", fontSize = 13.sp)
            Slider(
                value = settings.temporalFrames.toFloat(),
                onValueChange = { vm.setTemporalFrames(it.toInt()) },
                valueRange = 1f..8f, steps = 6
            )
            Text("Edge overlay opacity: ${(settings.edgeOpacity * 100).toInt()}%", fontSize = 13.sp)
            Slider(value = settings.edgeOpacity, onValueChange = { vm.setEdgeOpacity(it) }, valueRange = 0f..1f)
        }

        // ---- auto extrema ----
        SettingCard("Auto hot/cold spots") {
            Text("Count (N): ${settings.autoMaxCount}", fontSize = 13.sp)
            Slider(
                value = settings.autoMaxCount.toFloat(),
                onValueChange = { vm.setAutoMaxCount(it.toInt().coerceIn(1, 20)) },
                valueRange = 1f..20f, steps = 18
            )
            Text("Hot threshold: ${settings.autoMaxThresholdC.toInt()} °C", fontSize = 13.sp)
            Slider(value = settings.autoMaxThresholdC, onValueChange = { vm.setAutoMaxThreshold(it) }, valueRange = -20f..150f)
            Text("Cold threshold: ${settings.autoMinThresholdC.toInt()} °C", fontSize = 13.sp)
            Slider(value = settings.autoMinThresholdC, onValueChange = { vm.setAutoMinThreshold(it) }, valueRange = -40f..100f)
        }

        // ---- storage & versions ----
        SettingCard("Storage & versions") {
            Text("Sessions: ${vm.storagePath}", fontSize = 12.sp)
            Text("App: HeatMapV1Android ${vm.appVersion}", fontSize = 12.sp)
            Text("OpenCV: ${if (FrameProcessor.openCvReady) "ready" else "unavailable (edge overlay disabled)"}", fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 16.sp)
            content()
        }
    }
}
