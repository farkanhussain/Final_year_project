package com.example.therapy_app

enum class DeepPhase {
    ASSESSMENT,
    DIAGNOSIS,
    CBT
}

enum class AssessmentQuestionType {
    CHECKBOX,
    NONE
}

class DeepSessionManager {

    var phase: DeepPhase = DeepPhase.ASSESSMENT
        private set

    var lastQuestionType: AssessmentQuestionType = AssessmentQuestionType.NONE
        private set

    private var questionnaireCompleted = false
    private var diagnosisDelivered = false
    private var cbtDelivered = false

    // ---------------------------------------------------------
    // RESET
    // ---------------------------------------------------------
    fun reset() {
        phase = DeepPhase.ASSESSMENT
        lastQuestionType = AssessmentQuestionType.NONE
        questionnaireCompleted = false
        diagnosisDelivered = false
        cbtDelivered = false
    }

    // ---------------------------------------------------------
    // STATE SETTERS
    // ---------------------------------------------------------
    fun setQuestionnaireCompleted() {
        questionnaireCompleted = true
    }

    fun setDiagnosisDelivered() {
        diagnosisDelivered = true
    }

    fun setCbtDelivered() {
        cbtDelivered = true
    }

    // ---------------------------------------------------------
    // PHASE ADVANCEMENT
    // ---------------------------------------------------------
    fun advancePhaseIfNeeded() {
        when (phase) {

            DeepPhase.ASSESSMENT -> {
                if (questionnaireCompleted) {
                    phase = DeepPhase.DIAGNOSIS
                }
            }

            DeepPhase.DIAGNOSIS -> {
                if (diagnosisDelivered) {
                    phase = DeepPhase.CBT
                }
            }

            DeepPhase.CBT -> {
                // End of simplified deep session
                if (cbtDelivered) {
                    // No next steps for now
                }
            }
        }
    }

    // ---------------------------------------------------------
    // QUESTION TYPE TRACKING
    // ---------------------------------------------------------
    fun updateLastQuestionType(type: AssessmentQuestionType) {
        lastQuestionType = type
    }

    fun canAsk(type: AssessmentQuestionType): Boolean {
        return lastQuestionType != type
    }
}
