package com.yanfeng.thermaldrone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yanfeng.thermaldrone.App
import com.yanfeng.thermaldrone.data.AppSettings
import com.yanfeng.thermaldrone.processing.Palette
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** SETTINGS tab. */
class SettingsViewModel(private val app: App) : ViewModel() {

    val settings: StateFlow<AppSettings> = app.settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val serverUrl: String get() = app.server.serverUrl()
    val sessionToken: String get() = app.server.sessionToken
    val commandLog = app.server.commandLog
    val storagePath: String get() = app.sessionRepo.sessionsRoot().absolutePath
    val appVersion: String = "2.1 (versionCode 3)"

    fun setFahrenheit(v: Boolean) = viewModelScope.launch { app.settingsRepo.setFahrenheit(v) }
    fun setDefaultPalette(p: Palette) = viewModelScope.launch { app.settingsRepo.setDefaultPalette(p) }
    fun setDenoiseKernel(k: Int) = viewModelScope.launch { app.settingsRepo.setDenoiseKernel(k) }
    fun setTemporalFrames(n: Int) = viewModelScope.launch { app.settingsRepo.setTemporalFrames(n) }
    fun setEdgeOpacity(o: Float) = viewModelScope.launch { app.settingsRepo.setEdgeOpacity(o) }
    fun setAutoMaxCount(n: Int) = viewModelScope.launch { app.settingsRepo.setAutoMaxCount(n) }
    fun setAutoMaxThreshold(t: Float) = viewModelScope.launch { app.settingsRepo.setAutoMaxThreshold(t) }
    fun setAutoMinThreshold(t: Float) = viewModelScope.launch { app.settingsRepo.setAutoMinThreshold(t) }
    fun setStreamUrl(u: String) = viewModelScope.launch { app.settingsRepo.setStreamUrl(u.trim()) }
    fun setStreamTempRange(minC: Float, maxC: Float) = viewModelScope.launch { app.settingsRepo.setStreamTempRange(minC, maxC) }
}
