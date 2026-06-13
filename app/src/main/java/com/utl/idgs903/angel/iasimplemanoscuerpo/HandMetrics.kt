package com.utl.idgs903.angel.iasimplemanoscuerpo

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.hypot

object HandMetrics {

    enum class Finger {
        THUMB, INDEX, MIDDLE, RING, PINKY
    }

    /**
     * Determina si la palma de la mano está mirando hacia la cámara (Frente) o no (Espalda).
     * Usa el Producto Cruz 2D de los vectores Muñeca->Índice y Muñeca->Meñique.
     */
    fun isPalmFacingCamera(landmarks: List<NormalizedLandmark>, handedness: String): Boolean {
        val wrist = landmarks[0]
        val indexMcp = landmarks[5]
        val pinkyMcp = landmarks[17]

        // Vector U: Muñeca -> Índice
        val ux = indexMcp.x() - wrist.x()
        val uy = indexMcp.y() - wrist.y()

        // Vector V: Muñeca -> Meñique
        val vx = pinkyMcp.x() - wrist.x()
        val vy = pinkyMcp.y() - wrist.y()

        // Producto Cruz 2D (Componente Z)
        val crossZ = (ux * vy) - (uy * vx)

        // En la imagen que ve MediaPipe:
        // Hemos invertido la lógica matemática a petición del usuario para corregir la percepción visual.
        return if (handedness == "Left") {
            crossZ > 0
        } else {
            crossZ < 0
        }
    }

    private fun dist(l1: NormalizedLandmark, l2: NormalizedLandmark): Double {
        return hypot((l1.x() - l2.x()).toDouble(), (l1.y() - l2.y()).toDouble())
    }

    /**
     * Determina si un dedo específico está levantado basándose en distancias relativas
     * para que sea completamente invariante a la rotación de la mano.
     */
    fun isFingerUp(finger: Finger, landmarks: List<NormalizedLandmark>, handedness: String, isFrontCamera: Boolean = true): Boolean {
        val wrist = landmarks[0]
        return when (finger) {
            Finger.THUMB -> {
                // Para el pulgar, medimos si la punta está más alejada de la base del meñique
                // que el nudillo del pulgar. Si es así, está abierto.
                val tip = landmarks[4]
                val ip = landmarks[3]
                val pinkyMcp = landmarks[17]
                dist(tip, pinkyMcp) > dist(ip, pinkyMcp)
            }
            Finger.INDEX -> {
                // Si la punta está más lejos de la muñeca que la articulación media, está levantado
                dist(landmarks[8], wrist) > dist(landmarks[6], wrist)
            }
            Finger.MIDDLE -> {
                dist(landmarks[12], wrist) > dist(landmarks[10], wrist)
            }
            Finger.RING -> {
                dist(landmarks[16], wrist) > dist(landmarks[14], wrist)
            }
            Finger.PINKY -> {
                dist(landmarks[20], wrist) > dist(landmarks[18], wrist)
            }
        }
    }

    /**
     * Devuelve la cantidad de dedos levantados en una mano (0 a 5).
     */
    fun countRaisedFingers(landmarks: List<NormalizedLandmark>, handedness: String, isFrontCamera: Boolean = true): Int {
        var count = 0
        if (isFingerUp(Finger.THUMB, landmarks, handedness, isFrontCamera)) count++
        if (isFingerUp(Finger.INDEX, landmarks, handedness, isFrontCamera)) count++
        if (isFingerUp(Finger.MIDDLE, landmarks, handedness, isFrontCamera)) count++
        if (isFingerUp(Finger.RING, landmarks, handedness, isFrontCamera)) count++
        if (isFingerUp(Finger.PINKY, landmarks, handedness, isFrontCamera)) count++
        return count
    }

    enum class HandPose {
        A_PULGAR_ARRIBA,
        A_PULGAR_ABAJO,
        B_CUATRO,
        D_UNO,
        F_OK,
        I,
        L,
        U,
        V_PAZ,
        W_TRES,
        Y,
        PUÑO,
        CINCO_MANO_ABIERTA,
        ROCK,
        TE_AMO_ILY,
        DESCONOCIDO
    }

    /**
     * Detecta un gesto estático basado en el estado de los dedos y relaciones clave.
     */
    fun detectPose(landmarks: List<NormalizedLandmark>, handedness: String, isFrontCamera: Boolean = true): HandPose {
        val thumbUp = isFingerUp(Finger.THUMB, landmarks, handedness, isFrontCamera)
        val indexUp = isFingerUp(Finger.INDEX, landmarks, handedness, isFrontCamera)
        val middleUp = isFingerUp(Finger.MIDDLE, landmarks, handedness, isFrontCamera)
        val ringUp = isFingerUp(Finger.RING, landmarks, handedness, isFrontCamera)
        val pinkyUp = isFingerUp(Finger.PINKY, landmarks, handedness, isFrontCamera)

        val fingers = listOf(thumbUp, indexUp, middleUp, ringUp, pinkyUp)

        // Calcular el tamaño de la mano (Distancia de la muñeca (0) al nudillo base medio (9))
        val handSize = hypot(
            (landmarks[0].x() - landmarks[9].x()).toDouble(),
            (landmarks[0].y() - landmarks[9].y()).toDouble()
        ).coerceAtLeast(0.01) // Prevenir división por cero

        // OK / Letra F: Índice y pulgar tocándose, otros 3 levantados
        val distThumbIndex = hypot(
            (landmarks[4].x() - landmarks[8].x()).toDouble(),
            (landmarks[4].y() - landmarks[8].y()).toDouble()
        )
        val relativeDistThumbIndex = distThumbIndex / handSize

        // Umbral relativo: si la punta del pulgar está cerca de la punta del índice 
        // respecto al tamaño de la mano (ej. < 0.3 o 30% del tamaño de la palma).
        if (relativeDistThumbIndex < 0.4 && middleUp && ringUp && pinkyUp && !indexUp) {
            return HandPose.F_OK
        }

        // Pulgar Arriba / Abajo (o letra A aproximada)
        if (fingers == listOf(true, false, false, false, false)) {
            return if (landmarks[4].y() < landmarks[2].y()) {
                HandPose.A_PULGAR_ARRIBA
            } else {
                HandPose.A_PULGAR_ABAJO
            }
        }

        // Diferenciar entre U y V (Ambas tienen índice y medio levantados)
        if (fingers == listOf(false, true, true, false, false)) {
            val distIndexMiddle = hypot(
                (landmarks[8].x() - landmarks[12].x()).toDouble(),
                (landmarks[8].y() - landmarks[12].y()).toDouble()
            )
            val relativeDistIndexMiddle = distIndexMiddle / handSize
            
            // Si la distancia es menor al 40% de la palma, están tocándose (U)
            return if (relativeDistIndexMiddle < 0.4) {
                HandPose.U
            } else {
                HandPose.V_PAZ
            }
        }

        return when (fingers) {
            listOf(false, false, false, false, false) -> HandPose.PUÑO
            listOf(false, true, false, false, false) -> HandPose.D_UNO // Letra D o número 1
            listOf(false, true, true, true, false) -> HandPose.W_TRES  // Letra W o número 3
            listOf(false, true, true, true, true) -> HandPose.B_CUATRO // Letra B o número 4
            listOf(true, true, true, true, true) -> HandPose.CINCO_MANO_ABIERTA
            listOf(false, true, false, false, true) -> HandPose.ROCK
            listOf(true, true, false, false, true) -> HandPose.TE_AMO_ILY
            listOf(true, true, false, false, false) -> HandPose.L
            listOf(false, false, false, false, true) -> HandPose.I // Letra I
            listOf(true, false, false, false, true) -> HandPose.Y // Letra Y (Shaka)
            else -> HandPose.DESCONOCIDO
        }
    }
}
