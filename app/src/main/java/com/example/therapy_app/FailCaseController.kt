package com.example.therapy_app

object FailCaseController {

    fun handleFailCase(
        mood: MoodResult,
        addMessage: (String) -> Unit,
        deepManager: DeepSessionManager
    ): Boolean {

        when (mood) {

            MoodResult.NEUTRAL -> return false

            // ⭐ DO NOT end exercise for SLIGHTLY_WORSE
            MoodResult.SLIGHTLY_WORSE -> {
                addMessage(FailCasePrompts.offerPause())
                return true   // pause, but do NOT end exercise
            }

            MoodResult.MUCH_WORSE -> {
                addMessage(FailCasePrompts.grounding())
                deepManager.endExercise()
                deepManager.phase = DeepPhase.THERAPY_EXERCISES
                return true
            }

            MoodResult.MISMATCH -> {
                addMessage(FailCasePrompts.mismatch())
                deepManager.endExercise()
                deepManager.phase = DeepPhase.THERAPY_EXERCISES
                return true
            }

            MoodResult.FATIGUE -> {
                addMessage(FailCasePrompts.fatigue())
                deepManager.endExercise()
                deepManager.phase = DeepPhase.THERAPY_EXERCISES
                return true
            }
        }
    }
}
