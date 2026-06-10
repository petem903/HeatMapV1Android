package com.yanfeng.thermaldrone.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yanfeng.thermaldrone.model.Measurement
import com.yanfeng.thermaldrone.util.Format
import com.yanfeng.thermaldrone.viewmodel.ActiveTool
import com.yanfeng.thermaldrone.viewmodel.InspectViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** INSPECT tab: captured-frame measurements + annotations. */
@Composable
fun InspectScreen(vm: InspectViewModel, sessionName: String, onSessionChange: (String) -> Unit) {
    val captures by vm.captures.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val meta by vm.meta.collectAsStateWithLifecycle()
    val bitmap by vm.bitmap.collectAsStateWithLifecycle()
    val tool by vm.tool.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    var annotateAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var annotationText by remember { mutableStateOf("") }

    LaunchedEffect(sessionName) { vm.loadSession(sessionName) }
    LaunchedEffect(status) { if (status != null) { kotlinx.coroutines.delay(2500); vm.clearStatus() } }

    Row(Modifier.fillMaxSize()) {
        // left: viewer
        Column(Modifier.weight(0.72f).fillMaxHeight()) {
            Text(
                "Session: $sessionName  (${captures.size} captures)",
                modifier = Modifier.padding(8.dp), fontSize = 14.sp
            )

            // capture strip
            LazyRow(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(captures) { cap ->
                    val thumb = remember(cap.jpg.absolutePath) {
                        android.graphics.BitmapFactory.decodeFile(cap.jpg.absolutePath)
                            ?.let { Bitmap.createScaledBitmap(it, 96, 77, true) }
                    }
                    Card(
                        Modifier
                            .size(width = 100.dp, height = 84.dp)
                            .clickable { vm.select(cap) }
                    ) {
                        if (thumb != null) {
                            Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else Text("?", Modifier.padding(8.dp))
                    }
                }
            }

            // image + gesture layer
            Box(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                val bmp = bitmap
                if (bmp == null) {
                    Text("Select a capture", Modifier.align(Alignment.Center))
                } else {
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    fun toFrame(px: Float, py: Float): Pair<Int, Int>? {
                        if (boxSize.width == 0) return null
                        val scale = minOf(boxSize.width.toFloat() / bmp.width, boxSize.height.toFloat() / bmp.height)
                        val w = bmp.width * scale; val h = bmp.height * scale
                        val l = (boxSize.width - w) / 2f; val t = (boxSize.height - h) / 2f
                        if (px < l || py < t || px > l + w || py > t + h) return null
                        return ((px - l) / scale).toInt() to ((py - t) / scale).toInt()
                    }
                    Image(
                        bmp.asImageBitmap(), null,
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { boxSize = it }
                            .pointerInput(tool) {
                                detectTapGestures(
                                    onTap = { o ->
                                        val p = toFrame(o.x, o.y) ?: return@detectTapGestures
                                        if (tool == ActiveTool.NONE) { annotateAt = p } else vm.onTap(p.first, p.second)
                                    },
                                    onLongPress = { o -> toFrame(o.x, o.y)?.let { annotateAt = it } }
                                )
                            }
                            .pointerInput(tool) {
                                var start: Pair<Int, Int>? = null
                                var last: Pair<Int, Int>? = null
                                detectDragGestures(
                                    onDragStart = { o -> start = toFrame(o.x, o.y); last = start },
                                    onDragEnd = {
                                        val s = start; val e = last
                                        if (s != null && e != null) vm.onDragComplete(s.first, s.second, e.first, e.second)
                                        start = null; last = null
                                    },
                                    onDragCancel = { start = null; last = null }
                                ) { change, _ -> toFrame(change.position.x, change.position.y)?.let { last = it } }
                            },
                        contentScale = ContentScale.Fit
                    )
                }
                status?.let {
                    Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xCC000000)).padding(8.dp))
                }
            }

            // tools
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "Annotate" to ActiveTool.NONE,
                    "Spot" to ActiveTool.SPOT,
                    "Line" to ActiveTool.LINE,
                    "Rect" to ActiveTool.RECT,
                    "Hot N" to ActiveTool.AUTO_MAX,
                    "Cold N" to ActiveTool.AUTO_MIN
                ).forEach { (label, t) ->
                    FilterChip(
                        selected = tool == t, onClick = { vm.setTool(t) },
                        label = { Text(label) },
                        modifier = Modifier.sizeIn(minHeight = 48.dp)
                    )
                }
            }
        }

        // right: measurement list panel
        Column(
            Modifier
                .weight(0.28f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp)
        ) {
            Text("Measurements", fontSize = 15.sp)
            val m = meta
            if (m == null) {
                Text("—", Modifier.padding(top = 8.dp))
            } else {
                Text(
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(m.timestampMs)),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(m.measurements, key = { it.id }) { meas ->
                        MeasurementRow(meas, m.useFahrenheit) { vm.removeMeasurement(meas.id) }
                    }
                }
            }
        }
    }

    // annotation dialog
    annotateAt?.let { at ->
        AlertDialog(
            onDismissRequest = { annotateAt = null; annotationText = "" },
            title = { Text("Annotate point (${at.first}, ${at.second})") },
            text = {
                OutlinedTextField(value = annotationText, onValueChange = { annotationText = it }, label = { Text("Note") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (annotationText.isNotBlank()) vm.annotate(at.first, at.second, annotationText.trim())
                    annotateAt = null; annotationText = ""
                }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { annotateAt = null; annotationText = "" },
                    modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun MeasurementRow(m: Measurement, f: Boolean, onDelete: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${m.type.name}${if (m.annotation.isNotBlank()) " — ${m.annotation}" else ""}", fontSize = 12.sp)
                val detail = when {
                    m.spotTempC != null -> Format.temp(m.spotTempC, f)
                    m.lineStats != null -> "min ${Format.temp(m.lineStats.minC, f)} avg ${Format.temp(m.lineStats.avgC, f)} max ${Format.temp(m.lineStats.maxC, f)}"
                    m.rectStats != null -> "min ${Format.temp(m.rectStats.minC, f)} avg ${Format.temp(m.rectStats.avgC, f)} max ${Format.temp(m.rectStats.maxC, f)} σ ${"%.2f".format(m.rectStats.stdevC)}"
                    m.extrema.isNotEmpty() -> m.extrema.joinToString(" ") { Format.temp(it.tempC, f) }
                    else -> ""
                }
                Text(detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Delete, "delete")
            }
        }
    }
}
