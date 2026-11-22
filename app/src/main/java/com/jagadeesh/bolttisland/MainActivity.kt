package com.jagadeesh.bolttisland

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(36), dp(24), dp(24)); setBackgroundColor(0xFF101116.toInt()) }
        root.addView(TextView(this).apply { text = "BOLTT ISLAND"; textSize = 26f; setTextColor(0xFFFFFFFF.toInt()); setPadding(0, dp(10), 0, dp(10)) })
        root.addView(TextView(this).apply { text = "Floating pill + shake-to-toggle torch for Boltt Ace 5G."; textSize = 15f; setTextColor(0xFFCCCCD5.toInt()) })
        root.addView(TextView(this).apply { text = "Setup:\\n1. Grant camera permission.\\n2. Grant display-over-other-apps permission.\\n3. Tap Start. Keep the ongoing notification active."; textSize = 14f; setTextColor(0xFFB7BAC7.toInt()); setPadding(0, dp(18), 0, dp(18)) })
        val overlay = Button(this).apply { text = "Grant floating-window permission" }
        val start = Button(this).apply { text = "Start Island + Shake Torch" }
        val stop = Button(this).apply { text = "Stop service" }
        root.addView(overlay); root.addView(start); root.addView(stop)
        root.addView(TextView(this).apply { text = "Shake firmly to switch torch ON/OFF. Tap the pill to expand or collapse it. Background restrictions may affect sensor detection."; textSize = 13f; setTextColor(0xFF9FA3B2.toInt()); setPadding(0, dp(18), 0, 0) })
        setContentView(root)
        overlay.setOnClickListener { if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) else Toast.makeText(this, "Permission already allowed", Toast.LENGTH_SHORT).show() }
        start.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA), 10); return@setOnClickListener }
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); Toast.makeText(this, "Allow overlay access, then tap Start again.", Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val i = Intent(this, IslandService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }
        stop.setOnClickListener { stopService(Intent(this, IslandService::class.java)) }
    }
}
