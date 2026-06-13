package com.utl.idgs903.angel.iasimplemanoscuerpo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var currentAction: String = "Ninguno"
    private var poseResult: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult? = null
    private var handResult: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult? = null

    private var imageWidth = 1
    private var imageHeight = 1
    private var scaleFactor = 1f

    private val textPaint = Paint().apply {
        color = Color.GREEN
        textSize = 50f // Reducido para que no sature la pantalla
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#80000000") // Semi-transparent black
        style = Paint.Style.FILL
    }

    private val pointPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 8f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun updateAction(action: String) {
        if (currentAction != action) {
            currentAction = action
            invalidate()
        }
    }

    fun updateResults(
        pose: com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult?, 
        hands: com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult?,
        imgWidth: Int,
        imgHeight: Int
    ) {
        poseResult = pose
        handResult = hands
        imageWidth = imgWidth
        imageHeight = imgHeight
        
        // Calcular el factor de escala para fillStart (lo que usa PreviewView)
        scaleFactor = kotlin.math.max(width.toFloat() / imageWidth, height.toFloat() / imageHeight)
        
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Dibujar puntos de la pose
        poseResult?.landmarks()?.forEach { landmarkList ->
            // Dibujar puntos
            landmarkList.forEach { landmark ->
                val x = landmark.x() * imageWidth * scaleFactor
                val y = landmark.y() * imageHeight * scaleFactor
                canvas.drawCircle(x, y, 8f, pointPaint)
            }
        }

        // Dibujar puntos de las manos
        handResult?.landmarks()?.forEach { landmarkList ->
            // Dibujar puntos de la mano
            landmarkList.forEach { landmark ->
                val x = landmark.x() * imageWidth * scaleFactor
                val y = landmark.y() * imageHeight * scaleFactor
                canvas.drawCircle(x, y, 8f, pointPaint)
            }
        }

        if (currentAction != "Ninguno" && currentAction.isNotBlank()) {
            val lines = currentAction.split("\n").filter { it.isNotBlank() }
            if (lines.isEmpty()) return

            val padding = 30f
            val lineHeight = textPaint.textSize + 10f
            
            var maxWidth = 0f
            for (line in lines) {
                val lineWidth = textPaint.measureText(line)
                if (lineWidth > maxWidth) maxWidth = lineWidth
            }

            val rectLeft = (width / 2f) - (maxWidth / 2f) - padding
            val rectTop = 80f - textPaint.textSize
            val rectRight = (width / 2f) + (maxWidth / 2f) + padding
            val rectBottom = rectTop + (lines.size * lineHeight) + padding

            canvas.drawRect(rectLeft, rectTop, rectRight, rectBottom, bgPaint)
            
            var currentY = 80f
            for (line in lines) {
                canvas.drawText(line, width / 2f, currentY, textPaint)
                currentY += lineHeight
            }
        }
    }
}
