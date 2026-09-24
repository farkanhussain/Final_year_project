package com.example.therapy_app

enum class MoodResult {
    NEUTRAL,
    SLIGHTLY_WORSE,
    MUCH_WORSE,
    MISMATCH,
    FATIGUE
}

object MoodEvaluator {

    fun evaluate(text: String): MoodResult {
        val t = text.lowercase()

        // ---------------------------------------------------------
        // MISMATCH (exact matches)
        // ---------------------------------------------------------
        if (listOf(
                "not relevant",
                "irrelevant",
                "doesn't fit",
                "does not fit",
                "not for me",
                "wrong exercise",
                "this isn't helping",
                "this doesn't help",
                "this isn't right",
                "not useful"
            ).any { t.contains(it) }
        ) {
            return MoodResult.MISMATCH
        }

        // ---------------------------------------------------------
        // MISMATCH (fuzzy match)
        // ---------------------------------------------------------
        if (t.contains("not") && t.contains("relevant")) {
            return MoodResult.MISMATCH
        }

        // ---------------------------------------------------------
        // OTHER MOOD STATES (inside when)
        // ---------------------------------------------------------
        return when {

            // Slightly worse
            listOf("not really", "didn't help", "no change", "same", "still the same")
                .any { t.contains(it) } -> MoodResult.SLIGHTLY_WORSE

            // Much worse
            listOf("worse", "more anxious", "more stressed", "this made me feel bad")
                .any { t.contains(it) } -> MoodResult.MUCH_WORSE

            // Fatigue
            listOf("tired", "don't want", "stop", "no more", "done")
                .any { t.contains(it) } -> MoodResult.FATIGUE

            else -> MoodResult.NEUTRAL
        }
    }
}
