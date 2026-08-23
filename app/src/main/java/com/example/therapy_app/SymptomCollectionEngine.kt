package com.example.therapy_app

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI


private const val MODEL_INPUT_DIM = 65


class SymptomCollectionEngine(
    private val featureIndexMap: Map<String, Int>,
    private val featureCols: List<String>,
    private val assessmentToFeatureMap: Map<Int, Int> = emptyMap()
) {

    // PHQ‑9 (9 items, 0..3)
    var phq9_1NUM = 0f
    var phq9_2NUM = 0f
    var phq9_3NUM = 0f
    var phq9_4NUM = 0f
    var phq9_5NUM = 0f
    var phq9_6NUM = 0f
    var phq9_7NUM = 0f
    var phq9_8NUM = 0f
    var phq9_9NUM = 0f

    // GAD‑7 (7 items, 0..3)
    var gad7_1NUM = 0f
    var gad7_2NUM = 0f
    var gad7_3NUM = 0f
    var gad7_4NUM = 0f
    var gad7_5NUM = 0f
    var gad7_6NUM = 0f
    var gad7_7NUM = 0f

    // ACHA core demographic
    var fulltime = 0f    // yes=1 / no=0
    var sex = 0f         // male=0 / female=1

    // Race flags (6)
    var race_white = 0f
    var race_black = 0f
    var race_hispanic = 0f
    var race_asian = 0f
    var race_native = 0f
    var race_other = 0f

    // Medical conditions (29 binary flags)
    var med_cond_1 = 0f
    var med_cond_2 = 0f
    var med_cond_3 = 0f
    var med_cond_4 = 0f
    var med_cond_5 = 0f
    var med_cond_6 = 0f
    var med_cond_7 = 0f
    var med_cond_8 = 0f
    var med_cond_9 = 0f
    var med_cond_10 = 0f
    var med_cond_11 = 0f
    var med_cond_12 = 0f
    var med_cond_13 = 0f
    var med_cond_14 = 0f
    var med_cond_15 = 0f
    var med_cond_16 = 0f
    var med_cond_17 = 0f
    var med_cond_18 = 0f
    var med_cond_19 = 0f
    var med_cond_20 = 0f
    var med_cond_21 = 0f
    var med_cond_22 = 0f
    var med_cond_23 = 0f
    var med_cond_24 = 0f
    var med_cond_25 = 0f
    var med_cond_26 = 0f
    var med_cond_27 = 0f
    var med_cond_28 = 0f
    var med_cond_29 = 0f

    // Emotional frequency (7 items, 0..6)
    var emo_1 = 0f
    var emo_2 = 0f
    var emo_3 = 0f
    var emo_4 = 0f
    var emo_5 = 0f
    var emo_6 = 0f
    var emo_7 = 0f

    // Depression history / services (4)
    var acha_depression = 0f
    var acha_services_1 = 0f
    var acha_services_2 = 0f
    var acha_services_3 = 0f


    fun buildModelInputVector(): FloatArray {
        val vector = floatArrayOf(
            // PHQ‑9 (9)
            phq9_1NUM,
            phq9_2NUM,
            phq9_3NUM,
            phq9_4NUM,
            phq9_5NUM,
            phq9_6NUM,
            phq9_7NUM,
            phq9_8NUM,
            phq9_9NUM,
            // GAD‑7 (7)
            gad7_1NUM,
            gad7_2NUM,
            gad7_3NUM,
            gad7_4NUM,
            gad7_5NUM,
            gad7_6NUM,
            gad7_7NUM,
            // ACHA core (3)
            fulltime,
            0f,    // international placeholder (kept for input shape)
            sex,
            // Race flags (6)
            race_white,
            race_black,
            race_hispanic,
            race_asian,
            race_native,
            race_other,
            // Medical conditions (29)
            med_cond_1,
            med_cond_2,
            med_cond_3,
            med_cond_4,
            med_cond_5,
            med_cond_6,
            med_cond_7,
            med_cond_8,
            med_cond_9,
            med_cond_10,
            med_cond_11,
            med_cond_12,
            med_cond_13,
            med_cond_14,
            med_cond_15,
            med_cond_16,
            med_cond_17,
            med_cond_18,
            med_cond_19,
            med_cond_20,
            med_cond_21,
            med_cond_22,
            med_cond_23,
            med_cond_24,
            med_cond_25,
            med_cond_26,
            med_cond_27,
            med_cond_28,
            med_cond_29,
            // Emotional frequency (7)
            emo_1,
            emo_2,
            emo_3,
            emo_4,
            emo_5,
            emo_6,
            emo_7,
            // Depression history / services (4)
            acha_depression,
            acha_services_1,
            acha_services_2,
            acha_services_3,
            // --- explicit placeholder to match model input length (66) ---
            0f
        )

        if (vector.size != MODEL_INPUT_DIM) {
            Log.e(
                "ModelInput",
                "buildModelInputVector: expected $MODEL_INPUT_DIM features but got ${vector.size}"
            )
        }

        return vector
    }


    fun sanityCheck() {
        val v = buildModelInputVector()
        Log.d("ModelInput", "size=${v.size} first10=${v.take(10)}")

        // PHQ indices 0..8
        for (i in 0..8) if (v[i] !in 0f..3f) Log.w(
            "ModelInput",
            "PHQ item $i out of range: ${v[i]}"
        )

        // GAD indices 9..15
        for (i in 9..15) if (v[i] !in 0f..3f) Log.w(
            "ModelInput",
            "GAD item ${i - 9} out of range: ${v[i]}"
        )

        // Emotional frequency indices calculation
        val emoStart = 9 + 7 + 3 + 6 + 29 // 54
        val emoEnd = emoStart + 7 - 1     // 60
        for (i in emoStart..emoEnd) if (v[i] !in 0f..6f) Log.w(
            "ModelInput",
            "EMO item ${i - emoStart + 1} out of range: ${v[i]}"
        )
    }


    // -----------------------------------------------------
    // STATE
    // -----------------------------------------------------
    private val symptoms = FloatArray(featureCols.size)
    private var symptomCount = 0

    var anxiety: Float = 0f
    var depression: Float = 0f
    var stress: Float = 0f
    var loneliness: Float = 0f

    var phq9Pred: Float = 0f
    var gad7Pred: Float = 0f
    var emotionLabel: String = "neutral"
    var emotionVector: FloatArray = floatArrayOf()


    // -----------------------------------------------------
    // RESET
    // -----------------------------------------------------
    fun reset() {
        for (i in symptoms.indices) symptoms[i] = 0f
        symptomCount = 0
    }

    fun getDiagnosisEvidence(): DiagnosisEvidence {
        val phqScore = computePhq9Score()
        val gadScore = computeGad7Score()

        phq9Pred = phqScore.toFloat()
        gad7Pred = gadScore.toFloat()

        return DiagnosisEvidence(
            phq9Score = phq9Pred,
            phq9Severity = phq9Severity(),
            gad7Score = gad7Pred,
            gad7Severity = gad7Severity(),

            acchaHopeless = emo_1,
            acchaOverwhelmed = emo_2,
            acchaExhausted = emo_3,
            acchaSad = emo_4,
            acchaFunctionalImpairment = emo_5,
            acchaSuicidalThoughts = emo_6,
            acchaSuicideAttempts = emo_7,

            acchaDiagnosed = acha_depression == 1f,
            acchaDiagnosed12Months = acha_services_1 == 1f,
            acchaTherapy = acha_services_2 == 1f,
            acchaMedication = acha_services_3 == 1f
        )
    }



    // -----------------------------------------------------
    // MAIN ENTRY (checkbox-based)
    // -----------------------------------------------------
    // Existing function left as-is for backward compatibility


    // New function: update the same symptoms vector from numeric assessment scores (questionIndex -> score 0..3)


    // -----------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------
    fun getSymptoms(): FloatArray = symptoms

    fun getSymptomCount(): Int = symptomCount

    fun getConfirmedSymptoms(): List<String> {
        return featureCols.filterIndexed { index, _ ->
            symptoms[index] >= 0.5f
        }
    }

    fun getConfirmedSymptomsText(): String {
        return getConfirmedSymptoms()
            .joinToString(", ")
            .ifEmpty { "None confirmed yet" }
    }

    // -----------------------------------------------------
// EMOTION DETECTION (OpenAI-powered, dynamic)
// -----------------------------------------------------
    suspend fun detectEmotion(text: String, client: OpenAI): String {
        val prompt = """
You are an emotion classifier.

Given the user's message, return ONE emotion label from this list:
- nervous
- anxious
- worried
- stressed
- sad
- low
- depressed
- lonely
- angry
- overwhelmed
- calm
- neutral
- not sure

Rules:
- Choose the closest emotional label.
- If multiple emotions appear, choose the strongest one.
- If unclear, return "not sure".
- Return ONLY the label. No explanation.

User message:
"$text"
""".trimIndent()

        Log.d("SymptomEngine", "detectEmotion called | text='${text.take(200)}' | promptLength=${prompt.length}")

        // Helper to normalize labels
        fun normalizeLabel(raw: String): String {
            return raw.lowercase().trim().removeSurrounding("\"").replace(Regex("[^a-z]"), "")
                .let {
                    when {
                        it.contains("anx") -> "anxious"
                        it.contains("stress") -> "stressed"
                        it.contains("depress") -> "depressed"
                        it.contains("sad") -> "sad"
                        it.contains("lonely") -> "lonely"
                        it.contains("angry") -> "angry"
                        it.contains("overwhelm") -> "overwhelmed"
                        it.contains("calm") -> "calm"
                        it.contains("neutral") -> "neutral"
                        it.contains("nerv") -> "nervous"
                        it.contains("worri") -> "worried"
                        it.contains("low") -> "low"
                        else -> raw.lowercase().trim()
                    }
                }
        }

        suspend fun callOnce(): String? {
            return try {
                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt
                            )
                        )
                    )
                )

                // Log top-level response metadata if available
                Log.d("SymptomEngine", "OpenAI response received | choices=${response.choices.size}")

                val choice = response.choices.firstOrNull()
                val raw = choice?.message?.content?.trim()
                Log.d("SymptomEngine", "Raw model output: ${raw ?: "null"}")

                if (raw.isNullOrBlank()) {
                    Log.w("SymptomEngine", "Model returned empty content")
                    return null
                }

                // Try to parse JSON-like output if model returned structured text
                try {
                    val json = org.json.JSONObject(raw)
                    val labelFromJson = when {
                        json.has("label") -> json.optString("label")
                        json.has("emotion") -> json.optString("emotion")
                        else -> null
                    }
                    val confidenceFromJson = if (json.has("confidence")) json.optDouble("confidence", Double.NaN) else Double.NaN
                    if (labelFromJson != null && labelFromJson.isNotBlank()) {
                        Log.d("SymptomEngine", "Parsed JSON label='$labelFromJson' confidence=${if (!confidenceFromJson.isNaN()) confidenceFromJson else "n/a"}")
                        return normalizeLabel(labelFromJson)
                    }
                } catch (je: Exception) {
                    Log.d("SymptomEngine", "Model output not JSON or JSON parse failed: ${je.message}")
                }

                // If not JSON, take the first token/line as label
                val firstLine = raw.lineSequence().firstOrNull()?.trim() ?: raw.trim()
                val token = firstLine.split(Regex("\\s+|,|:")).firstOrNull() ?: firstLine
                val normalized = normalizeLabel(token)
                Log.d("SymptomEngine", "Using token='$token' -> normalized='$normalized'")
                normalized

            } catch (e: Exception) {
                Log.e("SymptomEngine", "detectEmotion OpenAI call failed", e)
                null
            }
        }

        // Try once, then one retry on failure
        val attempt1 = callOnce()
        if (attempt1 != null) return attempt1

        Log.w("SymptomEngine", "First detectEmotion attempt returned null; retrying once")
        val attempt2 = callOnce()
        if (attempt2 != null) return attempt2

        Log.w("SymptomEngine", "Both attempts failed or returned null; returning 'not sure'")
        return "not sure"
    }


    // Expected input dimension from your training pipeline


    fun updateGad7Prediction(score: Float) {
        gad7Pred = score
    }

    fun updatePhq9Prediction(score: Float) {
        phq9Pred = score
    }

    fun updateEmotion(logits: FloatArray) {
        emotionVector = logits

        // Softmax
        val exp = logits.map { kotlin.math.exp(it) }
        val sumExp = exp.sum()
        val probs = exp.map { it / sumExp }

        // Emotion labels (must match your training order)
        val labels = listOf(
            "neutral",
            "sad",
            "anxious",
            "stressed",
            "angry",
            "lonely",
            "overwhelmed",
            "low"
        )

        val maxIndex = probs.indices.maxByOrNull { probs[it] } ?: 0
        emotionLabel = labels[maxIndex]
    }

    fun phq9Severity(): String {
        val score = phq9Pred
        return when {
            score <= 4 -> "Minimal depression"
            score <= 9 -> "Mild depression"
            score <= 14 -> "Moderate depression"
            score <= 19 -> "Moderately severe depression"
            else -> "Severe depression"
        }
    }

    fun gad7Severity(): String {
        val score = gad7Pred
        return when {
            score <= 4 -> "Minimal anxiety"
            score <= 9 -> "Mild anxiety"
            score <= 14 -> "Moderate anxiety"
            else -> "Severe anxiety"
        }
    }

    fun updateFromAssessmentScores(
        assessmentScores: Map<Int, Int>,
        questionnaire: QuestionnaireType
    ): FloatArray {

        for ((questionIndex, score) in assessmentScores) {

            val newValue = score.toFloat()

            // Update symptoms[] if you use assessmentToFeatureMap
            val featureIndex = assessmentToFeatureMap[questionIndex]
            if (featureIndex != null) {
                val prev = symptoms[featureIndex]
                if (prev == 0f && newValue > 0f) {
                    symptomCount++
                } else if (prev > 0f && newValue == 0f) {
                    symptomCount = (symptomCount - 1).coerceAtLeast(0)
                }
                symptoms[featureIndex] = newValue
            }

            // Update PHQ‑9 or GAD‑7 NUM fields
            when (questionnaire) {

                QuestionnaireType.PHQ9 -> {
                    when (questionIndex) {
                        0 -> phq9_1NUM = newValue
                        1 -> phq9_2NUM = newValue
                        2 -> phq9_3NUM = newValue
                        3 -> phq9_4NUM = newValue
                        4 -> phq9_5NUM = newValue
                        5 -> phq9_6NUM = newValue
                        6 -> phq9_7NUM = newValue
                        7 -> phq9_8NUM = newValue
                        8 -> phq9_9NUM = newValue
                    }
                }

                QuestionnaireType.GAD7 -> {
                    when (questionIndex) {
                        0 -> gad7_1NUM = newValue
                        1 -> gad7_2NUM = newValue
                        2 -> gad7_3NUM = newValue
                        3 -> gad7_4NUM = newValue
                        4 -> gad7_5NUM = newValue
                        5 -> gad7_6NUM = newValue
                        6 -> gad7_7NUM = newValue
                    }
                }
            }
        }

        return symptoms
    }


    fun computePhq9Score(): Int {
        return (phq9_1NUM + phq9_2NUM + phq9_3NUM + phq9_4NUM +
                phq9_5NUM + phq9_6NUM + phq9_7NUM + phq9_8NUM + phq9_9NUM).toInt()
    }

    fun computeGad7Score(): Int {
        return (gad7_1NUM + gad7_2NUM + gad7_3NUM + gad7_4NUM +
                gad7_5NUM + gad7_6NUM + gad7_7NUM).toInt()
    }


    // -----------------------------------------------------
    // DIAGNOSIS READINESS
    // -----------------------------------------------------
    fun isReadyForDiagnosis(): Boolean {
        return symptomCount >= 3
    }
}
