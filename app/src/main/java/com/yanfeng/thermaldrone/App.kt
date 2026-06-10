package com.yanfeng.thermaldrone

import android.app.Application
import android.hardware.usb.UsbManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.yanfeng.thermaldrone.data.ExportManager
import com.yanfeng.thermaldrone.data.SessionRepository
import com.yanfeng.thermaldrone.data.SettingsRepository
import com.yanfeng.thermaldrone.drone.DroneRepository
import com.yanfeng.thermaldrone.processing.FrameProcessor
import com.yanfeng.thermaldrone.server.GroundControlServer
import java.util.concurrent.atomic.AtomicReference

/** Composition root. */
class App : Application() {
    companion object { private const val TAG = "App" }

    lateinit var drone: DroneRepository
        private set
    lateinit var settingsRepo: SettingsRepository
        private set
    lateinit var sessionRepo: SessionRepository
        private set
    lateinit var exportManager: ExportManager
        private set
    lateinit var server: GroundControlServer
        private set
    lateinit var frameProcessor: FrameProcessor
        private set

    /** Latest rendered JPEG bytes (thermal + overlays) — fed by FlyViewModel, served by /stream. */
    val latestJpeg = AtomicReference<ByteArray?>(null)

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                drone.onUsbDetached()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        drone = DroneRepository()
        settingsRepo = SettingsRepository(this)
        sessionRepo = SessionRepository(this)
        exportManager = ExportManager(this, sessionRepo)
        frameProcessor = FrameProcessor()
        server = GroundControlServer(this, drone, sessionRepo) { latestJpeg.get() }
        server.start()

        try {
            registerReceiver(usbReceiver, IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED))
        } catch (t: Throwable) {
            Log.w(TAG, "usb receiver registration failed: ${t.message}")
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory — dropping temporal average buffer")
        frameProcessor.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) frameProcessor.onLowMemory()
    }
}
