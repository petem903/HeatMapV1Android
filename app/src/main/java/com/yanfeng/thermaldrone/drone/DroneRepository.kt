package com.yanfeng.thermaldrone.drone

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.util.Log
import android.view.Surface
import com.yanfeng.thermaldrone.model.ConnectionState
import com.yanfeng.thermaldrone.model.Telemetry
import com.yanfeng.thermaldrone.model.ThermalFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Direct connection to the drone's video feed (RTSP / RTP / HTTP stream) using
 * the platform MediaPlayer — works on Android 5.1+ controller tablets, no SDK
 * needed. The decoded video renders into the FLY screen's TextureView; sampled
 * frames are converted to pseudo-radiometric [ThermalFrame]s so all measurement
 * tools, palettes, capture and the /stream server keep working.
 */
class DroneRepository {
    companion object { private const val TAG = "DroneRepository" }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    /** Telemetry is not available over a plain video link — stays at defaults. */
    private val _telemetry = MutableStateFlow(Telemetry())
    val telemetry: StateFlow<Telemetry> = _telemetry

    /** Kept for session metadata / server compatibility; always false now. */
    private val _simulated = MutableStateFlow(false)
    val simulated: StateFlow<Boolean> = _simulated

    private val _banner = MutableStateFlow<String?>(null)
    val banner: StateFlow<String?> = _banner

    private val _frames = MutableSharedFlow<ThermalFrame>(replay = 1, extraBufferCapacity = 2)
    val frames: SharedFlow<ThermalFrame> = _frames

    /** True once MediaPlayer reports the first rendered video frame. */
    private val _streaming = MutableStateFlow(false)
    val streaming: StateFlow<Boolean> = _streaming

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var lastUrl: String? = null
    private val converting = AtomicBoolean(false)

    /** Video surface from the FLY screen's TextureView. */
    fun setSurface(s: Surface?) {
        surface = s
        try { mediaPlayer?.setSurface(s) } catch (t: Throwable) { Log.w(TAG, "setSurface: ${t.message}") }
    }

    /**
     * Opens the stream at [url] (e.g. rtsp://192.168.1.1:554/live).
     * Idempotent — no-op when already connecting/connected to the same URL.
     */
    fun connect(url: String) {
        if (_state.value != ConnectionState.DISCONNECTED && url == lastUrl) return
        disconnectInternal()
        if (url.isBlank()) { _banner.value = "Set the drone stream URL in Settings"; return }
        lastUrl = url
        _state.value = ConnectionState.CONNECTING
        try {
            val mp = MediaPlayer()
            mediaPlayer = mp
            mp.setDataSource(url)
            surface?.let { mp.setSurface(it) }
            mp.setOnPreparedListener {
                Log.i(TAG, "stream prepared: $url")
                try { it.start() } catch (t: Throwable) { Log.e(TAG, "start failed", t) }
                _state.value = ConnectionState.CONNECTED
                _banner.value = null
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) _streaming.value = true
                false
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "stream error what=$what extra=$extra")
                _banner.value = "Stream error ($what/$extra) — check URL & drone link"
                disconnectInternal()
                true
            }
            mp.setOnCompletionListener {
                _banner.value = "Stream ended"
                disconnectInternal()
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            Log.e(TAG, "connect failed", t)
            _banner.value = "Connect failed: ${t.message}"
            disconnectInternal()
        }
    }

    fun disconnect() {
        disconnectInternal()
        _banner.value = null
    }

    private fun disconnectInternal() {
        _streaming.value = false
        _state.value = ConnectionState.DISCONNECTED
        mediaPlayer?.let { mp ->
            try { mp.reset(); mp.release() } catch (t: Throwable) { Log.w(TAG, "release: ${t.message}") }
        }
        mediaPlayer = null
    }

    /**
     * Sampled video frame from the TextureView -> pseudo-radiometric ThermalFrame.
     * Luminance 0..255 is mapped linearly onto [tempMinC]..[tempMaxC]
     * (calibrate in Settings to match the drone camera's display range).
     * Drops frames while a conversion is still running — never backs up.
     */
    fun submitVideoFrame(bmp: Bitmap, tempMinC: Float, tempMaxC: Float) {
        if (_state.value != ConnectionState.CONNECTED) return
        if (!converting.compareAndSet(false, true)) return
        scope.launch {
            try {
                val w = bmp.width; val h = bmp.height
                val px = IntArray(w * h)
                bmp.getPixels(px, 0, w, 0, 0, w, h)
                val span = tempMaxC - tempMinC
                val temps = FloatArray(px.size)
                for (i in px.indices) {
                    val c = px[i]
                    val lum = (0.299f * ((c shr 16) and 0xFF) + 0.587f * ((c shr 8) and 0xFF) + 0.114f * (c and 0xFF)) / 255f
                    temps[i] = tempMinC + lum * span
                }
                _frames.emit(ThermalFrame(w, h, temps))
            } catch (t: Throwable) {
                Log.w(TAG, "frame convert failed: ${t.message}")
            } finally {
                converting.set(false)
            }
        }
    }

    /**
     * Flight control is done with the remote controller's own sticks.
     * The ground-control HTTP API keeps its shape but rejects flight commands.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun execute(cmd: DroneCommand): CommandResult =
        CommandResult.Rejected("flight control unavailable over video link — use the remote controller")

    fun onUsbDetached() {
        _banner.value = "USB link lost"
        disconnect()
    }

    fun clearBanner() { _banner.value = null }

    fun release() { disconnectInternal() }
}
