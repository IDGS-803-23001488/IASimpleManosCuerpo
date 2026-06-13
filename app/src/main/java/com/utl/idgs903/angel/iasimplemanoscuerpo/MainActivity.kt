package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.utl.idgs903.angel.iasimplemanoscuerpo.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    
    private val gestureAnalyzer = GestureAnalyzer()

    private var lastPoseResult: PoseLandmarkerResult? = null
    private var lastHandResult: HandLandmarkerResult? = null
    
    private var lastImageWidth: Int = 1
    private var lastImageHeight: Int = 1

    private fun setupGestureAnalyzerCallback() {
        gestureAnalyzer.onComboCompleted = { combo ->
            runOnUiThread {
                Toast.makeText(this@MainActivity, "¡COMBO '${combo.name.uppercase()}' COMPLETADO!", Toast.LENGTH_LONG).show()
                try {
                    val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                    toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 500)
                } catch (e: Exception) {
                    Log.e("Audio", "Error al reproducir sonido", e)
                }
                
                // Reiniciar secuencia después de 3 segundos
                binding.overlayView.postDelayed({
                    gestureAnalyzer.sequenceDetector.resetAll()
                }, 3000)
            }
        }
    }

    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""
    private var mjpegStreamReader: MjpegStreamReader? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                startCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.root.setOnLongClickListener {
            val intent = android.content.Intent(this, ComboListActivity::class.java)
            startActivity(intent)
            true
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        setupGestureAnalyzerCallback()
        setupMediaPipe()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                        startCamera()
                    }
                    dialog.dismiss()
                }
                .show()
        }
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
                startCamera()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                cameraMode = 0
                startCamera()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        val combos = SecuenciaConfigManager.loadCombos(this)
        gestureAnalyzer.sequenceDetector.updateCombos(combos)
    }

    private fun setupMediaPipe() {
        val poseBaseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU)
            .build()
        val poseOptions = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(poseBaseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                lastPoseResult = result
                analyzeGestures()
            }
            .setErrorListener { error ->
                Log.e("MediaPipe", "Error in pose: ${error.message}")
            }
            .build()
        poseLandmarker = PoseLandmarker.createFromOptions(this, poseOptions)

        val handBaseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .setDelegate(com.google.mediapipe.tasks.core.Delegate.GPU)
            .build()
        val handOptions = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(handBaseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setResultListener { result, _ ->
                lastHandResult = result
                analyzeGestures()
            }
            .setErrorListener { error ->
                Log.e("MediaPipe", "Error in hand: ${error.message}")
            }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(this, handOptions)
    }

    private fun analyzeGestures() {
        val isFrontCamera = cameraMode == 0
        val action = gestureAnalyzer.analyze(lastPoseResult, lastHandResult, isFrontCamera)
        runOnUiThread {
            binding.overlayView.updateAction(action)
            binding.overlayView.updateResults(lastPoseResult, lastHandResult, lastImageWidth, lastImageHeight)
        }
    }

    private fun startCamera() {
        mjpegStreamReader?.stop()
        mjpegStreamReader = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            if (cameraMode == 2) {
                // Modo Wi-Fi IP
                if (ipCameraUrl.isNotEmpty()) {
                    mjpegStreamReader = MjpegStreamReader(ipCameraUrl) { bitmap ->
                        processBitmap(bitmap)
                    }
                    mjpegStreamReader?.start()
                }
                return@addListener
            }

            // Modo Local
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = if (cameraMode == 0) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            try {
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Fallo al vincular casos de uso", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmapBuffer = android.graphics.Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        
        val matrix = android.graphics.Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        if (cameraMode == 0) {
            matrix.postScale(-1f, 1f) // Mirror for front camera
        }
        val rotatedBitmap = android.graphics.Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, false
        )
        
        processBitmap(rotatedBitmap)
    }

    private fun processBitmap(bitmap: android.graphics.Bitmap) {
        lastImageWidth = bitmap.width
        lastImageHeight = bitmap.height

        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestamp = System.currentTimeMillis()

        try {
            poseLandmarker?.detectAsync(mpImage, timestamp)
            handLandmarker?.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            Log.e("MediaPipe", "Error al procesar bitmap: ${e.message}")
        }
    }



    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
        handLandmarker?.close()
    }
}
