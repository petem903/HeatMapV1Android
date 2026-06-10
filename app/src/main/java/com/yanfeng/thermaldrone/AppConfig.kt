package com.yanfeng.thermaldrone

/** Central compile-time configuration. */
object AppConfig {
    /** Default drone video stream URL — editable in Settings. */
    const val DEFAULT_STREAM_URL = "rtsp://192.168.1.1:554/live"

    /** Default pseudo-radiometric calibration range for the video feed. */
    const val DEFAULT_STREAM_TMIN_C = 20f
    const val DEFAULT_STREAM_TMAX_C = 120f

    const val THERMAL_WIDTH = 640
    const val THERMAL_HEIGHT = 512

    /** Video sampling rate for the measurement pipeline. */
    const val VIDEO_SAMPLE_MS = 100L

    const val SERVER_PORT = 8080
    const val TELEMETRY_PERIOD_MS = 500L

    /** Telemetry failsafe thresholds. */
    const val TELEMETRY_WARN_MS = 3_000L
    const val TELEMETRY_AUTOLAND_MS = 8_000L

    /** Stream watchdog thresholds (during FLYING). */
    const val STREAM_WARN_MS = 2_000L
    const val STREAM_RESTART_MS = 5_000L

    /** Reconnect: attempts and exponential backoff base (2s/4s/8s). */
    const val RECONNECT_ATTEMPTS = 3
    val RECONNECT_BACKOFF_MS = longArrayOf(2_000L, 4_000L, 8_000L)

    const val ALTITUDE_MIN_M = 0.0
    const val ALTITUDE_MAX_M = 120.0

    const val SESSIONS_DIR = "HeatMapV1/sessions"

    const val MAX_TEMPORAL_FRAMES = 8
    const val MAX_AUTO_EXTREMA = 20
}
