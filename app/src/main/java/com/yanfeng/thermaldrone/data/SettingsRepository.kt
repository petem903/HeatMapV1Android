package com.yanfeng.thermaldrone.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yanfeng.thermaldrone.AppConfig
import com.yanfeng.thermaldrone.processing.Palette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "heatmap_settings")

data class AppSettings(
    val useFahrenheit: Boolean = false,
    val defaultPalette: Palette = Palette.IRON,
    val denoiseKernel: Int = 1,
    val temporalFrames: Int = 1,
    val edgeOpacity: Float = 0.5f,
    val autoMaxCount: Int = 3,
    val autoMaxThresholdC: Float = 40f,
    val autoMinThresholdC: Float = 15f,
    val streamUrl: String = AppConfig.DEFAULT_STREAM_URL,
    val streamTempMinC: Float = AppConfig.DEFAULT_STREAM_TMIN_C,
    val streamTempMaxC: Float = AppConfig.DEFAULT_STREAM_TMAX_C
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val FAHRENHEIT = booleanPreferencesKey("use_fahrenheit")
        val PALETTE = stringPreferencesKey("default_palette")
        val DENOISE = intPreferencesKey("denoise_kernel")
        val TEMPORAL = intPreferencesKey("temporal_frames")
        val EDGE_OPACITY = floatPreferencesKey("edge_opacity")
        val AUTO_MAX_COUNT = intPreferencesKey("auto_max_count")
        val AUTO_MAX_THRESH = floatPreferencesKey("auto_max_thresh")
        val AUTO_MIN_THRESH = floatPreferencesKey("auto_min_thresh")
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_TMIN = floatPreferencesKey("stream_tmin")
        val STREAM_TMAX = floatPreferencesKey("stream_tmax")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            useFahrenheit = p[Keys.FAHRENHEIT] ?: false,
            defaultPalette = Palette.fromName(p[Keys.PALETTE] ?: Palette.IRON.name),
            denoiseKernel = p[Keys.DENOISE] ?: 1,
            temporalFrames = p[Keys.TEMPORAL] ?: 1,
            edgeOpacity = p[Keys.EDGE_OPACITY] ?: 0.5f,
            autoMaxCount = p[Keys.AUTO_MAX_COUNT] ?: 3,
            autoMaxThresholdC = p[Keys.AUTO_MAX_THRESH] ?: 40f,
            autoMinThresholdC = p[Keys.AUTO_MIN_THRESH] ?: 15f,
            streamUrl = p[Keys.STREAM_URL] ?: AppConfig.DEFAULT_STREAM_URL,
            streamTempMinC = p[Keys.STREAM_TMIN] ?: AppConfig.DEFAULT_STREAM_TMIN_C,
            streamTempMaxC = p[Keys.STREAM_TMAX] ?: AppConfig.DEFAULT_STREAM_TMAX_C
        )
    }

    suspend fun setFahrenheit(v: Boolean) = context.dataStore.edit { it[Keys.FAHRENHEIT] = v }
    suspend fun setDefaultPalette(p: Palette) = context.dataStore.edit { it[Keys.PALETTE] = p.name }
    suspend fun setDenoiseKernel(k: Int) = context.dataStore.edit { it[Keys.DENOISE] = k }
    suspend fun setTemporalFrames(n: Int) = context.dataStore.edit { it[Keys.TEMPORAL] = n }
    suspend fun setEdgeOpacity(o: Float) = context.dataStore.edit { it[Keys.EDGE_OPACITY] = o }
    suspend fun setAutoMaxCount(n: Int) = context.dataStore.edit { it[Keys.AUTO_MAX_COUNT] = n }
    suspend fun setAutoMaxThreshold(t: Float) = context.dataStore.edit { it[Keys.AUTO_MAX_THRESH] = t }
    suspend fun setAutoMinThreshold(t: Float) = context.dataStore.edit { it[Keys.AUTO_MIN_THRESH] = t }
    suspend fun setStreamUrl(u: String) = context.dataStore.edit { it[Keys.STREAM_URL] = u }
    suspend fun setStreamTempRange(minC: Float, maxC: Float) = context.dataStore.edit {
        it[Keys.STREAM_TMIN] = minC; it[Keys.STREAM_TMAX] = maxC
    }
}
