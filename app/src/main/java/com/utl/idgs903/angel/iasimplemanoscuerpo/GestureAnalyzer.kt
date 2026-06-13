package com.utl.idgs903.angel.iasimplemanoscuerpo

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.abs
import kotlin.math.hypot

class GestureAnalyzer {

    var onComboCompleted: ((Combo) -> Unit)? = null
    val sequenceDetector = SecuenciaDetector { combo -> onComboCompleted?.invoke(combo) }

    // History for motion tracking
    private var noseXHistory = mutableListOf<Float>()
    private var noseYHistory = mutableListOf<Float>()
    private var rightHandXHistory = mutableListOf<Float>()
    private var leftHandXHistory = mutableListOf<Float>()
    private var hipYHistory = mutableListOf<Float>()

    private val historySize = 15

    // History for hand temporal smoothing
    private val leftHandPoseHistory = mutableListOf<String>()
    private val rightHandPoseHistory = mutableListOf<String>()
    private val handHistorySize = 7 // Smoothing window

    @Synchronized
    fun analyze(
        poseResult: PoseLandmarkerResult?,
        handResult: HandLandmarkerResult?,
        isFrontCamera: Boolean = true
    ): String {
        var action = "Ninguno"

        val poseLandmarks = poseResult?.landmarks()?.firstOrNull()
        val handLandmarks = handResult?.landmarks()
        val handednesses = handResult?.handednesses()

        // 1. Sentadillas (Squats) - Pose
        if (poseLandmarks != null) {
            val nose = poseLandmarks[0]
            val leftHip = poseLandmarks[23]
            val rightHip = poseLandmarks[24]
            val leftKnee = poseLandmarks[25]
            val rightKnee = poseLandmarks[26]

            val avgHipY = (leftHip.y() + rightHip.y()) / 2
            val avgKneeY = (leftKnee.y() + rightKnee.y()) / 2

            // Track hip movement
            hipYHistory.add(avgHipY)
            if (hipYHistory.size > historySize) hipYHistory.removeAt(0)

            // Sentadilla: cadera baja (valor y aumenta en la pantalla) y se acerca a las rodillas
            // Aumentamos tolerancia a 0.08 para requerir sentadilla más profunda (refinamiento)
            if (abs(avgHipY - avgKneeY) < 0.08f) {
                action = "Sentadillas"
            }

            // 2. Decir sí / no con la cabeza
            noseXHistory.add(nose.x())
            noseYHistory.add(nose.y())
            if (noseXHistory.size > historySize) noseXHistory.removeAt(0)
            if (noseYHistory.size > historySize) noseYHistory.removeAt(0)

            val xVariance = if (noseXHistory.isNotEmpty()) noseXHistory.maxOrNull()!! - noseXHistory.minOrNull()!! else 0f
            val yVariance = if (noseYHistory.isNotEmpty()) noseYHistory.maxOrNull()!! - noseYHistory.minOrNull()!! else 0f

            // Aumentamos los umbrales para requerir movimiento intencional (refinamiento)
            if (xVariance > 0.08f && yVariance < 0.04f) {
                action = "Decir no con la cabeza"
            } else if (yVariance > 0.08f && xVariance < 0.04f) {
                action = "Decir si con la cabeza"
            }

            // 3. Aplaudir (Distancia entre muñecas)
            val leftWrist = poseLandmarks[15]
            val rightWrist = poseLandmarks[16]
            val wristsDistance = hypot((leftWrist.x() - rightWrist.x()).toDouble(), (leftWrist.y() - rightWrist.y()).toDouble())
            if (wristsDistance < 0.1) {
                action = "Aplaudir"
            }
        }

        // 4. Manos (Gestos Estáticos con Suavizado Temporal)
        var leftPose = ""
        var rightPose = ""
        var handDetected = false

        if (handLandmarks != null && handednesses != null && handLandmarks.isNotEmpty()) {
            for (i in handLandmarks.indices) {
                val hand = handLandmarks[i]
                val handedness = handednesses[i].first().categoryName() // "Left" or "Right"
                
                handDetected = true
                val pose = HandMetrics.detectPose(hand, handedness, isFrontCamera)
                val isFrente = HandMetrics.isPalmFacingCamera(hand, handedness)
                val vistaText = if (isFrente) "Frente" else "Espalda"
                
                val nombreGesto = "${pose.name.replace("_", " ")} [$vistaText]"
                
                // Si la cámara es frontal (espejo), la "Izquierda" de la imagen es la "Derecha" real del usuario.
                val realHandedness = if (isFrontCamera) {
                    if (handedness == "Left") "Right" else "Left"
                } else {
                    handedness
                }

                if (realHandedness == "Right") {
                    rightPose = nombreGesto
                } else if (realHandedness == "Left") {
                    leftPose = nombreGesto
                }
            }
        }

        // Apply Temporal Smoothing
        if (leftPose.isNotEmpty()) {
            leftHandPoseHistory.add(leftPose)
        } else {
            leftHandPoseHistory.add("") // Vacío si no se detectó mano izquierda
        }
        
        if (rightPose.isNotEmpty()) {
            rightHandPoseHistory.add(rightPose)
        } else {
            rightHandPoseHistory.add("") // Vacío si no se detectó mano derecha
        }

        if (leftHandPoseHistory.size > handHistorySize) leftHandPoseHistory.removeAt(0)
        if (rightHandPoseHistory.size > handHistorySize) rightHandPoseHistory.removeAt(0)

        // Obtener la pose más frecuente (Moda) en la ventana de tiempo
        val smoothedLeftPose = leftHandPoseHistory.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""
        val smoothedRightPose = rightHandPoseHistory.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: ""

        // Process sequence
        sequenceDetector.processPoses(smoothedLeftPose, smoothedRightPose)

        if (smoothedLeftPose.isNotEmpty() || smoothedRightPose.isNotEmpty()) {
            val leftStr = if (smoothedLeftPose.isNotEmpty()) "Izq: $smoothedLeftPose" else ""
            val rightStr = if (smoothedRightPose.isNotEmpty()) "Der: $smoothedRightPose" else ""
            val separator = if (leftStr.isNotEmpty() && rightStr.isNotEmpty()) ", " else ""
            action = "$leftStr$separator$rightStr\n\n${sequenceDetector.feedbackText}"
        } else {
            if (action == "Ninguno") {
                action = sequenceDetector.feedbackText
            } else {
                action = "$action\n\n${sequenceDetector.feedbackText}"
            }
        }

        return action
    }
}
