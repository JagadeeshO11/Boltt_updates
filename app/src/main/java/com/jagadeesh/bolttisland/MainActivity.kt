package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.sqrt

/**
 * Boltt Island 2.4 - focused shake-to-torch build.
 *
 * Floating island / status-bar overlay code is intentionally left out of this
 * build. Android keeps ordinary application overlays below critical system
 * windows, so that approach cannot reliably surround the camera cutout.
 */
class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager
    private var flashCameraId: String? = null
    private var shakeEnabled = false
    private var torchEnabled = false
    private var threshold = 2.6f
    private var lastShakeAt = 0L
    private var lastMagnitude = SensorManager.GRAVITY_EARTH
    private lateinit var statusText: TextView
    private lateinit var shakeButton: Button
    private lateinit var torchButton: Button
    private lateinit var sensitivityText: TextView

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun rounded(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = rounded(0xFF1B1D29.toInt(), 22)
    }

    private fun button(value: String, primary: Boolean = false) = Button(this).apply {
        text = value
        isAllCaps = false
        textSize = 14f
        setTextColor(Color.WHITE)
        background = rounded(if (primary) 0xFF7058E8.toInt() else 0xFF343746.toInt(), 15)
        stateListAnimator = null
        minHeight = dp(50)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF10111A.toInt()
        window.navigationBarColor = 0xFF10111A.toInt()
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        findFlashCamera()
        buildScreen()
        refreshUi()
    }

    private fun buildScreen() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
            setBackgroundColor(0xFF10111A.toInt())
        }

        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        heading.addView(text("BOLTT ISLAND", 21f, Color.WHITE, true))
        heading.addView(text("Shake to Torch  ·  Version 2.4", 13f, 0xFFAAAABD.toInt()))
        page.addView(heading)
        page.addView(space(22))

        val hero = card()
        hero.addView(text("SHAKE. FLASH. DONE.", 11f, 0xFFC6B9FF.toInt(), true))
        hero.addView(space(9))
        hero.addView(text("Your flashlight,\\none quick shake away.", 27f, Color.WHITE, true))
        hero.addView(space(8))
        hero.addView(text(
            "Motion detection works while this app is open. No floating overlay or accessibility access needed.",
            14f, 0xFFC0C1CE.toInt()
        ))
        page.addView(hero)
        page.addView(space(16))

        val control = card()
        control.addView(text("SHAKE CONTROL", 16f, Color.WHITE, true))
        control.addView(space(6))
        control.addView(text("Enable this to toggle the rear camera flash with a firm shake.", 13f, 0xFFB8B9C7.toInt()))
        control.addView(space(13))
        shakeButton = button("Enable shake to torch", true)
        control.addView(shakeButton, LinearLayout.LayoutParams(-1, dp(50)))
        shakeButton.setOnClickListener { requestOrToggleShake() }
        control.addView(space(17))
        sensitivityText = text("Shake sensitivity · Balanced", 13f, 0xFFE2E0EC.toInt(), true)
        control.addView(sensitivityText)
        val sensitivity = SeekBar(this).apply {
            max = 20
            progress = 10
        }
        control.addView(sensitivity)
        sensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                // Higher progress means a lighter shake is required.
                threshold = 3.5f - progress * 0.09f
                val label = when {
                    progress < 6 -> "Firm"
                    progress > 14 -> "Sensitive"
                    else -> "Balanced"
                }
                sensitivityText.text = "Shake sensitivity · $label"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        page.addView(control)
        page.addView(space(16))

        val torch = card()
        torch.addView(text("MANUAL TORCH", 16f, Color.WHITE, true))
        torch.addView(space(6))
        torch.addView(text("A regular on/off control is included as a backup.", 13f, 0xFFB8B9C7.toInt()))
        torch.addView(space(12))
        torchButton = button("Turn flashlight ON")
        torch.addView(torchButton, LinearLayout.LayoutParams(-1, dp(50)))
        torchButton.setOnClickListener { requestOrToggleTorch() }
        page.addView(torch)
        page.addView(space(16))

        val status = card()
        status.addView(text("DEVICE STATUS", 11f, 0xFFC6B9FF.toInt(), true))
        status.addView(space(8))
        statusText = text("Checking flashlight and motion sensor…", 14f, Color.WHITE)
        status.addView(statusText)
        page.addView(status)
        page.addView(space(16))

        val note = text(
            "Privacy & battery\n• Motion sensor runs only while this screen is active.\n• Shake detection pauses when you leave the app.\n• No internet, analytics, background service or accessibility permission.\n• Flashlight is switched off when the app is closed.",
            12f, 0xFF9699AA.toInt()
        )
        note.setPadding(dp(4), dp(4), dp(4), dp(4))
        page.addView(note)

        setContentView(page)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(height))
    }

    private fun findFlashCamera() {
        flashCameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }

    private fun requestOrToggleShake() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_SHAKE)
            return
        }
        setShakeEnabled(!shakeEnabled)
    }

    private fun requestOrToggleTorch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_TORCH)
            return
        }
        setTorch(!torchEnabled)
    }

    private fun setShakeEnabled(enabled: Boolean) {
        if (enabled) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor == null) {
                Toast.makeText(this, "This phone has no accelerometer.", Toast.LENGTH_LONG).show()
                return
            }
            shakeEnabled = sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (!shakeEnabled) Toast.makeText(this, "Could not start motion detection.", Toast.LENGTH_SHORT).show()
        } else {
            shakeEnabled = false
            sensorManager.unregisterListener(this)
        }
        refreshUi()
    }

    private fun setTorch(enabled: Boolean) {
        if (flashCameraId == null) findFlashCamera()
        val id = flashCameraId
        if (id == null) {
            Toast.makeText(this, "No camera flash was detected.", Toast.LENGTH_LONG).show()
            return
        }
        try {
            cameraManager.setTorchMode(id, enabled)
            torchEnabled = enabled
            refreshUi()
            if (enabled) vibrateBriefly()
        } catch (_: Exception) {
            torchEnabled = false
            refreshUi()
            Toast.makeText(this, "Flashlight is unavailable. Close apps using the camera and retry.", Toast.LENGTH_LONG).show()
        }
    }

    private fun vibrateBriefly() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(35, 70))
                else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(35)
                }
            }
        } catch (_: Exception) { /* Haptic feedback is optional. */ }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!shakeEnabled || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val delta = kotlin.math.abs(magnitude - lastMagnitude)
        lastMagnitude = magnitude
        val now = SystemClock.elapsedRealtime()
        if (delta > threshold && now - lastShakeAt > 1300L) {
            lastShakeAt = now
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                setTorch(!torchEnabled)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun refreshUi() {
        if (!::statusText.isInitialized) return
        val sensorAvailable = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
        val flashAvailable = flashCameraId != null
        statusText.text = "Shake detection: ${if (shakeEnabled) "ON" else "OFF"}\n" +
            "Flashlight: ${if (torchEnabled) "ON" else "OFF"}\n" +
            "Accelerometer: ${if (sensorAvailable) "Available" else "Unavailable"}\n" +
            "Camera flash: ${if (flashAvailable) "Available" else "Unavailable"}"
        shakeButton.text = if (shakeEnabled) "Disable shake to torch" else "Enable shake to torch"
        torchButton.text = if (torchEnabled) "Turn flashlight OFF" else "Turn flashlight ON"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            when (requestCode) {
                REQUEST_SHAKE -> setShakeEnabled(true)
                REQUEST_TORCH -> setTorch(!torchEnabled)
            }
        } else if (requestCode == REQUEST_SHAKE || requestCode == REQUEST_TORCH) {
            Toast.makeText(this, "Camera permission is required to control the flashlight.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (shakeEnabled && checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (sensor != null) sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        if (torchEnabled) {
            try { flashCameraId?.let { cameraManager.setTorchMode(it, false) } } catch (_: Exception) {}
            torchEnabled = false
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_SHAKE = 21
        private const val REQUEST_TORCH = 22
    }
}
