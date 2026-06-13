package com.utl.idgs903.angel.iasimplemanoscuerpo

enum class ManoObjetivo {
    LEFT, RIGHT, ANY
}

data class PasoSecuencia(
    val nombreGesto: String,
    val manoObjetivo: ManoObjetivo,
    val cuadrosRequeridos: Int = 10
)

enum class SequenceState {
    WAITING_ACTIVATOR,
    IN_SEQUENCE,
    WAITING_DEACTIVATOR,
    COMPLETED
}

class RastreadorCombo(val combo: Combo, private val onComboCompleted: (Combo) -> Unit) {
    var currentState = if (combo.activador != null) SequenceState.WAITING_ACTIVATOR else SequenceState.IN_SEQUENCE
        private set
    var currentStepIndex = 0
        private set
    var framesHeld = 0
        private set
    var feedbackText = "Esperando..."
        private set

    init {
        updateFeedback()
    }

    fun reset() {
        currentState = if (combo.activador != null) SequenceState.WAITING_ACTIVATOR else SequenceState.IN_SEQUENCE
        currentStepIndex = 0
        framesHeld = 0
        updateFeedback()
    }

    fun processPoses(leftPose: String, rightPose: String) {
        if (combo.pasos.isEmpty() && combo.activador == null && combo.deactivador == null) {
            feedbackText = "${combo.name}: No tiene pasos configurados."
            return
        }

        if (currentState == SequenceState.COMPLETED) {
            return
        }

        when (currentState) {
            SequenceState.WAITING_ACTIVATOR -> {
                val activador = combo.activador ?: return
                if (isPoseMatching(leftPose, rightPose, activador)) {
                    framesHeld++
                    val percentage = (framesHeld.toFloat() / activador.cuadrosRequeridos) * 100
                    feedbackText = "${combo.name} - Validando Activador (${activador.nombreGesto}): ${percentage.toInt().coerceAtMost(100)}%"
                    
                    if (framesHeld >= activador.cuadrosRequeridos) {
                        currentState = SequenceState.IN_SEQUENCE
                        framesHeld = 0
                        // Si no hay pasos ni desactivador, se completó solo con el activador.
                        if (combo.pasos.isEmpty() && combo.deactivador == null) {
                            completeCombo()
                        } else {
                            updateFeedback()
                        }
                    }
                } else {
                    framesHeld = 0
                    updateFeedback()
                }
            }
            SequenceState.IN_SEQUENCE -> {
                if (combo.pasos.isEmpty()) {
                    // Pasar directo al desactivador si no hay pasos
                    if (combo.deactivador != null) {
                        currentState = SequenceState.WAITING_DEACTIVATOR
                        updateFeedback()
                    } else {
                        completeCombo()
                    }
                    return
                }

                val currentStep = combo.pasos[currentStepIndex]
                if (isPoseMatching(leftPose, rightPose, currentStep)) {
                    framesHeld++
                    val percentage = (framesHeld.toFloat() / currentStep.cuadrosRequeridos) * 100
                    feedbackText = "${combo.name} - Validando Paso ${currentStepIndex + 1}/${combo.pasos.size} (${currentStep.nombreGesto}): ${percentage.toInt().coerceAtMost(100)}%"
                    
                    if (framesHeld >= currentStep.cuadrosRequeridos) {
                        currentStepIndex++
                        framesHeld = 0
                        if (currentStepIndex >= combo.pasos.size) {
                            if (combo.deactivador != null) {
                                currentState = SequenceState.WAITING_DEACTIVATOR
                            } else {
                                completeCombo()
                            }
                        }
                        updateFeedback()
                    }
                } else {
                    framesHeld = 0
                    updateFeedback()
                }
            }
            SequenceState.WAITING_DEACTIVATOR -> {
                val deactivador = combo.deactivador ?: return
                if (isPoseMatching(leftPose, rightPose, deactivador)) {
                    framesHeld++
                    val percentage = (framesHeld.toFloat() / deactivador.cuadrosRequeridos) * 100
                    feedbackText = "${combo.name} - Validando Desactivador (${deactivador.nombreGesto}): ${percentage.toInt().coerceAtMost(100)}%"
                    
                    if (framesHeld >= deactivador.cuadrosRequeridos) {
                        completeCombo()
                    }
                } else {
                    framesHeld = 0
                    updateFeedback()
                }
            }
            SequenceState.COMPLETED -> {}
        }
    }

    private fun completeCombo() {
        currentState = SequenceState.COMPLETED
        feedbackText = "¡${combo.name.uppercase()} COMPLETADO!"
        onComboCompleted(combo)
    }

    private fun updateFeedback() {
        if (currentState == SequenceState.WAITING_ACTIVATOR) {
            feedbackText = "${combo.name}: Esperando Activador (${combo.activador?.nombreGesto})"
        } else if (currentState == SequenceState.IN_SEQUENCE) {
            val step = combo.pasos.getOrNull(currentStepIndex)
            feedbackText = if (step != null) {
                "${combo.name}: Esperando ${step.nombreGesto}"
            } else {
                "${combo.name}: Secuencia completada..."
            }
        } else if (currentState == SequenceState.WAITING_DEACTIVATOR) {
            feedbackText = "${combo.name}: Esperando Desactivador (${combo.deactivador?.nombreGesto})"
        } else if (currentState == SequenceState.COMPLETED) {
            feedbackText = "¡${combo.name.uppercase()} COMPLETADO!"
        }
    }

    private fun isPoseMatching(leftPose: String, rightPose: String, expected: PasoSecuencia): Boolean {
        val leftMatch = leftPose.contains(expected.nombreGesto, ignoreCase = true)
        val rightMatch = rightPose.contains(expected.nombreGesto, ignoreCase = true)

        return when (expected.manoObjetivo) {
            ManoObjetivo.LEFT -> leftMatch
            ManoObjetivo.RIGHT -> rightMatch
            ManoObjetivo.ANY -> leftMatch || rightMatch
        }
    }
}

class SecuenciaDetector(private val onComboCompleted: (Combo) -> Unit) {

    private val trackers = mutableListOf<RastreadorCombo>()

    fun updateCombos(newCombos: List<Combo>) {
        trackers.clear()
        for (combo in newCombos) {
            trackers.add(RastreadorCombo(combo) { completedCombo ->
                onComboCompleted(completedCombo)
            })
        }
    }

    fun resetAll() {
        trackers.forEach { it.reset() }
    }

    fun processPoses(leftPose: String, rightPose: String) {
        trackers.forEach { it.processPoses(leftPose, rightPose) }
    }

    val feedbackText: String
        get() {
            if (trackers.isEmpty()) return "No hay combos configurados."
            return trackers.joinToString("\n") { it.feedbackText }
        }
}
