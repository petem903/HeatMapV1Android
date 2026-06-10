package com.yanfeng.thermaldrone

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yanfeng.thermaldrone.ui.FlyScreen
import com.yanfeng.thermaldrone.ui.InspectScreen
import com.yanfeng.thermaldrone.ui.SessionsScreen
import com.yanfeng.thermaldrone.ui.SettingsScreen
import com.yanfeng.thermaldrone.ui.theme.HeatMapTheme
import com.yanfeng.thermaldrone.viewmodel.FlyViewModel
import com.yanfeng.thermaldrone.viewmodel.InspectViewModel
import com.yanfeng.thermaldrone.viewmodel.SessionsViewModel
import com.yanfeng.thermaldrone.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HeatMapTheme { MainScreen() }
        }
    }
}

/** Factory injecting the App composition root into ViewModels. */
class AppVmFactory(private val app: App) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        FlyViewModel::class.java -> FlyViewModel(app) as T
        InspectViewModel::class.java -> InspectViewModel(app) as T
        SessionsViewModel::class.java -> SessionsViewModel(app) as T
        SettingsViewModel::class.java -> SettingsViewModel(app) as T
        else -> throw IllegalArgumentException("Unknown VM $modelClass")
    }
}

private enum class Tab(val label: String) { FLY("FLY"), INSPECT("INSPECT"), SESSIONS("SESSIONS"), SETTINGS("SETTINGS") }

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as App
    val factory = AppVmFactory(app)
    val flyVm: FlyViewModel = viewModel(factory = factory)
    val inspectVm: InspectViewModel = viewModel(factory = factory)
    val sessionsVm: SessionsViewModel = viewModel(factory = factory)
    val settingsVm: SettingsViewModel = viewModel(factory = factory)

    var tab by rememberSaveable { mutableStateOf(Tab.FLY.name) }
    var inspectSession by rememberSaveable { mutableStateOf("default") }
    val current = Tab.valueOf(tab)

    // FLY tab locks landscape; others free.
    val activity = context as? Activity
    LaunchedEffect(current) {
        activity?.requestedOrientation =
            if (current == Tab.FLY) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = current == t,
                        onClick = { tab = t.name },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.FLY -> Icons.Default.FlightTakeoff
                                    Tab.INSPECT -> Icons.Default.Search
                                    Tab.SESSIONS -> Icons.Default.Folder
                                    Tab.SETTINGS -> Icons.Default.Settings
                                },
                                contentDescription = t.label
                            )
                        },
                        label = { Text(t.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (current) {
                Tab.FLY -> FlyScreen(flyVm)
                Tab.INSPECT -> InspectScreen(inspectVm, inspectSession) { inspectSession = it }
                Tab.SESSIONS -> SessionsScreen(sessionsVm) { name ->
                    inspectSession = name
                    tab = Tab.INSPECT.name
                }
                Tab.SETTINGS -> SettingsScreen(settingsVm)
            }
        }
    }
}
