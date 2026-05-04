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
import com.example.therapy_app.BuildConfig
import com.aallam.openai.api.BetaOpenAI
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.delay
import android.net.Uri

private val db = Firebase.firestore
private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    private val selectedTags = mutableListOf<String>()

    private lateinit var modelRunner: OnnxModelRunner

    private val VOICE_REQUEST_CODE = 101
    private val presetTags = listOf("Depression", "Anxiety", "Mindfulness")

    private val openAiKey by lazy { BuildConfig.OPENAI_API_KEY }

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
    private val symptomState = FloatArray(24) { -1f }   // -1 = unknown
    private var lastPromptTime = 0L
    private var userHasSpoken = false

    private var sessionId: String? = null

    private var openedFromInsightsCard = false


    private var sessionJustLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setDecorFitsSystemWindows(true)
        setContentView(R.layout.activity_chat)

         openedFromInsightsCard = intent.getBooleanExtra("from_insights_card", false)
        if (openedFromInsightsCard) {
            isFirstAiResponse = false
        }


        sessionId = intent.getStringExtra("SESSION_ID")

        modelRunner = OnnxModelRunner(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_chat)
        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val voiceButton = findViewById<ImageButton>(R.id.voiceButton)
        val saveSessionButton = findViewById<Button>(R.id.saveSessionButton)
        emergencyButton = findViewById(R.id.emergencyButton)
        emergencyOverlay = findViewById(R.id.emergencyOverlay)

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
        } else if (!openedFromInsightsCard) {
            addMessage("Hi, I’m here with you. What’s been on your mind today?", isUser = false)
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
                    processUserMessage(userText)
                }
                true // Consume the event
            } else {
                false // Pass the event on
            }
        }


        sendButton.setOnClickListener {
            val userText = messageInput.text.toString()
            if (userText.isNotBlank()) {
                userHasSpoken = true
                addMessage(userText, isUser = true)
                messageInput.setText("")
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

                messages.clear()
                messages.addAll(session.messages)

                adapter.notifyDataSetChanged() // 🔥 key fix

                findViewById<RecyclerView>(R.id.chatRecyclerView)
                    .post {
                        findViewById<RecyclerView>(R.id.chatRecyclerView)
                            .scrollToPosition(messages.size - 1)
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
        try {

            // =====================================================
            // SINGLE UNIFIED PIPELINE (CRISIS-FIRST ARCHITECTURE)
            // =====================================================

            if (CrisisDetector.isCrisisMessage(userMessage)) {

                addMessage(
                    """
        I'm really sorry you're feeling this way.

        You don’t have to go through this alone.

        Here are some support options you can use right now:

        $emergencyContacts

        If you'd like, tap the button below for immediate help.
        """.trimIndent(),
                    isUser = false
                )

                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000) // 5 seconds
                    showEmergencyButton()
                }

                return
            }

            // 2. Model A (emotion)
            val emotion = modelRunner.runModelA(userMessage)

            // 3. Symptom extraction (safe, non-blocking)
            extractSymptomsFromNaturalLanguage(userMessage)


            // 5. Model B ONLY if enough data
            val knownCount = symptomState.count { it != -1f }

            val disorder = if (knownCount >= 5) {
                modelRunner.runModelB(getSymptomVector())
            } else {
                "insufficient_data"
            }

            // 6. Fusion
            val interpretation = FusionLogic.fuse(emotion, disorder)

            // 7. FINAL AI RESPONSE (ONLY ONE OUTPUT PATH)
            sendToAI(userMessage, emotion, disorder, interpretation)

        } catch (e: Exception) {
            runOnUiThread {
                addMessage("ONNX Error: ${e.message}", isUser = false)
            }
        }
    }

    // ---------------------------------------------------------
    // NATURAL LANGUAGE SYMPTOM EXTRACTION (Soft)
    // ---------------------------------------------------------
    private fun extractSymptomsFromNaturalLanguage(text: String) {
        val lower = text.lowercase()

        // Keyword groups
        val sadnessWords = listOf("sad", "down", "low", "depressed", "empty", "unhappy")
        val fatigueWords = listOf("tired", "exhausted", "fatigued", "no energy", "drained")
        val sleepWords = listOf("insomnia", "can't sleep", "sleeping badly", "awake all night", "tossing and turning")
        val anxietyWords = listOf("anxious", "nervous", "on edge", "panic", "worried", "scared")

        // New symptom groups
        val restlessnessWords = listOf("restless", "fidgety", "can't sit still", "agitated", "pacing")
        val stressedWords = listOf("stressed", "overwhelmed", "pressure", "burnt out", "too much going on")
        val angerWords = listOf("angry", "annoyed", "irritable", "mad", "frustrated", "pissed", "furious")
        val socialMediaWords = listOf("doomscrolling", "scrolling", "social media", "instagram", "tiktok", "addicted to my phone")
        val antisocialWords = listOf("antisocial", "withdrawn", "avoiding people", "isolating", "staying in", "don't want to talk")
        val selfBlameWords = listOf("my fault", "blaming myself", "i failed", "guilty", "ashamed", "failure")
        val traumaWords = listOf("nightmare", "bad dream", "flashback", "reliving", "traumatic memory", "can't forget")
        val overreactingWords = listOf("overreacting", "snapped", "too sensitive", "melt down", "explosive", "volatile")
        val weightGainWords = listOf("weight gain", "gained weight", "eating more", "heavier", "appetite increased")

        // Mapping to symptomState indices
        if (sadnessWords.any { lower.contains(it) }) symptomState[0] = 1f
        if (fatigueWords.any { lower.contains(it) }) symptomState[1] = 1f
        if (sleepWords.any { lower.contains(it) }) symptomState[2] = 1f
        if (anxietyWords.any { lower.contains(it) }) symptomState[3] = 1f
        if (restlessnessWords.any { lower.contains(it) }) symptomState[4] = 1f
        if (stressedWords.any { lower.contains(it) }) symptomState[5] = 1f
        if (angerWords.any { lower.contains(it) }) symptomState[6] = 1f
        if (socialMediaWords.any { lower.contains(it) }) symptomState[7] = 1f
        if (antisocialWords.any { lower.contains(it) }) symptomState[8] = 1f
        if (selfBlameWords.any { lower.contains(it) }) symptomState[9] = 1f
        if (traumaWords.any { lower.contains(it) }) symptomState[10] = 1f
        if (overreactingWords.any { lower.contains(it) }) symptomState[11] = 1f
        if (weightGainWords.any { lower.contains(it) }) symptomState[12] = 1f
    }





    // ---------------------------------------------------------
    // SYMPTOM VECTOR FOR MODEL B
    // ---------------------------------------------------------
    private fun getSymptomVector(): FloatArray {
        return symptomState.map { if (it < 0) 0f else it }.toFloatArray()
    }

    // ---------------------------------------------------------
    // OPENAI CALL
    // ---------------------------------------------------------
    private fun sendToAI(
        userMessage: String,
        emotion: String,
        disorder: String,
        interpretation: String
    ) {
        val client = OpenAI(token = openAiKey)

        val isStartOfSession = isFirstAiResponse && !sessionJustLoaded && !openedFromInsightsCard

        val symptomLabels = mapOf(
            0 to "Sadness/Mood",
            1 to "Fatigue/Energy",
            2 to "Sleep issues",
            3 to "Anxiety/Nervousness",
            4 to "Restlessness",
            5 to "Stress levels",
            6 to "Anger/Irritability",
            7 to "Social media usage",
            8 to "Social withdrawal",
            9 to "Self-blame/Guilt",
            10 to "Nightmares/Traumatic memories",
            11 to "Emotional reactivity",
            12 to "Weight/Appetite changes"
        )

        val symptomsConfirmed = symptomState.withIndex()
            .filter { it.value == 1f }
            .map { symptomLabels[it.index] ?: "Symptom_${it.index}" }
            .joinToString(", ")
            .ifBlank { "None confirmed yet" }

        val knownCount = symptomState.count { it == 1f }

        // Pass the last few messages to help AI avoid repeating itself
        val recentHistory = messages.takeLast(6).joinToString("\n") {
            if(it.user) "User: ${it.text}" else "Assistant: ${it.text}"
        }

        val prompt = """
        [CONTEXT]
        - Current Emotion: $emotion
        - Detected Pattern: $disorder
        - Internal Interpretation: $interpretation
        - Confirmed Symptoms: $symptomsConfirmed (Total: $knownCount/24)
        - Session Status: ${if (isStartOfSession) "NEW SESSION" else "CONTINUING"}

        [RECENT CONVERSATION HISTORY]
        $recentHistory

        [LATEST USER MESSAGE]
        "$userMessage"
        
  LANGUAGE & CODE-SWITCHING RULE
  - Detect the user's language and script: English, Urdu (Perso‑Arabic RTL script), or Roman Urdu (LTR).
  - Preserve and mirror the user's script and direction: 
    - If the user types in Urdu script, reply in Urdu script and respect right‑to‑left layout.
    - If the user types in Roman Urdu, reply in Roman Urdu (left‑to‑right) so it mixes naturally with English.
  - Match the user's linguistic style and code‑switching: if the user mixes English and Urdu, reply using the same mix and preserve English technical terms unchanged.
  - Only transliterate or convert script when the user explicitly requests it (e.g., "Transliterate to Urdu script").
  - If detection confidence is low, ask a short clarifying question before generating a full reply.
  - Label any automatic translations by prepending "[Translated]" to translated text and indicate when transliteration was applied.
  - If the model replies only in English after detecting Urdu, re‑generate with the explicit instruction "Reply in the user's language" or offer the user a visible toggle to choose response language.


        ──────────────────────────────────────────────────────────
         PHASE 0: SAFETY OVERRIDE (ABSOLUTE PRIORITY)
        ──────────────────────────────────────────────────────────
        If suicide, self-harm, or severe hopelessness is mentioned:
        1. STOP all other tasks. Respond only with empathy + resources in user's language:
        $emergencyContacts

        ──────────────────────────────────────────────────────────
        PHASE 1: EXPLORATORY SYMPTOM COLLECTION (PRIORITY IF CONFIRMED < 3)
        ──────────────────────────────────────────────────────────
        - Validate emotion ($emotion) warmly.
        - EXPLORATION RULE: Ask ONE follow-up question ONLY if it relates to what the user just said. 
        - REPETITION GUARD: Check [RECENT CONVERSATION HISTORY]. DO NOT ask about energy or sleep if you already asked in the last 3 turns.
        - If the user provided a long, detailed message, skip Phase 1 and move to Phase 3 immediately to keep flow fast.

        ──────────────────────────────────────────────────────────
        PHASE 2: CONVERSATIONAL FLEXIBILITY
        ──────────────────────────────────────────────────────────
        - If the user talks about unrelated topics (hobbies, life, general facts), engage warmly and naturally. Do not force therapy talk.

        ──────────────────────────────────────────────────────────
        PHASE 3: REFLECTION & SUPPORT (IF CONFIRMED >= 3 OR HIGH-QUALITY INPUT)
        ──────────────────────────────────────────────────────────
        - If you have enough info ($knownCount >= 3) or the user's input is descriptive, provide a gentle reflection based on: $interpretation.
        - Offer ONE supportive CBT thought or "suggestion to consider."

        [RESPONSE RULES]
        - Be concise to speed up interaction.
        - Never ask more than ONE question.
        - Never repeat a question found in [RECENT CONVERSATION HISTORY].
    """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = "You are a warm, multilingual Therapy Assistant. You are concise, avoid repetition, and mirror the user's language (English/Urdu)."
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt
                            )
                        ),
                        temperature = 0.7
                    )
                )

                val aiReply = response.choices.first().message?.content ?: "I'm here with you."

                runOnUiThread {
                    addMessage(aiReply, isUser = false)
                    if (isFirstAiResponse) isFirstAiResponse = false
                    sessionJustLoaded = false
                }

            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("I'm listening, but having some connection trouble. Could you say that again?", isUser = false)
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
            id = sessionId ?: "", // important: keep track if existing session
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
    }
