package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.utl.idgs903.angel.iasimplemanoscuerpo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""

    private val uiHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (CameraSharedState.isServiceRunning) {
                CameraSharedState.latestBitmap?.let { bmp ->
                    binding.viewFinder.setImageBitmap(bmp)
                }

                binding.overlayView.updateResults(
                    CameraSharedState.lastPoseResult,
                    CameraSharedState.lastHandResult,
                    CameraSharedState.imageWidth,
                    CameraSharedState.imageHeight
                )
                
                binding.overlayView.updateAction(CameraSharedState.currentAction)
            } else {
                binding.viewFinder.setImageBitmap(null)
                binding.overlayView.updateResults(null, null, 1, 1)
            }
            uiHandler.postDelayed(this, 33) // ~30 fps
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.root.setOnLongClickListener {
            val intent = Intent(this, ComboListActivity::class.java)
            startActivity(intent)
            true
        }

        binding.btnSwitchCamera.setOnClickListener {
            val options = arrayOf("Cámara Frontal", "Cámara Trasera", "Cámara Wi-Fi IP")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Seleccionar Cámara")
                .setSingleChoiceItems(options, cameraMode) { dialog, which ->
                    cameraMode = which
                    if (cameraMode == 2) {
                        showIpCameraDialog()
                    } else {
                        if (binding.switchService.isChecked) {
                            startCameraService()
                        }
                    }
                    dialog.dismiss()
                }
                .show()
        }

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (cameraMode == 2 && ipCameraUrl.isEmpty()) {
                    showIpCameraDialog()
                } else {
                    startCameraService()
                }
            } else {
                stopCameraService()
            }
        }

        binding.btnSchedule.setOnClickListener {
            showScheduleDialog()
        }

        updateScheduleButtonUI()
    }

    private fun showScheduleDialog() {
        val options = arrayOf("Activo 24/7", "Programar Horario")
        val currentSelection = if (PrefsManager.isScheduleEnabled(this)) 1 else 0

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Configurar Horario")
            .setSingleChoiceItems(options, currentSelection) { dialog, which ->
                if (which == 0) {
                    PrefsManager.setScheduleEnabled(this, false)
                    updateScheduleButtonUI()
                    notifyServiceScheduleChanged()
                    dialog.dismiss()
                } else {
                    dialog.dismiss()
                    showTimePickerForStart()
                }
            }
            .show()
    }

    private fun showTimePickerForStart() {
        val currentHour = PrefsManager.getStartHour(this)
        val currentMinute = PrefsManager.getStartMinute(this)
        
        TimePickerDialog(this, { _, hour, minute ->
            PrefsManager.setStartHour(this, hour)
            PrefsManager.setStartMinute(this, minute)
            showTimePickerForEnd()
        }, currentHour, currentMinute, true).apply {
            setTitle("Hora de INICIO")
            show()
        }
    }

    private fun showTimePickerForEnd() {
        val currentHour = PrefsManager.getEndHour(this)
        val currentMinute = PrefsManager.getEndMinute(this)
        
        TimePickerDialog(this, { _, hour, minute ->
            PrefsManager.setEndHour(this, hour)
            PrefsManager.setEndMinute(this, minute)
            PrefsManager.setScheduleEnabled(this, true)
            updateScheduleButtonUI()
            notifyServiceScheduleChanged()
        }, currentHour, currentMinute, true).apply {
            setTitle("Hora de FIN")
            show()
        }
    }

    private fun updateScheduleButtonUI() {
        binding.btnSchedule.text = "🕒 " + PrefsManager.getScheduleString(this)
    }

    private fun showIpCameraDialog() {
        val input = android.widget.EditText(this)
        input.hint = "http://192.168.1.100:81/stream"
        input.setText(ipCameraUrl)
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("URL de Cámara Wi-Fi")
            .setView(input)
            .setPositiveButton("Conectar") { _, _ ->
                ipCameraUrl = input.text.toString()
                binding.switchService.isChecked = true
                startCameraService()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                if (!CameraSharedState.isServiceRunning) {
                    binding.switchService.isChecked = false
                }
            }
            .show()
    }

    private fun startCameraService() {
        val intent = Intent(this, BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_START
            putExtra(BackgroundCameraService.EXTRA_CAMERA_MODE, cameraMode)
            putExtra(BackgroundCameraService.EXTRA_IP_URL, ipCameraUrl)
        }
        androidx.core.content.ContextCompat.startForegroundService(this, intent)
    }

    private fun stopCameraService() {
        val intent = Intent(this, BackgroundCameraService::class.java).apply {
            action = BackgroundCameraService.ACTION_STOP
        }
        startService(intent)
    }

    private fun notifyServiceScheduleChanged() {
        if (CameraSharedState.isServiceRunning) {
            val intent = Intent(this, BackgroundCameraService::class.java).apply {
                action = BackgroundCameraService.ACTION_UPDATE_SCHEDULE
            }
            startService(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        binding.switchService.setOnCheckedChangeListener(null)
        binding.switchService.isChecked = CameraSharedState.isServiceRunning
        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (cameraMode == 2 && ipCameraUrl.isEmpty()) {
                    showIpCameraDialog()
                } else {
                    startCameraService()
                }
            } else {
                stopCameraService()
            }
        }
        updateScheduleButtonUI()

        if (CameraSharedState.isServiceRunning) {
            val intent = Intent(this, BackgroundCameraService::class.java).apply {
                action = BackgroundCameraService.ACTION_RELOAD_COMBOS
            }
            startService(intent)
        }
        uiHandler.post(updateRunnable)
    }

    override fun onPause() {
        super.onPause()
        uiHandler.removeCallbacks(updateRunnable)
    }
}
