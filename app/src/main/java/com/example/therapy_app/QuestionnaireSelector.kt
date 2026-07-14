package com.example.therapy_app

enum class QuestionnaireType {
    PHQ9,
    GAD7
}

object QuestionnaireSelector {

    /**
     * Selects PHQ‑9 or GAD‑7 based on detected emotion + cause text.
     * This is intentionally simple and reliable.
     */
    fun select(emotion: String?, cause: String?): QuestionnaireType {

        val e = emotion?.lowercase() ?: ""
        val c = cause?.lowercase() ?: ""

        // Anxiety indicators
        val anxietySignals = listOf(
            "nervous", "anxious", "worried", "panic", "fear",
            "overthinking", "tense", "on edge", "scared"
        )

        // Depression indicators
        val depressionSignals = listOf(
            "sad", "down", "hopeless", "empty", "tired",
            "exhausted", "low", "depressed", "worthless"
        )

        // Stress indicators (lean toward GAD‑7)
        val stressSignals = listOf(
            "overwhelmed", "pressure", "burnt out", "burnout",
            "too much", "can't cope", "stressed"
        )

        // Loneliness indicators (lean toward PHQ‑9)
        val lonelinessSignals = listOf(
            "lonely", "alone", "isolated", "ignored"
        )

        // --- Matching logic ---
        if (anxietySignals.any { e.contains(it) || c.contains(it) }) {
            return QuestionnaireType.GAD7
        }

        if (stressSignals.any { e.contains(it) || c.contains(it) }) {
            return QuestionnaireType.GAD7
        }

        if (depressionSignals.any { e.contains(it) || c.contains(it) }) {
            return QuestionnaireType.PHQ9
        }

        if (lonelinessSignals.any { e.contains(it) || c.contains(it) }) {
            return QuestionnaireType.PHQ9
        }

        // Default fallback
        return QuestionnaireType.PHQ9
    }
}


