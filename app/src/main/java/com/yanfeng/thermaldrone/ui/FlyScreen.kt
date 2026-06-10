package com.yanfeng.thermaldrone.ui

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yanfeng.thermaldrone.AppConfig
import com.yanfeng.thermaldrone.model.ConnectionState
import com.yanfeng.thermaldrone.processing.Palette
import com.yanfeng.thermaldrone.ui.components.ThermalSurfaceView
import com.yanfeng.thermaldrone.ui.components.WarningBanner
import com.yanfeng.thermaldrone.ui.theme.OkGreen
import com.yanfeng.thermaldrone.ui.theme.WarnAmber
import com.yanfeng.thermaldrone.util.Format
import com.yanfeng.thermaldrone.viewmodel.ActiveTool
import com.yanfeng.thermaldrone.viewmodel.FlyViewModel
import com.yanfeng.thermaldrone.viewmodel.ViewerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * FLY tab — live drone video viewer.
 *
 * Layout: slim status bar on top, full-bleed viewer in the middle with a
 * vertical measurement rail on the right, and a single bottom action bar
 * (Connect / Capture / palette / view mode / display sheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlyScreen(vm: FlyViewModel) {
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val banner by vm.banner.collectAsStateWithLifecycle()
    val bitmap by vm.displayBitmap.collectAsStateWithLifecycle()
    val fps by vm.fps.collectAsStateWithLifecycle()
    val stats by vm.liveStats.collectAsStateWithLifecycle()
    val tool by vm.activeTool.collectAsStateWithLifecycle()
    val viewerMode by vm.viewerMode.collectAsStateWithLifecycle()
    val settings by vm.processSettings.collectAsStateWithLifecycle()
    val toast by vm.captureToast.collectAsStateWithLifecycle()
    val f by vm.useFahrenheit.collectAsStateWithLifecycle()

    var showDisplaySheet by remember { mutableStateOf(false) }

    LaunchedEffect(toast) { if (toast != null) { delay(2500); vm.clearToast() } }

    Column(Modifier.fillMaxSize()) {
        // ---- status bar ----
        StatusBar(state, fps, stats.minC, stats.maxC, f)

        banner?.let {
            WarningBanner(it, critical = false, modifier = Modifier.clickable { vm.clearBanner() })
        }

        // ---- viewer ----
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            val textureRef = remember { mutableStateOf<TextureView?>(null) }
            val surfaceView = remember { mutableStateOf<ThermalSurfaceView?>(null) }

            // video TextureView (behind) — MediaPlayer decodes the RTSP feed here
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                                vm.setVideoSurface(Surface(st))
                            }
                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                vm.setVideoSurface(null); return true
                            }
                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                        textureRef.value = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // processed thermal viewer (front) — palettes, overlays, measurements
            AndroidView(
                factory = { ctx -> ThermalSurfaceView(ctx).also { surfaceView.value = it } },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tool) {
                        detectTapGestures { offset ->
                            surfaceView.value?.viewToFrame(offset.x, offset.y)?.let { (x, y) -> vm.onTap(x, y) }
                        }
                    }
                    .pointerInput(tool) {
                        var start: Pair<Int, Int>? = null
                        var last: Pair<Int, Int>? = null
                        detectDragGestures(
                            onDragStart = { offset ->
                                start = surfaceView.value?.viewToFrame(offset.x, offset.y)
                                last = start
                            },
                            onDragEnd = {
                                val s = start; val e = last
                                if (s != null && e != null) vm.onDragComplete(s.first, s.second, e.first, e.second)
                                start = null; last = null
                            },
                            onDragCancel = { start = null; last = null }
                        ) { change, _ ->
                            surfaceView.value?.viewToFrame(change.position.x, change.position.y)?.let { last = it }
                        }
                    }
            )

            LaunchedEffect(bitmap, viewerMode) { bitmap?.let { surfaceView.value?.setFrame(it) } }

            // sample the decoded video into the measurement pipeline while connected
            LaunchedEffect(state) {
                if (state == ConnectionState.CONNECTED) {
                    while (isActive) {
                        textureRef.value
                            ?.getBitmap(AppConfig.THERMAL_WIDTH, AppConfig.THERMAL_HEIGHT)
                            ?.let { vm.submitVideoBitmap(it) }
                        delay(AppConfig.VIDEO_SAMPLE_MS)
                    }
                }
            }
            DisposableEffect(Unit) { onDispose { surfaceView.value = null; textureRef.value = null } }

            // disconnected placeholder
            if (state == ConnectionState.DISCONNECTED) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("NO DRONE FEED", color = Color(0xFF888888), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(vm.streamUrl, color = Color(0xFF555555), fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { vm.connect() }) { Text("CONNECT TO DRONE") }
                }
            }
            if (state == ConnectionState.CONNECTING) {
                Text(
                    "CONNECTING…",
                    color = WarnAmber, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // measurement rail (right)
            if (state == ConnectionState.CONNECTED) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(8.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RailButton(Icons.Default.Adjust, "Spot", tool == ActiveTool.SPOT) {
                        vm.setTool(if (tool == ActiveTool.SPOT) ActiveTool.NONE else ActiveTool.SPOT)
                    }
                    RailButton(Icons.Default.Timeline, "Line", tool == ActiveTool.LINE) {
                        vm.setTool(if (tool == ActiveTool.LINE) ActiveTool.NONE else ActiveTool.LINE)
                    }
                    RailButton(Icons.Default.CropSquare, "Area", tool == ActiveTool.RECT) {
                        vm.setTool(if (tool == ActiveTool.RECT) ActiveTool.NONE else ActiveTool.RECT)
                    }
                    RailButton(Icons.Default.LocalFireDepartment, "Hot", tool == ActiveTool.AUTO_MAX) {
                        vm.setTool(if (tool == ActiveTool.AUTO_MAX) ActiveTool.NONE else ActiveTool.AUTO_MAX)
                    }
                    RailButton(Icons.Default.AcUnit, "Cold", tool == ActiveTool.AUTO_MIN) {
                        vm.setTool(if (tool == ActiveTool.AUTO_MIN) ActiveTool.NONE else ActiveTool.AUTO_MIN)
                    }
                    RailButton(Icons.Default.Delete, "Clear", false) { vm.clearMeasurements() }
                }
            }

            toast?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        // ---- bottom action bar ----
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                ConnectionState.DISCONNECTED ->
                    Button(
                        onClick = { vm.connect() },
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    ) { Text("CONNECT") }
                ConnectionState.CONNECTING ->
                    Button(onClick = {}, enabled = false, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("…") }
                else ->
                    OutlinedButton(
                        onClick = { vm.disconnect() },
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    ) { Text("DISCONNECT") }
            }

            Button(
                onClick = { vm.capture() },
                enabled = state == ConnectionState.CONNECTED,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.sizeIn(minHeight = 48.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("CAPTURE")
            }

            Spacer(Modifier.weight(1f))

            PaletteDropdown(settings.palette) { vm.setPalette(it) }

            FilterChip(
                selected = viewerMode == ViewerMode.OVERLAY,
                onClick = {
                    vm.setViewerMode(if (viewerMode == ViewerMode.OVERLAY) ViewerMode.THERMAL_ONLY else ViewerMode.OVERLAY)
                },
                label = { Text("Blend") },
                modifier = Modifier.sizeIn(minHeight = 48.dp)
            )

            OutlinedButton(onClick = { showDisplaySheet = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Icon(Icons.Default.Tune, "Display settings", Modifier.size(18.dp))
            }
        }
    }

    // ---- display settings sheet ----
    if (showDisplaySheet) {
        ModalBottomSheet(onDismissRequest = { showDisplaySheet = false }) {
            Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Display", fontSize = 17.sp, fontWeight = FontWeight.Bold)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto temperature range", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = settings.autoRange, onCheckedChange = { vm.setAutoRange(it) })
                }
                if (!settings.autoRange) {
                    Text("Min ${Format.temp(settings.manualMinC, f)}", fontSize = 12.sp)
                    Slider(
                        value = settings.manualMinC,
                        onValueChange = { vm.setManualRange(it, settings.manualMaxC) },
                        valueRange = -20f..150f
                    )
                    Text("Max ${Format.temp(settings.manualMaxC, f)}", fontSize = 12.sp)
                    Slider(
                        value = settings.manualMaxC,
                        onValueChange = { vm.setManualRange(settings.manualMinC, it) },
                        valueRange = -20f..150f
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Edge outline overlay", Modifier.weight(1f), fontSize = 14.sp)
                    Switch(checked = settings.edgeOverlay, onCheckedChange = { vm.setEdgeOverlay(it) })
                }
            }
        }
    }
}

// ---- pieces ---------------------------------------------------------------

@Composable
private fun StatusBar(state: ConnectionState, fps: Int, minC: Float, maxC: Float, fahrenheit: Boolean) {
    val (dotColor, label) = when (state) {
        ConnectionState.DISCONNECTED -> Color(0xFF757575) to "OFFLINE"
        ConnectionState.CONNECTING -> WarnAmber to "CONNECTING"
        else -> OkGreen to "LIVE"
    }
    Row(
        Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.weight(1f))
        if (state == ConnectionState.CONNECTED) {
            Text("▼ ${Format.temp(minC, fahrenheit)}", color = Color(0xFF80D8FF), fontSize = 12.sp)
            Text("▲ ${Format.temp(maxC, fahrenheit)}", color = Color(0xFFFF8A65), fontSize = 12.sp)
            Text("$fps fps", color = Color(0xFF9E9E9E), fontSize = 12.sp)
        }
    }
}

@Composable
private fun RailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (selected) Color.Black else Color.White
    Column(
        Modifier
            .clickable(onClick = onClick)
            .background(bg, RoundedCornerShape(10.dp))
            .sizeIn(minWidth = 52.dp, minHeight = 48.dp)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, label, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, color = fg, fontSize = 10.sp)
    }
}

@Composable
private fun PaletteDropdown(current: Palette, onSet: (Palette) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
            Text(current.displayName)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Palette.entries.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.displayName) },
                    onClick = { onSet(p); open = false },
                    modifier = Modifier.height(48.dp)
                )
            }
        }
    }
}
