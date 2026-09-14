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
import android.os.Handler
import android.os.Looper
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


    private var latestAssessmentScores: Map<Int, Int> = emptyMap()

    private var latestSelectedSymptoms: List<String> = emptyList()
    private var loadedTimestamp: Long? = null





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
            Log.d("DeepButton", "DeepSessionManager reset | deepManagerHash=${deepManager.hashCode()}")

            // Start in WAITING_FOR_USER_SYMPTOMS (ensure resetConversation sets phase)
            deepManager.reset()
            Log.d("DeepButton", "After resetConversation | phase=${deepManager.phase} | contextEmotion=${deepManager.contextEmotion} | contextCause=${deepManager.contextCause} | selectedQuestionnaire=${deepManager.selectedQuestionnaire?.name ?: "null"}")

            // === ESSENTIAL STARTUP LOG (added) ===
            Log.i("DeepButton", "deepManager created | hash=${deepManager.hashCode()} | phase=${deepManager.phase}")

            // =====================================================
            // RESET SYMPTOM ENGINE
            // =====================================================
            symptomEngine = SymptomCollectionEngine(featureIndexMap, featureCols)
            Log.d("DeepButton", "Symptom engine reset | symptomEngineHash=${symptomEngine.hashCode()}")

            // =====================================================
            // RESET SESSION TRACKING FLAGS
            // =====================================================
            isFirstAiResponse = true
            sessionJustLoaded = false
            openedFromInsightsCard = false

            Log.d("DeepButton", "Session flags reset | isFirstAiResponse=$isFirstAiResponse sessionJustLoaded=$sessionJustLoaded openedFromInsightsCard=$openedFromInsightsCard")

            enableChatInput()

            // =====================================================
            // FIRST THERAPIST-STYLE MESSAGE
            // =====================================================
            Log.d("DeepButton", "About to send first AI message | deepManagerHash=${deepManager.hashCode()} | phase=${deepManager.phase}")
            addMessage(
                "I'm here with you. What’s been troubling you lately?",
                isUser = false
            )
            Log.d("DeepButton", "First AI message sent")
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

            // -----------------------------------------------------
            // Extract scores
            // -----------------------------------------------------
            for (i in 0 until questionnaireLayout.childCount) {
                val row = questionnaireLayout.getChildAt(i)
                val tagObj = row.tag
                if (tagObj !is Pair<*, *>) continue

                val rowTag = tagObj.first as? String
                val radioGroupId = tagObj.second as? Int
                if (rowTag == null || radioGroupId == null) continue

                val radioGroup = row.findViewById<RadioGroup>(radioGroupId)
                val score = extractScoreFromRadioGroup(radioGroup)

                if (score > 0) selectedSymptoms.add(rowTag)

                val index = rowTag.removePrefix("q").toIntOrNull()
                if (index != null) {
                    assessmentScores[index] = score
                    deepManager.updateAssessmentScore(index, score)
                }
            }

            // -----------------------------------------------------
            // Update engine + deep model
            // -----------------------------------------------------
            val questionnaire = deepManager.selectedQuestionnaire!!

            symptomEngine.updateFromAssessmentScores(
                assessmentScores,
                questionnaire
            )

            val symptomVector = symptomEngine.buildModelInputVector()
            val result = onnxRunner.runDeepSession(symptomVector)

            symptomEngine.updatePhq9Prediction(result.phq9)
            symptomEngine.updateGad7Prediction(result.gad7)
            symptomEngine.updateEmotion(result.emotionVector)

            // -----------------------------------------------------
            // Store results
            // -----------------------------------------------------
            latestAssessmentScores = assessmentScores.toMap()
            latestSelectedSymptoms = selectedSymptoms.toList()

            deepManager.setQuestionnaireCompleted()

            // -----------------------------------------------------
            // Send questionnaire summary
            // -----------------------------------------------------
            val answerSummary = buildAssessmentAnswerSummary(
                questionnaire = questionnaire,
                scores = latestAssessmentScores
            )

            addMessage(answerSummary, isUser = false)

            // -----------------------------------------------------
            // NEW LOGIC: Switch to extended PHQ‑9 sub‑state
            // -----------------------------------------------------
            if (questionnaire == QuestionnaireType.PHQ9) {

                val phq9Score = symptomEngine.computePhq9Score()

                if (phq9Score >= 5) {
                    // Switch into extended assessment mode
                    deepManager.assessmentMode = AssessmentMode.EXTENDED_PHQ9
                    deepManager.extendedQuestionIndex = 0

                    // Hide questionnaire UI
                    questionnaireContainer.visibility = View.GONE
                    inputBar.visibility = View.VISIBLE
                    recyclerView.visibility = View.VISIBLE
                    enableChatInput()

                    // Start conversational ACCHA questions
                    sendNextExtendedAssessmentQuestion()
                    return@setOnClickListener
                }
            }

            // -----------------------------------------------------
            // If not extended PHQ‑9 → continue normally
            // -----------------------------------------------------
            deepManager.advancePhaseIfNeeded()

            questionnaireContainer.visibility = View.GONE
            inputBar.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            enableChatInput()
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

        // Top of processUserMessage coroutine
        Log.d("ProcessUserMessage", "ENTER handler | userMessage='${userMessage.take(200)}' | deepManagerPhase=${deepManager.phase}")


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
                    // EXTENDED PHQ‑9 conversational mode (intercepts messages)
                    // -----------------------------------------------------
                    if (deepManager.assessmentMode == AssessmentMode.EXTENDED_PHQ9) {

                        handleExtendedAssessmentResponse(userMessage)

                        // ❗ Only return if we are STILL in extended mode
                        if (deepManager.assessmentMode == AssessmentMode.EXTENDED_PHQ9) {
                            return@launch
                        }

                        // ❗ If assessmentMode changed to STANDARD,
                        // it means extended assessment has finished.
                        // Allow router to continue so DIAGNOSIS runs immediately.
                    }

                    // -----------------------------------------------------
                    // NORMAL DEEP SESSION FLOW BEGINS HERE
                    // -----------------------------------------------------

                    // -----------------------------------------------------
                    // AUTO-GENERATE EXERCISES
                    // -----------------------------------------------------
                    // This phase is system-driven. An empty userMessage is
                    // an internal trigger and must NOT go through intent
                    // classification.

                    if (deepManager.phase == DeepPhase.THERAPY_EXERCISES &&
                        userMessage.isBlank() &&
                        !deepManager.exercisesDelivered()
                    ) {

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Entering THERAPY_EXERCISES — generating exercise list"
                        )

                        val disorder = lastDiagnosis ?: "general_distress"
                        val therapyType = deepManager.therapyType ?: "general"

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Starting exercise generation. disorder=$disorder, therapyType=$therapyType"
                        )

                        // ---------------------------------------------------------
                        // 1. GENERATE EXERCISES
                        // ---------------------------------------------------------

                        val exerciseNames = withContext(Dispatchers.IO) {

                            MultiTherapyExerciseGenerator().generateExercises(
                                therapyType = therapyType,
                                disorder = disorder,
                                symptoms = symptomEngine.getSymptoms(),
                                userMessage = "",
                                treatmentPattern = deepManager.getTreatmentPattern(
                                    disorder,
                                    symptomEngine.getSymptoms(),
                                    5f, 5f, 5f, 5f, 5f,
                                    80f,
                                    "neutral"
                                ),
                                client = client,

                                dynamicGenerator = { prompt: String ->

                                    val response = client.chatCompletion(
                                        ChatCompletionRequest(
                                            model = ModelId("gpt-4o-mini"),
                                            messages = listOf(
                                                ChatMessage(
                                                    ChatRole.User,
                                                    prompt
                                                )
                                            )
                                        )
                                    )

                                    response.choices
                                        .first()
                                        .message
                                        ?.content
                                        ?: ""
                                },

                                deepManager = deepManager,
                                context = this@ChatActivity
                            )
                        }

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Generated exercise names: ${exerciseNames.joinToString()}"
                        )

                        // ---------------------------------------------------------
                        // 2. ENSURE FULL EXERCISE OBJECTS ARE CACHED
                        // ---------------------------------------------------------

                        val cachedFull = deepManager.generatedDbtExercises

                        if (cachedFull == null || cachedFull.isEmpty()) {

                            Log.w(
                                "THERAPY_EXERCISES",
                                "Generator did not cache full exercise objects"
                            )

                            val fallbackExercises = exerciseNames.map { name ->

                                DbtExercise(
                                    name = name,
                                    description = name,
                                    url = "https://example.com/exercises/" +
                                            "${normalizeName(name).replace(" ", "-").lowercase()}",
                                    steps = listOf(
                                        "Step 1 for $name"
                                    )
                                )
                            }

                            deepManager.generatedDbtExercises = fallbackExercises

                            Log.d(
                                "THERAPY_EXERCISES",
                                "Cached fallback full exercises: " +
                                        "${fallbackExercises.map { it.name }}"
                            )

                        } else {

                            Log.d(
                                "THERAPY_EXERCISES",
                                "Cached full exercises: " +
                                        "${cachedFull.map { it.name }}"
                            )
                        }

                        // ---------------------------------------------------------
                        // 3. MAKE SURE GENERATION ACTUALLY RETURNED EXERCISES
                        // ---------------------------------------------------------

                        if (exerciseNames.isEmpty()) {

                            Log.e(
                                "THERAPY_EXERCISES",
                                "❌ No exercises generated — cannot display exercise"
                            )

                            addMessage(
                                text = "I’m having trouble preparing an exercise right now. Please try again in a moment.",
                                isUser = false,
                                type = MessageType.NORMAL
                            )

                            return@launch
                        }

                        // ---------------------------------------------------------
                        // 4. STORE GENERATED EXERCISE NAMES
                        // ---------------------------------------------------------

                        try {

                            deepManager.setGeneratedExercises(exerciseNames)

                            Log.d(
                                "THERAPY_EXERCISES",
                                "Generated exercises stored successfully"
                            )

                        } catch (e: Exception) {

                            Log.e(
                                "THERAPY_EXERCISES",
                                "setGeneratedExercises failed",
                                e
                            )
                        }

                        // ---------------------------------------------------------
                        // 5. GET THE MOST SUITABLE EXERCISE
                        //
                        // The generator returns exercises in suitability order,
                        // so the first exercise is the recommended one.
                        // ---------------------------------------------------------

                        val bestExercise = deepManager.generatedDbtExercises
                            ?.firstOrNull()

                        if (bestExercise == null) {

                            Log.e(
                                "THERAPY_EXERCISES",
                                "❌ Could not retrieve the first full exercise object"
                            )

                            addMessage(
                                text = "I’m having trouble preparing an exercise right now. Please try again in a moment.",
                                isUser = false,
                                type = MessageType.NORMAL
                            )

                            return@launch
                        }

                        // ---------------------------------------------------------
                        // 6. BUILD CONCISE EXERCISE MESSAGE
                        // ---------------------------------------------------------

                        val exerciseMessage = buildString {

                            append("I think this exercise may be a good place to start:\n\n")

                            append("${bestExercise.name}\n\n")

                            if (bestExercise.description.isNotBlank()) {
                                append("${bestExercise.description}\n\n")
                            }

                            if (bestExercise.steps.isNotEmpty()) {

                                append("What you'll do:\n")

                                bestExercise.steps.forEachIndexed { index, step ->
                                    append("${index + 1}. $step\n")
                                }
                            }

                            append("\nWhen you're ready, choose this exercise and we'll go through it together.")
                        }

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Displaying best exercise: ${bestExercise.name}"
                        )

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Description: ${bestExercise.description}"
                        )

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Steps: ${bestExercise.steps.joinToString()}"
                        )

                        // ---------------------------------------------------------
                        // 7. DIRECTLY DISPLAY THE BEST EXERCISE
                        //
                        // Do NOT send this through OpenAI.
                        // ---------------------------------------------------------

                        addMessage(
                            text = exerciseMessage,
                            isUser = false,
                            type = MessageType.NORMAL
                        )

                        // ---------------------------------------------------------
                        // 8. ONLY NOW MARK EXERCISES AS DELIVERED
                        // ---------------------------------------------------------

                        deepManager.setExercisesDelivered()

                        Log.d(
                            "THERAPY_EXERCISES",
                            "Best exercise displayed successfully → exercisesDelivered=true"
                        )

                        return@launch
                    }

                    // -----------------------------------------------------
                    // INTENT HANDLER
                    // -----------------------------------------------------

                    val intent = classifyIntent(userMessage, deepManager.phase)

                    Log.d("ExerciseFlow", "Intent = $intent")
                    Log.d("ExerciseFlow", "Phase = ${deepManager.phase}")
                    Log.d(
                        "ExerciseFlow",
                        "Exercises delivered = ${deepManager.exercisesDelivered()}"
                    )


// -----------------------------------------------------
// END SESSION (global)
// -----------------------------------------------------
                    if (intent == "END_SESSION") {
                        Log.d("ExerciseFlow", "END_SESSION detected → moving to SESSION_COMPLETE")
                        deepManager.setSessionComplete()
                        processUserMessage("")
                        return@launch
                    }




// -----------------------------------------------------
// FINISH EXERCISES → SESSION_COMPLETE
// (only valid during EXERCISE_GUIDANCE)
// -----------------------------------------------------
                    if (deepManager.phase == DeepPhase.EXERCISE_GUIDANCE &&
                        intent == "FINISHED_EXERCISES") {

                        Log.d("ExerciseFlow", "FINISHED_EXERCISES detected → transitioning to SESSION_COMPLETE")

                        deepManager.phase = DeepPhase.SESSION_COMPLETE
                        processUserMessage("")   // triggers the SESSION_COMPLETE block

                        return@launch
                    }

// -----------------------------------------------------
// ANOTHER EXERCISE? — AFTER COMPLETION
// -----------------------------------------------------
                    if (
                        deepManager.phase == DeepPhase.THERAPY_EXERCISES &&
                        deepManager.waitingForAnotherExercise
                    ) {

                        // Tokenise message safely (prevents "another" from matching "no")
                        val tokens = userMessage
                            .lowercase()
                            .split(" ", ",", ".", "!", "?", ";", ":")

                        val wantsAnother =
                            intent == "SELECT_EXERCISE" ||
                                    tokens.contains("yes") ||
                                    tokens.contains("another") ||
                                    tokens.contains("next") ||
                                    tokens.contains("sure")

                        val wantsToFinish =
                            intent == "END_SESSION" ||
                                    tokens.contains("no") ||
                                    tokens.contains("finish") ||
                                    tokens.contains("done")

                        if (wantsToFinish) {

                            Log.d("EXERCISE_FLOW", "User does not want another exercise → SESSION_COMPLETE")

                            deepManager.waitingForAnotherExercise = false
                            deepManager.setSessionComplete()

                            processUserMessage("")
                            return@launch
                        }

                        if (wantsAnother) {

                            Log.d("EXERCISE_FLOW", "User wants another exercise → showing remaining exercises")

                            deepManager.waitingForAnotherExercise = false

                            // Get remaining exercises (excludes completed ones)
                            val remainingExercises = deepManager.remainingExercises()

                            Log.d("EXERCISE_FLOW", "Remaining exercises: ${remainingExercises.map { it.name }}")

                            // If none remain → finish session
                            if (remainingExercises.isEmpty()) {

                                Log.d("EXERCISE_FLOW", "No remaining exercises → SESSION_COMPLETE")

                                deepManager.setSessionComplete()
                                processUserMessage("")
                                return@launch
                            }

                            // Show remaining exercises
                            val exerciseMessage = buildString {
                                append("Absolutely. Here are the other exercises you can try:\n\n")
                                remainingExercises.forEachIndexed { index, exercise ->
                                    append("${index + 1}. ${exercise.name}\n")
                                    append("${exercise.description}\n\n")
                                }
                                append("Choose one you'd like to try.")
                            }

                            addMessage(
                                text = exerciseMessage,
                                isUser = false,
                                type = MessageType.NORMAL
                            )

                            return@launch
                        }

                        // If unclear → ask again
                        addMessage(
                            text = "Would you like to try another exercise, or finish the session?",
                            isUser = false,
                            type = MessageType.NORMAL
                        )

                        return@launch
                    }

                    // -----------------------------------------------------
                    // EXERCISE SELECTION (THERAPY_EXERCISES)
                    // -----------------------------------------------------
                    if (deepManager.phase == DeepPhase.THERAPY_EXERCISES &&
                        deepManager.exercisesDelivered() &&
                        (
                                intent == "SELECT_EXERCISE" ||
                                        deepManager.detectChosenExercise(userMessage) != null
                                )
                    ) {

                        Log.d("EXERCISE_TRANSITION", "Exercise selection triggered")
                        Log.d("EXERCISE_TRANSITION", "intent=$intent")
                        Log.d("EXERCISE_TRANSITION", "userMessage=\"$userMessage\"")

                        // ---------------------------------------------------------
                        // 1. Detect the FULL selected exercise object
                        // ---------------------------------------------------------

                        val selectedExercise =
                            deepManager.detectChosenExerciseObject(userMessage)

                        Log.d(
                            "EXERCISE_TRANSITION",
                            "selectedExercise=${selectedExercise?.name}"
                        )

                        // ---------------------------------------------------------
                        // 2. Make sure an exercise was actually selected
                        // ---------------------------------------------------------

                        if (selectedExercise == null) {

                            Log.e(
                                "EXERCISE_TRANSITION",
                                "❌ Could not identify selected exercise"
                            )

                            addMessage(
                                text = "I couldn't tell which exercise you'd like to try. Please tell me the exercise name.",
                                isUser = false,
                                type = MessageType.NORMAL
                            )

                            return@launch
                        }

                        // ---------------------------------------------------------
                        // 3. Start the selected exercise
                        // ---------------------------------------------------------

                        deepManager.beginExercise(selectedExercise)

                        Log.d(
                            "EXERCISE_TRANSITION",
                            "beginExercise() → " +
                                    "name=${deepManager.currentExerciseName}, " +
                                    "step=${deepManager.currentExerciseStep}, " +
                                    "steps=${deepManager.currentExerciseSteps.size}"
                        )

                        // ---------------------------------------------------------
                        // 4. Generate FIRST STEP
                        // ---------------------------------------------------------

                        val firstStep = withContext(Dispatchers.IO) {

                            try {

                                deepManager.generateExerciseStep(
                                    exerciseName = selectedExercise.name,
                                    userInput = "",
                                    disorder = lastDiagnosis ?: "general_distress",
                                    symptoms = symptomEngine.getSymptoms(),
                                    steps = selectedExercise.steps,
                                    stepIndex = deepManager.currentExerciseStep,
                                    client = client
                                )

                            } catch (e: Exception) {

                                Log.e(
                                    "EXERCISE_TRANSITION",
                                    "Error generating first step",
                                    e
                                )

                                selectedExercise.steps.firstOrNull()
                                    ?: "Let's begin by taking a moment to focus on this exercise."
                            }
                        }

                        // ---------------------------------------------------------
                        // 5. Display FIRST STEP
                        // ---------------------------------------------------------

                        addMessage(
                            text = firstStep,
                            isUser = false,
                            type = MessageType.NORMAL
                        )

                        Log.d(
                            "EXERCISE_TRANSITION",
                            "First step displayed for ${selectedExercise.name}"
                        )

                        return@launch
                    }


                    // -----------------------------------------------------
                    // 1. PHASE SYSTEM (diagnostic + defensive)
                    // -----------------------------------------------------
                    Log.d("DeepSession", "Current phase: ${deepManager.phase} | session=${deepManager::class.simpleName}")

                    when (deepManager.phase) {

                        DeepPhase.CONTEXT_INTAKE -> {

                            Log.d(
                                "DeepSession",
                                "Entering CONTEXT_INTAKE | " +
                                        "contextEmotion=${deepManager.contextEmotion} | " +
                                        "contextCause=${deepManager.contextCause}"
                            )

                            // -----------------------------------------------------
                            // STEP 1 — Detect emotion
                            // -----------------------------------------------------
                            if (deepManager.contextEmotion == null) {

                                Log.d(
                                    "DeepSession",
                                    "No contextEmotion found. Running symptomEngine.detectEmotion " +
                                            "on userMessage='$userMessage'"
                                )

                                Log.i(
                                    "DeepSession",
                                    "Calling symptomEngine.detectEmotion | " +
                                            "text='${userMessage.take(200)}' | " +
                                            "deepManagerHash=${deepManager.hashCode()}"
                                )

                                val emotionResultRaw: Any? = try {
                                    symptomEngine.detectEmotion(userMessage, client)
                                } catch (e: Exception) {
                                    Log.e(
                                        "DeepSession",
                                        "detectEmotion threw exception",
                                        e
                                    )
                                    null
                                }

                                if (emotionResultRaw == null) {

                                    Log.w(
                                        "DeepSession",
                                        "detectEmotion returned null. Sending gentle re-probe."
                                    )

                                    addMessage(
                                        "I hear you. Could you share a little more about how you're feeling right now?",
                                        isUser = false
                                    )

                                    return@launch
                                }

                                val detectedEmotion = when (emotionResultRaw) {

                                    is String -> emotionResultRaw

                                    is Map<*, *> -> {
                                        (emotionResultRaw["label"] as? String)
                                            ?: (emotionResultRaw["emotion"] as? String)
                                            ?: emotionResultRaw.toString()
                                    }

                                    else -> emotionResultRaw.toString()
                                }

                                Log.d(
                                    "DeepSession",
                                    "Detected emotion='$detectedEmotion'"
                                )

                                deepManager.contextEmotion = detectedEmotion

                                // -----------------------------------------------------
                                // Crisis detection
                                // -----------------------------------------------------
                                val crisisKeywords = listOf(
                                    "suicide",
                                    "kill myself",
                                    "end it",
                                    "can't go on",
                                    "hopeless",
                                    "pointless",
                                    "empty",
                                    "worthless",
                                    "die",
                                    "self harm"
                                )

                                val crisisDetected = crisisKeywords.any { keyword ->
                                    userMessage.lowercase().contains(keyword)
                                }

                                if (crisisDetected) {

                                    Log.w(
                                        "DeepSession",
                                        "Crisis detected in intake phase"
                                    )

                                    val strongRepeat =
                                        deepManager.isStrongCrisisMessage(userMessage)

                                    if (
                                        strongRepeat ||
                                        deepManager.shouldTriggerCrisisPopup()
                                    ) {
                                        showEmergencyDialog()
                                    } else {
                                        Log.w(
                                            "DeepSession",
                                            "Crisis popup suppressed due to cooldown"
                                        )
                                    }

                                    // Do NOT stop the session.
                                    // Continue the intake flow normally.
                                }

                                // -----------------------------------------------------
                                // Empathetic reflection
                                // -----------------------------------------------------
                                addMessage(
                                    "Thank you for sharing that. I’d like to understand what’s been contributing to how you’re feeling.",
                                    isUser = false
                                )


                                Log.i(
                                    "DeepSession",
                                    "ProbeSent: ask_for_cause | emotion=$detectedEmotion"
                                )

                                return@launch
                            }

                            // -----------------------------------------------------
                            // STEP 2 — Detect cause
                            // -----------------------------------------------------
                            if (deepManager.contextCause == null) {

                                Log.d(
                                    "DeepSession",
                                    "No contextCause found. Treating current userMessage " +
                                            "as cause: '$userMessage'"
                                )

                                val trimmed = userMessage.trim()

                                val ambiguousReplies = setOf(
                                    "ok",
                                    "yes",
                                    "no",
                                    "fine"
                                )

                                if (
                                    trimmed.length < 3 ||
                                    trimmed.lowercase() in ambiguousReplies
                                ) {

                                    Log.w(
                                        "DeepSession",
                                        "User reply too short/ambiguous to be a cause. Re-probing."
                                    )

                                    addMessage(
                                        "I hear you. Could you tell me a bit more about what’s been " +
                                                "making you feel ${deepManager.contextEmotion}?",
                                        isUser = false
                                    )

                                    return@launch
                                }

                                // -----------------------------------------------------
                                // Store cause and summary
                                // -----------------------------------------------------
                                deepManager.contextCause = userMessage.trim()
                                deepManager.contextSummary =
                                    "User is feeling ${deepManager.contextEmotion} due to recent stressors."


                                Log.d(
                                    "DeepSession",
                                    "Set contextCause and contextSummary | " +
                                            "cause='${deepManager.contextCause}'"
                                )

                                // -----------------------------------------------------
                                // Crisis detection based on cause
                                // -----------------------------------------------------
                                val crisisKeywords = listOf(
                                    "suicide",
                                    "kill myself",
                                    "end it",
                                    "can't go on",
                                    "hopeless",
                                    "pointless",
                                    "empty",
                                    "worthless",
                                    "die",
                                    "self harm"
                                )

                                val crisisDetected = crisisKeywords.any { keyword ->
                                    userMessage.lowercase().contains(keyword)
                                }

                                if (crisisDetected) {

                                    Log.w(
                                        "DeepSession",
                                        "Crisis detected in intake phase"
                                    )

                                    val strongRepeat =
                                        deepManager.isStrongCrisisMessage(userMessage)

                                    if (
                                        strongRepeat ||
                                        deepManager.shouldTriggerCrisisPopup()
                                    ) {
                                        showEmergencyDialog()
                                    } else {
                                        Log.w(
                                            "DeepSession",
                                            "Crisis popup suppressed due to cooldown"
                                        )
                                    }

                                    // Do NOT stop the session.
                                    // Continue the intake flow normally.
                                }

                                // -----------------------------------------------------
                                // Cause classification
                                // -----------------------------------------------------
                                val causeLower = userMessage.lowercase()

                                val causeCategory = when {

                                    listOf(
                                        "empty",
                                        "pointless",
                                        "meaningless",
                                        "nothing matters"
                                    ).any { causeLower.contains(it) } ->
                                        "existential distress"

                                    listOf(
                                        "hopeless",
                                        "no future",
                                        "can't go on"
                                    ).any { causeLower.contains(it) } ->
                                        "hopelessness"

                                    listOf(
                                        "tired",
                                        "exhausted",
                                        "burnt out",
                                        "burnout"
                                    ).any { causeLower.contains(it) } ->
                                        "burnout"

                                    listOf(
                                        "alone",
                                        "lonely",
                                        "no one",
                                        "isolated"
                                    ).any { causeLower.contains(it) } ->
                                        "loneliness"

                                    listOf(
                                        "relationship",
                                        "breakup",
                                        "partner",
                                        "family"
                                    ).any { causeLower.contains(it) } ->
                                        "relationship stress"

                                    listOf(
                                        "school",
                                        "uni",
                                        "grades",
                                        "exam",
                                        "workload"
                                    ).any { causeLower.contains(it) } ->
                                        "academic pressure"

                                    listOf(
                                        "trauma",
                                        "past",
                                        "abuse",
                                        "violence"
                                    ).any { causeLower.contains(it) } ->
                                        "trauma-related distress"

                                    listOf(
                                        "worthless",
                                        "failure",
                                        "hate myself"
                                    ).any { causeLower.contains(it) } ->
                                        "self-worth issues"

                                    else ->
                                        "general emotional distress"
                                }

                                deepManager.contextCauseCategory = causeCategory

                                Log.d(
                                    "DeepSession",
                                    "Cause category classified as '$causeCategory'"
                                )

                                // -----------------------------------------------------
                                // Empathetic reflection
                                // -----------------------------------------------------
                                addMessage(
                                    "Thank you for explaining that. It sounds like you're dealing with $causeCategory, and I want to support you through this.",
                                    isUser = false
                                )


                                // -----------------------------------------------------
                                // Questionnaire selection
                                // -----------------------------------------------------
                                deepManager.selectAndSetQuestionnaireIfNeeded()

                                Log.d(
                                    "DeepSession",
                                    "After selectAndSetQuestionnaireIfNeeded | " +
                                            "selectedQuestionnaire=" +
                                            "${deepManager.selectedQuestionnaire?.name ?: "null"}"
                                )

                                val questionnaire = deepManager.selectedQuestionnaire

                                if (questionnaire == null) {

                                    Log.w(
                                        "DeepSession",
                                        "No questionnaire selected. Sending fallback message " +
                                                "and keeping intake phase active."
                                    )

                                    addMessage(
                                        "I'm preparing the right questionnaire for you. One moment…",
                                        isUser = false
                                    )

                                    return@launch
                                }

                                val questionnaireName = questionnaire.name

                                // -----------------------------------------------------
                                // Single combined questionnaire intro (no duplicates)
                                // -----------------------------------------------------
                                addMessage(
                                    "Based on what you've shared, we'll continue with the $questionnaireName. You can begin whenever you're ready.",
                                    isUser = false
                                )


                                // -----------------------------------------------------
                                // Transition to ASSESSMENT
                                // -----------------------------------------------------
                                Log.i(
                                    "DeepSession",
                                    "Advancing phase after cause collected"
                                )

                                deepManager.advancePhaseIfNeeded()

                                return@launch
                            }
                        }

                        DeepPhase.ASSESSMENT -> {

                            Log.d(
                                "DeepSession",
                                "Entering ASSESSMENT | " +
                                        "selectedQuestionnaire=${deepManager.selectedQuestionnaire?.name ?: "null"}"
                            )

                            // -----------------------------------------------------
                            // Safety check
                            // -----------------------------------------------------
                            val questionnaire = deepManager.selectedQuestionnaire

                            if (questionnaire == null) {

                                Log.w(
                                    "DeepSession",
                                    "ASSESSMENT entered but selectedQuestionnaire is null."
                                )

                                addMessage(
                                    "I'm preparing the right questionnaire for you. One moment…",
                                    isUser = false
                                )

                                return@launch
                            }

                            // -----------------------------------------------------
                            // Present the selected questionnaire
                            // -----------------------------------------------------
                            when (questionnaire) {

                                QuestionnaireType.PHQ9 -> {

                                    Log.d(
                                        "DeepSession",
                                        "ASSESSMENT: Presenting PHQ-9"
                                    )

                                    showQuestionnaire(
                                        QuestionnaireData.phq9
                                    )
                                }

                                QuestionnaireType.GAD7 -> {

                                    Log.d(
                                        "DeepSession",
                                        "ASSESSMENT: Presenting GAD-7"
                                    )

                                    showQuestionnaire(
                                        QuestionnaireData.gad7
                                    )
                                }
                            }

                            Log.d(
                                "DeepSession",
                                "ASSESSMENT questionnaire displayed successfully: ${questionnaire.name}"
                            )

                            // -----------------------------------------------------
                            // Do NOT advance the phase here.
                            //
                            // The questionnaire submit button handles the user's
                            // responses and the transition to the next phase.
                            // -----------------------------------------------------

                            return@launch
                        }

                        DeepPhase.DIAGNOSIS -> {

                            Log.e(
                                "PHASE_TRACE",
                                ">>> ENTERING DIAGNOSIS PHASE <<< " +
                                        "| diagnosisDelivered=${deepManager.diagnosisDelivered} " +
                                        "| phase=${deepManager.phase} " +
                                        "| userMessage=\"$userMessage\""
                            )

                            // -----------------------------------------------------
                            // GUARD — Prevent repeated diagnosis generation
                            // -----------------------------------------------------
                            if (deepManager.diagnosisDelivered) {

                                Log.w(
                                    "DiagnosisDebug",
                                    "DIAGNOSIS already delivered — skipping duplicate diagnosis generation."
                                )

                                Log.e(
                                    "PHASE_TRACE",
                                    ">>> EXITING DIAGNOSIS (already delivered) <<<"
                                )

                                return@launch
                            }

                            Log.d(
                                "DeepSession",
                                "Entering DIAGNOSIS | " +
                                        "questionnaire=${deepManager.selectedQuestionnaire?.name ?: "null"} | " +
                                        "scores=$latestAssessmentScores"
                            )

                            // -----------------------------------------------------
                            // Safety check — questionnaire must exist
                            // -----------------------------------------------------
                            val questionnaire = deepManager.selectedQuestionnaire

                            if (questionnaire == null) {
                                Log.w(
                                    "DeepSession",
                                    "DIAGNOSIS entered but selectedQuestionnaire is null."
                                )

                                addMessage(
                                    "I couldn't determine which questionnaire was completed. Please try again.",
                                    isUser = false
                                )

                                Log.e(
                                    "PHASE_TRACE",
                                    ">>> EXITING DIAGNOSIS (null questionnaire) <<<"
                                )

                                return@launch
                            }

                            // -----------------------------------------------------
                            // Safety check — scores must exist
                            // -----------------------------------------------------
                            if (latestAssessmentScores.isEmpty()) {
                                Log.w(
                                    "DeepSession",
                                    "DIAGNOSIS entered but no assessment scores were stored."
                                )

                                addMessage(
                                    "I couldn't find your questionnaire responses. Please complete the questionnaire again.",
                                    isUser = false
                                )

                                Log.e(
                                    "PHASE_TRACE",
                                    ">>> EXITING DIAGNOSIS (empty scores) <<<"
                                )

                                return@launch
                            }

                            // -----------------------------------------------------
                            // The questionnaire summary is sent by the submit handler.
                            // This phase only generates the diagnosis interpretation.
                            // -----------------------------------------------------

                            Log.e(
                                "DiagnosisDebug",
                                "Sending diagnosis message | " +
                                        "diagnosisDelivered(beforeSend)=${deepManager.diagnosisDelivered} " +
                                        "| symptoms=$latestSelectedSymptoms"
                            )

                            sendDiagnosisMessage(latestSelectedSymptoms)

                            Log.e(
                                "PHASE_TRACE",
                                "Diagnosis generation initiated. Waiting for AI delivery callback."
                            )

                            Log.e(
                                "PHASE_TRACE",
                                ">>> EXITING DIAGNOSIS PHASE <<<"
                            )

                            return@launch
                        }


                        DeepPhase.TREATMENT_PATTERN -> {

                            Log.d(
                                "DeepSession",
                                "Entering TREATMENT_PATTERN | lastDiagnosis=$lastDiagnosis"
                            )

                            // -----------------------------------------------------
                            // Lock user input while background processing occurs
                            // -----------------------------------------------------

                            isUserInputLocked = true

                            try {

                                // -----------------------------------------------------
                                // 1. Get current disorder
                                // -----------------------------------------------------

                                val disorder = lastDiagnosis ?: "general_distress"

                                // -----------------------------------------------------
                                // 2. Get confirmed symptoms
                                // -----------------------------------------------------

                                val symptoms = symptomEngine.getSymptoms()

                                Log.d(
                                    "DeepSession",
                                    "Generating treatment pattern | " +
                                            "disorder=$disorder | " +
                                            "symptomCount=${symptomEngine.getSymptomCount()}"
                                )

                                // -----------------------------------------------------
                                // 3. Generate treatment pattern
                                // -----------------------------------------------------

                                val pattern = withContext(Dispatchers.IO) {

                                    deepManager.getTreatmentPattern(
                                        disorder = disorder,
                                        symptoms = symptoms,
                                        mood = 5f,
                                        sleep = 5f,
                                        activity = 5f,
                                        stress = 5f,
                                        progress = 5f,
                                        adherence = 80f,
                                        emotion = symptomEngine.emotionLabel
                                    )
                                }

                                Log.d(
                                    "DeepSession",
                                    "Treatment pattern generated | " +
                                            "cluster=${pattern.cluster} | " +
                                            "focus=${pattern.focus} | " +
                                            "methods=${pattern.methods.joinToString()}"
                                )

                                // -----------------------------------------------------
                                // 4. Store treatment pattern
                                // -----------------------------------------------------

                                deepManager.setLastTreatmentPattern(pattern)

                                // -----------------------------------------------------
                                // 5. Mark treatment pattern as complete
                                // -----------------------------------------------------

                                deepManager.setPatternDelivered()

                                Log.d(
                                    "DeepSession",
                                    "Treatment pattern stored and marked as delivered"
                                )

                                // -----------------------------------------------------
                                // 6. Advance immediately
                                //
                                // TREATMENT_PATTERN is background-only.
                                // No user-facing message is generated here.
                                // -----------------------------------------------------

                                deepManager.advancePhaseIfNeeded()

                                Log.d(
                                    "DeepSession",
                                    "Treatment pattern complete → phase=${deepManager.phase}"
                                )

                                // -----------------------------------------------------
                                // 7. Continue immediately into the next background
                                // phase.
                                //
                                // If THERAPY_TYPE is also background-only, process it
                                // here rather than waiting for user input.
                                // -----------------------------------------------------

                                processUserMessage("")

                            } catch (e: Exception) {

                                Log.e(
                                    "DeepSession",
                                    "Error generating treatment pattern",
                                    e
                                )

                                addMessage(
                                    "I’m having a little trouble preparing the next part of your support plan. " +
                                            "Please give me a moment and try again.",
                                    isUser = false
                                )

                            } finally {

                                // -----------------------------------------------------
                                // Unlock only after the background transition has
                                // finished processing.
                                // -----------------------------------------------------

                                isUserInputLocked = false
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

                            isUserInputLocked = false

                            // ---------------------------------------------------------
                            // 5. Continue immediately into next phase
                            //
                            // THERAPY_TYPE is background-only.
                            // No user-facing response is generated here.
                            // ---------------------------------------------------------
                            processUserMessage("")

                            return@launch


                        }


                        DeepPhase.THERAPY_EXERCISES -> {

                            // ---------------------------------------------------------
                            // THERAPY_EXERCISES is handled by processUserMessage()
                            // before intent classification.
                            //
                            // Exercise generation is system-driven and occurs when
                            // the phase is entered. User messages are handled separately
                            // by the THERAPY_EXERCISES selection logic.
                            // ---------------------------------------------------------

                            Log.d(
                                "THERAPY_EXERCISES",
                                "Phase reached — exercise generation is handled by processUserMessage()"
                            )

                            return@launch
                        }


                        DeepPhase.EXERCISE_GUIDANCE -> {

                            // Guard: exercises must have been delivered
                            if (!deepManager.exercisesDelivered()) {
                                Log.e(
                                    "EXERCISE_FLOW",
                                    "❌ exercisesDelivered=false — cannot enter EXERCISE_GUIDANCE"
                                )
                                return@launch
                            }

                            // Guard: missing exercise context
                            if (deepManager.currentExerciseName.isNullOrBlank()) {
                                Log.e(
                                    "EXERCISE_FLOW",
                                    "❌ currentExerciseName is NULL — exercise was never started!"
                                )
                                return@launch
                            }

                            if (deepManager.currentExerciseSteps.isEmpty()) {
                                Log.e(
                                    "EXERCISE_FLOW",
                                    "❌ currentExerciseSteps is EMPTY — steps were never loaded!"
                                )
                                return@launch
                            }

                            // LOG: User replied — check current step
                            Log.d(
                                "EXERCISE_FLOW",
                                "User replied. currentExerciseName=${deepManager.currentExerciseName}, " +
                                        "stepIndex=${deepManager.currentExerciseStep}, " +
                                        "stepsSize=${deepManager.currentExerciseSteps.size}"
                            )

                            // -----------------------------------------------------
                            // USER ENDS THE EXERCISE MANUALLY
                            // -----------------------------------------------------
                            if (intent == "FINISHED_EXERCISES" || intent == "NO_TO_EXERCISES") {

                                Log.d(
                                    "EXERCISE_FLOW",
                                    "User ended exercise manually."
                                )

                                deepManager.endExercise()

                                return@launch
                            }

                            // -----------------------------------------------------
                            // PULL CURRENT EXERCISE CONTEXT
                            // -----------------------------------------------------
                            val exerciseName = deepManager.currentExerciseName
                            val steps = deepManager.currentExerciseSteps
                            val stepIndex = deepManager.currentExerciseStep
                            val disorder = lastDiagnosis ?: "general_distress"
                            val symptoms = symptomEngine.getSymptoms()

                            // LOG: Full exercise context
                            Log.d(
                                "EXERCISE_FLOW",
                                "Exercise context: name=$exerciseName, " +
                                        "steps=${steps.joinToString()}, " +
                                        "stepIndex=$stepIndex"
                            )

                            // -----------------------------------------------------
                            // COMPLETION CHECK
                            //
                            // currentExerciseStep represents the step the user has
                            // JUST responded to.
                            //
                            // isExerciseComplete() returns true when the current step
                            // is the final step.
                            // -----------------------------------------------------
                            val isComplete = deepManager.isExerciseComplete()

                            Log.d(
                                "EXERCISE_FLOW",
                                "Completion check via isExerciseComplete(): " +
                                        "stepIndex=$stepIndex, " +
                                        "lastIndex=${steps.lastIndex}, " +
                                        "totalSteps=${steps.size}, " +
                                        "isComplete=$isComplete"
                            )

                            // -----------------------------------------------------
                            // FINAL STEP → CLOSE EXERCISE
                            // -----------------------------------------------------
                            if (isComplete) {

                                Log.d(
                                    "EXERCISE_FLOW",
                                    "Final step reached at stepIndex=$stepIndex. " +
                                            "User has responded to final step → closing exercise."
                                )

                                val closure = withContext(Dispatchers.IO) {
                                    try {

                                        Log.d(
                                            "EXERCISE_FLOW",
                                            "Generating exercise closure for exercise=$exerciseName"
                                        )

                                        deepManager.generateExerciseClosure(
                                            exerciseName = exerciseName,
                                            disorder = disorder,
                                            symptoms = symptoms,
                                            client = client
                                        )

                                    } catch (e: Exception) {

                                        Log.e(
                                            "EXERCISE_FLOW",
                                            "Error generating exercise closure",
                                            e
                                        )

                                        "Great work. Let's pause here — you've completed the exercise."
                                    }
                                }

                                Log.d(
                                    "EXERCISE_FLOW",
                                    "Exercise closure generated. Ending exercise."
                                )

                                // endExercise():
                                // - clears current exercise state
                                // - sets waitingForAnotherExercise = true
                                // - returns phase to THERAPY_EXERCISES
                                deepManager.endExercise()

                                Log.d(
                                    "EXERCISE_FLOW",
                                    "Exercise ended. " +
                                            "phase=${deepManager.phase}, " +
                                            "waitingForAnotherExercise=${deepManager.waitingForAnotherExercise}"
                                )

                                // Send closure message
                                addMessage(
                                    closure,
                                    false
                                )

                                // Ask what the user wants to do next
                                Log.d(
                                    "EXERCISE_FLOW",
                                    "Prompting user for next action " +
                                            "(another exercise or finish)."
                                )

                                addMessage(
                                    "Would you like to try another exercise, or finish the session?",
                                    false
                                )

                                return@launch
                            }

                            // -----------------------------------------------------
                            // NOT FINAL → ADVANCE TO NEXT STEP
                            // -----------------------------------------------------

                            Log.d(
                                "EXERCISE_FLOW",
                                "Current step $stepIndex is not final. " +
                                        "Advancing to next step."
                            )

                            val advanced = deepManager.advanceExerciseStep()

                            if (!advanced) {

                                Log.e(
                                    "EXERCISE_FLOW",
                                    "❌ Failed to advance exercise step. " +
                                            "currentStep=${deepManager.currentExerciseStep}"
                                )

                                return@launch
                            }

                            val nextStepIndex = deepManager.currentExerciseStep

                            Log.d(
                                "EXERCISE_FLOW",
                                "Step advanced successfully. " +
                                        "oldStepIndex=$stepIndex, " +
                                        "newStepIndex=$nextStepIndex"
                            )

                            // -----------------------------------------------------
                            // GENERATE NEXT STEP
                            // -----------------------------------------------------

                            Log.d(
                                "EXERCISE_FLOW",
                                "Generating next step using " +
                                        "stepIndex=$nextStepIndex for exercise=$exerciseName"
                            )

                            val nextStep = withContext(Dispatchers.IO) {
                                try {

                                    deepManager.generateExerciseStep(
                                        exerciseName = exerciseName,
                                        userInput = userMessage,
                                        disorder = disorder,
                                        symptoms = symptoms,
                                        steps = steps,
                                        stepIndex = nextStepIndex,
                                        client = client
                                    )

                                } catch (e: Exception) {

                                    Log.e(
                                        "EXERCISE_FLOW",
                                        "Error generating next step",
                                        e
                                    )

                                    "Let's continue — tell me one more thing you're noticing."
                                }
                            }

                            // LOG: After generating next step
                            Log.d(
                                "EXERCISE_FLOW",
                                "Generated next step. " +
                                        "stepIndex=$nextStepIndex, " +
                                        "nextStepPreview=${nextStep.take(60)}"
                            )

                            // Send next step to UI
                            Log.d(
                                "EXERCISE_FLOW",
                                "Sending next step to UI."
                            )

                            addMessage(
                                nextStep,
                                false
                            )

                            return@launch
                        }

                        // ⭐ NEW FINAL PHASE — show mood AFTER session is complete
                        DeepPhase.SESSION_COMPLETE -> {
                            return@launch
                        }
                    }

                    // ⭐ FINAL FALLBACK — correct location
                    Log.w("DeepSession", "⚠️ No phase matched this message. Triggering fallback neutral prompt.")

                    addMessage(
                        "I sense you might be feeling a bit neutral or uncertain. How would you describe your emotion right now?",
                        isUser = false
                    )

                    return@launch

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

        val symptomText = if (selectedSymptoms.isEmpty()) {
            "None reported"
        } else {
            selectedSymptoms.joinToString(", ")
        }

        Log.e(
            "DiagnosisDebug",
            "sendDiagnosisMessage() called with selectedSymptoms=$selectedSymptoms" +
                    " | count=${selectedSymptoms.size}" +
                    " | symptomEngineConfirmed=${symptomEngine.getConfirmedSymptoms()}" +
                    " | engineCount=${symptomEngine.getConfirmedSymptoms().size}"
        )

        // -----------------------------------------------------
        // 1. Get PHQ‑9 / GAD‑7 + ACCHA evidence
        // -----------------------------------------------------
        val evidence = symptomEngine.getDiagnosisEvidence()

        // -----------------------------------------------------
        // 2. Build PHQ‑9 / GAD‑7 summary
        // -----------------------------------------------------
        val summary = """
PHQ‑9 (depression):
- Score: ${evidence.phq9Score.toInt()}
- Severity: ${evidence.phq9Severity}

GAD‑7 (anxiety):
- Score: ${evidence.gad7Score.toInt()}
- Severity: ${evidence.gad7Severity}
""".trimIndent()

        // -----------------------------------------------------
        // 3. Build ACCHA extended assessment summary
        // -----------------------------------------------------
        val acchaSummary = """
ACCHA Extended Assessment (12‑month history):

Emotional Frequency:
- Hopelessness: ${evidence.acchaHopeless}
- Overwhelm: ${evidence.acchaOverwhelmed}
- Exhaustion: ${evidence.acchaExhausted}
- Sadness: ${evidence.acchaSad}
- Functional impairment: ${evidence.acchaFunctionalImpairment}

Risk Indicators:
- Suicidal ideation: ${evidence.acchaSuicidalThoughts}
- Suicide attempts: ${evidence.acchaSuicideAttempts}

Depression History:
- Ever diagnosed with depression: ${evidence.acchaDiagnosed}
- Diagnosed in last 12 months: ${evidence.acchaDiagnosed12Months}

Current Support:
- Currently in therapy: ${evidence.acchaTherapy}
- Currently on medication: ${evidence.acchaMedication}
""".trimIndent()

        // -----------------------------------------------------
        // 4. Hidden context (emotion + cause + category)
        // -----------------------------------------------------
        val hiddenContext = """
INTERNAL CONTEXT (not shown to user):

Emotion detected:
- ${deepManager.contextEmotion ?: "Not identified"}

Cause described:
- ${deepManager.contextCause ?: "Not identified"}

Cause category:
- ${deepManager.contextCauseCategory ?: "Not classified"}

Interpretation notes:
- Emotion and cause may influence PHQ‑9/GAD‑7 scores.
- Academic pressure, relationship stress, loneliness, trauma, or existential distress may amplify symptoms.
- Consider whether the cause explains concentration issues, sleep disruption, appetite changes, or fatigue.
""".trimIndent()

        // -----------------------------------------------------
        // 5. Hidden health profile (medical + demographics)
        // -----------------------------------------------------
        val hiddenHealthProfile = """
HEALTH PROFILE (internal only):

Medical conditions:
${
            if (deepManager.medicalConditions.isEmpty()) "- None reported" else deepManager.medicalConditions.joinToString(
                "\n- ",
                prefix = "- "
            )
        }

Demographics:
- Full‑time student: ${deepManager.fullTimeStatus}
- International student: ${deepManager.internationalStatus}
- Ethnicity: ${
            if (deepManager.ethnicityList.isEmpty()) "Not specified" else deepManager.ethnicityList.joinToString(
                ", "
            )
        }

Interpretation notes:
- Student status may influence stress, sleep, concentration, and anxiety.
- International status may relate to isolation, cultural adjustment, or migration stress.
- Cultural background may influence how distress is expressed (e.g., somatic symptoms).
- Medical conditions may mimic or amplify fatigue, sleep issues, or concentration problems.
""".trimIndent()

        // -----------------------------------------------------
        // 6. Build final diagnosis interpretation prompt
        // -----------------------------------------------------
        val prompt = """
You are interpreting standardized mental‑health screening scores.

$hiddenContext

$hiddenHealthProfile

SCREENING RESULTS:
$summary

ACCHA EXTENDED ASSESSMENT:
$acchaSummary

SYMPTOMS SELECTED:
$symptomText

TASK:
- Provide a very concise interpretation.
- Use bullet points only.
- Mention depression and anxiety separately.
- Silently consider the internal context and health profile.
- If ACCHA values show frequent hopelessness, exhaustion, sadness, or overwhelm, describe this as “ongoing” or “long‑lasting” feelings.
- If functional impairment is elevated, gently note that these feelings may have affected day‑to‑day life.
- If suicidal thoughts or past attempts appear, acknowledge them with care and encourage reaching out to someone trusted or a professional.
- If the user has a past diagnosis, mention that these scores may reflect continuing or returning symptoms.
- If the user is in therapy or on medication, contextualize the scores in a supportive way.
- Keep sentences short and simple.
- Keep the tone warm, supportive, and non‑clinical.
- Do not give advice, treatment plans, or instructions.
- Remind the user this is not a formal diagnosis.
""".trimIndent()

        // -----------------------------------------------------
        // 7. Send interpretation to AI
        // -----------------------------------------------------
        sendToAI(
            userMessage = prompt,
            emotion = "not_applicable",
            disorder = "pending",
            phase = "DIAGNOSIS",
            exercises = emptyList(),
            cluster = null,
            focus = null,
            methods = null,
            onDelivered = {
                Log.e("DiagnosisDebug", "Diagnosis delivered")

                deepManager.setDiagnosisDelivered()
                deepManager.advancePhaseIfNeeded()

                Log.e(
                    "PHASE_TRACE",
                    "Diagnosis complete → phase=${deepManager.phase}"
                )

                processUserMessage("")
            }
        )
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
            methods: List<String>? = null,
            onDelivered: (() -> Unit)? = null
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
[CONTEXT INTAKE BEHAVIOR]
────────────────────────────────────────
When Phase is CONTEXT_INTAKE follow these rules exactly:
- **Primary goal**: collect **emotion** and **cause** for the current session before any assessment.
- **Emotion detection**: if the session has no emotion, ask one short, empathetic probe for emotion (example: "I’m sorry you’re feeling this way — how would you describe your emotion right now?").
- **Cause probe**: if emotion is present but cause is missing, ask **one** gentle question focused on cause (example: "What do you think is causing that feeling right now?"). Offer a short option list if user is brief: "Is it work, relationships, health, or something else?"
- **Accept brief answers**: treat a normal short answer (≥3 characters and not a token like 'ok'/'yes') as the cause. If the reply is ambiguous (ok/yes/no/fine), re‑probe once with a short follow-up.
- **Do not advance phases**: never imply or attempt to progress the session; wait for the external system to change the phase.
- **Safety override**: if the user expresses suicidal ideation, self‑harm, intent to harm others, or extreme hopelessness, follow the SAFETY CHECK and stop intake.
- **Tone and length**: be warm, concise, and non‑judgmental. Ask only one question per response in intake.

────────────────────────────────────────
[TREATMENT PATTERN Model C]
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

Use the weighting rules and cluster definitions provided.

────────────────────────────────────────
PHASE BEHAVIOR
────────────────────────────────────────
▶ CONTEXT_INTAKE
- Collect emotion and cause only.
- Ask one short, empathetic question to elicit missing info.
- Re‑probe once for ambiguous replies.
- Do not ask assessment questions or suggest questionnaires.
- Respect opt‑out and safety rules.

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

This is an INTERNAL/BACKGROUND phase.
Do NOT generate a user-facing response.
Do NOT explain or mention the selected therapy type to the user.
Do NOT describe why the therapy type was selected.
Do NOT ask the user any questions.
Do NOT generate exercises.
Do NOT acknowledge that therapy type selection is taking place.
The external system handles therapy type selection, storage, and progression.
Remain silent while this phase is processed in the background.

▶ THERAPY_EXERCISES

This phase is controlled by the application.
The application determines which exercise or exercises are presented to the user.
The exercise list is NOT generated or selected by the AI.

Rules:
- Do NOT generate, invent, or independently select exercises.
- Treat the exercises provided by the application as the only valid exercises.
- If ONE exercise is provided, discuss ONLY that exercise.
- Do NOT introduce, suggest, or add other exercises unless the application explicitly provides them.
- Do NOT replace, modify, or contradict the exercise information provided by the application.
- Keep exercise descriptions concise, clear, and easy to understand.
- If exercise steps are provided by the application, summarise or explain them without changing their intended meaning.
- Do NOT repeat the exercise list unless the application provides it again or the user explicitly asks to see it.
- If the user selects or agrees to an exercise, respond naturally and supportively.
- The external system handles exercise selection, initialization, step generation, completion, and progression.
- Do NOT attempt to control, advance, or change the exercise phase.
- Do NOT decide when an exercise is complete.
- Keep responses concise and supportive.
- One question is allowed when appropriate.


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

                    // Notify caller only after the AI response has actually
                    // been successfully added to the conversation.
                    onDelivered?.invoke()
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
        try {
            Log.d(
                "DeepSession",
                "showQuestionnaire() called | questionCount=${questionList.size} thread=${Thread.currentThread().name} deepManagerHash=${deepManager.hashCode()} selectedQ=${deepManager.selectedQuestionnaire?.name ?: "null"} activityState=${lifecycle.currentState}"
            )


            // Ensure we run UI work on main thread
            if (Looper.myLooper() != Looper.getMainLooper()) {
                Log.w("DeepSession", "showQuestionnaire invoked off main thread. Posting to main.")
                Handler(Looper.getMainLooper()).post {
                    try {
                        showQuestionnaire(questionList) // re-enter on main thread (will hit this branch but run on main)
                    } catch (e: Exception) {
                        Log.e("DeepSession", "Exception while re-invoking showQuestionnaire on main thread", e)
                    }
                }
                return
            }

            // Visibility changes
            Log.d("DeepSession", "Setting questionnaire UI visibility: container=VISIBLE inputBar=GONE recyclerView=GONE")
            questionnaireContainer.visibility = View.VISIBLE
            inputBar.visibility = View.GONE
            recyclerView.visibility = View.GONE

            // Remove all dynamic rows (keep title at index 0 and submit button at the end)
            Log.d("DeepSession", "Cleaning up questionnaireLayout children before rebuild. childCount=${questionnaireLayout.childCount}")
            for (i in questionnaireLayout.childCount - 1 downTo 1) {
                val view = questionnaireLayout.getChildAt(i)
                val tagInfo = when (val t = view.tag) {
                    null -> "null"
                    is Pair<*, *> -> "Pair(${t.first}, ${t.second})"
                    is String -> "String(${t})"
                    else -> t.toString()
                }
                Log.v("DeepSession", "Inspecting child index=$i tag=$tagInfo")
                if (view.tag is String || view.tag is Pair<*, *>) {
                    questionnaireLayout.removeViewAt(i)
                    Log.v("DeepSession", "Removed dynamic child at index=$i")
                }
            }

            // Build rows
            questionList.forEachIndexed { index, questionText ->
                Log.d("DeepSession", "Building row index=$index text='${questionText.take(80)}'")

                val rowTag = "q$index"

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 12, 0, 12)
                }

                val questionLabel = TextView(this).apply {
                    text = questionText
                    textSize = 16f
                    setTextColor(Color.WHITE)
                }

                val options = listOf(
                    "Not at all" to 0,
                    "Several days" to 1,
                    "More than half the days" to 2,
                    "Nearly every day" to 3
                )

                val optionsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                }

                val radioGroup = RadioGroup(this).apply {
                    id = View.generateViewId()
                    orientation = RadioGroup.VERTICAL
                }

                Log.d("DeepSession", "Created RadioGroup for row=$rowTag id=${radioGroup.id}")

                options.forEach { (label, score) ->
                    val radio = RadioButton(this).apply {
                        text = label
                        tag = score
                        setTextColor(Color.WHITE)
                    }

                    radio.setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) {
                            Log.d("DeepSession", "Radio checked | row=$rowTag label='$label' score=$score thread=${Thread.currentThread().name}")

                            // Emergency trigger for PHQ‑9 item 9
                            if (index == 8 && score >= 1) {
                                Log.i("DeepSession", "PHQ9 item 9 triggered emergency dialog (index=8 score=$score)")
                                try {
                                    showEmergencyDialog()
                                } catch (e: Exception) {
                                    Log.e("DeepSession", "Exception in showEmergencyDialog()", e)
                                }
                            }

                            try {
                                deepManager.updateAssessmentScore(index, score)
                                Log.v("DeepSession", "updateAssessmentScore called for index=$index score=$score")
                            } catch (e: Exception) {
                                Log.e("DeepSession", "Exception while calling updateAssessmentScore(index=$index, score=$score)", e)
                            }
                        }
                    }

                    radioGroup.addView(radio)
                }

                optionsRow.addView(radioGroup)

                row.addView(questionLabel)
                row.addView(optionsRow)

                // store both the question tag string and the radioGroup id in the row.tag as a Pair
                row.tag = Pair(rowTag, radioGroup.id)
                Log.d("DeepSession", "Row tag set | row=$rowTag radioGroupId=${radioGroup.id}")

                // Insert above submit button
                val insertIndex = questionnaireLayout.childCount - 1
                questionnaireLayout.addView(row, insertIndex)
                Log.v("DeepSession", "Inserted row at index=$insertIndex for $rowTag")
            }

            Log.d("DeepSession", "Finished building questionnaire UI. finalChildCount=${questionnaireLayout.childCount}")
        } catch (e: Exception) {
            Log.e("DeepSession", "Unhandled exception in showQuestionnaire()", e)
        }
    }



    suspend fun classifyIntent(userMessage: String, phase: DeepPhase): String {

        // ⭐ HARD OVERRIDE FOR EXERCISE GUIDANCE ⭐
        if (phase == DeepPhase.EXERCISE_GUIDANCE) {
            val lower = userMessage.lowercase()

            if (
                lower.contains("stop") ||
                lower.contains("done") ||
                lower.contains("finished") ||
                lower.contains("end") ||
                lower.contains("that's enough") ||
                lower.contains("end this")
            ) {
                return "FINISHED_EXERCISES"
            }

            return "EXERCISE_STEP"
        }

        // ⭐ NORMAL CLASSIFIER LOGIC BELOW ⭐
        val prompt = """
Classify the user's message into one category. Follow these rules carefully.

CURRENT PHASE: $phase

────────────────────────────────────────
PHASE: QUESTIONNAIRE
────────────────────────────────────────
1. SYMPTOM_DESCRIPTION — describing feelings, emotions, problems, or difficulties.

2. YES_TO_QUESTIONNAIRE — agreeing to complete a questionnaire
   (yes, sure, let's do it).

3. NO_TO_QUESTIONNAIRE — declining
   (no, not now, maybe later).

────────────────────────────────────────
PHASE: EXERCISE_SELECTION
────────────────────────────────────────
4. SELECT_EXERCISE — the user chooses a specific exercise.
   - If the user message EXACTLY matches one of the exercise names shown earlier,
     classify as SELECT_EXERCISE.
   - If the user message CONTAINS an exercise name,
     classify as SELECT_EXERCISE.

────────────────────────────────────────
PHASE: EXERCISE_FLOW
────────────────────────────────────────
5. EXERCISE_STEP — the user is actively participating in an exercise.

────────────────────────────────────────
PHASE: EXERCISE_COMPLETION
────────────────────────────────────────
6. FINISHED_EXERCISES — the user indicates they have completed the exercise.

────────────────────────────────────────
PHASE: SESSION_ENDING
────────────────────────────────────────
7. END_SESSION — the user wants to end the entire conversation.

────────────────────────────────────────
PHASE: NEUTRAL
────────────────────────────────────────
8. ACKNOWLEDGEMENT — neutral confirmation.

────────────────────────────────────────
PHASE: OTHER
────────────────────────────────────────
9. UNRELATED — anything else.

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

    private fun assessmentScoreLabel(score: Int): String {
        return when (score) {
            0 -> "Not at all"
            1 -> "Several days"
            2 -> "More than half the days"
            3 -> "Nearly every day"
            else -> "Not answered"
        }
    }

    private fun buildAssessmentAnswerSummary(
        questionnaire: QuestionnaireType,
        scores: Map<Int, Int>
    ): String {

        // Select the correct question list
        val questions = when (questionnaire) {
            QuestionnaireType.PHQ9 -> QuestionnaireData.phq9
            QuestionnaireType.GAD7 -> QuestionnaireData.gad7
        }

        val questionnaireName = questionnaire.name

        // Build answer lines
        val answers = questions.mapIndexed { index, question ->

            // Correct score lookup (no offsets)
            val score = scores[index] ?: 0

            "${index + 1}. $question — **${assessmentScoreLabel(score)}**"
        }

        return """
Here are your questionnaire responses:

${answers.joinToString("\n")}

I'll use these responses to help interpret your screening results.
""".trimIndent()
    }

    private fun sendNextExtendedAssessmentQuestion() {

        val freq = QuestionnaireData.achaDepressionFrequency
        val diag = QuestionnaireData.achaDepressionDiagnosis

        val question = when (deepManager.extendedQuestionIndex) {
            in 0..6 -> freq[deepManager.extendedQuestionIndex]
            in 7..10 -> diag[deepManager.extendedQuestionIndex - 7]
            else -> {
                // Finished extended assessment
                deepManager.assessmentMode = AssessmentMode.STANDARD
                deepManager.advancePhaseIfNeeded()
                return
            }
        }

        val prompt = """
You are a supportive mental‑health assistant.

TASK:
- Ask the following question conversationally.
- Keep the tone warm and non‑clinical.
- Encourage the user to answer naturally.
- Do NOT interpret the answer yet.
- Only ask the question.

QUESTION:
$question
""".trimIndent()

        sendToAI(
            userMessage = prompt,
            emotion = "not_applicable",
            disorder = "pending",
            phase = "ASSESSMENT", // stays in same phase
            exercises = emptyList(),
            cluster = null,
            focus = null,
            methods = null
        )
    }

    private suspend fun handleExtendedAssessmentResponse(userText: String) {

        val idx = deepManager.extendedQuestionIndex
        val er = deepManager.extendedResponses

        when (idx) {

            // -----------------------------------------------------
            // ACCHA Emotional Frequency (dynamic 0–6 scale)
            // -----------------------------------------------------
            0 -> {
                er.hopeless = classifyFrequency(userText)
                symptomEngine.emo_1 = er.hopeless!!.toFloat()
            }

            1 -> {
                er.overwhelmed = classifyFrequency(userText)
                symptomEngine.emo_2 = er.overwhelmed!!.toFloat()
            }

            2 -> {
                er.exhausted = classifyFrequency(userText)
                symptomEngine.emo_3 = er.exhausted!!.toFloat()
            }

            3 -> {
                er.sad = classifyFrequency(userText)
                symptomEngine.emo_4 = er.sad!!.toFloat()
            }

            4 -> {
                er.functionalImpairment = classifyFrequency(userText)
                symptomEngine.emo_5 = er.functionalImpairment!!.toFloat()
            }

            // -----------------------------------------------------
            // ACCHA Risk Indicators (binary)
            // -----------------------------------------------------
            5 -> {
                er.suicidalThoughts = if (parseYesNo(userText)) 1 else 0
                symptomEngine.emo_6 = er.suicidalThoughts!!.toFloat()
            }

            6 -> {
                er.suicideAttempts = if (parseYesNo(userText)) 1 else 0
                symptomEngine.emo_7 = er.suicideAttempts!!.toFloat()
            }

            // -----------------------------------------------------
            // ACCHA Depression History / Services (binary)
            // -----------------------------------------------------
            7 -> {
                er.diagnosedDepression = parseYesNo(userText)
                symptomEngine.acha_depression =
                    if (er.diagnosedDepression == true) 1f else 0f

                // Skip 8–10 if user says "no"
                if (er.diagnosedDepression == false) {
                    deepManager.extendedQuestionIndex = 11
                    finishExtendedAssessment()
                    return
                }
            }

            8 -> {
                er.diagnosedLast12Months = parseYesNo(userText)
                symptomEngine.acha_services_1 =
                    if (er.diagnosedLast12Months == true) 1f else 0f
            }

            9 -> {
                er.currentTherapy = parseYesNo(userText)
                symptomEngine.acha_services_2 =
                    if (er.currentTherapy == true) 1f else 0f
            }

            10 -> {
                er.currentMedication = parseYesNo(userText)
                symptomEngine.acha_services_3 =
                    if (er.currentMedication == true) 1f else 0f
            }
        }

        // -----------------------------------------------------
        // Move to next question
        // -----------------------------------------------------
        deepManager.extendedQuestionIndex++

        // -----------------------------------------------------
        // End-of-assessment check
        // -----------------------------------------------------
        if (deepManager.extendedQuestionIndex >= 11) {
            finishExtendedAssessment()
            return
        }

        sendNextExtendedAssessmentQuestion()
    }

    private fun finishExtendedAssessment() {
        // Reset mode
        deepManager.assessmentMode = AssessmentMode.STANDARD

        // Advance to DIAGNOSIS immediately
        deepManager.advancePhaseIfNeeded()
    }


    suspend fun classifyFrequency(userMessage: String): Int {

        val prompt = """
Map the user's response to a frequency bucket from 0 to 6.

ACCHA Frequency Scale:
0 = Never
1 = 1–2 times
2 = 3–4 times
3 = 5–6 times
4 = 7–8 times
5 = 9–10 times
6 = 11+ times, or everyday, or very frequent

Rules:
- Interpret ANY natural language phrasing.
- If the user expresses uncertainty (e.g., "not sure", "maybe"), choose the closest reasonable bucket.
- If the user expresses very high frequency (e.g., "all the time", "constantly"), return 6.
- If the user expresses very low frequency (e.g., "rarely"), return 1.
- Return ONLY the number (0–6). No words.

User response: "$userMessage"
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

        val content = response.choices.first().message.content?.trim() ?: "0"

        return content.toIntOrNull() ?: 0
    }


    fun parseYesNo(text: String): Boolean {
        val t = text.lowercase()
        return "yes" in t || "yeah" in t || "yep" in t
    }





    private fun normalizeName(s: String): String =
        s.trim()
            .removeSuffix(".")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[^\\p{L}\\p{N}\\s\\-]"), "") // remove stray punctuation except hyphen












}
