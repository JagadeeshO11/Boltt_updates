package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var runButton: Button
    private lateinit var torchButton: Button
    private lateinit var sensitivityLabel: TextView
    private var running = false
    private var torchOn = false
    private var sensitivity = 70
    private lateinit var cameraManager: CameraManager
    private var flashId: String? = null
    private var manualTorchOwned = false
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == flashId) { torchOn = enabled; if (!enabled) manualTorchOwned = false; render() }
        }
        override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == flashId) { torchOn = false; manualTorchOwned = false; render() }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ShakeTorchService.ACTION_STATE) return
            running = intent.getBooleanExtra(ShakeTorchService.EXTRA_RUNNING, running)
            torchOn = intent.getBooleanExtra(ShakeTorchService.EXTRA_TORCH, torchOn)
            render()
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun bg(color: Int, radius: Int = 18) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun txt(s: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = bg(0xFF1B1D29.toInt(), 22)
    }
    private fun btn(s: String, primary: Boolean = false) = Button(this).apply {
        text = s; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
        background = bg(if (primary) 0xFF7058E8.toInt() else 0xFF343746.toInt(), 15)
        stateListAnimator = null; minHeight = dp(50)
    }
    private fun gap(h: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, dp(h))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFF10111A.toInt()
        window.navigationBarColor = 0xFF10111A.toInt()
        sensitivity = getPreferences(MODE_PRIVATE).getInt("sensitivity", 70).coerceIn(0, 100)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        findFlash()
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(26), dp(20), dp(28))
            setBackgroundColor(0xFF10111A.toInt())
        }
        page.addView(txt("BOLTT ISLAND", 21f, Color.WHITE, true))
        page.addView(txt("Shake to Torch · Version 2.5", 13f, 0xFFAAAABD.toInt()))
        page.addView(gap(22))

        val hero = card()
        hero.addView(txt("YOUR FLASHLIGHT, READY", 11f, 0xFFC6B9FF.toInt(), true))
        hero.addView(gap(9))
        hero.addView(txt("Shake. Flash.\nKeep moving.", 27f, Color.WHITE, true))
        hero.addView(gap(8))
        hero.addView(txt("Shake detection stays active in the background while its ongoing notification is shown.", 14f, 0xFFC0C1CE.toInt()))
        page.addView(hero); page.addView(gap(16))

        val controls = card()
        controls.addView(txt("BACKGROUND SHAKE", 16f, Color.WHITE, true))
        controls.addView(gap(6))
        controls.addView(txt("Low-rate foreground sensor service. Stop it here or from its notification.", 13f, 0xFFB8B9C7.toInt()))
        controls.addView(gap(13))
        runButton = btn("Start background shake", true)
        controls.addView(runButton, LinearLayout.LayoutParams(-1, dp(50)))
        runButton.setOnClickListener { startOrStop() }
        controls.addView(gap(17))
        sensitivityLabel = txt("Sensitivity · 70%", 13f, 0xFFE2E0EC.toInt(), true)
        controls.addView(sensitivityLabel)
        val seek = SeekBar(this).apply { max = 100; progress = sensitivity }
        controls.addView(seek)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                sensitivity = value
                sensitivityLabel.text = "Sensitivity · " + value + "%"
                getPreferences(MODE_PRIVATE).edit().putInt("sensitivity", value).apply()
                if (fromUser && running) sendService(ShakeTorchService.ACTION_SET_SENSITIVITY, value)
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        page.addView(controls); page.addView(gap(16))

        val torch = card()
        torch.addView(txt("MANUAL TORCH", 16f, Color.WHITE, true))
        torch.addView(gap(6))
        torch.addView(txt("Manual control without changing the background shake setting.", 13f, 0xFFB8B9C7.toInt()))
        torch.addView(gap(12))
        torchButton = btn("Turn flashlight ON")
        torch.addView(torchButton, LinearLayout.LayoutParams(-1, dp(50)))
        torchButton.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
            else if (running) sendService(ShakeTorchService.ACTION_TOGGLE_TORCH) else toggleManualTorch()
        }
        page.addView(torch); page.addView(gap(16))

        val stateCard = card()
        stateCard.addView(txt("STATUS", 11f, 0xFFC6B9FF.toInt(), true))
        stateCard.addView(gap(8))
        status = txt("Checking service…", 14f, Color.WHITE)
        stateCard.addView(status)
        page.addView(stateCard); page.addView(gap(16))
        page.addView(txt(
            "BATTERY & SYSTEM SAFETY\n• 20 sensor samples/second with batching where supported.\n• No wake lock, network, analytics or auto-start after reboot.\n• If Android or another app is using the flash, this app will not take it over.\n• Ongoing notification is required for background operation.",
            12f, 0xFF9699AA.toInt()
        ))
        setContentView(page)
        render()
    }

    private fun findFlash() {
        flashId = try { cameraManager.cameraIdList.firstOrNull { cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } } catch (_: Exception) { null }
    }

    private fun toggleManualTorch() {
        val id = flashId ?: run { Toast.makeText(this, "No camera flash found.", Toast.LENGTH_SHORT).show(); return }
        if (torchOn && !manualTorchOwned) { Toast.makeText(this, "Flash is already controlled by Android or another app.", Toast.LENGTH_SHORT).show(); return }
        try {
            val next = !manualTorchOwned
            cameraManager.setTorchMode(id, next)
            manualTorchOwned = next
            torchOn = next
            render()
        } catch (_: Exception) {
            manualTorchOwned = false
            Toast.makeText(this, "Flash is busy or unavailable. Try again when the camera is free.", Toast.LENGTH_LONG).show()
        }
    }

    private fun startOrStop() {
        if (running) {
            sendService(ShakeTorchService.ACTION_STOP)
            running = false; render(); return
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA); return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS); return
        }
        startForegroundService(Intent(this, ShakeTorchService::class.java)
            .setAction(ShakeTorchService.ACTION_START)
            .putExtra(ShakeTorchService.EXTRA_SENSITIVITY, sensitivity))
        running = true; render()
    }

    private fun sendService(action: String, value: Int? = null) {
        val i = Intent(this, ShakeTorchService::class.java).setAction(action)
        if (value != null) i.putExtra(ShakeTorchService.EXTRA_SENSITIVITY, value)
        startService(i)
    }

    private fun render() {
        if (!::status.isInitialized) return
        status.text = "Background shake: " + (if (running) "RUNNING" else "STOPPED") +
            "\nTorch state: " + (if (torchOn) "ON" else "OFF") +
            "\nSensitivity: " + sensitivity + "%"
        runButton.text = if (running) "Stop background shake" else "Start background shake"
        torchButton.text = if (torchOn) "Turn flashlight OFF" else "Turn flashlight ON"
        sensitivityLabel.text = "Sensitivity · " + sensitivity + "%"
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ShakeTorchService.ACTION_STATE)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(receiver, filter)
        cameraManager.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        startService(Intent(this, ShakeTorchService::class.java).setAction(ShakeTorchService.ACTION_QUERY))
    }

    override fun onPause() {
        try { cameraManager.unregisterTorchCallback(torchCallback) } catch (_: Exception) {}
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        super.onPause()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (results.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permission not granted.", Toast.LENGTH_SHORT).show(); return
        }
        if (code == REQUEST_CAMERA || code == REQUEST_NOTIFICATIONS) startOrStop()
    }

    companion object {
        private const val REQUEST_CAMERA = 31
        private const val REQUEST_NOTIFICATIONS = 32
    }
}