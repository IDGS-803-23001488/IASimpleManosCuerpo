package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.graphics.Bitmap
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

object CameraSharedState {
    @Volatile
    var isServiceRunning: Boolean = false

    @Volatile
    var latestBitmap: Bitmap? = null

    @Volatile
    var lastPoseResult: PoseLandmarkerResult? = null

    @Volatile
    var lastHandResult: HandLandmarkerResult? = null

    @Volatile
    var currentAction: String = "Ninguno"

    @Volatile
    var imageWidth: Int = 1

    @Volatile
    var imageHeight: Int = 1
}
