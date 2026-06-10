package com.yanfeng.thermaldrone.model

/** Drone connection state machine states. */
enum class ConnectionState {
    DISCONNECTED, CONNECTING, CONNECTED, ARMED, FLYING, LANDING;

    val isAirborne: Boolean get() = this == FLYING || this == LANDING
}

/** Legal transitions; state machine is idempotent (same-state = allowed no-op). */
object StateMachine {
    private val legal: Map<ConnectionState, Set<ConnectionState>> = mapOf(
        ConnectionState.DISCONNECTED to setOf(ConnectionState.CONNECTING),
        ConnectionState.CONNECTING to setOf(ConnectionState.CONNECTED, ConnectionState.DISCONNECTED),
        ConnectionState.CONNECTED to setOf(ConnectionState.ARMED, ConnectionState.DISCONNECTED),
        ConnectionState.ARMED to setOf(ConnectionState.FLYING, ConnectionState.CONNECTED, ConnectionState.DISCONNECTED),
        ConnectionState.FLYING to setOf(ConnectionState.LANDING, ConnectionState.DISCONNECTED),
        ConnectionState.LANDING to setOf(ConnectionState.DISCONNECTED, ConnectionState.CONNECTED)
    )

    fun canTransition(from: ConnectionState, to: ConnectionState): Boolean =
        from == to || legal[from]?.contains(to) == true
}

/** Live telemetry snapshot. */
data class Telemetry(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeMslM: Double = 0.0,
    val altitudeAglM: Double = 0.0,
    val speedMps: Double = 0.0,
    val headingDeg: Double = 0.0,
    val batteryPercent: Int = 0,
    val signalPercent: Int = 0,
    val flightTimeSec: Long = 0L,
    val satellites: Int = 0,
    val timestampMs: Long = System.currentTimeMillis()
)

/** One raw thermal frame: temperatures in °C, row-major. */
class ThermalFrame(
    val width: Int,
    val height: Int,
    val tempsC: FloatArray,
    val timestampMs: Long = System.currentTimeMillis()
) {
    fun tempAt(x: Int, y: Int): Float {
        val cx = x.coerceIn(0, width - 1)
        val cy = y.coerceIn(0, height - 1)
        return tempsC[cy * width + cx]
    }

    fun copy(): ThermalFrame = ThermalFrame(width, height, tempsC.copyOf(), timestampMs)
}
