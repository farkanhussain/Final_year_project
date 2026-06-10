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
import kotlinx.coroutines.CoroutineScope
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.delay
import kotlin.collections.mapIndexedNotNull
import android.net.Uri
import android.util.Log
import android.graphics.Color
import org.json.JSONObject

private val db = Firebase.firestore
private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

class ChatActivity : AppCompatActivity() {

    // ==========================================================
// STATIC DEEP SESSION PROMPT TEMPLATE
// ==========================================================

    private var isUserInputLocked: Boolean = false

    private var cachedDisorder: String? = null

    private var sessionStatus: String = "NEW"

    var lastAiUnknownSymptoms: List<String> = emptyList()

    private var diagnosisDelivered = false

    private val cbtGenerator = CBTExerciseGenerator()

    private lateinit var symptomEngine: SymptomCollectionEngine

    private lateinit var deepManager: DeepSessionManager

    private lateinit var chatMoodSelector: LinearLayout
    private var deepMoodSelected = false
    private var deepMoodInt = -1

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

    val symptomQuestions: Map<String, String> = mapOf(
        "feeling.nervous" to "Have you been feeling nervous, anxious, or on edge?",
        "panic" to "Have you experienced panic or sudden intense fear?",
        "breathing.rapidly" to "Have you had episodes of rapid or difficult breathing?",
        "sweating" to "Have you experienced sweating during moments of stress or anxiety?",
        "trouble.in.concentration" to "Have you had trouble concentrating or focusing?",
        "having.trouble.in.sleeping" to "Have you been having trouble sleeping?",
        "having.trouble.with.work" to "Have you been struggling with work or responsibilities?",
        "hopelessness" to "Have you been feeling hopeless or like things won’t improve?",
        "feeling.negative" to "Have you been experiencing persistent negative thoughts?",
        "feeling.tired" to "Have you been feeling tired or low on energy?",
        "anger" to "Have you been feeling angry or irritable?",
        "over.react" to "Have you been overreacting or snapping easily?",
        "change.in.eating" to "Have you noticed changes in your eating habits?",
        "close.friend" to "Have you been feeling lonely or without close support?",
        "avoids.people.or.activities" to "Have you been avoiding people or activities?",
        "popping.up.stressful.memory" to "Have stressful memories been popping up unexpectedly?",
        "having.nightmares" to "Have you been having nightmares?",
        "blamming.yourself" to "Have you been blaming yourself or feeling guilty?",
        "suicidal.thought" to "Have you had thoughts of harming yourself?",
        "social.media.addiction" to "Have you been spending excessive time on social media or feeling unable to disconnect?",
        "weight.gain" to "Have you experienced recent weight gain or changes in your body weight?",
        "material.possessions" to "Have you been relying on buying things or material possessions to cope with emotions?",
        "introvert" to "Have you been withdrawing socially or preferring to isolate yourself?",
        "loss.of.interest" to "Have you lost interest in activities you used to enjoy?"

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
    private var symptomState = FloatArray(24) { -1f }   // -1 = unknown
    private var lastPromptTime = 0L
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
            sessionMode = "quick"
            sessionStatus = "NEW"

            sessionSelector.visibility = View.GONE
            findViewById<TextView>(R.id.sessionIntroText).visibility = View.GONE

            enableChatInput()

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

            // =====================================================
            // RESET CORE DEEP STATE MACHINE
            // =====================================================
            deepManager = DeepSessionManager()   // starts in ASSESSMENT by default
            Log.d("DeepButton", "DeepSessionManager reset to ASSESSMENT")

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

            // =====================================================
            // SHOW QUESTIONNAIRE (Assessment Phase)
            // =====================================================
            showSymptomQuestionnaire()
            Log.d("DeepButton", "Questionnaire displayed")
        }

        btnSubmitSymptoms.setOnClickListener {

            Log.e("QuestionnaireDebug", "---- SUBMIT PRESSED ----")
            Log.e("QuestionnaireDebug", "questionnaireLayout childCount = ${questionnaireLayout.childCount}")

            val selectedSymptoms = mutableListOf<String>()

            for (i in 0 until questionnaireLayout.childCount) {
                val row = questionnaireLayout.getChildAt(i)

                Log.e("QuestionnaireDebug", "Row $i found. tag=${row.tag}")

                // Only process question rows (rows with a REAL symptomKey)
                if (row.tag is String) {
                    val symptomKey = row.tag as String
                    Log.e("QuestionnaireDebug", "Row $i is a question row. symptomKey=$symptomKey")

                    // FIX: Find YES/NO checkboxes by tag
                    val yesBox = row.findViewWithTag<CheckBox>("yes")
                    val noBox = row.findViewWithTag<CheckBox>("no")

                    Log.e(
                        "QuestionnaireDebug",
                        "Row $i checkboxes: yesBox=$yesBox isChecked=${yesBox?.isChecked}, noBox=$noBox isChecked=${noBox?.isChecked}"
                    )

                    // FIX: Add symptom only if YES is selected
                    if (yesBox?.isChecked == true) {
                        selectedSymptoms.add(symptomKey)
                        Log.e("QuestionnaireDebug", "Row $i → YES selected → added symptom: $symptomKey")
                    } else {
                        Log.e("QuestionnaireDebug", "Row $i → YES not selected")
                    }

                } else {
                    Log.e("QuestionnaireDebug", "Row $i skipped (no valid tag)")
                }
            }

            Log.e("QuestionnaireDebug", "FINAL selectedSymptoms = $selectedSymptoms")

            // Update engine
            symptomEngine.updateFromCheckboxSelections(selectedSymptoms)
            Log.e("QuestionnaireDebug", "Engine after update = ${symptomEngine.getConfirmedSymptoms()}")

            // Deep session state
            deepManager.setQuestionnaireCompleted()
            deepManager.advancePhaseIfNeeded()

            questionnaireContainer.visibility = View.GONE
            inputBar.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE

            enableChatInput()

            // Send to AI
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
            val isEnterKeyDown = event != null &&
                    event.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                    event.action == android.view.KeyEvent.ACTION_DOWN

            val isSendAction = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND

            if (isSendAction || isEnterKeyDown) {
                val userText = messageInput.text.toString()

                if (userText.isNotBlank()) {
                    userHasSpoken = true
                    addMessage(userText, isUser = true)
                    messageInput.setText("")

                    // 🚀 NEW CLEAN LOGIC:
                    // Session mode must be chosen via buttons before typing.
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

                    // 🚀 NORMAL CHATBOT LOGIC (session already chosen)
                    processUserMessage(userText)
                }

                true // consume event
            } else {
                false // pass event
            }
        }




        sendButton.setOnClickListener {
            val userText = messageInput.text.toString()

            if (userText.isNotBlank()) {
                userHasSpoken = true
                addMessage(userText, isUser = true)
                messageInput.setText("")

                // 🚀 NEW CLEAN LOGIC:
                // Session mode is ALWAYS chosen via buttons now.
                // No need to infer "quick" or "deep" from typed text.
                if (sessionMode == null) {
                    // Safety check — should never happen unless UI is bypassed
                    addMessage(
                        if (selectedLanguage == "ur")
                            "براہ کرم پہلے سیشن کی قسم منتخب کریں۔"
                        else
                            "Please select a session type first.",
                        isUser = false
                    )
                    return@setOnClickListener
                }

                // 🚀 NORMAL CHATBOT LOGIC (session already chosen)
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
    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(Message(text, isUser))
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
        Log.d("LifecycleCheck", "deepManager at start = $deepManager")
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

            // =====================================================
            // QUICK MODE
            // =====================================================
            if (mode == "quick") {

                val emotion = modelRunner.runModelA(userMessage).toString()

                sendToAI(
                    userMessage = userMessage,
                    disorder = "quick_mode",
                    phase = "QUICK",
                    cbtExercises = emptyList()
                )


                return
            }

            // =====================================================
            // DEEP MODE ONLY
            // =====================================================
            // =====================================================
// DEEP SESSION MODE
// =====================================================
            if (mode != "deep") return

            Log.d("ProcessUserMessage", "🟣 DEEP mode triggered")

            try {

                Log.d("DeepSession", "Current phase: ${deepManager.phase}")

                // =====================================================
                // 1. ASSESSMENT (QUESTIONNAIRE ONLY)
                // =====================================================
                if (deepManager.phase == DeepPhase.ASSESSMENT) {

                    // User should NOT type symptoms here.
                    // They must use the questionnaire UI.
                    addMessage(
                        "Please use the questionnaire above to select any symptoms you’ve been experiencing.",
                        isUser = false
                    )
                    return
                }

                // =====================================================
                // 2. DIAGNOSIS (MESSAGE-BASED)
                // =====================================================
                if (deepManager.phase == DeepPhase.DIAGNOSIS) {

                    deepManager.setDiagnosisDelivered()
                    deepManager.advancePhaseIfNeeded()

                    sendDiagnosisMessage(symptomEngine.getConfirmedSymptoms())

                    return
                }

                // =====================================================
                // 3. CBT (MESSAGE-BASED)
                // =====================================================
                if (deepManager.phase == DeepPhase.CBT) {

                    isUserInputLocked = true

                    val disorder = lastDiagnosis ?: "general_distress"

                    lifecycleScope.launch(Dispatchers.IO) {

                        val exercises = try {
                            cbtGenerator.generateHybridExercises(
                                disorder = disorder,
                                symptoms = symptomEngine.getSymptoms(),
                                userMessage = userMessage
                            ) { prompt ->

                                val response = client.chatCompletion(
                                    ChatCompletionRequest(
                                        model = ModelId("gpt-4o-mini"),
                                        messages = listOf(
                                            ChatMessage(ChatRole.User, prompt)
                                        )
                                    )
                                )

                                response.choices.first().message?.content ?: ""
                            }

                        } catch (e: Exception) {
                            emptyList()
                        }

                        withContext(Dispatchers.Main) {

                            isUserInputLocked = false

                            sendToAI(
                                userMessage = userMessage,
                                disorder = disorder,
                                phase = "CBT",
                                cbtExercises = exercises
                            )

                        }
                    }

                    return
                }

            } catch (e: Exception) {
                Log.e("DeepSession", "Error in deep session block", e)
            }

        } catch (e: Exception) {
                Log.e("DeepSession", "Error: ${e.message}")
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

        // Debug log
        Log.e(
            "DiagnosisDebug",
            "sendDiagnosisMessage() called with selectedSymptoms=" + selectedSymptoms +
                    " | count=" + selectedSymptoms.size +
                    " | symptomEngineConfirmed=" + symptomEngine.getConfirmedSymptoms() +
                    " | engineCount=" + symptomEngine.getConfirmedSymptoms().size
        )

        // 1. Get evidence
        val evidence = symptomEngine.getDiagnosisEvidence()

        // 2. Convert to Low/Medium/High labels
        val labels = evidence.toWeightLabels()

        // 3. Build summary
        val labelSummary = """
        Anxiety: ${labels["anxiety"]}
        Depression: ${labels["depression"]}
        Stress: ${labels["stress"]}
        Loneliness: ${labels["loneliness"]}
    """.trimIndent()

        // 4. Build prompt
        val prompt = """
You are diagnosing the user based on selected symptoms.

SYMPTOMS:
$symptomText

EVIDENCE LEVELS (Low / Medium / High):
$labelSummary

TASK:
- Display the evidence levels clearly before giving the diagnosis
- Explain that these levels represent relative indicators, not probabilities or certainties
- Identify the most likely disorder(s)
- Include disorders from the dataset AND disorders not in the dataset
- Keep the tone supportive and non-clinical
- Provide a short explanation
""".trimIndent()

        addMessage("Thanks — let me take a look at these symptoms.", false)

        sendToAI(
            userMessage = prompt,
            disorder = "pending",
            phase = "DIAGNOSIS",
            cbtExercises = emptyList()
        )

        deepManager.setDiagnosisDelivered()
        deepManager.advancePhaseIfNeeded()
    }










    // ---------------------------------------------------------
    // OPENAI CALL
    // ---------------------------------------------------------
    private fun sendToAI(
        userMessage: String,
        disorder: String,
        phase: String,
        cbtExercises: List<String> = emptyList()
    ) {
        Log.d("AI_DEBUG", "---- sendToAI CALLED ----")
        Log.d("AI_DEBUG", "User message: $userMessage")
        Log.d("AI_DEBUG", "Disorder: $disorder")
        Log.d("AI_DEBUG", "Phase: $phase")
        Log.d("AI_DEBUG", "CBT exercises count: ${cbtExercises.size}")
        Log.d("AI_DEBUG", "Session mode: $sessionMode")

        // Clean history for OpenAI
        val cleanedHistory = cleanMessagesForOpenAI(messages)

        val historyText = cleanedHistory.joinToString("\n") { msg ->
            "${msg.role.uppercase()}: ${msg.content}"
        }

        // Symptoms (from questionnaire only)
        val symptomsText = symptomEngine.getConfirmedSymptomsText()

        // CBT exercises text
        val cbtExercisesText = if (cbtExercises.isEmpty()) {
            "None available"
        } else {
            cbtExercises.joinToString("\n") { "- $it" }
        }

        // Session status (simplified)
        val sessionStatus = if (isFirstAiResponse) "NEW" else "CONTINUING"

        Log.d(
            "DeepSessionDebug",
            "symptomsConfirmed=" + symptomEngine.getConfirmedSymptoms() +
                    " | symptomCount=" + symptomEngine.getConfirmedSymptoms().size +
                    " | symptomsText=" + symptomEngine.getConfirmedSymptomsText() +
                    " | phase=" + phase +
                    " | sessionMode=" + sessionMode +
                    " | sessionStatus=" + sessionStatus
        )



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
1) Emotional interpretation  
2) ONE simple CBT suggestion  
3) Warm closing  
4) Invitation to Deep Support Session  

If no emotional content:
Respond with exactly:
"I'm here with you. Could you share a bit about what you're feeling right now?"
""".trimIndent()

        // ---------------------------------------------------------
        // DEEP SUPPORT SESSION PROMPT
        // ---------------------------------------------------------
        val builtDeepPrompt = """
[ROLE]
You are a supportive CBT‑informed conversational assistant operating inside a structured therapy system.
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
[CBT EXERCISES]
────────────────────────────────────────
$cbtExercisesText

────────────────────────────────────────
SAFETY CHECK
────────────────────────────────────────
If the user expresses suicide, self‑harm, intent to harm others, or extreme hopelessness:
- Respond with immediate empathy and validation
- Do NOT continue structured content
- Encourage reaching out to someone they trust or a professional
- Keep the response short, calm, and supportive

────────────────────────────────────────
RESPONSE RULES
────────────────────────────────────────
- Respond naturally, warmly, and conversationally
- Do NOT mention or explain phases explicitly
- Do NOT control progression of the session
- Focus ONLY on the user’s latest message
- Keep responses concise and human‑like
- Ask ONLY ONE question per response (unless in DIAGNOSIS or CBT phases)

────────────────────────────────────────
PHASE BEHAVIOUR (STYLE ONLY)
────────────────────────────────────────

▶ ASSESSMENT
- User already completed questionnaire
- Do NOT ask for more symptoms

▶ DIAGNOSIS
- Provide a gentle, non‑medical explanation of likely disorder(s)
- Include dataset AND non‑dataset disorders
- No questions

▶ CBT
- Provide 2–3 personalised CBT exercises
- Explain each clearly
- No questions

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

        Log.e("AI_DEBUG", "Final prompt length: ${finalPrompt.length}")
        Log.e("AI_DEBUG", "Final prompt preview:\n${finalPrompt.take(2000)}")

        // ---------------------------------------------------------
        // BUILD OPENAI REQUEST
        // ---------------------------------------------------------
        val openAIRequest = mutableListOf<OpenAIMessage>()

        openAIRequest += OpenAIMessage("system", finalPrompt)

        if (sessionMode == "quick") {
            openAIRequest += OpenAIMessage("user", userMessage)
        } else {
            val safeHistory = cleanedHistory.takeLast(3)
            openAIRequest += safeHistory
            openAIRequest += OpenAIMessage("user", userMessage)
        }

        // ---------------------------------------------------------
        // SEND TO OPENAI
        // ---------------------------------------------------------
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val sdkMessages = openAIRequest.map {
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

                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = sdkMessages,
                        temperature = 0.7
                    )
                )

                val aiReply = response.choices.first().message?.content ?: "I'm here with you."

                withContext(Dispatchers.Main) {
                    addMessage(aiReply, isUser = false)
                    isFirstAiResponse = false
                    sessionJustLoaded = false
                }

            } catch (e: Exception) {
                Log.e("OPENAI_ERROR", "chatCompletion failed", e)

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
            messages = messages.toList()
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

    private fun showSymptomQuestionnaire() {
        questionnaireContainer.visibility = View.VISIBLE
        inputBar.visibility = View.GONE
        recyclerView.visibility = View.GONE

        // Remove all dynamic question rows (keep title + submit button)
        for (i in questionnaireLayout.childCount - 1 downTo 1) {
            val view = questionnaireLayout.getChildAt(i)
            if (view.tag is String) {
                questionnaireLayout.removeViewAt(i)
            }
        }

        // Add YES/NO rows
        for ((symptomKey, questionText) in symptomQuestions) {

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 12, 0, 12)
                tag = symptomKey   // 🔥 FIX #1 — store REAL symptom key
            }

            val questionLabel = TextView(this).apply {
                text = questionText
                textSize = 16f
                setTextColor(Color.WHITE)
            }

            val yesBox = CheckBox(this).apply {
                text = "Yes"
                tag = "yes"        // 🔥 FIX #2 — correct tag
                setTextColor(Color.WHITE)
            }

            val noBox = CheckBox(this).apply {
                text = "No"
                tag = "no"         // 🔥 FIX #3 — correct tag
                setTextColor(Color.WHITE)
            }

            // Mutually exclusive logic
            yesBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) noBox.isChecked = false
            }

            noBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) yesBox.isChecked = false
            }

            val optionsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(yesBox)
                addView(noBox)
            }

            row.addView(questionLabel)
            row.addView(optionsRow)

            // Insert above submit button
            questionnaireLayout.addView(row, questionnaireLayout.childCount - 1)
        }
    }









    fun markSessionContinuing() {
        sessionStatus = "CONTINUING"
    }

    // Extracts unknown symptoms from AI reply using <symptom:...> tags
    fun extractUnknownSymptoms(aiReply: String): List<String> {
        val regex = "<symptom:(.*?)>".toRegex()
        return regex.findAll(aiReply).map { it.groupValues[1].trim() }.toList()
    }


    fun safe(value: String?, default: String = "unknown"): String {
        return if (value.isNullOrBlank()) default else value
    }






        }
