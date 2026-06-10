package com.example.therapy_app

class CBTExerciseGenerator {

    // Public entry point
    suspend fun generateHybridExercises(
        disorder: String,
        symptoms: FloatArray,
        userMessage: String,
        dynamicGenerator: suspend (String) -> String
    ): List<String> {

        // 1) Try dynamic (API-based) exercises
        val dynamic = try {
            val prompt = buildDynamicPrompt(disorder, symptoms, userMessage)
            val raw = dynamicGenerator(prompt)
            parseDynamicResponse(raw)
        } catch (e: Exception) {
            emptyList()
        }

        if (dynamic.isNotEmpty()) {
            return dynamic.take(3)
        }

        // 2) Fallback to static rule-based exercises
        return generateStaticExercises(disorder, symptoms).take(3)
    }

    // -----------------------------
    // Dynamic prompt (API side)
    // -----------------------------
    private fun buildDynamicPrompt(
        disorder: String,
        symptoms: FloatArray,
        userMessage: String
    ): String {
        return """
            You are a warm, evidence-based CBT therapist.

            Based on the following information:
            - Predicted difficulty / disorder: $disorder
            - Symptom vector (0/1 flags): ${symptoms.joinToString()}
            - User's recent message: "$userMessage"

            Generate 3 short CBT exercises.

            Each exercise MUST:
            - be 1–2 sentences
            - be specific and actionable
            - use simple everyday language
            - focus on thoughts, emotions, or behaviours

            Return them as a numbered list:
            1) ...
            2) ...
            3) ...
        """.trimIndent()
    }

    private fun parseDynamicResponse(raw: String): List<String> {
        return raw
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                line.replace(Regex("""^\d+[\).\-\s]+"""), "")
            }
            .filter { it.isNotBlank() }
    }

    // -----------------------------
    // Static fallback CBT rules
    // -----------------------------
    private fun generateStaticExercises(
        disorder: String,
        symptoms: FloatArray
    ): List<String> {

        val list = mutableListOf<String>()

        // Thought-focused
        if (disorder.contains("anxiety", ignoreCase = true)) {
            list += "Try a grounding exercise: name 5 things you see, 4 you touch, 3 you hear, 2 you smell, 1 you taste."
        }

        if (disorder.contains("depression", ignoreCase = true)) {
            list += "Break one small task into tiny steps and complete just the first step today."
        }

        // Behaviour-focused
        if (symptoms.any { it == 1f }) {
            list += "Reflect on one unhelpful behaviour today and what triggered it."
        }

        // General fallback
        if (list.isEmpty()) {
            list += "Write a CBT thought record: situation → thoughts → emotions → behaviour."
        }

        return list
    }
}