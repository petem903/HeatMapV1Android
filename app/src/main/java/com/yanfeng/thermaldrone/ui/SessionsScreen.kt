package com.yanfeng.thermaldrone.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yanfeng.thermaldrone.model.SessionInfo
import com.yanfeng.thermaldrone.util.Format
import com.yanfeng.thermaldrone.viewmodel.SessionsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** SESSIONS tab: thumbnail + location + count, swipe-to-delete, exports. */
@Composable
fun SessionsScreen(vm: SessionsViewModel, onOpenSession: (String) -> Unit) {
    val sessions by vm.sessions.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }
    LaunchedEffect(status) { if (status != null) { kotlinx.coroutines.delay(3000); vm.clearStatus() } }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions (${sessions.size})", fontSize = 18.sp, modifier = Modifier.weight(1f))
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
            Spacer(Modifier.width(10.dp))
            OutlinedButton(onClick = { vm.refresh() }, modifier = Modifier.sizeIn(minHeight = 48.dp)) { Text("Refresh") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { vm.exportAll() }, enabled = !busy, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                Text("Export all")
            }
        }

        status?.let {
            Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
        }

        if (sessions.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Text("No sessions yet — capture from the FLY tab.", Modifier.align(Alignment.Center))
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sessions, key = { it.name }) { s ->
                    SessionRow(s, busy, onOpen = { onOpenSession(s.name) },
                        onExport = { vm.exportSession(s.name) }, onDelete = { vm.delete(s.name) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SessionRow(
    s: SessionInfo,
    busy: Boolean,
    onOpen: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.error).padding(end = 20.dp)
            ) {
                Icon(Icons.Default.Delete, "delete", Modifier.align(Alignment.CenterEnd), tint = Color.White)
            }
        }
    ) {
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val thumb = remember(s.thumbnailPath) {
                    s.thumbnailPath?.let { android.graphics.BitmapFactory.decodeFile(it) }
                }
                if (thumb != null) {
                    Image(thumb.asImageBitmap(), null, Modifier.size(width = 96.dp, height = 72.dp), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.size(width = 96.dp, height = 72.dp).background(MaterialTheme.colorScheme.surfaceVariant))
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(s.name, fontSize = 15.sp)
                    Text("${s.captureCount} capture(s)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (s.latitude != null && s.longitude != null) {
                        Text(Format.gps(s.latitude, s.longitude), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    s.firstCaptureMs?.let {
                        Text(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(it)),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Button(onClick = onExport, enabled = !busy, modifier = Modifier.sizeIn(minHeight = 48.dp)) {
                    Text("Export")
                }
            }
        }
    }
}
