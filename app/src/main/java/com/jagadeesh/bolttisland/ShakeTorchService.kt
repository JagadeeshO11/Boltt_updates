package com.jagadeesh.bolttisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import kotlin.math.sqrt

class ShakeTorchService : Service(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var camera: CameraManager
    private var flashId: String? = null
    private var listening = false
    private var torchOn = false
    private var torchOwned = false
    private var sensitivity = 70
    private var threshold = thresholdFor(70)
    private var lastShake = 0L
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var initialized = false
    private var peaks = 0
    private val handler = Handler(Looper.getMainLooper())

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId != flashId) return
            torchOn = enabled
            if (!enabled) torchOwned = false
            publish(); updateNotification()
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId != flashId) return
            torchOn = false; torchOwned = false
            publish(); updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        findFlash()
        createChannel()
        camera.registerTorchCallback(torchCallback, handler)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_QUERY -> { publish(); if (!listening) stopSelf() }
            ACTION_STOP -> { stopMonitoring(); stopSelf(); return START_NOT_STICKY }
            ACTION_SET_SENSITIVITY -> {
                sensitivity = intent.getIntExtra(EXTRA_SENSITIVITY, sensitivity).coerceIn(0, 100)
                threshold = thresholdFor(sensitivity)
                publish(); updateNotification()
            }
            ACTION_TOGGLE_TORCH -> toggleTorch()
            ACTION_START, null -> {
                sensitivity = (intent?.getIntExtra(EXTRA_SENSITIVITY, 70) ?: 70).coerceIn(0, 100)
                threshold = thresholdFor(sensitivity)
                startAsForeground()
                startMonitoring()
            }
        }
        return START_NOT_STICKY
    }

    private fun startAsForeground() {
        val n = notification()
        if (Build.VERSION.SDK_INT >= 34)
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun startMonitoring() {
        if (listening) { publish(); return }
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) { stopSelf(); return }
        initialized = false; peaks = 0
        // 20 Hz with batching: less work than SENSOR_DELAY_GAME, still catches deliberate shakes.
        listening = sensors.registerListener(this, sensor, 50_000, 200_000)
        if (!listening) { stopSelf(); return }
        publish(); updateNotification()
    }

    private fun stopMonitoring() {
        if (listening) sensors.unregisterListener(this)
        listening = false
        if (torchOwned) setTorch(false)
        publish()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!listening || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val a = 0.82f
        if (!initialized) {
            gravityX = event.values[0]; gravityY = event.values[1]; gravityZ = event.values[2]
            initialized = true; return
        }
        gravityX = a * gravityX + (1f - a) * event.values[0]
        gravityY = a * gravityY + (1f - a) * event.values[1]
        gravityZ = a * gravityZ + (1f - a) * event.values[2]
        val x = event.values[0] - gravityX
        val y = event.values[1] - gravityY
        val z = event.values[2] - gravityZ
        val acceleration = sqrt(x * x + y * y + z * z)
        val now = SystemClock.elapsedRealtime()
        if (acceleration > threshold && now - lastShake > 1400L) {
            peaks++
            if (peaks >= 2) {
                lastShake = now; peaks = 0
                toggleTorch()
            }
        } else if (acceleration < threshold * 0.55f) peaks = 0
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun toggleTorch() {
        if (flashId == null) findFlash()
        if (flashId == null) { publish(); return }
        // Never turn off a flash that this service did not turn on itself.
        if (torchOn && !torchOwned) { publish(); return }
        setTorch(!torchOwned)
    }

    private fun setTorch(enabled: Boolean) {
        val id = flashId ?: return
        try {
            camera.setTorchMode(id, enabled)
            torchOwned = enabled
            torchOn = enabled
            publish(); updateNotification()
        } catch (_: CameraAccessException) {
            torchOwned = false; publish(); updateNotification()
        } catch (_: SecurityException) {
            torchOwned = false; publish(); updateNotification()
        } catch (_: IllegalArgumentException) {
            torchOwned = false; publish()
        }
    }

    private fun findFlash() {
        flashId = try {
            camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }

    private fun publish() {
        sendBroadcast(Intent(ACTION_STATE).setPackage(packageName)
            .putExtra(EXTRA_RUNNING, listening)
            .putExtra(EXTRA_TORCH, torchOn)
            .putExtra(EXTRA_SENSITIVITY, sensitivity))
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val c = NotificationChannel(CHANNEL_ID, "Shake to Torch", NotificationManager.IMPORTANCE_LOW)
            c.description = "Ongoing notification while background shake detection is enabled"
            getSystemService(NotificationManager::class.java).createNotificationChannel(c)
        }
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ShakeTorchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setContentTitle("Boltt Island · Shake to Torch")
            .setContentText("Active · Sensitivity " + sensitivity + "%")
            .setContentIntent(open).setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop).build()
    }

    private fun updateNotification() {
        if (listening) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopMonitoring()
        try { camera.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.jagadeesh.bolttisland.START"
        const val ACTION_STOP = "com.jagadeesh.bolttisland.STOP"
        const val ACTION_SET_SENSITIVITY = "com.jagadeesh.bolttisland.SET_SENSITIVITY"
        const val ACTION_TOGGLE_TORCH = "com.jagadeesh.bolttisland.TOGGLE_TORCH"
        const val ACTION_STATE = "com.jagadeesh.bolttisland.STATE"
        const val ACTION_QUERY = "com.jagadeesh.bolttisland.QUERY"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_TORCH = "torch"
        const val EXTRA_SENSITIVITY = "sensitivity"
        private const val NOTIFICATION_ID = 2405
        private const val CHANNEL_ID = "shake_torch_service"
        private fun thresholdFor(v: Int) = 4.0f - v * 0.026f
    }
}