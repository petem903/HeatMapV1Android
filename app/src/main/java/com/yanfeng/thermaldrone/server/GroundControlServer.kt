package com.yanfeng.thermaldrone.server

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yanfeng.thermaldrone.AppConfig
import com.yanfeng.thermaldrone.data.SessionRepository
import com.yanfeng.thermaldrone.drone.CommandResult
import com.yanfeng.thermaldrone.drone.DroneCommand
import com.yanfeng.thermaldrone.drone.DroneRepository
import com.yanfeng.thermaldrone.util.TokenGenerator
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Embedded laptop ground-control server on :8080.
 *   GET  /stream    — MJPEG of live thermal view (with overlays)
 *   GET  /telemetry — JSON, 500 ms cadence
 *   GET  /snapshot  — latest composite JPEG
 *   POST /command   — Bearer-token guarded flight commands
 *   GET  /health    — liveness
 */
class GroundControlServer(
    private val context: Context,
    private val drone: DroneRepository,
    private val sessions: SessionRepository,
    /** Latest rendered JPEG (thermal view + overlays), updated by FlyViewModel. */
    private val latestJpeg: () -> ByteArray?
) {
    companion object { private const val TAG = "GroundControl" }

    /** New random 6-char token on every app start. */
    val sessionToken: String = TokenGenerator.generate()

    private val gson = Gson()
    private var engine: ApplicationEngine? = null
    private val logFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private val _commandLog = MutableStateFlow<List<String>>(emptyList())
    val commandLog: StateFlow<List<String>> = _commandLog

    fun serverUrl(): String = "http://${localIp()}:${AppConfig.SERVER_PORT}"

    fun start() {
        if (engine != null) return // idempotent
        try {
            engine = embeddedServer(CIO, port = AppConfig.SERVER_PORT) {
                routing {
                    get("/health") {
                        call.respondText("""{"status":"ok","app":"HeatMapV1Android","version":"2.0"}""", ContentType.Application.Json)
                    }

                    get("/telemetry") {
                        val t = drone.telemetry.value
                        val body = JsonObject().apply {
                            addProperty("state", drone.state.value.name)
                            addProperty("simulated", drone.simulated.value)
                            addProperty("latitude", t.latitude)
                            addProperty("longitude", t.longitude)
                            addProperty("altitude_msl_m", t.altitudeMslM)
                            addProperty("altitude_agl_m", t.altitudeAglM)
                            addProperty("speed_mps", t.speedMps)
                            addProperty("heading_deg", t.headingDeg)
                            addProperty("battery_percent", t.batteryPercent)
                            addProperty("signal_percent", t.signalPercent)
                            addProperty("flight_time_sec", t.flightTimeSec)
                            addProperty("satellites", t.satellites)
                            addProperty("timestamp_ms", t.timestampMs)
                        }
                        call.respondText(gson.toJson(body), ContentType.Application.Json)
                    }

                    get("/snapshot") {
                        val jpeg = latestJpeg() ?: sessions.latestComposite()?.readBytes()
                        if (jpeg == null) call.respondText("", status = HttpStatusCode.NotFound)
                        else call.respondBytes(jpeg, ContentType.Image.JPEG)
                    }

                    get("/stream") {
                        val boundary = "heatmapframe"
                        call.response.header("Cache-Control", "no-cache, private")
                        call.response.header("Connection", "close")
                        call.respondBytesWriter(ContentType.parse("multipart/x-mixed-replace; boundary=$boundary")) {
                            while (currentCoroutineContext().isActive) {
                                val jpeg = latestJpeg()
                                if (jpeg != null) {
                                    writeStringUtf8("--$boundary\r\nContent-Type: image/jpeg\r\nContent-Length: ${jpeg.size}\r\n\r\n")
                                    writeFully(jpeg, 0, jpeg.size)
                                    writeStringUtf8("\r\n")
                                    flush()
                                }
                                kotlinx.coroutines.delay(100) // ~10 fps over the wire
                            }
                        }
                    }

                    post("/command") {
                        val ip = call.request.local.remoteHost
                        // strict Bearer auth
                        val auth = call.request.headers["Authorization"] ?: ""
                        if (auth != "Bearer $sessionToken") {
                            logCommand(ip, "DENIED(bad token)")
                            call.respondText("""{"error":"unauthorized"}""", ContentType.Application.Json, HttpStatusCode.Unauthorized)
                            return@post
                        }
                        val bodyText = call.receiveText()
                        val cmd = parseCommand(bodyText)
                        if (cmd == null) {
                            logCommand(ip, "REJECTED(malformed): ${bodyText.take(120)}")
                            call.respondText("""{"error":"malformed command"}""", ContentType.Application.Json, HttpStatusCode.BadRequest)
                            return@post
                        }
                        val result = drone.execute(cmd.second)
                        logCommand(ip, "${cmd.first} -> $result")
                        when (result) {
                            is CommandResult.Accepted ->
                                call.respondText("""{"result":"accepted","action":"${cmd.first}"}""", ContentType.Application.Json)
                            is CommandResult.Rejected ->
                                call.respondText(gson.toJson(mapOf("result" to "rejected", "reason" to result.reason)),
                                    ContentType.Application.Json, HttpStatusCode.Conflict)
                        }
                    }
                }
            }.start(wait = false)
            Log.i(TAG, "server up at ${serverUrl()} token=$sessionToken")
        } catch (t: Throwable) {
            Log.e(TAG, "server start failed", t)
            engine = null
        }
    }

    /** Strict validation: known actions only, altitude clamped/validated 0–120 m. */
    private fun parseCommand(body: String): Pair<String, DroneCommand>? { return try {
        val json = JsonParser.parseString(body).asJsonObject
        when (val action = json.get("action")?.asString) {
            "takeoff" -> action to DroneCommand.Takeoff
            "land" -> action to DroneCommand.Land
            "return_home" -> action to DroneCommand.ReturnHome
            "set_altitude" -> {
                val alt = json.get("altitude_m")?.asDouble ?: return null
                if (!alt.isFinite()) return null
                action to DroneCommand.SetAltitude(alt.coerceIn(AppConfig.ALTITUDE_MIN_M, AppConfig.ALTITUDE_MAX_M))
            }
            "move_relative" -> {
                val dx = json.get("dx_m")?.asDouble ?: 0.0
                val dy = json.get("dy_m")?.asDouble ?: 0.0
                val dz = json.get("dz_m")?.asDouble ?: 0.0
                if (!dx.isFinite() || !dy.isFinite() || !dz.isFinite()) return null
                action to DroneCommand.MoveRelative(dx, dy, dz)
            }
            else -> null
        }
    } catch (t: Throwable) {
        null
    } }

    private fun logCommand(sourceIp: String, detail: String) {
        val line = "${logFmt.format(Date())} [$sourceIp] $detail"
        Log.i(TAG, "/command $line")
        _commandLog.value = (_commandLog.value + line).takeLast(200)
    }

    private fun localIp(): String {
        // Prefer Wi-Fi interface
        try {
            @Suppress("DEPRECATION")
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            val ip = wifi?.connectionInfo?.ipAddress ?: 0
            if (ip != 0) {
                return "%d.%d.%d.%d".format(ip and 0xFF, ip shr 8 and 0xFF, ip shr 16 and 0xFF, ip shr 24 and 0xFF)
            }
        } catch (_: Throwable) { }
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress ?: "0.0.0.0"
        } catch (t: Throwable) {
            "0.0.0.0"
        }
    }

    fun stop() {
        engine?.stop(500, 1000)
        engine = null
    }
}
