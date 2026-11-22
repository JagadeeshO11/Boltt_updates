package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import kotlin.math.sqrt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var sensors: SensorManager
    private lateinit var camera: CameraManager
    private var flashId: String? = null
    private var torchOn = false
    private var shakeEnabled = false
    private var lastShake = 0L
    private var threshold = 2.5f
    private var overlayView: LinearLayout? = null
    private var overlayHeader: LinearLayout? = null
    private var overlayLabel: TextView? = null
    private var overlayMeta: TextView? = null
    private var overlayExpanded = false
    private var windowManager: WindowManager? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private lateinit var shakeStatus: TextView
    private lateinit var torchStatus: TextView
    private lateinit var shakeButton: Button
    private lateinit var overlayButton: Button
    private lateinit var torchButton: Button
    private lateinit var displayInfo: TextView
    private lateinit var root: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 22) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        background = rounded(0xFF191B27.toInt(), 24)
    }
    private fun actionButton(value: String, primary: Boolean = false) = Button(this).apply {
        text = value; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else 0xFFE9E8F2.toInt())
        background = rounded(if (primary) 0xFF7257E8.toInt() else 0xFF303342.toInt(), 16)
        stateListAnimator = null
        minHeight = dp(48)
        setPadding(dp(12), 0, dp(12), 0)
    }
    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF10111A.toInt()
        window.navigationBarColor = 0xFF10111A.toInt()
        if (Build.VERSION.SDK_INT >= 29) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        sensors = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        refreshFlash()
        buildUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(0xFF10111A.toInt())
            clipToPadding = false
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(32))
        }
        scroll.addView(root)

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val mark = TextView(this).apply {
            text = "B"; textSize = 23f; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            background = rounded(0xFF7257E8.toInt(), 15)
        }
        top.addView(mark, LinearLayout.LayoutParams(dp(46), dp(46)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(label("BOLTT ISLAND", 16f, Color.WHITE, true))
            addView(label("Made for your Boltt Ace 5G", 12f, 0xFF9DA0B3.toInt()))
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val version = label("2.2", 12f, 0xFFD7D0FF.toInt(), true).apply {
            setPadding(dp(12), dp(7), dp(12), dp(7)); background = rounded(0xFF29243E.toInt(), 30)
        }
        top.addView(version)
        root.addView(top)
        addGap(root, 24)

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = rounded(0xFF24203A.toInt(), 26)
        }
        hero.addView(label("YOUR PHONE, AT A GLANCE", 11f, 0xFFC5B9FF.toInt(), true))
        addGap(hero, 8)
        hero.addView(label("A little island.\nA lot more useful.", 29f, Color.WHITE, true))
        addGap(hero, 8)
        hero.addView(label("Quick actions and live status without leaving the app you're using.", 14f, 0xFFC5C2D2.toInt()))
        addGap(hero, 18)
        val preview = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(12), dp(16), dp(12))
            background = rounded(Color.BLACK, 30)
        }
        preview.addView(label("●", 10f, 0xFF8D78FF.toInt(), true))
        preview.addView(label("   BOLTT  ·  READY", 12f, Color.WHITE, true))
        preview.addView(label("   ◉", 12f, 0xFFB7A9FF.toInt(), true))
        hero.addView(preview, LinearLayout.LayoutParams(-1, dp(48)))
        root.addView(hero)
        addGap(root, 18)

        val diagnostics = card()
        diagnostics.addView(label("DISPLAY & CAMERA CUTOUT", 17f, Color.WHITE, true))
        addGap(diagnostics, 6)
        diagnostics.addView(label("Read the phone's actual pixel dimensions, density, safe insets and camera cutout bounds. These values will guide accurate island placement.", 13f, 0xFFB4B6C6.toInt()))
        addGap(diagnostics, 12)
        val inspectButton = actionButton("Measure screen and cutout", true)
        diagnostics.addView(inspectButton, LinearLayout.LayoutParams(-1, dp(48)))
        addGap(diagnostics, 10)
        displayInfo = label("Not measured yet. Tap the button above.", 12f, 0xFFD8D9E4.toInt())
        diagnostics.addView(displayInfo)
        inspectButton.setOnClickListener { inspectDisplay() }
        root.addView(diagnostics)
        addGap(root, 14)

        val status = card()
        status.addView(label("LIVE DEVICE STATUS", 11f, 0xFFB9A9FF.toInt(), true))
        addGap(status, 12)
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val statusText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
        shakeStatus = label("Shake control is OFF", 16f, Color.WHITE, true)
        torchStatus = label("Flashlight · OFF", 13f, 0xFFAEB0C0.toInt())
        statusText.addView(shakeStatus); addGap(statusText, 4); statusText.addView(torchStatus)
        statusRow.addView(statusText)
        statusRow.addView(label("●", 15f, 0xFF7BE0B2.toInt(), true))
        status.addView(statusRow)
        root.addView(status)
        addGap(root, 14)

        val island = card()
        island.addView(label("FLOATING ISLAND", 18f, Color.WHITE, true))
        addGap(island, 6)
        island.addView(label("Your mini control centre stays above other apps when enabled. Tap the pill to expand it.", 13f, 0xFFB4B6C6.toInt()))
        addGap(island, 14)
        overlayButton = actionButton("Enable floating island", true)
        island.addView(overlayButton, LinearLayout.LayoutParams(-1, dp(50)))
        overlayButton.setOnClickListener { toggleOverlay() }
        addGap(island, 10)
        island.addView(label("Includes torch, music shortcut, drag positioning and quick expand/collapse. No duplicated status-bar clock or battery.", 12f, 0xFF9296A8.toInt()))
        root.addView(island)
        addGap(root, 14)

        val torchCard = card()
        torchCard.addView(label("QUICK TORCH", 17f, Color.WHITE, true))
        addGap(torchCard, 5)
        torchCard.addView(label("Use the hardware flash without opening another app.", 13f, 0xFFB4B6C6.toInt()))
        addGap(torchCard, 13)
        torchButton = actionButton("Turn flashlight ON", true)
        torchCard.addView(torchButton, LinearLayout.LayoutParams(-1, dp(50)))
        torchButton.setOnClickListener { requestTorchToggle() }
        root.addView(torchCard)
        addGap(root, 14)

        val shake = card()
        shake.addView(label("SHAKE TO TORCH", 17f, Color.WHITE, true))
        addGap(shake, 5)
        shake.addView(label("Shake firmly to switch the flashlight. For battery and privacy, motion detection runs only while Boltt Island is open.", 13f, 0xFFB4B6C6.toInt()))
        addGap(shake, 12)
        shakeButton = actionButton("Enable shake control")
        shake.addView(shakeButton, LinearLayout.LayoutParams(-1, dp(48)))
        shakeButton.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.CAMERA), 11)
            } else {
                shakeEnabled = !shakeEnabled
                updateShakeUi()
                if (shakeEnabled) startSensor() else stopSensor()
            }
        }
        addGap(shake, 12)
        shake.addView(label("Sensitivity", 12f, 0xFFE2E1EA.toInt()))
        val seek = SeekBar(this).apply { max = 20; progress = 10 }
        shake.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { threshold = 3.2f - p * 0.07f }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        root.addView(shake)
        addGap(root, 14)

        val info = card()
        info.addView(label("DESIGNED TO BE TRANSPARENT", 11f, 0xFFB9A9FF.toInt(), true))
        addGap(info, 8)
        info.addView(label("• No internet permission or analytics\n• No hidden auto-start\n• No always-on foreground service\n• Overlay permission is requested only when you enable the island\n• Shake sensor pauses when you leave the app", 13f, 0xFFB4B6C6.toInt()))
        root.addView(info)
        addGap(root, 18)
        root.addView(label("BOLTT ISLAND  ·  BUILT FOR YOUR DEVICE", 10f, 0xFF777B8E.toInt(), true).apply { gravity = Gravity.CENTER })
        setContentView(scroll)
    }

    private fun inspectDisplay() {
        displayInfo.text = "Reading Android display information…"
        val decor = window.decorView
        decor.requestApplyInsets()
        decor.post {
            val metrics = readRealDisplayMetrics()
            val density = resources.displayMetrics.density
            val densityDpi = resources.displayMetrics.densityDpi
            val rotation = readDisplayRotation()
            val origin = IntArray(2)
            decor.getLocationOnScreen(origin)
            val details = StringBuilder()
            details.append("SCREEN (physical pixels)\\n")
            details.append("Width: ${metrics.widthPixels}px  |  Height: ${metrics.heightPixels}px\\n")
            details.append("Logical size: ${(metrics.widthPixels / density).toInt()} × ${(metrics.heightPixels / density).toInt()} dp\\n")
            details.append("Density: ${"%.2f".format(java.util.Locale.US, density)}  |  ${densityDpi} dpi\\n")
            details.append("Rotation: ${rotation}  |  App window origin: ${origin[0]}, ${origin[1]} px\\n\\n")
            if (Build.VERSION.SDK_INT >= 28) {
                val cutout = decor.rootWindowInsets?.displayCutout
                if (cutout == null) {
                    details.append("DISPLAY CUTOUT\\nNo cutout data reported by Android yet. Tap Measure again after the screen settles.")
                } else {
                    details.append("DISPLAY CUTOUT (pixel bounds)\\n")
                    details.append("Safe insets: L ${cutout.safeInsetLeft}  T ${cutout.safeInsetTop}  R ${cutout.safeInsetRight}  B ${cutout.safeInsetBottom} px\\n")
                    val rects = cutout.boundingRects
                    if (rects.isEmpty()) details.append("Bounding rectangles: none reported\\n")
                    else rects.forEachIndexed { index, rect ->
                        details.append("Cutout ${index + 1}: left=${rect.left}, top=${rect.top}, right=${rect.right}, bottom=${rect.bottom} px\\n")
                        details.append("  Size: ${rect.width()} × ${rect.height()} px; centre: ${rect.centerX()}, ${rect.centerY()} px\\n")
                    }
                }
            } else {
                details.append("DISPLAY CUTOUT\\nAndroid API 28+ is required for cutout bounds.")
            }
            displayInfo.text = details.toString()
        }
    }

    @Suppress("DEPRECATION")
    private fun windowManagerDefaultDisplay(): android.view.Display? =
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay

    @Suppress("DEPRECATION")
    private fun readRealDisplayMetrics(): android.util.DisplayMetrics =
        android.util.DisplayMetrics().also { windowManagerDefaultDisplay()?.getRealMetrics(it) }

    @Suppress("DEPRECATION")
    private fun readDisplayRotation(): Int = windowManagerDefaultDisplay()?.rotation ?: 0

    private fun refreshFlash() {
        flashId = try {
            camera.cameraIdList.firstOrNull {
                camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) { null }
    }

    private fun updateShakeUi() {
        shakeStatus.text = if (shakeEnabled) "Shake control is ON" else "Shake control is OFF"
        shakeButton.text = if (shakeEnabled) "Disable shake control" else "Enable shake control"
    }

    private fun startSensor() {
        val sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            shakeEnabled = false; updateShakeUi()
            Toast.makeText(this, "Accelerometer not available", Toast.LENGTH_LONG).show()
            return
        }
        sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }
    private fun stopSensor() { sensors.unregisterListener(this) }

    override fun onSensorChanged(event: SensorEvent?) {
        if (!shakeEnabled || event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val force = sqrt(x*x + y*y + z*z)
        val now = SystemClock.elapsedRealtime()
        if (force > threshold && now - lastShake > 1200L) {
            lastShake = now
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) setTorch(!torchOn)
        }
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun requestTorchToggle() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 12)
        } else setTorch(!torchOn)
    }

    private fun setTorch(enabled: Boolean) {
        if (flashId == null) refreshFlash()
        val id = flashId
        if (id == null) {
            Toast.makeText(this, "No camera flash is available on this device.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            camera.setTorchMode(id, enabled)
            torchOn = enabled
            val state = if (torchOn) "ON" else "OFF"
            if (::torchStatus.isInitialized) torchStatus.text = "Flashlight · $state"
            if (::torchButton.isInitialized) torchButton.text = if (torchOn) "Turn flashlight OFF" else "Turn flashlight ON"
            updatePillStatus()
        } catch (_: Exception) {
            torchOn = false
            if (::torchStatus.isInitialized) torchStatus.text = "Flashlight · unavailable"
            Toast.makeText(this, "Android could not access the flash. Close camera apps and retry.", Toast.LENGTH_LONG).show()
        }
    }

    private fun toggleOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Allow display over other apps, then return and tap Enable again.", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Open Settings → Apps → Boltt Island → Display over other apps.", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (overlayView != null) { removeOverlay(); return }
        try { showOverlay() } catch (_: Exception) {
            Toast.makeText(this, "Android could not create the floating island. Check overlay permission.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(9))
            background = GradientDrawable().apply {
                setColor(Color.BLACK); cornerRadius = dp(28).toFloat()
                setStroke(dp(1), 0xFF34313F.toInt())
            }
            elevation = dp(10).toFloat()
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(label("●", 10f, 0xFF9A83FF.toInt(), true))
        overlayLabel = label("  BOLTT ISLAND  ·  READY", 11f, Color.WHITE, true)
        header.addView(overlayLabel, LinearLayout.LayoutParams(0, dp(25), 1f))
        overlayMeta = label("●", 10f, 0xFF80DDB4.toInt(), true)
        header.addView(overlayMeta)
        pill.addView(header, LinearLayout.LayoutParams(-1, dp(27)))
        overlayHeader = header

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        val flash = pillAction("⚡", "Torch")
        val music = pillAction("♫", "Music")
        val close = pillAction("×", "Close")
        actions.addView(flash, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        actions.addView(music, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        actions.addView(close, LinearLayout.LayoutParams(0, dp(42), 1f))
        pill.addView(actions)
        actions.visibility = View.GONE
        header.setOnClickListener { overlayExpanded = !overlayExpanded; actions.visibility = if (overlayExpanded) View.VISIBLE else View.GONE; updateOverlaySize() }
        flash.setOnClickListener { requestTorchToggle() }
        music.setOnClickListener { openMusicApp() }
        close.setOnClickListener { removeOverlay() }
        makeDraggable(header)

        val metrics = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager?.defaultDisplay?.getRealMetrics(metrics)
        val screenCenter = metrics.widthPixels / 2
        var cutoutCenter = screenCenter
        var cutoutTop = -1
        if (Build.VERSION.SDK_INT >= 28) {
            val cutout = window.decorView.rootWindowInsets?.displayCutout
            val rect = cutout?.boundingRects?.maxByOrNull { it.width() * it.height() }
            if (rect != null) {
                cutoutCenter = rect.centerX()
                cutoutTop = rect.centerY() - dp(21)
            }
        }
        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val statusBarHeight = if (statusBarId > 0) resources.getDimensionPixelSize(statusBarId) else dp(24)
        val topY = if (cutoutTop >= 0) cutoutTop.coerceAtLeast(0) else statusBarHeight + dp(3)
        val params = WindowManager.LayoutParams(
            dp(174), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = topY
            x = cutoutCenter - screenCenter
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        windowManager?.addView(pill, params)
        overlayView = pill
        overlayParams = params
        overlayExpanded = false
        overlayButton.text = "Disable floating island"
        updatePillStatus()
    }

    private fun pillAction(symbol: String, title: String) = TextView(this).apply {
        text = "$symbol\n$title"; textSize = 11f; gravity = Gravity.CENTER
        setTextColor(Color.WHITE); background = rounded(0xFF292936.toInt(), 14)
        isClickable = true
    }

    private fun makeDraggable(target: View) {
        var downX = 0f; var downY = 0f
        var startX = 0; var startY = 0
        var moved = false
        target.setOnTouchListener { _, event ->
            val params = overlayParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt()
                    val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = (startY + dy).coerceAtLeast(0)
                        try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) target.performClick()
                    true
                }
                else -> true
            }
        }
    }

    private fun updateOverlaySize() {
        val params = overlayParams ?: return
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        try { windowManager?.updateViewLayout(overlayView, params) } catch (_: Exception) {}
    }

    private fun updatePillStatus() {
        overlayLabel?.text = if (torchOn) "  TORCH  ·  ON" else "  BOLTT ISLAND  ·  READY"
        overlayMeta?.text = if (torchOn) "●" else "◆"
    }

    private fun openMusicApp() {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MUSIC)
        try { startActivity(intent) } catch (_: Exception) {
            Toast.makeText(this, "No music player app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun removeOverlay() {
        try { overlayView?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        overlayView = null; overlayParams = null; overlayHeader = null
        overlayLabel = null; overlayMeta = null; overlayExpanded = false
        if (::overlayButton.isInitialized) overlayButton.text = "Enable floating island"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if ((requestCode == 11 || requestCode == 12) && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 11) {
                shakeEnabled = true; updateShakeUi(); startSensor()
            } else setTorch(!torchOn)
        } else if (requestCode == 11 || requestCode == 12) {
            Toast.makeText(this, "Camera permission is needed for flashlight control.", Toast.LENGTH_LONG).show()
        }
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
