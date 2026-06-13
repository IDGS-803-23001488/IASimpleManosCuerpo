package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermisosActivity : AppCompatActivity() {

    private lateinit var tvStatusCamera: TextView
    private lateinit var btnCamera: Button

    private lateinit var tvStatusOverlay: TextView
    private lateinit var btnOverlay: Button

    private lateinit var tvStatusBattery: TextView
    private lateinit var btnBattery: Button

    private lateinit var tvStatusAutoStart: TextView
    private lateinit var btnAutoStart: Button

    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permisos)

        tvStatusCamera = findViewById(R.id.tvStatusCamera)
        btnCamera = findViewById(R.id.btnCamera)
        
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay)
        btnOverlay = findViewById(R.id.btnOverlay)

        tvStatusBattery = findViewById(R.id.tvStatusBattery)
        btnBattery = findViewById(R.id.btnBattery)

        tvStatusAutoStart = findViewById(R.id.tvStatusAutoStart)
        btnAutoStart = findViewById(R.id.btnAutoStart)

        btnContinue = findViewById(R.id.btnContinue)

        btnCamera.setOnClickListener {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        btnAutoStart.setOnClickListener {
            try {
                val intent = Intent()
                intent.component = ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                startActivity(intent)
            } catch (e: Exception) {
                // Not a Xiaomi device or different version, fallback to generic app settings
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }

        btnContinue.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun checkPermissions() {
        var allGranted = true

        // 1. Camera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            tvStatusCamera.text = "✅"
            btnCamera.isEnabled = false
            btnCamera.text = "OK"
        } else {
            tvStatusCamera.text = "❌"
            btnCamera.isEnabled = true
            btnCamera.text = "PEDIR"
            allGranted = false
        }

        // 2. Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
            tvStatusOverlay.text = "✅"
            btnOverlay.isEnabled = false
            btnOverlay.text = "OK"
        } else {
            tvStatusOverlay.text = "❌"
            btnOverlay.isEnabled = true
            btnOverlay.text = "PEDIR"
            allGranted = false
        }

        // 3. Battery
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && powerManager.isIgnoringBatteryOptimizations(packageName)) {
            tvStatusBattery.text = "✅"
            btnBattery.isEnabled = false
            btnBattery.text = "OK"
        } else {
            tvStatusBattery.text = "❌"
            btnBattery.isEnabled = true
            btnBattery.text = "PEDIR"
            allGranted = false
        }

        // 4. AutoStart (Cannot be verified programmatically, we assume warning)
        // We will just let the user go there and then click continue.

        if (allGranted) {
            btnContinue.isEnabled = true
            btnContinue.setBackgroundColor(android.graphics.Color.parseColor("#03DAC5"))
        } else {
            btnContinue.isEnabled = false
            btnContinue.setBackgroundColor(android.graphics.Color.GRAY)
        }
    }
}
