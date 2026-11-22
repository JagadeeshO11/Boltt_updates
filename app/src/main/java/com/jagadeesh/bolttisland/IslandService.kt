package com.jagadeesh.bolttisland

import android.app.Notification
import android.app.NotificationChannel
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.sqrt

class IslandService : Service(), SensorEventListener {
    private val channelId = "boltt_island_service"
    private lateinit var sensors: SensorManager
    private lateinit var cameras: CameraManager
    private var flashId: String? = null
    private var torchOn = false
    private var lastShake = 0L
    private var wm: WindowManager? = null
    private var pill: LinearLayout? = null
    private var label: TextView? = null
    private var expanded = false

    override fun onCreate() {
        super.onCreate()
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        cameras = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        flashId = cameras.cameraIdList.firstOrNull { id -> cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        createChannel()
        startForeground(42, makeNotification())
        sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        showPill()
    }
    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) { val c = NotificationChannel(channelId, "Boltt Island", android.app.NotificationManager.IMPORTANCE_LOW); getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(c) }
    }
    private fun makeNotification(): Notification {
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, channelId) else Notification.Builder(this)
        return b.setContentTitle("Boltt Island is running").setContentText("Shake to toggle torch. Tap the floating pill to expand.").setSmallIcon(android.R.drawable.ic_menu_camera).setOngoing(true).build()
    }
    private fun showPill() {
        if (Build.VERSION.SDK_INT < 23 || !Settings.canDrawOverlays(this)) return
        try {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val view = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(34, 14, 34, 14); setBackgroundColor(Color.BLACK) }
            label = TextView(this).apply { text = "●  BOLTT ISLAND"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER }
            view.addView(label)
            view.setOnClickListener { expanded = !expanded; label?.text = if (expanded) "●  BOLTT ISLAND\\nShake: Torch ON/OFF\\nTap to collapse" else "●  BOLTT ISLAND"; view.setPadding(34, if (expanded) 24 else 14, 34, if (expanded) 24 else 14) }
            val p = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, android.graphics.PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = 24 }
            wm?.addView(view, p); pill = view
        } catch (_: Exception) { Toast.makeText(this, "Floating island could not be shown", Toast.LENGTH_SHORT).show() }
    }
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0] / SensorManager.GRAVITY_EARTH; val y = event.values[1] / SensorManager.GRAVITY_EARTH; val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val force = sqrt(x * x + y * y + z * z); val now = System.currentTimeMillis()
        if (force > 2.7f && now - lastShake > 900L) { lastShake = now; toggleTorch() }
    }
    private fun toggleTorch() {
        val id = flashId ?: run { Toast.makeText(this, "No camera flash detected", Toast.LENGTH_SHORT).show(); return }
        try { torchOn = !torchOn; cameras.setTorchMode(id, torchOn); label?.text = if (expanded) "●  BOLTT ISLAND\\nTorch: ${if (torchOn) "ON" else "OFF"}" else "●  TORCH ${if (torchOn) "ON" else "OFF"}" }
        catch (_: Exception) { torchOn = false; Toast.makeText(this, "Torch unavailable. Check camera access.", Toast.LENGTH_SHORT).show() }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onDestroy() {
        sensors.unregisterListener(this); try { flashId?.let { cameras.setTorchMode(it, false) } } catch (_: Exception) {}
        pill?.let { try { wm?.removeView(it) } catch (_: Exception) {} }; super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
