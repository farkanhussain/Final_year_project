package com.example.therapy_app
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.delay
import android.net.Uri
import android.util.Log
import android.graphics.Color
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import com.google.firebase.firestore.FirebaseFirestore
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.RadioButton



private val db = Firebase.firestore
private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

class ChatActivity : AppCompatActivity() {

    private var loadedTimestamp: Long? = null

    private lateinit var moodConfirmationCard: LinearLayout
    private lateinit var moodEmoji: TextView
    private lateinit var btnConfirmMood: Button
    private lateinit var btnChangeMood: Button


    private var aiMoodIndex: Int = -1





    private var finalMoodEmoji: String = ""


    private lateinit var onnxRunner: OnnxModelRunner
    private lateinit var quickSessionEngine: QuickSessionEngine

    // Real-time assessment state (UI-side)
    private val selectedSymptoms = mutableSetOf<String>()

    // High-severity symptoms that trigger early diagnosis
    private val criticalSymptoms = setOf(
        "panic_attacks",
        "intrusive_thoughts",
        "hallucinations",
        "self_harm",
        "mania"
    )
    private var hasDismissedDiagnosisPopup = false
    private var isUserInputLocked: Boolean = false

    private var cachedDisorder: String? = null

    private var sessionStatus: String = "NEW"

    var lastAiUnknownSymptoms: List<String> = emptyList()

    private var diagnosisDelivered = false

    private lateinit var symptomEngine: SymptomCollectionEngine

    private lateinit var deepManager: DeepSessionManager

    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var voiceButton: ImageButton

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    private val selectedTags = mutableListOf<String>()

    // Exact feature order from your CSV (must match Python feature_cols)
    private val featureCols = listOf(
        "feeling.nervous",
        "panic",
        "breathing.rapidly",
        "sweating",
        "trouble.in.concentration",
        "having.trouble.in.sleeping",
        "having.trouble.with.work",
        "hopelessness",
        "anger",
        "over.react",
        "change.in.eating",
        "suicidal.thought",
        "feeling.tired",
        "close.friend",
        "social.media.addiction",
        "weight.gain",
        "material.possessions",
        "introvert",
        "popping.up.stressful.memory",
        "having.nightmares",
        "avoids.people.or.activities",
        "feeling.negative",
        "trouble.concentrating",
        "blamming.yourself"
    )


    private lateinit var questionnaireContainer: ScrollView
    private lateinit var questionnaireLayout: LinearLayout
    private lateinit var questionnaireTitle: TextView
    private lateinit var btnSubmitSymptoms: Button
    private var lastDiagnosis: String? = null
    private lateinit var inputBar: LinearLayout

    private lateinit var recyclerView: RecyclerView

    private var lastAskedFeature: String? = null

    private val featureIndexMap: Map<String, Int> = featureCols.mapIndexed { i, name -> name to i }.toMap()

    private var selectedLanguage: String? = null

    private lateinit var modelRunner: OnnxModelRunner

    private val VOICE_REQUEST_CODE = 101
    private val presetTags = listOf("Depression", "Anxiety", "Mindfulness")

    private val openAiKey by lazy { BuildConfig.OPENAI_API_KEY }
    private lateinit var client: OpenAI

    private val emergencyContacts = """
                - Samaritans (UK): 116 123 (free, 24/7)
                - NHS 111 for urgent mental health help
                - Emergency services: 999 if in immediate danger
                """.trimIndent()

    private lateinit var emergencyButton: Button
    private lateinit var emergencyOverlay: View

    private var isFirstAiResponse = true

    private var isAiResponding = false

    // ---------------------------------------------------------
    // Conversational Symptom System (Soft + Optional)
    // ---------------------------------------------------------

    private var userHasSpoken = false

    private var sessionId: String? = null

    private var sessionMode: String? = null


    private var openedFromInsightsCard = false


    private var sessionJustLoaded = false




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.w("LifecycleCheck", "🔥 onCreate() fired in ${this::class.java.simpleName}")
        Log.w("LifecycleCheck", "🔥 Activity instance hash = ${this.hashCode()}")

        try {
            Log.w("LifecycleCheck", "🔥 deepManager at onCreate = $deepManager")
        } catch (e: Exception) {
            Log.w("LifecycleCheck", "🔥 deepManager not accessible yet (${e.message})")
        }

        Log.d("OPENAI", "Key length = ${openAiKey.length}")

        client = OpenAI(
            token = openAiKey
        )

        onnxRunner = OnnxModelRunner(this)


        window.setDecorFitsSystemWindows(true)
        setContentView(R.layout.activity_chat)

         openedFromInsightsCard = intent.getBooleanExtra("from_insights_card", false)
        if (openedFromInsightsCard) {
            isFirstAiResponse = false
        }







        sessionId = intent.getStringExtra("SESSION_ID")

        modelRunner = OnnxModelRunner(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_chat)
        val languageSelector = findViewById<LinearLayout>(R.id.languageSelector)
        val sessionSelector = findViewById<LinearLayout>(R.id.sessionSelector)
        val btnEnglish = findViewById<Button>(R.id.btnEnglish)
        val btnUrdu = findViewById<Button>(R.id.btnUrdu)
        val btnQuick = findViewById<Button>(R.id.btnQuick)
        val btnDeep = findViewById<Button>(R.id.btnDeep)
        recyclerView = findViewById(R.id.chatRecyclerView)
        questionnaireContainer = findViewById(R.id.questionnaireContainer)
        questionnaireLayout = findViewById(R.id.questionnaireLayout)
        questionnaireTitle = findViewById(R.id.questionnaireTitle)
        btnSubmitSymptoms = findViewById(R.id.btnSubmitSymptoms)
        inputBar = findViewById(R.id.inputBar)
        messageInput = findViewById(R.id.messageInput)
        sendButton = findViewById(R.id.sendButton)
        voiceButton = findViewById(R.id.voiceButton)
        val saveSessionButton = findViewById<Button>(R.id.saveSessionButton)
        emergencyButton = findViewById(R.id.emergencyButton)
        emergencyOverlay = findViewById(R.id.emergencyOverlay)









        messageInput.isEnabled = false
        sendButton.isEnabled = false
        voiceButton.isEnabled = false

        btnEnglish.setOnClickListener {
            selectedLanguage = "en"

            // Translate session buttons to English
            btnQuick.text = "Quick Check‑In"
            btnDeep.text = "Deep Support Session"

            languageSelector.visibility = View.GONE
            sessionSelector.visibility = View.VISIBLE
        }

        btnUrdu.setOnClickListener {
            selectedLanguage = "ur"

            // Translate session buttons to Urdu
            btnQuick.text = "مختصر چیک اِن"
            btnDeep.text = "تفصیلی سیشن"

            languageSelector.visibility = View.GONE
            sessionSelector.visibility = View.VISIBLE
        }

        btnQuick.setOnClickListener {

            Log.d("QuickButton", "🟢 Quick button clicked")

            // =====================================================
            // SESSION STATE
            // =====================================================
            sessionMode = "quick"
            sessionStatus = "NEW"

            isFirstAiResponse = true
            sessionJustLoaded = true
            openedFromInsightsCard = false

            Log.d("QuickButton", "Session flags reset")

            // =====================================================
            // UI CLEANUP
            // =====================================================
            sessionSelector.visibility = View.GONE
            findViewById<TextView>(R.id.sessionIntroText).visibility = View.GONE

            inputBar.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            enableChatInput()

            // =====================================================
            // CREATE QUICK SESSION ENGINE (lightweight)
            // =====================================================
            quickSessionEngine = QuickSessionEngine(
                onnxRunner = onnxRunner,
                client = client,
                dynamicGenerator = { prompt -> quickDynamicGenerator(prompt) },
                deepManager = deepManager,
                context = this@ChatActivity        // ⭐ FIXED
            )



            Log.d("QuickButton", "QuickSessionEngine created")

            // =====================================================
            // FIRST MESSAGE
            // =====================================================
            if (selectedLanguage == "ur") {
                addMessage("بہت اچھا — ہم ایک مختصر چیک اِن کریں گے۔ آج آپ کے ذہن میں کیا چل رہا ہے؟", false)
            } else {
                addMessage("Great — we’ll do a short 5‑Minute Check‑In. What’s been on your mind today?", false)
            }
        }




        btnDeep.setOnClickListener {

            Log.d("DeepButton", "🔵 Deep button clicked")
            Log.d("DeepButton", "Activity instance hash = ${this.hashCode()}")

            // =====================================================
            // SESSION STATE
            // =====================================================
            sessionMode = "deep"
            sessionStatus = "NEW"

            Log.d("DeepButton", "Session mode set: $sessionMode | status: $sessionStatus")

            // =====================================================
            // UI CLEANUP
            // =====================================================
            sessionSelector.visibility = View.GONE
            findViewById<TextView>(R.id.sessionIntroText).visibility = View.GONE

            // Hide questionnaire (do NOT show it yet)
            questionnaireContainer.visibility = View.GONE

            // Show chat UI
            inputBar.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            // =====================================================
            // RESET CORE DEEP STATE MACHINE
            // =====================================================
            deepManager = DeepSessionManager()
            Log.d("DeepButton", "DeepSessionManager reset")

            // Start in WAITING_FOR_USER_SYMPTOMS
            deepManager.resetConversation()

            // =====================================================
            // RESET SYMPTOM ENGINE
            // =====================================================
            symptomEngine = SymptomCollectionEngine(featureIndexMap, featureCols)
            Log.d("DeepButton", "Symptom engine reset")

            // =====================================================
            // RESET SESSION TRACKING FLAGS
            // =====================================================
            isFirstAiResponse = true
            sessionJustLoaded = false
            openedFromInsightsCard = false

            Log.d("DeepButton", "Session flags reset")

            enableChatInput()

            // =====================================================
            // FIRST THERAPIST-STYLE MESSAGE
            // =====================================================
            addMessage(
                "I'm here with you. What’s been troubling you lately?",
                isUser = false
            )
        }


        btnSubmitSymptoms.setOnClickListener {
            Log.e("QuestionnaireDebug", "---- SUBMIT PRESSED ----")
            Log.e("QuestionnaireDebug", "questionnaireLayout childCount = ${questionnaireLayout.childCount}")

            val selectedSymptoms = mutableListOf<String>()
            val assessmentScores = mutableMapOf<Int, Int>()

            fun extractScoreFromRadioGroup(rg: RadioGroup?): Int {
                if (rg == null) return 0
                val checkedId = rg.checkedRadioButtonId
                if (checkedId == -1) return 0
                val rb = rg.findViewById<RadioButton>(checkedId) ?: return 0
                return when (val t = rb.tag) {
                    is Int -> t
                    is String -> t.toIntOrNull() ?: 0
                    else -> 0
                }
            }

            for (i in 0 until questionnaireLayout.childCount) {
                val row = questionnaireLayout.getChildAt(i)
                Log.e("QuestionnaireDebug", "Row $i found. tag=${row.tag}")

                val tagObj = row.tag
                if (tagObj !is Pair<*, *>) {
                    Log.e("QuestionnaireDebug", "Row $i skipped (no valid question tag Pair)")
                    continue
                }

                val rowTag = tagObj.first as? String
                val radioGroupId = tagObj.second as? Int
                if (rowTag == null || radioGroupId == null) {
                    Log.e("QuestionnaireDebug", "Row $i has invalid tag pair: $tagObj")
                    continue
                }

                val radioGroup = row.findViewById<RadioGroup>(radioGroupId)
                val score = extractScoreFromRadioGroup(radioGroup)
                Log.e("QuestionnaireDebug", "Row $i ($rowTag) → score=$score")

                if (score > 0) {
                    selectedSymptoms.add(rowTag)
                    Log.e("QuestionnaireDebug", "Row $i → score>0 → added symptom tag: $rowTag")
                } else {
                    Log.e("QuestionnaireDebug", "Row $i → score==0 → not added")
                }

                val index = rowTag.removePrefix("q").toIntOrNull()
                if (index != null) {
                    assessmentScores[index] = score
                    deepManager.updateAssessmentScore(index, score)
                } else {
                    Log.e("QuestionnaireDebug", "Row $i ($rowTag) cannot parse index to update deepManager")
                }
            }

            Log.e("QuestionnaireDebug", "FINAL selectedSymptoms = $selectedSymptoms")
            Log.e("QuestionnaireDebug", "Collected assessmentScores = $assessmentScores")

            // 1. Update engine with questionnaire selections (use the assessment map)
            symptomEngine.updateFromAssessmentScores(assessmentScores)
            Log.e("QuestionnaireDebug", "Engine after update = ${symptomEngine.getConfirmedSymptoms()}")

            // 2. Build symptom vector for deep model
            val symptomVector = symptomEngine.buildModelInputVector()

            // 3. Run deep model (PHQ‑9, GAD‑7, emotion)
            val result = onnxRunner.runDeepSession(symptomVector)

            // 4. Store model outputs in symptom engine
            symptomEngine.updatePhq9Prediction(result.phq9)
            symptomEngine.updateGad7Prediction(result.gad7)
            symptomEngine.updateEmotion(result.emotionVector)

            Log.e("DeepModel", "PHQ9=${result.phq9}, GAD7=${result.gad7}, Emotion=${result.emotionVector.toList()}")

            // 5. Deep session state
            deepManager.setQuestionnaireCompleted()
            deepManager.advancePhaseIfNeeded()

            // 6. Hide questionnaire UI
            questionnaireContainer.visibility = View.GONE
            inputBar.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            enableChatInput()

            // 7. Send diagnosis
            sendDiagnosisMessage(selectedSymptoms)
        }






        emergencyOverlay.visibility = View.GONE

        emergencyButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:999")
            }
            startActivity(intent)
        }

        emergencyOverlay.setOnTouchListener { _, _ ->
            hideEmergencyButton()
            true
        }



        messageInput.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter


        val incomingInsights = intent.getStringArrayListExtra("therapy_insights")

        if (incomingInsights != null && incomingInsights.isNotEmpty()) {
            incomingInsights.forEach { insight ->
                addMessage("Therapist Insight: $insight", isUser = false)
            }
        }

        if (sessionId != null) {
            loadExistingSession()
        }

        // Handle physical "Enter" key and keyboard "Send" action
        messageInput.setOnEditorActionListener { _, actionId, event ->

            Log.d("SendButton", "EditorActionListener triggered")

            val isEnterKeyDown = event != null &&
                    event.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_DOWN

            val isSendAction = actionId == EditorInfo.IME_ACTION_SEND

            if (isSendAction || isEnterKeyDown) {

                Log.d("SendButton", "Detected SEND or ENTER action")
                Log.d("SendButton", "messageInput = $messageInput")
                Log.d("SendButton", "messageInput.text = ${messageInput.text}")

                val userText = messageInput.text?.toString() ?: "NULL_TEXT"

                Log.d("SendButton", "User text extracted: '$userText'")

                if (userText.isNotBlank()) {
                    userHasSpoken = true
                    addMessage(userText, isUser = true)
                    messageInput.setText("")

                    Log.d("SendButton", "Session mode at click time = $sessionMode")

                    if (sessionMode == null) {
                        addMessage(
                            if (selectedLanguage == "ur")
                                "براہ کرم پہلے سیشن کی قسم منتخب کریں۔"
                            else
                                "Please select a session type first.",
                            isUser = false
                        )
                        return@setOnEditorActionListener true
                    }

                    Log.d("SendButton", "Calling processUserMessage() from EditorAction")
                    processUserMessage(userText)
                }

                true
            } else {
                false
            }
        }


// ===============================
// SEND BUTTON CLICK LISTENER
// ===============================
        sendButton.setOnClickListener {

            Log.d("SendButton", "Send button clicked")
            Log.d("SendButton", "messageInput = $messageInput")
            Log.d("SendButton", "messageInput.text = ${messageInput.text}")

            val userText = messageInput.text?.toString() ?: "NULL_TEXT"

            Log.d("SendButton", "User text extracted: '$userText'")

            if (userText.isNotBlank()) {
                userHasSpoken = true
                addMessage(userText, isUser = true)
                messageInput.setText("")

                Log.d("SendButton", "Session mode at click time = $sessionMode")

                if (sessionMode == null) {
                    addMessage(
                        if (selectedLanguage == "ur")
                            "براہ کرم پہلے سیشن کی قسم منتخب کریں۔"
                        else
                            "Please select a session type first.",
                        isUser = false
                    )
                    return@setOnClickListener
                }

                Log.d("SendButton", "Calling processUserMessage() from sendButton")
                processUserMessage(userText)
            }
        }






        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        saveSessionButton.setOnClickListener {
            showSaveDialog()
        }
    }

    private fun enableChatInput() {
        messageInput.isEnabled = true
        sendButton.isEnabled = true
        voiceButton.isEnabled = true
    }


    private fun loadExistingSession() {
        val user = auth.currentUser ?: return
        val id = sessionId ?: return

        db.collection("users")
            .document(user.uid)
            .collection("sessions")
            .document(id)
            .get()
            .addOnSuccessListener { doc ->

                val session = doc.toObject(TherapySession::class.java)
                    ?: return@addOnSuccessListener

                sessionJustLoaded = true

                // ⭐ 1. Clean messages BEFORE adding them to UI
                val cleaned = session.messages
                    .filter { it.text != null && it.text.isNotBlank() } // remove null/empty
                    .filter { msg ->
                        // remove fallback messages
                        val fallbackPatterns = listOf(
                            "connection trouble",
                            "having some trouble",
                            "didn’t catch that",
                            "didn't catch that",
                            "say that again"
                        )
                        fallbackPatterns.none { pattern ->
                            msg.text!!.contains(pattern, ignoreCase = true)
                        }
                    }

                // ⭐ 2. Replace your old logic with cleaned messages
                messages.clear()
                messages.addAll(cleaned)

                adapter.notifyDataSetChanged()

                val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
                recyclerView.post {
                    recyclerView.scrollToPosition(messages.size - 1)

                    // ⭐ 3. Add deep session invitation (safe)
                    addMessage(
                        "If you'd like to continue this with more depth, I can guide you through a Deep Support Session.",
                        isUser = false
                    )
                }
            }
    }


    // ---------------------------------------------------------
    // ADD MESSAGE
    // ---------------------------------------------------------
    private fun addMessage(
        text: String,
        isUser: Boolean,
        type: MessageType = MessageType.NORMAL,
        emoji: String? = null
    ) {
        Log.e("AddMessageDebug", "addMessage called: text=$text | isUser=$isUser | type=$type | emoji=$emoji")
        Log.e("AddMessageDebug", "RecyclerView = $recyclerView | Adapter = $adapter")

        // Create the message with the new fields
        val message = Message(
            text = text,
            user = isUser,
            type = type,
            emoji = emoji
        )

        messages.add(message)
        adapter.notifyItemInserted(messages.size - 1)

        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recyclerView.scrollToPosition(messages.size - 1)
    }


    private fun showEmergencyButton() {
        emergencyButton.visibility = View.VISIBLE
        emergencyOverlay.visibility = View.VISIBLE
    }

    private fun hideEmergencyButton() {
        emergencyButton.visibility = View.GONE
        emergencyOverlay.visibility = View.GONE
    }

    // ---------------------------------------------------------
    // VOICE RECOGNITION
    // ---------------------------------------------------------
    private fun startVoiceRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        startActivityForResult(intent, VOICE_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == VOICE_REQUEST_CODE && resultCode == RESULT_OK) {
            val result = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = result?.get(0) ?: return
            userHasSpoken = true
            addMessage(spokenText, isUser = true)
            processUserMessage(spokenText)
        }
    }




    // ---------------------------------------------------------
    // PROCESS USER MESSAGE (Soft + Optional Symptom Flow)
    // ---------------------------------------------------------
    private fun processUserMessage(userMessage: String) {

        Log.d("LifecycleCheck", "🟦 processUserMessage() called")
        Log.d("LifecycleCheck", "Activity instance hash = ${this.hashCode()}")
        Log.d(
            "LifecycleCheck",
            "deepManager at start = ${
                if (::deepManager.isInitialized) deepManager else "NOT INITIALIZED"
            }"
        )
        Log.d("LifecycleCheck", "sessionMode at start = $sessionMode")
        Log.d("LifecycleCheck", "sessionStatus at start = $sessionStatus")

        Log.d("ProcessUserMessage", "🔵 Function entered with message: $userMessage")


        try {

            val mode = sessionMode?.trim()?.lowercase() ?: ""

            Log.d("ProcessUserMessage", "Session mode detected: '$mode'")

            if (mode.isEmpty()) {
                addMessage(
                    if (selectedLanguage == "ur")
                        "براہ کرم پہلے سیشن کی قسم منتخب کریں۔"
                    else
                        "Please select a session type first.",
                    isUser = false
                )
                return
            }

            // =====================================================
            // CRISIS-FIRST ARCHITECTURE
            // =====================================================
            if (CrisisDetector.isCrisisMessage(userMessage)) {

                addMessage(
                    """
                I'm really sorry you're feeling this way.

                You don’t have to go through this alone.

                Here are some support options you can use right now:

                $emergencyContacts
                """.trimIndent(),
                    isUser = false
                )

                lifecycleScope.launch {
                    delay(5000)
                    showEmergencyButton()
                }

                return
            }

            // ============================================
            // QUICK SESSION MODE
            // ============================================
            if (sessionMode == "quick") {

                Log.e("ENTRY_CHECK", "🔵 QUICK MODE block ENTERED in processUserMessage()")

                lifecycleScope.launch {

                    try {
                        Log.e("ENTRY_CHECK", "🔵 Calling quickSessionEngine.processQuickSessionMessage()...")

                        val quickResult =
                            quickSessionEngine.processQuickSessionMessage(userMessage)

                        Log.e("ENTRY_CHECK", "🟢 quickSessionEngine returned successfully")
                        Log.e(
                            "ENTRY_CHECK",
                            "🟢 Emotion=${quickResult.emotion}, Condition=${quickResult.condition}"
                        )
                        Log.e(
                            "ENTRY_CHECK",
                            "🟢 Exercises=${quickResult.exercises.joinToString()}"
                        )

                        Log.e("ENTRY_CHECK", "🟢 Calling sendToAI() for QUICK mode...")

                        sendToAI(
                            userMessage = userMessage,
                            emotion = quickResult.emotion,
                            disorder = quickResult.condition,
                            phase = "QUICK",
                            exercises = quickResult.exercises
                        )

                        Log.e("ENTRY_CHECK", "🟢 sendToAI() call completed")

                    } catch (e: Exception) {

                        Log.e("ENTRY_CHECK", "🔴 ERROR inside quick session coroutine", e)

                        addMessage(
                            "Something went wrong during the quick session. Please try again.",
                            isUser = false
                        )
                    }
                }

                return
            }

            // =====================================================
            // DEEP MODE ONLY
            // =====================================================
            if (mode != "deep") return

            lifecycleScope.launch {

                Log.d("ProcessUserMessage", "🟣 DEEP mode triggered")



                if (deepManager.phase == DeepPhase.SESSION_COMPLETE) {

                    Log.d("DeepSession", "SESSION_COMPLETE triggered early exit")

                    val diagnosis = lastDiagnosis ?: "general distress"
                    val therapyType = deepManager.therapyType ?: "CBT"

                    val summaryText = """
Here’s a quick summary of today’s session.
You described symptoms such as ${symptomEngine.getConfirmedSymptoms().joinToString(", ")}.
These point most strongly toward $diagnosis.

Based on this, the most suitable therapy approach is $therapyType.
You completed a $therapyType-based exercise today, and a follow-up session will be arranged for next week following $therapyType guidelines.
""".trimIndent()

                    addMessage(
                        text = summaryText,
                        isUser = false,
                        type = MessageType.SUMMARY
                    )

                    return@launch
                }

                try {

                    // -----------------------------------------------------
                    // 0. CONVERSATION STATE LAYER
                    // -----------------------------------------------------

// ✅ CLASSIFY ONCE (IMPORTANT FIX)
                    val intent = classifyIntent(userMessage)

                    Log.d("ExerciseFlow", "Intent = $intent")
                    Log.d("ExerciseFlow", "Phase = ${deepManager.phase}")
                    Log.d("ExerciseFlow", "Exercises delivered = ${deepManager.exercisesDelivered()}")

                    // -----------------------------------------------------
                    // SESSION TERMINATION (UNIFIED)
                    // -----------------------------------------------------
                    if (intent == "END_SESSION") {
                        deepManager.setSessionComplete()

                        // IMPORTANT: allow SESSION_COMPLETE phase to run
                        processUserMessage("")
                        return@launch
                    }

                    // -----------------------------------------------------
                    // EXERCISE SELECTION → MOVE TO EXERCISE_GUIDANCE
                    // -----------------------------------------------------
                    // -----------------------------------------------------
// EXERCISE SELECTION → MOVE TO EXERCISE_GUIDANCE
// -----------------------------------------------------
                    if (deepManager.exercisesDelivered() &&
                        deepManager.phase == DeepPhase.THERAPY_EXERCISES &&
                        (intent == "SELECT_EXERCISE" ||
                                intent == "YES_TO_EXERCISES" ||
                                intent == "ACKNOWLEDGEMENT")) {

                        Log.d("EXERCISE_TRANSITION", "intent=$intent")
                        Log.d("EXERCISE_TRANSITION", "userMessage=\"$userMessage\"")
                        Log.d("EXERCISE_TRANSITION", "phase=${deepManager.phase}, delivered=${deepManager.exercisesDelivered()}")


                        // 1️⃣ Detect chosen exercise NAME
                        val chosenName = deepManager.detectChosenExercise(userMessage)
                        Log.d("EXERCISE_TRANSITION", "chosenName=$chosenName, userMessage=\"$userMessage\"")

                        // 2️⃣ Convert name → full DbtExercise
                        val chosenExercise = deepManager.generatedDbtExercises
                            ?.find { it.name.equals(chosenName, ignoreCase = true) }

                        Log.d("EXERCISE_TRANSITION", "matchedExercise=${chosenExercise?.name}")

                        // 3️⃣ Fallback if detection fails
                        val finalExercise = chosenExercise
                            ?: deepManager.generatedDbtExercises?.firstOrNull()
                            ?: return@launch

                        Log.d("EXERCISE_TRANSITION", "finalExercise=${finalExercise.name}")

                        // 4️⃣ Initialize exercise
                        deepManager.beginExercise(finalExercise)
                        deepManager.awaitingExerciseConsent = false
                        Log.d("EXERCISE_TRANSITION", "beginExercise() → phase=${deepManager.phase}")

                        // 5️⃣ Generate FIRST STEP
                        val firstStep = withContext(Dispatchers.IO) {
                            try {
                                deepManager.generateExerciseStep(
                                    exerciseName = finalExercise.name,
                                    userInput = "",
                                    disorder = lastDiagnosis ?: "general_distress",
                                    symptoms = symptomEngine.getSymptoms(),
                                    steps = finalExercise.steps,
                                    stepIndex = deepManager.currentExerciseStep,
                                    client = client
                                )
                            } catch (e: Exception) {
                                "Let's begin. Write one short sentence related to \"${finalExercise.name}\"."
                            }
                        }

                        // 6️⃣ Send first step
                        addMessage(firstStep, false)
                        return@launch
                    }


                    // -----------------------------------------------------
                    // 1. PHASE SYSTEM
                    // -----------------------------------------------------
                    Log.d("DeepSession", "Current phase: ${deepManager.phase}")

                    when (deepManager.phase) {

                        DeepPhase.CONTEXT_INTAKE -> {

                            // Step 1 — detect emotion if missing
                            if (deepManager.contextEmotion == null) {

                                val emotion = symptomEngine.detectEmotion(userMessage, client)
                                deepManager.contextEmotion = emotion

                                addMessage(
                                    "Thanks for sharing that. What do you think is causing you to feel $emotion?",
                                    isUser = false
                                )
                                return@launch
                            }

                            // Step 2 — detect cause if missing
                            if (deepManager.contextCause == null) {

                                deepManager.contextCause = userMessage
                                deepManager.contextSummary =
                                    "User feels ${deepManager.contextEmotion} because: $userMessage"

                                // Select PHQ‑9 or GAD‑7
                                val questionnaire = QuestionnaireSelector.select(
                                    deepManager.contextEmotion,
                                    deepManager.contextCause
                                )
                                deepManager.selectedQuestionnaire = questionnaire

                                addMessage(
                                    "Thanks for explaining that. I’ll use the ${questionnaire.name} questionnaire next.",
                                    isUser = false
                                )

                                deepManager.advancePhaseIfNeeded()
                                return@launch
                            }

                            // If both emotion + cause already collected, just advance
                            deepManager.advancePhaseIfNeeded()
                            return@launch
                        }

                        DeepPhase.ASSESSMENT -> {

                            val questionnaire = deepManager.selectedQuestionnaire

                            // Safety fallback (should not happen)
                            if (questionnaire == null) {
                                addMessage(
                                    "I'm preparing the right questionnaire for you. One moment…",
                                    false
                                )
                                return@launch
                            }

                            when (questionnaire) {

                                QuestionnaireType.PHQ9 -> {
                                    // Build PHQ‑9 UI
                                    showQuestionnaire(QuestionnaireData.phq9)

                                    addMessage(
                                        "Please complete the PHQ‑9 questionnaire above. It helps us understand how your mood has been recently.",
                                        false
                                    )
                                }

                                QuestionnaireType.GAD7 -> {
                                    // Build GAD‑7 UI
                                    showQuestionnaire(QuestionnaireData.gad7)

                                    addMessage(
                                        "Please complete the GAD‑7 questionnaire above. It helps us understand how anxiety may be affecting you.",
                                        false
                                    )
                                }
                            }

                            return@launch
                        }


                        DeepPhase.DIAGNOSIS -> {

                            deepManager.setDiagnosisDelivered()
                            deepManager.advancePhaseIfNeeded()

                            sendDiagnosisMessage(symptomEngine.getConfirmedSymptoms())
                            return@launch
                        }

                        DeepPhase.TREATMENT_PATTERN -> {

                            isUserInputLocked = true

                            val disorder = lastDiagnosis ?: "general_distress"

                            withContext(Dispatchers.IO) {

                                val pattern = deepManager.getTreatmentPattern(
                                    disorder,
                                    symptomEngine.getSymptoms(),
                                    5f, 5f, 5f, 5f, 5f, 80f,
                                    "neutral"
                                )

                                deepManager.setLastTreatmentPattern(pattern)
                                deepManager.setPatternDelivered()
                            }

                            withContext(Dispatchers.Main) {

                                isUserInputLocked = false

                                deepManager.advancePhaseIfNeeded()

                                // Trigger therapy type generation immediately
                                processUserMessage("")
                            }

                            return@launch
                        }

                        DeepPhase.THERAPY_TYPE -> {

                            isUserInputLocked = true

                            val disorder = lastDiagnosis ?: "general_distress"
                            val symptoms = symptomEngine.getSymptoms()

                            val treatmentPattern = deepManager.getTreatmentPattern(
                                disorder = disorder,
                                symptoms = symptoms,
                                mood = 5f,
                                sleep = 5f,
                                activity = 5f,
                                stress = 5f,
                                progress = 5f,
                                adherence = 80f,
                                emotion = "neutral"
                            )

                            // -------------------------------
                            // 1. SELECT THERAPY TYPE (LLM)
                            // -------------------------------
                            val rawTherapyType = withContext(Dispatchers.IO) {
                                try {
                                    val prompt = """
You are selecting the most appropriate therapy modality for the user based on their treatment pattern.

────────────────────────────────────────
USER CONTEXT
────────────────────────────────────────
- Disorder: $disorder
- Treatment Cluster: ${treatmentPattern.cluster}
- Treatment Focus: ${treatmentPattern.focus}
- Recommended Methods: ${treatmentPattern.methods.joinToString()}

────────────────────────────────────────
TASK
────────────────────────────────────────
Return ONLY one therapy type from the list below.

CBT
DBT
IPT
MBT
Psychodynamic Therapy
Humanistic Therapy
ACT
CFT

────────────────────────────────────────
REQUIREMENTS
────────────────────────────────────────
- Respond with ONLY the therapy type name.
- No explanations.
- No extra text.
- No bullet points.
- No reasoning.
- No emojis.
""".trimIndent()

                                    val response = client.chatCompletion(
                                        ChatCompletionRequest(
                                            model = ModelId("gpt-4o-mini"),
                                            messages = listOf(ChatMessage(ChatRole.User, prompt))
                                        )
                                    )

                                    response.choices.first().message?.content?.trim() ?: "CBT"

                                } catch (e: Exception) {
                                    "CBT"
                                }
                            }

                            // -------------------------------
                            // Normalize and validate output
                            // -------------------------------
                            fun normalizeTherapyType(s: String): String =
                                s.split(Regex("[\\n\\r]"))[0].trim().removeSuffix(".").replace(Regex("\\s+"), " ")

                            val normalizedRaw = normalizeTherapyType(rawTherapyType)
                            val normalizedUpper = normalizedRaw.uppercase()

                            // Allowed canonical set (uppercased keys)
                            val allowed = setOf(
                                "CBT", "DBT", "IPT", "MBT",
                                "PSYCHODYNAMIC THERAPY", "HUMANISTIC THERAPY",
                                "ACT", "CFT"
                            )

                            val chosenTherapyType = if (normalizedUpper in allowed) {
                                // store the cleaned original-cased label (preserve user-friendly casing)
                                normalizedRaw
                            } else {
                                // fallback
                                "CBT"
                            }

                            deepManager.therapyType = chosenTherapyType

                            Log.d("DBT_DEBUG", "Therapy type selected (raw) = $rawTherapyType")
                            Log.d("DBT_DEBUG", "Therapy type selected (normalized) = $chosenTherapyType")

                            // Map to display full name using normalizedUpper for matching
                            val fullName = when (chosenTherapyType.uppercase()) {
                                "CBT" -> "Cognitive Behavioural Therapy (CBT)"
                                "DBT" -> "Dialectical Behaviour Therapy (DBT)"
                                "IPT" -> "Interpersonal Therapy (IPT)"
                                "MBT" -> "Mindfulness‑Based Therapy (MBT)"
                                "ACT" -> "Acceptance and Commitment Therapy (ACT)"
                                "CFT" -> "Compassion‑Focused Therapy (CFT)"
                                "HUMANISTIC THERAPY" -> "Humanistic Therapy"
                                "PSYCHODYNAMIC THERAPY" -> "Psychodynamic Therapy"
                                else -> chosenTherapyType
                            }

                            // ---------------------------------------------------------
                            // 2. SELECT WEBSITE URL FOR THE THERAPY TYPE (use normalized key)
                            // ---------------------------------------------------------
                            val therapyUrl = when (chosenTherapyType.uppercase()) {
                                "DBT" -> "https://www.merseycare.nhs.uk/patient-leaflets/dialectical-behaviour-therapy"
                                // add other therapy URLs here using the same uppercase keys
                                else -> null
                            }

                            // ---------------------------------------------------------
                            // 3. FETCH SITE TEXT + SUMMARISE USING OPENAI (IO + safe)
                            // ---------------------------------------------------------
                            val summary = if (therapyUrl != null) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val rawText = fetchWebsiteText(therapyUrl)
                                        summariseTherapyDefinition(rawText, chosenTherapyType)
                                    } catch (e: Exception) {
                                        Log.e("TherapyType", "Failed to fetch/summarise therapy page: ${e.message}")
                                        "A brief definition for this therapy type is not yet available."
                                    }
                                }
                            } else {
                                "A brief definition for this therapy type is not yet available."
                            }

                            // ---------------------------------------------------------
                            // 4. UPDATE SESSION STATE
                            // ---------------------------------------------------------
                            deepManager.setTherapyTypeDelivered()
                            deepManager.advancePhaseIfNeeded()
                            Log.d("DeepSession", "Phase after therapy type: ${deepManager.phase}")

                            deepManager.awaitingExerciseConsent = true
                            isUserInputLocked = false

                            // ---------------------------------------------------------
                            // 5. SEND FINAL MESSAGE TO USER
                            // ---------------------------------------------------------
                            addMessage(
                                "Based on everything so far, the most suitable therapy approach is $fullName.\n\n" +
                                        "$summary\n\n" +
                                        (therapyUrl?.let { "You can read the full definition here:\n$it\n\n" } ?: "") +
                                        "Would you like to try some $chosenTherapyType exercises to help you apply this approach in a practical, supportive way?",
                                false
                            )

                            return@launch
                        }


                        DeepPhase.THERAPY_EXERCISES -> {

                            // If exercises were already shown, do NOT regenerate them
                            if (deepManager.exercisesDelivered()) {
                                Log.d("THERAPY_EXERCISES", "Exercises already delivered — skipping generation.")
                                return@launch
                            }

                            isUserInputLocked = true

                            val disorder = lastDiagnosis ?: "general_distress"
                            val therapyType = deepManager.therapyType ?: "general"

                            Log.d("THERAPY_EXERCISES", "Starting exercise generation. disorder=$disorder, therapyType=$therapyType")

                            // Generate exercises (generator caches full objects on deepManager.generatedDbtExercises)
                            val exerciseNames = withContext(Dispatchers.IO) {
                                MultiTherapyExerciseGenerator().generateExercises(
                                    therapyType = therapyType,
                                    disorder = disorder,
                                    symptoms = symptomEngine.getSymptoms(),
                                    userMessage = "",
                                    treatmentPattern = deepManager.getTreatmentPattern(
                                        disorder,
                                        symptomEngine.getSymptoms(),
                                        5f, 5f, 5f, 5f, 5f, 80f,
                                        "neutral"
                                    ),
                                    client = client,
                                    dynamicGenerator = { prompt: String ->
                                        val response = client.chatCompletion(
                                            ChatCompletionRequest(
                                                model = ModelId("gpt-4o-mini"),
                                                messages = listOf(ChatMessage(ChatRole.User, prompt))
                                            )
                                        )
                                        response.choices.first().message?.content ?: ""
                                    },
                                    deepManager = deepManager,
                                    context = this@ChatActivity
                                )
                            }

                            Log.d("THERAPY_EXERCISES", "Generated exercise names: ${exerciseNames.joinToString()}")

                            // Ensure the canonical full-object store exists and log it
                            val cachedFull = deepManager.generatedDbtExercises
                            if (cachedFull == null || cachedFull.isEmpty()) {
                                Log.w("THERAPY_EXERCISES", "Warning: generator did not cache full exercise objects. Names only: ${exerciseNames.joinToString()}")
                                // As a defensive fallback, convert names -> minimal DbtExercise objects and cache them
                                val fallbackExercises = exerciseNames.map { name ->
                                    DbtExercise(
                                        name = name,
                                        description = name, // minimal placeholder
                                        url = "https://example.com/exercises/${normalizeName(name).replace(" ", "-").lowercase()}",
                                        steps = listOf("Step 1 for $name")
                                    )
                                }
                                deepManager.generatedDbtExercises = fallbackExercises
                                Log.d("THERAPY_EXERCISES", "Cached fallback full exercises: ${fallbackExercises.map { it.name }}")
                            } else {
                                Log.d("THERAPY_EXERCISES", "Cached full exercises: ${cachedFull.map { it.name }}")
                            }

                            // Store names for UI if you keep that separate (optional). Do NOT overwrite the full-object store.
                            if (exerciseNames.isNotEmpty()) {
                                try {
                                    deepManager.setGeneratedExercises(exerciseNames) // optional: keep for UI
                                    deepManager.setExercisesDelivered()
                                    Log.d("THERAPY_EXERCISES", "Exercises delivered flag set")
                                } catch (e: Exception) {
                                    Log.w("THERAPY_EXERCISES", "setGeneratedExercises failed: ${e.message}")
                                    // still mark delivered if full objects exist
                                    deepManager.setExercisesDelivered()
                                }
                            } else {
                                Log.e("THERAPY_EXERCISES", "❌ No exercises generated — cannot continue.")
                            }

                            isUserInputLocked = false

                            // Send NAMES ONLY to UI
                            Log.d("THERAPY_EXERCISES", "Sending exercise names to UI: ${exerciseNames.joinToString()}")
                            sendToAI(
                                userMessage = userMessage,
                                emotion = "not_applicable",
                                disorder = disorder,
                                phase = "THERAPY_EXERCISES",
                                exercises = exerciseNames
                            )

                            return@launch
                        }



                        DeepPhase.EXERCISE_GUIDANCE -> {

                            // Guard: exercises must have been delivered
                            if (!deepManager.exercisesDelivered()) {
                                Log.e("EXERCISE_FLOW", "❌ exercisesDelivered=false — cannot enter EXERCISE_GUIDANCE")
                                return@launch
                            }

                            // ⭐ NEW: Guard against missing exercise context
                            if (deepManager.currentExerciseName.isNullOrBlank()) {
                                Log.e("EXERCISE_FLOW", "❌ currentExerciseName is NULL — exercise was never started!")
                                return@launch
                            }

                            if (deepManager.currentExerciseSteps.isEmpty()) {
                                Log.e("EXERCISE_FLOW", "❌ currentExerciseSteps is EMPTY — steps were never loaded!")
                                return@launch
                            }

                            // ⭐ LOG: User replied — check current step BEFORE anything else
                            Log.d(
                                "EXERCISE_FLOW",
                                "User replied. currentExerciseName=${deepManager.currentExerciseName}, " +
                                        "stepIndex=${deepManager.currentExerciseStep}, " +
                                        "stepsSize=${deepManager.currentExerciseSteps.size}"
                            )

                            // User ends the exercise manually
                            if (intent == "FINISHED_EXERCISES" || intent == "NO_TO_EXERCISES") {
                                Log.d("EXERCISE_FLOW", "User ended exercise manually.")
                                deepManager.endExercise()
                                return@launch
                            }

                            // Pull current exercise context
                            val exerciseName = deepManager.currentExerciseName
                            val steps = deepManager.currentExerciseSteps
                            val stepIndex = deepManager.currentExerciseStep
                            val disorder = lastDiagnosis ?: "general_distress"
                            val symptoms = symptomEngine.getSymptoms()

                            // ⭐ NEW: Log full exercise context
                            Log.d(
                                "EXERCISE_FLOW",
                                "Exercise context: name=$exerciseName, steps=${steps.joinToString()}, stepIndex=$stepIndex"
                            )

                            // ⭐ LOG: Before completion check
                            Log.d(
                                "EXERCISE_FLOW",
                                "Before completion check: stepIndex=$stepIndex, totalSteps=${steps.size}"
                            )

                            // ⭐ Completion check BEFORE generating next step
                            if (stepIndex >= steps.size - 1) {

                                Log.d("EXERCISE_FLOW", "Final step reached at stepIndex=$stepIndex. Closing exercise immediately.")

                                val closure = withContext(Dispatchers.IO) {
                                    try {
                                        deepManager.generateExerciseClosure(
                                            exerciseName = exerciseName,
                                            disorder = disorder,
                                            symptoms = symptoms,
                                            client = client
                                        )
                                    } catch (e: Exception) {
                                        "Great work. Let's pause here — you've completed the exercise."
                                    }
                                }

                                deepManager.endExercise()
                                Log.d("EXERCISE_FLOW", "Exercise ended. Sending closure message.")
                                addMessage(closure, false)
                                return@launch
                            }

                            // ⭐ LOG: Before generating next step
                            Log.d(
                                "EXERCISE_FLOW",
                                "Generating next step using stepIndex=$stepIndex for exercise=$exerciseName"
                            )

                            // ⭐ Generate next step using step-aware engine
                            val nextStep = withContext(Dispatchers.IO) {
                                try {
                                    deepManager.generateExerciseStep(
                                        exerciseName = exerciseName,
                                        userInput = userMessage,
                                        disorder = disorder,
                                        symptoms = symptoms,
                                        steps = steps,
                                        stepIndex = stepIndex,
                                        client = client
                                    )
                                } catch (e: Exception) {
                                    "Let's continue — tell me one more thing you're noticing."
                                }
                            }

                            // ⭐ LOG: After generating next step
                            Log.d(
                                "EXERCISE_FLOW",
                                "Generated next step. stepIndex=$stepIndex, nextStepPreview=${nextStep.take(60)}"
                            )

                            // ⭐ Increment step counter AFTER generating the step
                            deepManager.advanceExerciseStep()

                            // ⭐ LOG: After advancing step counter
                            Log.d(
                                "EXERCISE_FLOW",
                                "Step advanced. newStepIndex=${deepManager.currentExerciseStep}"
                            )

                            // Send next step to UI
                            Log.d("EXERCISE_FLOW", "Sending next step to UI.")
                            addMessage(nextStep, false)
                            return@launch
                        }



                        // ⭐ NEW FINAL PHASE — show mood AFTER session is complete
                        DeepPhase.SESSION_COMPLETE -> {
                            return@launch
                        }
                    }

                } catch (e: Exception) {
                    Log.e("DeepSession", "Error in deep session block", e)
                }

            } // ← closes lifecycleScope.launch

        } catch (e: Exception) {
            Log.e("ProcessUserMessage", "Error processing message", e)
        }
    }






            fun cleanMessagesForOpenAI(rawMessages: List<Message>): List<OpenAIMessage> {

        val fallbackPatterns = listOf(
            "connection trouble",
            "having some trouble",
            "didn’t catch that",
            "didn't catch that",
            "say that again"
        )

        return rawMessages
            .filter { it.text != null && it.text.isNotBlank() }
            .filter { msg ->
                fallbackPatterns.none { pattern ->
                    msg.text!!.contains(pattern, ignoreCase = true)
                }
            }
            .map { msg ->
                val role = if (msg.user == true) "user" else "assistant"
                OpenAIMessage(role = role, content = msg.text!!.trim())
            }
            .takeLast(3)
    }



    private fun sendDiagnosisMessage(selectedSymptoms: List<String>) {

        val symptomText = selectedSymptoms.joinToString(", ")

        Log.e(
            "DiagnosisDebug",
            "sendDiagnosisMessage() called with selectedSymptoms=$selectedSymptoms" +
                    " | count=${selectedSymptoms.size}" +
                    " | symptomEngineConfirmed=${symptomEngine.getConfirmedSymptoms()}" +
                    " | engineCount=${symptomEngine.getConfirmedSymptoms().size}"
        )

        // 1. Get PHQ‑9 / GAD‑7 evidence (new system)
        val evidence = symptomEngine.getDiagnosisEvidence()

        // 2. Build clinical summary
        val summary = """
PHQ‑9 (depression):
- Score: ${evidence.phq9Score.toInt()}
- Severity: ${evidence.phq9Severity}

GAD‑7 (anxiety):
- Score: ${evidence.gad7Score.toInt()}
- Severity: ${evidence.gad7Severity}
""".trimIndent()

        // 3. Show summary message in chat
        val summaryText = """
Based on your questionnaire responses:

$summary

These scores are screening indicators, not a formal diagnosis, but they help us understand how you're feeling.
""".trimIndent()

        addMessage(summaryText, false)

        // 4. Build DIAGNOSIS prompt (clinically grounded)
        val prompt = """
You are interpreting standardized mental‑health screening scores.

SCREENING RESULTS:
$summary

SYMPTOMS SELECTED:
$symptomText

TASK:
- Provide a **very concise** interpretation.
- Use **bullet points**.
- Mention depression and anxiety separately.
- If both scores are elevated, mention **mixed anxiety‑depression**.
- Use **short, simple sentences** (max 1–2 lines each).
- Keep the tone supportive and non‑clinical.
- Remind the user this is **not a formal diagnosis**.
""".trimIndent()

        sendToAI(
            userMessage = prompt,
            emotion = "not_applicable",
            disorder = "pending",
            phase = "DIAGNOSIS",
            exercises = emptyList(),
            cluster = null,
            focus = null,
            methods = null
        )

        deepManager.setDiagnosisDelivered()
        deepManager.advancePhaseIfNeeded()
    }





    // ---------------------------------------------------------
    // OPENAI CALL
    // ---------------------------------------------------------
    private fun sendToAI(
        userMessage: String,
        emotion: String,
        disorder: String,
        phase: String,
        exercises: List<String> = emptyList(),
        cluster: String? = null,
        focus: String? = null,
        methods: List<String>? = null
    )
    {

        val isQuick = (sessionMode == "quick")
        val isDeep = (sessionMode == "deep")

        Log.d("SendToAI", "sendToAI() ENTERED")
        Log.d("AI_DEBUG", "---- sendToAI CALLED ----")
        Log.d("AI_DEBUG", "User message: $userMessage")
        Log.d("AI_DEBUG", "Disorder: $disorder")
        Log.d("AI_DEBUG", "Phase: $phase")
        Log.d("AI_DEBUG", "Exercises count: ${exercises.size}")   // ⭐ UPDATED
        Log.d("AI_DEBUG", "Session mode: $sessionMode")

// ---------------------------------------------------------
// Clean history for OpenAI
// ---------------------------------------------------------
        val cleanedHistory = cleanMessagesForOpenAI(messages)

        val historyText = cleanedHistory.joinToString("\n") { msg ->
            "${msg.role.uppercase()}: ${msg.content}"
        }

// ---------------------------------------------------------
// Symptoms (Deep Mode only)
// ---------------------------------------------------------
        val symptomsText = if (isDeep && ::symptomEngine.isInitialized) {
            symptomEngine.getConfirmedSymptomsText()
        } else {
            "None (quick mode)"
        }

// ---------------------------------------------------------
// Exercises (multi‑therapy)
// ---------------------------------------------------------
        val exercisesText = if (exercises.isEmpty()) {
            "None available"
        } else {
            exercises.joinToString("\n") { "- $it" }
        }

// ---------------------------------------------------------
// Session status
// ---------------------------------------------------------
        val sessionStatus = if (isFirstAiResponse) "NEW" else "CONTINUING"

// ---------------------------------------------------------
// Debug logging
// ---------------------------------------------------------
        Log.d(
            "DeepSessionDebug",
            "symptomsConfirmed=" +
                    (if (isDeep && ::symptomEngine.isInitialized)
                        symptomEngine.getConfirmedSymptoms().toString()
                    else
                        "[] (quick mode)") +
                    " | symptomCount=" +
                    (if (isDeep && ::symptomEngine.isInitialized)
                        symptomEngine.getConfirmedSymptoms().size
                    else
                        0) +
                    " | symptomsText=" + symptomsText +
                    " | phase=" + phase +
                    " | sessionMode=" + sessionMode +
                    " | sessionStatus=" + sessionStatus +
                    " | exercises=" + exercisesText
        )

        val clusterText = cluster ?: "Not available"
        val focusText = focus ?: "Not available"
        val methodsText = methods?.joinToString() ?: "None specified"




        // ---------------------------------------------------------
        // QUICK SESSION PROMPT
        // ---------------------------------------------------------
        val quickPrompt = """
[ROLE]
You are a supportive assistant for a 5‑minute emotional check‑in.

[CONTEXT]
- Mode: QUICK
- Respond ONLY to the latest user message.
- Ignore all previous conversation history.
- No questions.
- No symptom collection.
- No CBT phases.
- Keep responses short, warm, and emotionally attuned.

[DETECTED EMOTION]
$emotion

[DETECTED CONDITION]
$disorder

[RECOMMENDED EXERCISES]
$exercisesText

[USER MESSAGE]
"$userMessage"

────────────────────────────────────────
SAFETY CHECK
────────────────────────────────────────
If the user expresses suicide, self‑harm, intent to harm others, or extreme hopelessness:
Respond with empathy, grounding, and encourage reaching out to someone they trust or a professional.
Do not continue the Quick Session structure.

────────────────────────────────────────
RESPONSE FORMAT
────────────────────────────────────────
If emotional content is present:
1) Brief emotional interpretation  
2) ONE simple, supportive exercise (from the list above)  
3) Warm closing  
4) Invitation to begin a Deep Support Session  

If no emotional content:
Respond with exactly:
"I'm here with you. Could you share a bit about what you're feeling right now."

""".trimIndent()


        // ---------------------------------------------------------
        // DEEP SUPPORT SESSION PROMPT
        // ---------------------------------------------------------
        val builtDeepPrompt = """
[ROLE]
You are a supportive, evidence‑based therapeutic assistant operating inside a structured therapy system.
You do NOT control session phases. You only respond according to the phase provided.

────────────────────────────────────────
[SESSION CONTEXT]
────────────────────────────────────────
- Phase: $phase
- Mode: DEEP SUPPORT SESSION
- Session Status: $sessionStatus

────────────────────────────────────────
[SYMPTOMS SELECTED BY USER]
────────────────────────────────────────
$symptomsText

────────────────────────────────────────
[RECENT HISTORY]
────────────────────────────────────────
$historyText

────────────────────────────────────────
[USER MESSAGE]
────────────────────────────────────────
"$userMessage"

────────────────────────────────────────
[TREATMENT PATTERN (Model C)]
────────────────────────────────────────
Cluster: $cluster
Focus: $focus
Methods: $methodsText

────────────────────────────────────────
[RECOMMENDED EXERCISES]
────────────────────────────────────────
$exercisesText

────────────────────────────────────────
SAFETY CHECK
────────────────────────────────────────
If the user expresses suicide, self‑harm, intent to harm others, or extreme hopelessness:
- Respond with immediate empathy and validation
- Do NOT continue structured content
- Encourage reaching out to someone they trust or a professional
- Keep the response short, calm, and supportive

────────────────────────────────────────
RESPONSE RULES (UPDATED FOR MULTI‑THERAPY)
────────────────────────────────────────
- Respond naturally, warmly, and conversationally
- Keep responses concise by default (short paragraphs or bullet points)
- Expand only when clearly helpful or requested
- Use simple, human language
- Do NOT mention or explain phases explicitly
- Do NOT control progression of the session
- Focus ONLY on the user’s latest message
- Ask ONLY ONE question per response (unless in DIAGNOSIS or THERAPY_EXERCISES phases)

────────────────────────────────────────
DYNAMIC DIAGNOSIS RULES
────────────────────────────────────────
You are NOT limited to the disorders in the dataset or Model B.
You may identify ANY mental‑health condition that matches the user’s symptom pattern,
even if it is not included in the training data.

Examples include (but are NOT limited to):
- Burnout / work‑related exhaustion
- Social anxiety
- OCD‑like intrusive rumination
- Bipolar‑like activation patterns
- Emotional eating
- Digital addiction
- Compulsive buying
- Trauma‑related stress
- Adjustment disorder
- Sleep disturbance patterns

You must name the condition directly if the symptoms match.

────────────────────────────────────────
WEIGHTING RULES
────────────────────────────────────────
For every condition you identify, assign a weight:
- HIGH = strong match to 3 or more core symptoms
- MEDIUM = partial match to 2 symptoms
- LOW = weak match to 1 symptom

Weights are relative indicators, not probabilities.

────────────────────────────────────────
SYMPTOM CLUSTER DEFINITIONS
────────────────────────────────────────
Use these clusters to reason about patterns:

- Exhaustion cluster:
  feeling.tired, trouble.in.concentration, having.trouble.with.work, having.trouble.in.sleeping

- Hyperarousal cluster:
  feeling.nervous, panic, breathing.rapidly, sweating

- Intrusion cluster:
  popping.up.stressful.memory, having.nightmares

- Avoidance cluster:
  avoids.people.or.activities, introvert, close.friend

- Impulse coping cluster:
  material.possessions, social.media.addiction, change.in.eating, weight.gain

You may combine clusters to infer broader patterns.

────────────────────────────────────────
PHASE BEHAVIOUR (UPDATED FOR NEW ARCHITECTURE)
────────────────────────────────────────

▶ ASSESSMENT
- User already completed questionnaire
- Keep responses short and focused
- Do NOT ask for more symptoms

▶ DIAGNOSIS
- Provide a concise explanation of the top conditions
- Use bullet points and short sentences
- Include dataset AND non‑dataset disorders
- Use dynamic diagnosis rules and weighting
- No questions

▶ TREATMENT_PATTERN
- Briefly acknowledge the treatment pattern
- Do NOT generate exercises yet
- No questions

▶ THERAPY_TYPE
- Briefly explain why the chosen therapy type fits the pattern
- Keep it simple and supportive
- No questions

▶ THERAPY_EXERCISES
- Provide 2–3 personalised exercises aligned with the chosen therapy type
- Keep explanations short unless the user asks for more detail
- One question allowed at the end

────────────────────────────────────────
IMPORTANT PRINCIPLE
────────────────────────────────────────
The external system controls progression.
You only adapt tone and content to the current phase.
""".trimIndent()

        // ---------------------------------------------------------
// SELECT PROMPT
// ---------------------------------------------------------
        val finalPrompt = if (sessionMode == "quick") quickPrompt else builtDeepPrompt

        Log.e("AI_DEBUG", "-------------------------------")
        Log.e("AI_DEBUG", "FINAL PROMPT SELECTED")
        Log.e("AI_DEBUG", "Mode = $sessionMode")
        Log.e("AI_DEBUG", "Prompt length = ${finalPrompt.length}")
        Log.e("AI_DEBUG", "Prompt preview:\n${finalPrompt.take(2000)}")
        Log.e("AI_DEBUG", "-------------------------------")

// ---------------------------------------------------------
// BUILD OPENAI REQUEST
// ---------------------------------------------------------
        val openAIRequest = mutableListOf<OpenAIMessage>()

        openAIRequest += OpenAIMessage("system", finalPrompt)
        Log.e("AI_DEBUG", "Added SYSTEM message")

        if (sessionMode == "quick") {
            openAIRequest += OpenAIMessage("user", userMessage)
            Log.e("AI_DEBUG", "QUICK MODE → Added USER message: \"$userMessage\"")
        } else {
            val safeHistory = cleanedHistory.takeLast(3)
            openAIRequest += safeHistory
            Log.e("AI_DEBUG", "DEEP MODE → Added ${safeHistory.size} history messages")

            openAIRequest += OpenAIMessage("user", userMessage)
            Log.e("AI_DEBUG", "DEEP MODE → Added USER message: \"$userMessage\"")
        }

// Dump final request structure
        Log.e("AI_DEBUG", "-------------------------------")
        Log.e("AI_DEBUG", "FINAL OPENAI REQUEST STRUCTURE")
        openAIRequest.forEachIndexed { index, msg ->
            Log.e(
                "AI_DEBUG",
                "[$index] role=${msg.role} | length=${msg.content.length} | preview=${msg.content.take(120)}"
            )
        }
        Log.e("AI_DEBUG", "Total messages sent = ${openAIRequest.size}")
        Log.e("AI_DEBUG", "-------------------------------")

// ---------------------------------------------------------
// SEND TO OPENAI
// ---------------------------------------------------------
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.e("AI_DEBUG", "Preparing SDK messages...")

                val sdkMessages = openAIRequest.map {
                    Log.e("AI_DEBUG", "SDK MAP → role=${it.role} | length=${it.content.length}")
                    ChatMessage(
                        role = when (it.role.lowercase()) {
                            "system" -> ChatRole.System
                            "assistant" -> ChatRole.Assistant
                            "user" -> ChatRole.User
                            else -> ChatRole.User
                        },
                        content = it.content
                    )
                }

                Log.e("AI_DEBUG", "Calling OpenAI API...")

                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = sdkMessages,
                        temperature = 0.7
                    )
                )

                Log.e("AI_DEBUG", "OpenAI API returned successfully")

                val replyContent = response.choices.first().message?.content
                Log.e("AI_DEBUG", "Raw AI reply = ${replyContent?.take(500)}")

                val aiReply = replyContent ?: "I'm here with you."

                withContext(Dispatchers.Main) {
                    Log.e("AI_DEBUG", "Posting AI reply to UI")
                    addMessage(aiReply, isUser = false)
                    isFirstAiResponse = false
                    sessionJustLoaded = false
                }

            } catch (e: Exception) {
                Log.e("AI_DEBUG", "❌ OpenAI ERROR: ${e.message}")
                Log.e("AI_DEBUG", Log.getStackTraceString(e))

                withContext(Dispatchers.Main) {
                    addMessage(
                        "I'm having trouble connecting right now. Could you try again?",
                        isUser = false
                    )
                }
            }
        }
    }









        // ---------------------------------------------------------
    // SAVE DIALOG (unchanged)
    // ---------------------------------------------------------
    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tags, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.titleInput)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.tagChipGroup)
        val customTagInput = dialogView.findViewById<EditText>(R.id.customTagInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveTagsButton)

        chipGroup.removeAllViews()

        presetTags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isClickable = true
                setChipBackgroundColorResource(R.color.red_dark)
                setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.white))
                chipStrokeWidth = 2f
                chipStrokeColor = ContextCompat.getColorStateList(this@ChatActivity, R.color.red_dark)
                rippleColor = ContextCompat.getColorStateList(this@ChatActivity, R.color.red)
            }
            chipGroup.addView(chip)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        saveButton.setOnClickListener {
            selectedTags.clear()

            for (i in 0 until chipGroup.childCount) {
                val view = chipGroup.getChildAt(i)
                if (view is Chip && view.isChecked) {
                    selectedTags.add(view.text.toString())
                }
            }

            val customTag = customTagInput.text.toString().trim()
            if (customTag.isNotEmpty()) selectedTags.add(customTag)

            val title = titleInput.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "Please enter a title.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()
            saveSession(title)
        }

        dialog.show()
    }

    // ---------------------------------------------------------
    // SAVE SESSION TO FIREBASE (unchanged)
    private fun saveSession(title: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You must be logged in to save sessions.", Toast.LENGTH_LONG)
                .show()
            return
        }

        val session = TherapySession(
            id = sessionId ?: "",
            title = title,
            tags = selectedTags.toList(),
            messages = messages.toList(),
            timestamp = loadedTimestamp ?: System.currentTimeMillis()
        )


        val ref = db.collection("users")
            .document(user.uid)
            .collection("sessions")

        if (sessionId == null) {
            // NEW SESSION → create document and capture ID
            ref.add(session)
                .addOnSuccessListener { docRef ->
                    sessionId = docRef.id

                    // 🔥 Invalidate insights cache because new therapist messages were added
                    TherapyCache.cachedTherapistMessages = null

                    Toast.makeText(this, "Session saved!", Toast.LENGTH_LONG).show()

                    val intent = Intent(this, TherapyActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error saving session: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }

        } else {
            // EXISTING SESSION → overwrite same document
            ref.document(sessionId!!).set(session)
                .addOnSuccessListener {

                    // 🔥 Invalidate insights cache because session was updated
                    TherapyCache.cachedTherapistMessages = null

                    Toast.makeText(this, "Session updated!", Toast.LENGTH_LONG).show()

                    val intent = Intent(this, TherapyActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(intent)
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error updating session: ${e.message}", Toast.LENGTH_LONG)
                        .show()
                }
        }
    }

    private fun showQuestionnaire(questionList: List<String>) {

        questionnaireContainer.visibility = View.VISIBLE
        inputBar.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // Remove all dynamic rows (keep title at index 0 and submit button at the end)
        for (i in questionnaireLayout.childCount - 1 downTo 1) {
            val view = questionnaireLayout.getChildAt(i)
            if (view.tag is String || view.tag is Pair<*, *>) {
                questionnaireLayout.removeViewAt(i)
            }
        }

        // Build PHQ‑9 / GAD‑7 rows
        questionList.forEachIndexed { index, questionText ->

            val rowTag = "q$index"

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
                // store question id and radioGroup id together in the tag so submit handler can find the RadioGroup directly
                // tag will be set after radioGroup is created below
            }

            val questionLabel = TextView(this).apply {
                text = questionText
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            // PHQ‑9 / GAD‑7 scoring options (0–3)
            val options = listOf(
                "Not at all" to 0,
                "Several days" to 1,
                "More than half the days" to 2,
                "Nearly every day" to 3
            )

            val optionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }

            // Create radio group for single selection and give it a stable generated id
            val radioGroup = RadioGroup(this).apply {
                id = View.generateViewId()
                orientation = RadioGroup.VERTICAL
            }

            options.forEach { (label, score) ->
                val radio = RadioButton(this).apply {
                    text = label
                    tag = score
                    setTextColor(Color.WHITE)
                }

                radio.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {

                        // Emergency trigger for PHQ‑9 item 9
                        if (index == 8 && score >= 1) {
                            showEmergencyDialog()
                        }

                        deepManager.updateAssessmentScore(index, score)
                    }
                }

                radioGroup.addView(radio)
            }

            optionsRow.addView(radioGroup)

            row.addView(questionLabel)
            row.addView(optionsRow)

            // store both the question tag string and the radioGroup id in the row.tag as a Pair
            row.tag = Pair(rowTag, radioGroup.id)

            // Insert above submit button
            questionnaireLayout.addView(row, questionnaireLayout.childCount - 1)
        }
    }



    private fun showDiagnosisReadyDialog() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Diagnosis Ready")
            .setMessage("I have enough information to make a diagnosis. Would you like to continue?")
            .setCancelable(false)
            .setPositiveButton("Yes") { _, _ ->

                // 1. Collect symptoms (keeps the same downstream message semantics)
                val selected = collectSelectedSymptoms()

                // 1b. Build assessmentScores map expected by updateFromAssessmentScores
                // If collectSelectedSymptoms returns tags like "q3", treat them as score>0 (use 1).
                // If you have a canonical source of full scores (RadioGroup), prefer building the full map there.
                val assessmentScores = mutableMapOf<Int, Int>()
                for (tag in selected) {
                    val idx = tag.removePrefix("q").toIntOrNull()
                    if (idx != null) assessmentScores[idx] = 1
                }

                // 2. Update engine using the assessment-based API
                symptomEngine.updateFromAssessmentScores(assessmentScores)

                // 3. Update deep session state
                deepManager.setQuestionnaireCompleted()
                deepManager.advancePhaseIfNeeded()

                // 4. Hide questionnaire UI
                questionnaireContainer.visibility = View.GONE
                inputBar.visibility = View.VISIBLE
                recyclerView.visibility = View.VISIBLE

                enableChatInput()

                // 5. Send diagnosis
                sendDiagnosisMessage(selected)
            }
            .setNegativeButton("No") { d, _ ->
                hasDismissedDiagnosisPopup = true   // 🔥 Prevent popup from showing again
                d.dismiss()
            }
            .show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(Color.parseColor("#4CAF50")) // Green

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(Color.parseColor("#F44336")) // Red
    }


    suspend fun classifyIntent(userMessage: String): String {

        val prompt = """
Classify the user's message into one category. Follow these rules carefully.

PHASE A — Questionnaire
-----------------------
1. SYMPTOM_DESCRIPTION — describing feelings, emotions, problems, or difficulties.

2. YES_TO_QUESTIONNAIRE — agreeing to complete a questionnaire 
   (yes, sure, let's do it).

3. NO_TO_QUESTIONNAIRE — declining 
   (no, not now, maybe later).


PHASE B — Exercise Consent
--------------------------
4. YES_TO_EXERCISES — explicitly agreeing to try therapy exercises 
   (yes, sure, let's do it, sounds good, go ahead, please).
   *Do NOT classify "ok" or "okay" as YES_TO_EXERCISES.*

5. NO_TO_EXERCISES — declining therapy exercises 
   (no, not now, maybe later, don't want to, not today).


PHASE C — Exercise Selection
----------------------------
6. SELECT_EXERCISE — the user chooses a specific exercise.
   This includes ANY message that mentions:
   - the name of an exercise (self‑soothe, wise mind, grounding, check the facts)
   - a numbered exercise (exercise 1, exercise 2, the first one)
   - a description of an exercise (the breathing one, the grounding one)
   - a desire to try a specific exercise (“I want to do the soothing exercise”)

   SELECT_EXERCISE takes priority over YES_TO_EXERCISES.


PHASE D — Exercise Flow
-----------------------
7. EXERCISE_STEP — the user is actively participating in an exercise.
   This includes ANY message where the user:
   - follows instructions from the exercise
   - describes sensory details
   - reflects on feelings as part of the exercise
   - writes a sentence requested by the exercise

   EXERCISE_STEP takes priority over SYMPTOM_DESCRIPTION and ACKNOWLEDGEMENT 
   **only when the user is already inside an exercise**.


PHASE E — Exercise Completion
-----------------------------
8. FINISHED_EXERCISES — the user indicates they have completed the exercise.
   Examples:
   - “I’m done with this exercise”
   - “I finished the exercise”
   - “I completed it”
   - “That’s all for this exercise”
   - “I’m finished for today”
   - “I want to stop the exercise”

   *Do NOT classify these as END_SESSION.*


PHASE F — Session Ending
------------------------
9. END_SESSION — the user wants to end the entire conversation.
   Includes:
   - bye
   - goodbye
   - see you
   - talk later
   - no thanks bye
   - end chat
   - “I want to stop the session”
   - “I want to end the conversation”

   *Do NOT classify exercise‑related endings as END_SESSION.*


PHASE G — Neutral
-----------------
10. ACKNOWLEDGEMENT — neutral confirmation 
    (ok, okay, alright, I see, got it)
    *Do NOT classify these as YES_TO_EXERCISES.*


PHASE H — Other
---------------
11. UNRELATED — anything else.


User message: "$userMessage"

Return ONLY the category name.
""".trimIndent()

        val response = client.chatCompletion(
            ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.System,
                        content = prompt
                    )
                ),
                temperature = 0.0
            )
        )

        val content = response.choices.first().message.content

        return content?.trim() ?: "UNRELATED"
    }



    private fun collectSelectedSymptoms(): List<String> {
        val selected = mutableListOf<String>()

        for (i in 0 until questionnaireLayout.childCount) {
            val row = questionnaireLayout.getChildAt(i)

            if (row.tag is String) {
                val symptomKey = row.tag as String

                val yesBox = row.findViewWithTag<CheckBox>("yes")
                val noBox = row.findViewWithTag<CheckBox>("no")

                if (yesBox?.isChecked == true) {
                    selected.add(symptomKey)
                }
            }
        }

        return selected
    }

    private fun showEmergencyDialog() {
        val message = """
        Thank you for sharing that — it’s really important.

        You’re not alone, and support is available. 
        Here are some options if you need help right now:

        $emergencyContacts
    """.trimIndent()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Your Safety Matters")
            .setMessage(message)
            .setCancelable(true)
            .setPositiveButton("Call 999") { _, _ ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:999")
                }
                startActivity(intent)
            }
            .setNegativeButton("I'm not in immediate danger") { d, _ ->
                d.dismiss()
            }
            .show()

        // Match diagnosis dialog styling
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(Color.parseColor("#F44336")) // Red (urgent)

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            ?.setTextColor(Color.parseColor("#000000")) // Black (neutral)
    }

    fun buildSymptomSummary(selected: List<String>): String {
        if (selected.isEmpty()) {
            return "You didn’t select any symptoms. I’ll continue based on what you’ve shared so far."
        }

        val bulletList = selected.joinToString("\n• ") { it }

        return """
Here’s a quick summary of what you selected:
• $bulletList

I’ll use these to understand what might be going on. Let me know if anything needs changing.
""".trimIndent()
    }

    suspend fun quickDynamicGenerator(prompt: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = prompt
                            )
                        ),
                        temperature = 0.7
                    )
                )

                response.choices.first().message?.content ?: ""
            } catch (e: Exception) {
                Log.e("QuickCBT", "Dynamic CBT generation failed", e)
                ""
            }

        }

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


    private fun sentimentToEmoji(score: Float): String {
        return when {
            score <= -0.6f -> "😢"
            score <= -0.2f -> "😕"
            score <= 0.2f -> "😐"
            score <= 0.6f -> "🙂"
            else -> "😄"
        }
    }

    private fun emojiToMoodIndex(emoji: String): Int {
        return when (emoji) {
            "😢" -> 0
            "😕" -> 1
            "😐" -> 2
            "🙂" -> 3
            "😄" -> 4
            else -> 2
        }
    }







    suspend fun summariseTherapyDefinition(rawText: String, therapyType: String): String {
        val prompt = """
You are a clinical summarization assistant.

Your task:
- Read the therapy definition extracted from an authentic website.
- Produce a **very concise**, clinically accurate summary.
- Begin with **one short sentence** explaining the therapy.
- Then provide **3–4 short bullet points** describing what the therapy involves.
- Use simple, supportive language.
- No hallucinations: only summarise what is in the provided text.
- Do NOT quote the website.
- Do NOT invent new details.
- Do NOT exceed 4 bullet points.

THERAPY TYPE: $therapyType

WEBSITE CONTENT:
$rawText
""".trimIndent()


        val response = client.chatCompletion(
            ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(ChatMessage(ChatRole.User, prompt))
            )
        )

        return response.choices.first().message?.content?.trim()
            ?: "Unable to summarise this therapy type."
    }

    private fun normalizeName(s: String): String =
        s.trim()
            .removeSuffix(".")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s\\-]"), "") // remove stray punctuation except hyphen












}
