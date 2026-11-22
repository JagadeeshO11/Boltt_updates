package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var camera: CameraManager
    private var flashId: String? = null
    private var torchOn = false
    private var shakeEnabled = false
    private var overlayEnabled = false
    private var lastShake = 0L
    private var threshold = 2.5f
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private lateinit var shakeStatus: TextView
    private lateinit var torchStatus: TextView
    private lateinit var shakeButton: Button
    private lateinit var overlayButton: Button
    private lateinit var root: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
        setBackgroundColor(0xFF1B1D27.toInt())
    }
    private fun button(label: String, primary: Boolean = false) = Button(this).apply {
        text = label; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else 0xFFE6E8F2.toInt())
        setBackgroundTintList(android.content.res.ColorStateList.valueOf(if (primary) 0xFF6750D8.toInt() else 0xFF303342.toInt()))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        refreshFlash()
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply { setBackgroundColor(0xFF10111A.toInt()) }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(28), dp(20), dp(28))
        }
        scroll.addView(root)
        root.addView(text("BOLTT", 13f, 0xFFB8ACFF.toInt(), true))
        root.addView(text("Island", 34f, Color.WHITE, true).apply { setPadding(0, 0, 0, dp(4)) })
        root.addView(text("A calmer, safer shortcut for your Boltt Ace 5G.", 14f, 0xFFB8BBCB.toInt()).apply { setPadding(0, 0, 0, dp(20)) })

        val status = card()
        status.addView(text("DEVICE STATUS", 11f, 0xFFB8ACFF.toInt(), true))
        shakeStatus = text("●  Shake control is OFF", 16f, Color.WHITE, true)
        shakeStatus.setPadding(0, dp(12), 0, dp(8)); status.addView(shakeStatus)
        torchStatus = text("Torch: OFF", 14f, 0xFFB8BBCB.toInt())
        status.addView(torchStatus)
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val controls = card()
        controls.addView(text("SHAKE TO TORCH", 18f, Color.WHITE, true))
        controls.addView(text("Shake firmly while this app is open to switch the flashlight. The sensor pauses automatically when you leave the app.", 13f, 0xFFB8BBCB.toInt()).apply { setPadding(0, dp(8), 0, dp(12)) })
        shakeButton = button("Enable shake control", true)
        controls.addView(shakeButton)
        controls.addView(text("Sensitivity", 13f, 0xFFE6E8F2.toInt()).apply { setPadding(0, dp(12), 0, 0) })
        val seek = SeekBar(this).apply { max = 20; progress = 10 }
        controls.addView(seek)
        controls.addView(text("Lower = requires a stronger shake. Higher = more sensitive.", 11f, 0xFF9296A8.toInt()))
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { threshold = 3.2f - p * 0.07f }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        shakeButton.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 11)
            } else {
                shakeEnabled = !shakeEnabled
                updateShakeUi()
                if (shakeEnabled) startSensor() else stopSensor()
            }
        }

        val torchCard = card()
        torchCard.addView(text("FLASHLIGHT", 18f, Color.WHITE, true))
        torchCard.addView(text("Manual control works independently of shake detection.", 13f, 0xFFB8BBCB.toInt()).apply { setPadding(0, dp(7), 0, dp(12)) })
        val torchButton = button("Turn torch ON", true)
        torchCard.addView(torchButton)
        torchButton.setOnClickListener { if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 12) else setTorch(!torchOn) }
        root.addView(torchCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        val islandCard = card()
        islandCard.addView(text("FLOATING ISLAND", 18f, Color.WHITE, true))
        islandCard.addView(text("Optional mini pill shown over other apps. It is removed when this app closes, so no hidden background process is needed.", 13f, 0xFFB8BBCB.toInt()).apply { setPadding(0, dp(7), 0, dp(12)) })
        overlayButton = button("Enable floating pill")
        islandCard.addView(overlayButton)
        overlayButton.setOnClickListener { toggleOverlay() }
        root.addView(islandCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        root.addView(text("PRIVACY & BATTERY", 12f, 0xFFB8ACFF.toInt(), true).apply { setPadding(2, dp(4), 0, dp(8)) })
        root.addView(text("• No internet permission or analytics\n• No boot receiver or auto-start\n• No always-on foreground service\n• Shake sensor runs only while this screen is visible\n• Torch is switched OFF when the app exits", 13f, 0xFFB8BBCB.toInt()))
        setContentView(scroll)
    }

    private fun refreshFlash() {
        flashId = try { camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } } catch (_: Exception) { null }
    }
    private fun updateShakeUi() {
        shakeStatus.text = if (shakeEnabled) "●  Shake control is ON" else "●  Shake control is OFF"
        shakeButton.text = if (shakeEnabled) "Disable shake control" else "Enable shake control"
    }
    private fun startSensor() {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) { shakeEnabled = false; updateShakeUi(); Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_LONG).show(); return }
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
    private fun stopSensor() { sensors.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!shakeEnabled || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val force = kotlin.math.sqrt(x*x + y*y + z*z)
        val now = android.os.SystemClock.elapsedRealtime()
        if (force > threshold && now - lastShake > 1200L) {
            lastShake = now
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) setTorch(!torchOn)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun setTorch(enabled: Boolean) {
        val id = flashId
        if (id == null) { refreshFlash(); Toast.makeText(this, "No camera flash found on this device", Toast.LENGTH_SHORT).show(); return }
        try {
            camera.setTorchMode(id, enabled); torchOn = enabled
            torchStatus.text = "Torch: " + if (torchOn) "ON" else "OFF"
            overlayView?.let { (it as? TextView)?.text = "  BOLTT  •  " + if (torchOn) "TORCH ON  " else "READY  " }
            root.findViewsWithText(ArrayList<View>(), "Turn torch ON", View.FIND_VIEWS_WITH_TEXT)
        } catch (_: Exception) { torchOn = false; torchStatus.text = "Torch: unavailable"; Toast.makeText(this, "Android could not access the torch. Close camera apps and retry.", Toast.LENGTH_LONG).show() }
    }

    private fun toggleOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toast.makeText(this, "Allow display over other apps, then tap this button again.", Toast.LENGTH_LONG).show()
            return
        }
        if (overlayView != null) { removeOverlay(); return }
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val pill = TextView(this).apply {
                text = "  BOLTT  •  READY  "; textSize = 12f; setTextColor(Color.WHITE)
                setPadding(dp(18), dp(10), dp(18), dp(10)); gravity = Gravity.CENTER
                setBackgroundColor(0xFF191922.toInt())
                setOnClickListener { setTorch(!torchOn); text = "  BOLTT  •  " + if (torchOn) "TORCH ON  " else "READY  " }
            }
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL; y = dp(28) }
            windowManager?.addView(pill, params); overlayView = pill; overlayEnabled = true
            overlayButton.text = "Disable floating pill"
        } catch (_: Exception) { Toast.makeText(this, "Android did not allow the floating pill.", Toast.LENGTH_LONG).show() }
    }
    private fun removeOverlay() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null; overlayEnabled = false
        if (::overlayButton.isInitialized) overlayButton.text = "Enable floating pill"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 11 || requestCode == 12) && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 11) { shakeEnabled = true; updateShakeUi(); startSensor() }
            else setTorch(!torchOn)
        } else if (requestCode == 11 || requestCode == 12) Toast.makeText(this, "Camera permission is needed for the flashlight.", Toast.LENGTH_LONG).show()
    }
    override fun onResume() {
        super.onResume()
        if (shakeEnabled && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startSensor()
        if (overlayView != null && Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) removeOverlay()
    }
    override fun onPause() { stopSensor(); super.onPause() }
    override fun onDestroy() {
        stopSensor()
        if (torchOn) try { flashId?.let { camera.setTorchMode(it, false) } } catch (_: Exception) {}
        removeOverlay()
        super.onDestroy()
    }
}
