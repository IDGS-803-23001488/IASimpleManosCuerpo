package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.Calendar
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class BackgroundCameraService : LifecycleService() {

    private lateinit var cameraExecutor: ExecutorService
    private var poseLandmarker: PoseLandmarker? = null
    private var handLandmarker: HandLandmarker? = null
    private val gestureAnalyzer = GestureAnalyzer()

    private var cameraMode = 0 // 0: Frontal, 1: Trasera, 2: Wi-Fi IP
    private var ipCameraUrl = ""
    private var mjpegStreamReader: MjpegStreamReader? = null
    private var isCameraActuallyRunning = false

    private val scheduleHandler = Handler(Looper.getMainLooper())
    private val scheduleRunnable = object : Runnable {
        override fun run() {
            checkSchedule()
            scheduleHandler.postDelayed(this, 30000) // Check every 30 seconds
        }
    }

    companion object {
        const val CHANNEL_ID = "CameraServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RELOAD_COMBOS = "ACTION_RELOAD_COMBOS"
        const val ACTION_UPDATE_SCHEDULE = "ACTION_UPDATE_SCHEDULE"
        const val EXTRA_CAMERA_MODE = "EXTRA_CAMERA_MODE"
        const val EXTRA_IP_URL = "EXTRA_IP_URL"
    }

    override fun onCreate() {
        super.onCreate()
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupMediaPipe()
        setupGestureAnalyzerCallback()
        loadCombos()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        when (intent?.action) {
            ACTION_START -> {
                cameraMode = intent.getIntExtra(EXTRA_CAMERA_MODE, cameraMode)
                val newUrl = intent.getStringExtra(EXTRA_IP_URL)
                if (newUrl != null) ipCameraUrl = newUrl
                
                CameraSharedState.isServiceRunning = true
                startForegroundService("Iniciando...")
                scheduleHandler.removeCallbacks(scheduleRunnable)
                scheduleHandler.post(scheduleRunnable)
            }
            ACTION_STOP -> {
                scheduleHandler.removeCallbacks(scheduleRunnable)
                stopCamera()
                stopForeground(true)
                stopSelf()
                CameraSharedState.isServiceRunning = false
            }
            ACTION_RELOAD_COMBOS -> {
                loadCombos()
            }
            ACTION_UPDATE_SCHEDULE -> {
                checkSchedule()
            }
        }
        return START_STICKY
    }

    private fun checkSchedule() {
        val enabled = PrefsManager.isScheduleEnabled(this)
        if (!enabled) {
            if (!isCameraActuallyRunning) {
                startCamera()
                updateNotification("IA Gestos Activa", "Analizando cámara 24/7...")
            }
            return
        }

        val startH = PrefsManager.getStartHour(this)
        val startM = PrefsManager.getStartMinute(this)
        val endH = PrefsManager.getEndHour(this)
        val endM = PrefsManager.getEndMinute(this)

        val cal = Calendar.getInstance()
        val currentH = cal.get(Calendar.HOUR_OF_DAY)
        val currentM = cal.get(Calendar.MINUTE)

        val currentTotalMins = currentH * 60 + currentM
        val startTotalMins = startH * 60 + startM
        val endTotalMins = endH * 60 + endM

        val isWithinSchedule = if (startTotalMins <= endTotalMins) {
            currentTotalMins in startTotalMins..endTotalMins
        } else {
            // Horario cruzado de medianoche (ej. 22:00 a 06:00)
            currentTotalMins >= startTotalMins || currentTotalMins <= endTotalMins
        }

        if (isWithinSchedule) {
            if (!isCameraActuallyRunning) {
                startCamera()
                updateNotification("IA Gestos Activa", "Analizando dentro de horario...")
            }
        } else {
            if (isCameraActuallyRunning) {
                stopCamera()
                updateNotification("IA Gestos en Reposo", "Fuera de horario de funcionamiento.")
                CameraSharedState.latestBitmap = null
            }
        }
    }

    private fun loadCombos() {
        val combos = SecuenciaConfigManager.loadCombos(this)
        gestureAnalyzer.sequenceDetector.updateCombos(combos)
    }

    private fun startForegroundService(text: String) {
        createNotificationChannel()
        
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("IA Gestos")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun updateNotification(title: String, text: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Camera Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
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
                CameraSharedState.lastPoseResult = result
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
                CameraSharedState.lastHandResult = result
                analyzeGestures()
            }
            .setErrorListener { error ->
                Log.e("MediaPipe", "Error in hand: ${error.message}")
            }
            .build()
        handLandmarker = HandLandmarker.createFromOptions(this, handOptions)
    }

    private fun setupGestureAnalyzerCallback() {
        gestureAnalyzer.onComboCompleted = { combo ->
            try {
                val toneGen = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 100)
                toneGen.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 500)
            } catch (e: Exception) {
                Log.e("Audio", "Error al reproducir sonido", e)
            }
            
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                gestureAnalyzer.sequenceDetector.resetAll()
            }, 3000)
        }
    }

    private fun analyzeGestures() {
        val isFrontCamera = cameraMode == 0
        val action = gestureAnalyzer.analyze(CameraSharedState.lastPoseResult, CameraSharedState.lastHandResult, isFrontCamera)
        CameraSharedState.currentAction = action
    }

    private fun startCamera() {
        isCameraActuallyRunning = true
        mjpegStreamReader?.stop()
        mjpegStreamReader = null

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            if (cameraMode == 2) {
                if (ipCameraUrl.isNotEmpty()) {
                    mjpegStreamReader = MjpegStreamReader(ipCameraUrl) { bitmap ->
                        processBitmap(bitmap)
                    }
                    mjpegStreamReader?.start()
                }
                return@addListener
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
                    this, cameraSelector, imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraX", "Fallo al vincular casos de uso", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        isCameraActuallyRunning = false
        mjpegStreamReader?.stop()
        mjpegStreamReader = null
        try {
            val cameraProvider = ProcessCameraProvider.getInstance(this).get()
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e("CameraService", "Error stopping camera", e)
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        if (cameraMode == 0) {
            matrix.postScale(-1f, 1f) // Mirror
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, false
        )
        
        processBitmap(rotatedBitmap)
    }

    private fun processBitmap(bitmap: Bitmap) {
        CameraSharedState.imageWidth = bitmap.width
        CameraSharedState.imageHeight = bitmap.height
        CameraSharedState.latestBitmap = bitmap

        val mpImage = BitmapImageBuilder(bitmap).build()
        val timestamp = System.currentTimeMillis()

        try {
            poseLandmarker?.detectAsync(mpImage, timestamp)
            handLandmarker?.detectAsync(mpImage, timestamp)
        } catch (e: Exception) {
            Log.e("MediaPipe", "Error al procesar bitmap: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scheduleHandler.removeCallbacks(scheduleRunnable)
        stopCamera()
        cameraExecutor.shutdown()
        poseLandmarker?.close()
        handLandmarker?.close()
        CameraSharedState.isServiceRunning = false
    }
}
