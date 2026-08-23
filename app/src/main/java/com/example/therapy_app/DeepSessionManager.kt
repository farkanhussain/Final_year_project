package com.example.therapy_app

import android.util.Log
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

// ---------------------------------------------------------
// NEW PHASES
// ---------------------------------------------------------

enum class DeepPhase {
    CONTEXT_INTAKE,
    ASSESSMENT,            // User selects symptoms
    DIAGNOSIS,             // LLM gives diagnosis summary
    TREATMENT_PATTERN,     // Model C returns cluster/focus/methods
    THERAPY_TYPE,          // LLM chooses therapy type (CBT, DBT, IPT, MBT, Psychodynamic, Humanistic)
    THERAPY_EXERCISES, // LLM generates exercises for chosen therapy type

    EXERCISE_GUIDANCE,

    SESSION_COMPLETE
}

enum class AssessmentQuestionType {
    CHECKBOX,
    NONE
}

enum class ConversationState {
    WAITING_FOR_USER_SYMPTOMS,
    READY_FOR_QUESTIONNAIRE,
    QUESTIONNAIRE_ACTIVE
}

enum class AssessmentMode {
    STANDARD,
    EXTENDED_PHQ9
}

data class ExtendedAssessmentResponse(
    var hopeless: Int? = null,
    var overwhelmed: Int? = null,
    var exhausted: Int? = null,
    var sad: Int? = null,
    var functionalImpairment: Int? = null,
    var suicidalThoughts: Int? = null,
    var suicideAttempts: Int? = null,

    var diagnosedDepression: Boolean? = null,
    var diagnosedLast12Months: Boolean? = null,
    var currentTherapy: Boolean? = null,
    var currentMedication: Boolean? = null
)



class DeepSessionManager {

    var medicalConditions: List<String> = emptyList()
    var fullTimeStatus: String = ""
    var internationalStatus: String = ""
    var ethnicityList: List<String> = emptyList()

    var assessmentMode = AssessmentMode.STANDARD
    var extendedQuestionIndex = 0
    val extendedResponses = ExtendedAssessmentResponse()


    // Crisis state tracking
    var crisisDetected: Boolean = false
    var lastCrisisTimestamp: Long = 0L

    private val assessmentScores = IntArray(9) { 0 }   // PHQ‑9 default
    private var questionnaireType: QuestionnaireType? = null

    var contextEmotion: String? = null
    var contextCause: String? = null

    var contextCauseCategory: String? = null

    var contextSummary: String? = null
    var selectedQuestionnaire: QuestionnaireType? = null


    var generatedDbtExercises: List<DbtExercise>? = null


    // ⭐ NEW: full step list for the chosen exercise
    var currentExerciseSteps: List<String> = emptyList()

    // ⭐ NEW: index of the current step
    var currentExerciseStepIndex: Int = 0

    // ⭐ NEW: name of the chosen exercise
    var currentExerciseName: String = ""


    var currentExerciseStep: Int = 0
    val maxExerciseSteps: Int = 3


    // ⭐ Stores the currently selected exercise
    var currentExercise: String? = null



    private var generatedExercises: List<String> = emptyList()

    var awaitingExerciseConsent = false

    var therapyType: String? = null


    private var sessionCompleted = false



    var lastTreatmentPattern: TreatmentPattern? = null
        private set

    fun setLastTreatmentPattern(pattern: TreatmentPattern) {
        lastTreatmentPattern = pattern
    }


    private val httpClient = HttpClient {
        install(ContentNegotiation) { json() }
    }

    private val modelC = ModelCClient(httpClient)

    // ---------------------------------------------------------
    // Conversation State Layer
    // ---------------------------------------------------------
    var conversationState: ConversationState = ConversationState.WAITING_FOR_USER_SYMPTOMS
        private set

    fun setUserSymptomsProvided() {
        conversationState = ConversationState.READY_FOR_QUESTIONNAIRE
    }

    fun activateQuestionnaire() {
        conversationState = ConversationState.QUESTIONNAIRE_ACTIVE
    }

    fun resetConversation() {
        conversationState = ConversationState.WAITING_FOR_USER_SYMPTOMS
    }

    fun userWantsQuestionnaire(message: String): Boolean {
        val m = message.lowercase()
        return m.contains("start questionnaire") ||
                m.contains("do the questionnaire") ||
                m.contains("questionnaire") ||
                m.contains("yes")
    }

    // ---------------------------------------------------------
    // Deep Session Phase Logic
    // ---------------------------------------------------------
    var phase: DeepPhase = DeepPhase.ASSESSMENT

    var lastQuestionType: AssessmentQuestionType = AssessmentQuestionType.NONE
        private set

    private var questionnaireCompleted = false
    private var diagnosisDelivered = false
    private var patternDelivered = false
    var therapyTypeDelivered = false

    private var exercisesDeliveredFlag: Boolean = false

    private val selectedSymptoms = mutableSetOf<String>()

    private val criticalSymptoms = setOf(
        "panic_attacks",
        "intrusive_thoughts",
        "hallucinations",
        "self_harm",
        "mania"
    )

    fun reset() {
        // Start deep session in CONTEXT_INTAKE (new first phase)
        phase = DeepPhase.CONTEXT_INTAKE

        // Reset context‑intake fields
        contextEmotion = null
        contextCause = null
        contextSummary = null
        selectedQuestionnaire = null

        // Reset questionnaire state
        lastQuestionType = AssessmentQuestionType.NONE
        questionnaireCompleted = false

        // Reset phase delivery flags
        diagnosisDelivered = false
        patternDelivered = false
        therapyTypeDelivered = false

        // Reset exercise delivery flag (new)
        exercisesDeliveredFlag = false

        // Reset selected symptoms
        selectedSymptoms.clear()



        // Reset session completion flag
        sessionCompleted = false

        // Reset generated exercises
        generatedExercises = emptyList()

        // Reset conversation state machine
        resetConversation()
    }


    fun updateAssessment(symptoms: List<String>) {
        selectedSymptoms.clear()
        selectedSymptoms.addAll(symptoms)
    }

    fun isDiagnosisReady(): Boolean {
        val total = selectedSymptoms.size
        val criticalCount = selectedSymptoms.count { it in criticalSymptoms }

        if (criticalCount >= 1) return true
        if (total >= 6) return true

        return false
    }


    fun setQuestionnaireCompleted() {
        questionnaireCompleted = true
    }

    fun setDiagnosisDelivered() {
        diagnosisDelivered = true
    }

    fun setPatternDelivered() {
        patternDelivered = true
    }

    fun setTherapyTypeDelivered() {
        therapyTypeDelivered = true
    }

    fun setExercisesDelivered() {
        exercisesDeliveredFlag = true
    }

    fun setSessionCompleted() {
        sessionCompleted = true
    }


    fun advancePhaseIfNeeded() {
        when (phase) {

            DeepPhase.CONTEXT_INTAKE -> {
                // Auto‑advance ONLY when emotion, cause, AND questionnaire are present
                val hasEmotion = contextEmotion != null
                val hasCause = contextCause != null
                val hasQuestionnaire = selectedQuestionnaire != null

                Log.d("DeepSession", "advancePhaseIfNeeded: CONTEXT_INTAKE check | emotion=$hasEmotion cause=$hasCause questionnaire=$hasQuestionnaire")

                if (hasEmotion && hasCause && hasQuestionnaire) {
                    phase = DeepPhase.ASSESSMENT
                    Log.i("DeepSession", "Phase advanced to ASSESSMENT")
                } else {
                    Log.d("DeepSession", "Not advancing from CONTEXT_INTAKE; missing: " +
                            "${if (!hasEmotion) "emotion " else ""}" +
                            "${if (!hasCause) "cause " else ""}" +
                            "${if (!hasQuestionnaire) "questionnaire" else ""}")
                }
            }



            DeepPhase.ASSESSMENT -> {
                if (questionnaireCompleted) {
                    phase = DeepPhase.DIAGNOSIS
                }
            }

            DeepPhase.DIAGNOSIS -> {
                if (diagnosisDelivered) {
                    phase = DeepPhase.TREATMENT_PATTERN
                }
            }

            DeepPhase.TREATMENT_PATTERN -> {
                if (patternDelivered) {
                    phase = DeepPhase.THERAPY_TYPE
                }
            }

            DeepPhase.THERAPY_TYPE -> {
                if (therapyTypeDelivered) {
                    phase = DeepPhase.THERAPY_EXERCISES
                }
            }

            DeepPhase.THERAPY_EXERCISES -> {
                // Do NOT auto‑advance.
                // User must choose an exercise.
                // Intent handler will set:
                // phase = DeepPhase.EXERCISE_GUIDANCE
            }

            DeepPhase.EXERCISE_GUIDANCE -> {
                // Do NOT auto‑advance.
                // When exercise is complete:
                // phase = DeepPhase.SESSION_COMPLETE
            }

            DeepPhase.SESSION_COMPLETE -> {
                // End of deep session
            }
        }
    }




    // ---------------------------------------------------------
    // Model C Integration (unchanged except for new output)
    // ---------------------------------------------------------
    suspend fun getTreatmentPattern(
        disorder: String,
        symptoms: FloatArray,
        mood: Float,
        sleep: Float,
        activity: Float,
        stress: Float,
        progress: Float,
        adherence: Float,
        emotion: String
    ): TreatmentPattern {
        return modelC.predictTreatmentPattern(
            disorder,
            symptoms,
            mood,
            sleep,
            activity,
            stress,
            progress,
            adherence,
            emotion
        )
    }



    fun exercisesDelivered(): Boolean = exercisesDeliveredFlag

    // User explicitly wants to start exercises
    fun startExercises() {
        phase = DeepPhase.THERAPY_EXERCISES

    }


    fun skipExercises() {
        phase = DeepPhase.SESSION_COMPLETE
    }


    fun setSessionComplete() {
        phase = DeepPhase.SESSION_COMPLETE
    }

    fun setGeneratedExercises(exercises: List<String>) {
        generatedExercises = exercises
    }

    fun getGeneratedExercises(): List<String> {
        return generatedExercises
    }




    fun moodIndexToEmoji(index: Int): String {
        return when (index) {
            0 -> "😢"
            1 -> "😕"
            2 -> "😐"
            3 -> "🙂"
            4 -> "😄"
            else -> "🙂"
        }
    }



    fun beginExercise(exercise: DbtExercise) {
        currentExerciseName = exercise.name
        currentExerciseSteps = exercise.steps
        currentExerciseStepIndex = 0
        phase = DeepPhase.EXERCISE_GUIDANCE
    }


    fun advanceExerciseStep() {

        // ⭐ LOG: Before increment
        Log.d(
            "EXERCISE_FLOW_STEPADV",
            "advanceExerciseStep(): BEFORE increment -> currentStepIndex=$currentExerciseStep"
        )
        currentExerciseStep++
        // ⭐ LOG: After increment
        Log.d(
            "EXERCISE_FLOW_STEPADV",
            "advanceExerciseStep(): AFTER increment -> currentStepIndex=$currentExerciseStep"
        )
    }

    fun isExerciseComplete(): Boolean {

        val complete = currentExerciseStep >= currentExerciseSteps.size

        // ⭐ LOG: Completion evaluation
        Log.d(
            "EXERCISE_FLOW_COMPLETE",
            "isExerciseComplete(): stepIndex=$currentExerciseStep, " +
                    "totalSteps=${currentExerciseSteps.size}, " +
                    "complete=$complete"
        )

        return complete
    }



    fun endExercise() {
        currentExerciseName = ""
        currentExerciseSteps = emptyList()
        currentExerciseStep = 0
        phase = DeepPhase.THERAPY_EXERCISES
    }



    suspend fun generateExerciseStep(
        exerciseName: String,
        userInput: String,
        disorder: String,
        symptoms: FloatArray,
        steps: List<String>,          // ⭐ NEW: full step list
        stepIndex: Int,               // ⭐ NEW: current step index
        client: OpenAI
    ): String {

        // ⭐ LOG: Incoming parameters
        Log.d(
            "EXERCISE_FLOW_STEPGEN",
            "generateExerciseStep() called with: " +
                    "exerciseName=$exerciseName, " +
                    "stepIndex=$stepIndex, " +
                    "stepsSize=${steps.size}, " +
                    "userInput=\"$userInput\""
        )

        val isFinalStep = stepIndex >= steps.size - 1
        val currentStep = steps.getOrNull(stepIndex) ?: "Unknown Step"
        val remainingSteps = if (!isFinalStep) steps.drop(stepIndex + 1) else emptyList()

        // ⭐ LOG: Step resolution
        Log.d(
            "EXERCISE_FLOW_STEPGEN",
            "Resolved step: currentStep=\"$currentStep\", " +
                    "isFinalStep=$isFinalStep, " +
                    "remainingSteps=${remainingSteps.joinToString()}"
        )

        val prompt = """
You are a warm, evidence‑based mental health assistant guiding the user through a therapeutic exercise.

EXERCISE: $exerciseName
CURRENT STEP: $currentStep
REMAINING STEPS: ${remainingSteps.joinToString()}
USER INPUT: "$userInput"

USER CONTEXT:
- Disorder: $disorder
- Symptoms: ${symptoms.joinToString()}

STRICT RULES:
- Follow the exact step sequence shown above.
- Never repeat a previous step.
- Never skip steps.
- Never invent new steps.
- Never switch exercises.
- Keep the step short (1–2 sentences).
- Personalise the guidance based on the user's message.
- Ask for only ONE small reflection.
- If the user shows grounding, insight, emotional regulation, or fatigue, begin closing the exercise early.
- If this is the FINAL STEP, provide a gentle closing message with NO further instructions.

STRUCTURE:
1. If continuing:
   - Provide one short step based on CURRENT STEP.
   - End with one simple instruction (e.g., “Write one sentence about…”).

2. If closing:
   - Provide a short, supportive closing.
   - Do NOT ask for more input.

Return ONLY the step or closure.
""".trimIndent()

        // ⭐ LOG: Prompt preview
        Log.d(
            "EXERCISE_FLOW_STEPGEN",
            "Prompt built (first 200 chars): ${prompt.take(200)}"
        )

        val response = client.chatCompletion(
            ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(ChatMessage(ChatRole.User, prompt))
            )
        )

        // ⭐ LOG: Raw OpenAI response
        Log.d(
            "EXERCISE_FLOW_STEPGEN",
            "Raw OpenAI response: ${response.choices.firstOrNull()?.message?.content}"
        )

        val output = response.choices.first().message?.content?.trim()
            ?: "Let's continue — tell me one more thing you're noticing."

        // ⭐ LOG: Final output
        Log.d(
            "EXERCISE_FLOW_STEPGEN",
            "Final step output (trimmed): ${output.take(120)}"
        )

        return output
    }









    fun detectChosenExercise(userMessage: String): String? {
        // Prefer canonical full-object store if available, otherwise fall back to names list
        val exercises = generatedDbtExercises?.map { it.name } ?: generatedExercises ?: return null

        val normalizedMessage = normalizeName(userMessage).lowercase()

        Log.d("DetectExercise", "msg=\"$normalizedMessage\"")
        Log.d("DetectExercise", "exercises=${exercises.joinToString()}")

        // 1️⃣ Full-name match (hyphen-insensitive, punctuation-insensitive)
        for (exercise in exercises) {
            val normalizedExercise = normalizeName(exercise).lowercase()
            if (normalizedMessage.contains(normalizedExercise)) {
                Log.d("DetectExercise", "MATCH full=\"$exercise\"")
                return exercise
            }
        }

        // 2️⃣ Keyword match (first word)
        for (exercise in exercises) {
            val keyword = normalizeName(exercise).lowercase().split(" ").firstOrNull() ?: continue
            if (normalizedMessage.contains(keyword)) {
                Log.d("DetectExercise", "MATCH keyword=\"$exercise\"")
                return exercise
            }
        }

        // 3️⃣ Fallback ONLY if message is extremely generic
        val weakTriggers = listOf("yes", "ok")
        val weakMatch = weakTriggers.any { normalizedMessage.contains(it) }

        if (weakMatch) {
            val fallback = exercises.firstOrNull()
            Log.d("DetectExercise", "Fallback → $fallback")
            return fallback
        }

        Log.d("DetectExercise", "No match")
        return null
    }


    suspend fun generateExerciseClosure(
        exerciseName: String,
        disorder: String,
        symptoms: FloatArray,
        client: OpenAI
    ): String {

        val prompt = """
You are a warm, evidence‑based mental health assistant.

The user has completed the exercise: $exerciseName

USER CONTEXT:
- Disorder: $disorder
- Symptoms: ${symptoms.joinToString()}

TASK:
- Provide a short, supportive closing message (1–2 sentences).
- Acknowledge their effort.
- Reflect briefly on the benefit of the exercise.
- After the closing message, ask the user whether they want to:
  (a) try another exercise, or
  (b) finish the session.
- Do NOT continue the exercise.
- Do NOT ask for any exercise‑related reflection.

Return ONLY the closing message + the question.
""".trimIndent()

        val response = client.chatCompletion(
            ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(ChatMessage(ChatRole.User, prompt))
            )
        )

        return response.choices.first().message?.content?.trim()
            ?: "Great work today — would you like to try another exercise, or finish the session?"
    }

    fun updateAssessmentScore(questionIndex: Int, score: Int) {
        assessmentScores[questionIndex] = score
    }

    // Add inside DeepSessionManager
    fun selectAndSetQuestionnaireIfNeeded() {
        if (contextEmotion == null || contextCause == null) {
            Log.d("DeepSession", "selectAndSetQuestionnaireIfNeeded: missing emotion or cause")
            return
        }

        if (selectedQuestionnaire != null) {
            Log.d("DeepSession", "selectAndSetQuestionnaireIfNeeded: already set to ${selectedQuestionnaire?.name}")
            return
        }

        Log.d("DeepSession", "Selecting questionnaire for emotion='$contextEmotion' cause='$contextCause'")
        val q = try {
            QuestionnaireSelector.select(contextEmotion, contextCause)
        } catch (e: Exception) {
            Log.e("DeepSession", "QuestionnaireSelector.select threw", e)
            null
        }

        selectedQuestionnaire = q ?: defaultForEmotion(contextEmotion)
        Log.i("DeepSession", "selectedQuestionnaire set to ${selectedQuestionnaire?.name}")
    }

    private fun defaultForEmotion(emotion: String?): QuestionnaireType {
        return when (emotion?.lowercase()?.trim()) {
            "anxious", "anxiety", "nervous", "stressed", "overwhelmed", "worried" -> QuestionnaireType.GAD7
            "sad", "low", "depressed", "lonely" -> QuestionnaireType.PHQ9
            "angry", "calm", "neutral", "not sure" -> QuestionnaireType.GAD7
            else -> QuestionnaireType.GAD7
        }
    }

    fun shouldTriggerCrisisPopup(): Boolean {
        val now = System.currentTimeMillis()

        // If no crisis has ever been detected → show popup
        if (!crisisDetected) {
            crisisDetected = true
            lastCrisisTimestamp = now
            return true
        }

        // If crisis already detected → allow popup again only if:
        // - 20 seconds have passed
        // - OR the user repeats a strong crisis phrase
        val timeSinceLast = now - lastCrisisTimestamp
        val allowRepeat = timeSinceLast > 20_000 // 20 seconds

        if (allowRepeat) {
            lastCrisisTimestamp = now
            return true
        }

        return false
    }

    fun isStrongCrisisMessage(text: String): Boolean {
        val crisisPhrases = listOf(
            "i'm going to end it",
            "i will end it",
            "i want to die",
            "i'm going to kill myself",
            "i can't go on",
            "life feels pointless",
            "life is pointless",
            "i want to end everything"
        )
        val lower = text.lowercase()
        return crisisPhrases.any { lower.contains(it) }
    }

    fun preloadHealthProfile(
        auth: FirebaseAuth,
        db: FirebaseFirestore,
        onComplete: () -> Unit = {}
    ) {
        val userId = auth.currentUser?.uid ?: return

        val healthRef = db.collection("users")
            .document(userId)
            .collection("health")

        // Reset values before loading
        medicalConditions = emptyList()
        fullTimeStatus = ""
        internationalStatus = ""
        ethnicityList = emptyList()

        // Track completion of both Firestore calls
        var pending = 2

        fun checkDone() {
            pending--
            if (pending == 0) onComplete()
        }

        // -----------------------------------------------------
        // Load medical conditions
        // -----------------------------------------------------
        healthRef.document("medical_conditions")
            .get()
            .addOnSuccessListener { doc ->
                medicalConditions = doc.get("conditions") as? List<String> ?: emptyList()
                checkDone()
            }
            .addOnFailureListener {
                medicalConditions = emptyList()
                checkDone()
            }

        // -----------------------------------------------------
        // Load demographics
        // -----------------------------------------------------
        healthRef.document("demographics")
            .get()
            .addOnSuccessListener { doc ->
                fullTimeStatus = doc.getString("full_time_student") ?: ""
                internationalStatus = doc.getString("international_student") ?: ""
                ethnicityList = doc.get("race") as? List<String> ?: emptyList()
                checkDone()
            }
            .addOnFailureListener {
                fullTimeStatus = ""
                internationalStatus = ""
                ethnicityList = emptyList()
                checkDone()
            }
    }







    private fun normalizeName(s: String): String =
        s.trim()
            .removeSuffix(".")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s\\-]"), "") // remove stray punctuation except hyphen










}
