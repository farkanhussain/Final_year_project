package com.example.therapy_app

object FusionLogic {

    fun fuse(emotion: String, disorder: String): String {

        val normalizedEmotion = emotion.lowercase()
        val normalizedDisorder = disorder.lowercase()

        return when (normalizedDisorder) {

            "depression" -> handleDepression(normalizedEmotion)

            "anxiety" -> handleAnxiety(normalizedEmotion)

            "stress" -> handleStress(normalizedEmotion)

            else -> defaultFusion(normalizedEmotion, normalizedDisorder)
        }
    }

    // ---------------------------------------------------------
    // Depression Fusion
    // ---------------------------------------------------------
    private fun handleDepression(emotion: String): String {
        val depressiveEmotions = listOf(
            "sadness", "emptiness", "hopelessness", "worthlessness", "loneliness"
        )

        return if (emotion in depressiveEmotions) {
            "Your emotional language reflects deep sadness and hopelessness, and your symptoms align with depression. " +
                    "This combination suggests you may be experiencing depressive feelings."
        } else {
            "Your symptoms match depression patterns, while your emotional tone reflects $emotion. " +
                    "This may indicate mixed emotional states that are still important to pay attention to."
        }
    }

    // ---------------------------------------------------------
    // Anxiety Fusion
    // ---------------------------------------------------------
    private fun handleAnxiety(emotion: String): String {
        val anxietyEmotions = listOf(
            "anxiety", "fear", "nervousness", "panic", "worry"
        )

        return if (emotion in anxietyEmotions) {
            "Your emotional language shows signs of anxiety, and your symptoms also match anxiety patterns. " +
                    "This suggests heightened anxious feelings."
        } else {
            "Your symptoms indicate anxiety, while your emotional tone reflects $emotion. " +
                    "Anxiety can sometimes appear in different emotional forms."
        }
    }

    // ---------------------------------------------------------
    // Stress Fusion
    // ---------------------------------------------------------
    private fun handleStress(emotion: String): String {
        val stressEmotions = listOf(
            "overwhelm", "irritability", "tension", "frustration"
        )

        return if (emotion in stressEmotions) {
            "Your emotional language and symptoms both point toward stress. " +
                    "You may be feeling overwhelmed or under pressure."
        } else {
            "Your symptoms suggest stress, although your emotional tone reflects $emotion. " +
                    "Stress can show up in many emotional ways."
        }
    }

    // ---------------------------------------------------------
    // Default Fusion (fallback)
    // ---------------------------------------------------------
    private fun defaultFusion(emotion: String, disorder: String): String {
        return "Your emotional tone reflects $emotion and your symptoms suggest $disorder. " +
                "These patterns may be related and worth exploring further."
    }
}
