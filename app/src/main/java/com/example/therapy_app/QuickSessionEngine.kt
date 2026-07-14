package com.example.therapy_app

import android.content.Context
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------
data class QuickSessionResponse(
    val emotion: String,
    val source: String,
    val condition: String,

    val exercises: List<String>
)

// ---------------------------------------------------------
// QUICK SESSION ENGINE (UPDATED FOR NEW ARCHITECTURE)
// ----

class QuickSessionEngine(
    private val onnxRunner: OnnxModelRunner,
    private val client: OpenAI,
    private val dynamicGenerator: suspend (String) -> String,
    private val deepManager: DeepSessionManager,
    private val context: Context            // ⭐ NEW
) {

    // -----------------------------------------------------
    // MAIN QUICK SESSION LOGIC (LEVEL 1 HYBRID)
    // -----------------------------------------------------
    suspend fun processQuickSessionMessage(text: String): QuickSessionResponse {

        // 1. Run Model A
        val result = onnxRunner.runModelA(text)

        // 2. Determine emotion + condition
        val (emotion, source) = if (result.confidence >= 0.40f) {
            result.label to "ModelA"
        } else {
            semanticEmotionDetector(text) to "Fallback"
        }

        val condition = mapEmotionToCondition(emotion)

        // ⭐ NEW: Use a generic multi‑therapy generator
        val generator = MultiTherapyExerciseGenerator()

        // ⭐ NEW: Default treatment pattern for Quick Mode
        val defaultPattern = TreatmentPattern(
            cluster = "general_support",
            focus = "general_emotional_regulation",
            methods = listOf("breathing", "reflection", "grounding")
        )
        val exercises = generator.generateExercises(
            therapyType = "general_support",
            disorder = condition,
            symptoms = FloatArray(0),
            userMessage = text,
            treatmentPattern = defaultPattern,
            client = client,
            dynamicGenerator = dynamicGenerator,
            deepManager = deepManager,
            context = context                 // ⭐ NEW
        )




        // 4. Return structured quick-session response
        return QuickSessionResponse(
            emotion = emotion,
            source = source,
            condition = condition,
            exercises = exercises
        )
    }

    // -----------------------------------------------------
    // FALLBACK EMOTION DETECTOR (STUB)
    // -----------------------------------------------------
    private fun semanticEmotionDetector(text: String): String {
        val lower = text.lowercase()

        return when {
            "happy" in lower || "excited" in lower -> "positive"
            "angry" in lower || "mad" in lower -> "anger"
            "confused" in lower -> "confusion"
            "bored" in lower -> "boredom"
            else -> "unknown"
        }
    }

    // -----------------------------------------------------
    // EMOTION → CONDITION MAPPING
    // -----------------------------------------------------
    private fun mapEmotionToCondition(emotion: String): String {
        return when (emotion.lowercase()) {
            "sadness" -> "Depression"
            "anxiety" -> "Anxiety Disorder"
            "hopelessness" -> "Depression"
            "guilt" -> "Depression"
            "anger" -> "Stress Response"
            "loneliness" -> "Social Withdrawal"
            "positive" -> "Healthy Mood"
            "confusion" -> "Cognitive Overload"
            "boredom" -> "Low Engagement"
            else -> "General Emotional Distress"
        }
    }
}
