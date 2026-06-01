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

    private var lastAskedFeature: String? = null


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

    private var sessionMode: String? = null


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

            // Ask user which type of session they want
            addMessage(
                "Before we begin, would you like a 5‑Minute Check‑In or a Deep Support Session today?",
                isUser = false
            )

            // Wait for user response in onSendMessage()
            sessionMode = null
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

                    // If sessionMode not chosen yet, try to infer from this message
                    if (sessionMode == null) {
                        val choice = userText.lowercase()

                        sessionMode = when {
                            choice.contains("quick") || choice.contains("check") || choice.contains("5") ->
                                "quick"

                            choice.contains("deep") || choice.contains("long") || choice.contains("support") ->
                                "deep"

                            else -> {
                                addMessage(
                                    "Just to confirm — would you prefer a 5‑Minute Check‑In or a Deep Support Session?",
                                    isUser = false
                                )
                                // Do not call processUserMessage here; wait for the user's next input
                                return@setOnEditorActionListener true
                            }
                        }

                        // Start the appropriate flow message
                        if (sessionMode == "quick") {
                            addMessage(
                                "Great — we’ll do a short 5‑Minute Check‑In. What’s been on your mind today?",
                                isUser = false
                            )
                        } else {
                            addMessage(
                                "Alright — we’ll take our time with a Deep Support Session. What’s been weighing on you lately?",
                                isUser = false
                            )
                        }

                        // Process the same message that selected the session mode
                        processUserMessage(userText)
                    } else {
                        // Normal flow after session type chosen
                        processUserMessage(userText)
                    }
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

                // 🔥 SESSION MODE SELECTION LOGIC
                if (sessionMode == null) {
                    val choice = userText.lowercase()

                    sessionMode = when {
                        choice.contains("quick") || choice.contains("check") || choice.contains("5") ->
                            "quick"

                        choice.contains("deep") || choice.contains("long") || choice.contains("support") ->
                            "deep"

                        else -> {
                            addMessage(
                                "Just to confirm — would you prefer a 5‑Minute Check‑In or a Deep Support Session?",
                                isUser = false
                            )
                            return@setOnClickListener
                        }
                    }

                    // Start the appropriate flow
                    if (sessionMode == "quick") {
                        addMessage(
                            "Great — we’ll do a short 5‑Minute Check‑In. What’s been on your mind today?",
                            isUser = false
                        )
                    } else {
                        addMessage(
                            "Alright — we’ll take our time with a Deep Support Session. What’s been weighing on you lately?",
                            isUser = false
                        )
                    }

                    // 🔥 Process the SAME message that selected the session mode
                    processUserMessage(userText)
                    return@setOnClickListener
                }

                // 🔥 NORMAL CHATBOT LOGIC (after session type chosen)
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

                adapter.notifyDataSetChanged()

                val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
                recyclerView.post {
                    recyclerView.scrollToPosition(messages.size - 1)

                    // ⭐ After loading an existing session, offer deep support
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
        try {

            // =====================================================
            // NORMALISE SESSION MODE (BUT DO NOT OVERRIDE IT)
            // =====================================================
            val mode = sessionMode?.trim()?.lowercase() ?: ""

            // If no session mode chosen yet → do nothing.
            // The send button logic will handle session selection.
            if (mode.isEmpty()) {
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

                If you'd like, tap the button below for immediate help.
                """.trimIndent(),
                    isUser = false
                )

                CoroutineScope(Dispatchers.Main).launch {
                    delay(5000)
                    showEmergencyButton()
                }

                return
            }

            // =====================================================
            // QUICK SESSION → MODEL A ONLY
            // =====================================================
            if (mode == "quick") {

                val emotion = modelRunner.runModelA(userMessage)

                sendToAI(
                    userMessage = userMessage,
                    emotion = emotion,
                    disorder = "not_applicable",
                    interpretation = emotion
                )

                return
            }

            // =====================================================
            // DEEP SUPPORT SESSION → MODEL B ONLY
            // =====================================================
            if (mode == "deep") {

                extractSymptomsFromNaturalLanguage(userMessage)

                val disorder = modelRunner.runModelB(getSymptomVector())

                sendToAI(
                    userMessage = userMessage,
                    emotion = "not_applicable",
                    disorder = disorder,
                    interpretation = disorder
                )

                return
            }

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

        // Emotional symptoms
        val sadnessWords = listOf("sad", "down", "low", "depressed", "empty", "unhappy")
        val anxietyWords = listOf("anxious", "nervous", "on edge", "panic", "worried", "scared")
        val angerWords = listOf("angry", "furious", "irritated", "mad", "frustrated", "pissed")

        // Physical symptoms
        val fatigueWords = listOf("tired", "exhausted", "fatigued", "no energy", "drained")
        val sleepWords = listOf("insomnia", "can't sleep", "sleeping badly", "awake all night", "tossing and turning")
        val restlessnessWords = listOf("restless", "fidgety", "can't sit still", "agitated", "pacing")

        // Behavioural symptoms
        val stressedWords = listOf("stressed", "overwhelmed", "pressure", "burnt out", "too much going on")
        val antisocialWords = listOf("antisocial", "withdrawn", "avoiding people", "isolating", "staying in", "don't want to talk")
        val overreactingWords = listOf("overreacting", "snapped", "too sensitive", "melt down", "explosive", "volatile")
        val socialMediaWords = listOf("doomscrolling", "scrolling", "social media", "instagram", "tiktok", "addicted to my phone")

        // Cognitive symptoms
        val selfBlameWords = listOf("my fault", "blaming myself", "i failed", "guilty", "ashamed", "failure")
        val traumaWords = listOf("nightmare", "bad dream", "flashback", "reliving", "traumatic memory", "can't forget")
        val weightGainWords = listOf("weight gain", "gained weight", "eating more", "heavier", "appetite increased")

        // Mapping to symptomState indices (your existing 0–12 mapping)
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
    private fun getSymptomVector(expectedLen: Int = 24): FloatArray {
        // Ensure symptomState has at least expectedLen entries
        val vector = FloatArray(expectedLen) { idx ->
            val v = if (idx < symptomState.size) symptomState[idx] else 0f
            when {
                v.isNaN() -> 0f
                v < 0f -> 0f
                else -> v
            }
        }
        return vector
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

        val recentHistory = messages.takeLast(6).joinToString("\n") {
            if (it.user) "User: ${it.text}" else "Assistant: ${it.text}"
        }

        // ---------------------------------------------------------
        // QUICK SESSION PROMPT (5‑Minute Check‑In)
        // ---------------------------------------------------------
        val quickPrompt = """
[MODEL SELECTION]
Use Model A exclusively. 
Model A contains emotions associated with depression (e.g., sadness, anger, worthlessness, irritability, guilt, hopelessness).

[CONTEXT]
- Session Mode: QUICK (5‑Minute Check‑In)
- This is a short, supportive emotional check‑in.
- Ignore all previous conversation history.
- Do NOT collect symptoms.
- Do NOT ask questions.
- Do NOT follow CBT phases.
- Do NOT continue any previous diagnostic flow.

[LATEST USER MESSAGE]
"$userMessage"

──────────────────────────────────────────────────────────
QUICK SESSION RULES — MODEL A ONLY
──────────────────────────────────────────────────────────
Your job is to:
1) Identify the dominant emotion using Model A  
2) Determine whether the emotion aligns with depression‑related patterns  
3) Provide:
   - A brief emotional interpretation  
   - ONE personalised CBT recommendation  
   - A warm closing message  
   - A gentle invitation to begin a Deep Support Session  

STRICT PROHIBITIONS:
- No questions.
- No symptom collection.
- No multi‑step CBT.
- No exploration of triggers, thoughts, or behaviours.
- No continuation of previous threads.

──────────────────────────────────────────────────────────
CRISIS DETECTION (ALWAYS FIRST)
──────────────────────────────────────────────────────────
If the user expresses suicide, self‑harm, intent to harm others, or extreme hopelessness:
Respond ONLY with empathy + grounding + safety resources:
{emergencyContacts}

──────────────────────────────────────────────────────────
RESPONSE STYLE
──────────────────────────────────────────────────────────
- Warm, concise, emotionally attuned.
- Mirror the user’s emotional tone.
- Keep the entire session under 5 minutes.
- End the session after the recommendation + invitation.
""".trimIndent()



        // ---------------------------------------------------------
        // DEEP SUPPORT SESSION PROMPT
        // ---------------------------------------------------------
        val deepPrompt ="""
[MODEL SELECTION]
Use Model B exclusively.
Model B contains symptoms and behavioural/emotional patterns for depression, anxiety, stress, loneliness, anger, and related conditions.

[CONTEXT]
- Current Emotion: $emotion
- Detected Pattern: $disorder
- Internal Interpretation: $interpretation
- Confirmed Symptoms: $symptomsConfirmed (Total: $knownCount/24)
- Session Status: ${if (isStartOfSession) "NEW" else "CONTINUING"}
- Session Mode: DEEP SUPPORT SESSION

[RECENT CONVERSATION HISTORY]
$recentHistory

[LATEST USER MESSAGE]
"$userMessage"

──────────────────────────────────────────────────────────
DEEP SUPPORT SESSION RULES — MODEL B
──────────────────────────────────────────────────────────
Your goal is to explore the user’s emotional and behavioural experience in a natural, human way.

- Ask ONE meaningful follow‑up question per turn.
- The question MUST be based on what the user actually said.
- Do NOT repeat the same question type.
- Choose the question category based on the user’s message:
  • If they express an emotion → explore the emotion  
  • If they describe a behaviour → explore the behaviour  
  • If they mention physical symptoms → explore the physical symptoms  
  • If they describe thoughts → explore the thoughts  
  • If they describe triggers → explore the triggers  

- Once the user provides:
  • ≥3 symptoms OR  
  • rich emotional/behavioural detail  
  → Move automatically to diagnosis.

──────────────────────────────────────────────────────────
PHASE 0 — CRISIS DETECTION
──────────────────────────────────────────────────────────
If crisis → respond ONLY with empathy + grounding + safety resources:
$emergencyContacts

──────────────────────────────────────────────────────────
PHASE 1 — FLEXIBLE SYMPTOM & EMOTION EXPLORATION
──────────────────────────────────────────────────────────
- Ask ONE question that directly responds to the user’s message.
- Do NOT default to physical symptoms.
- If the user expresses anger, sadness, fear, guilt, hopelessness, or overwhelm:
  → Explore the emotion.
- If the user describes behaviours (e.g., isolation, overreacting):
  → Explore the behaviour.
- If the user mentions physical symptoms:
  → Explore the physical symptoms.
- Never repeat the same question type twice in a row.

──────────────────────────────────────────────────────────
PHASE 2 — DIAGNOSIS (MODEL B)
──────────────────────────────────────────────────────────
- Provide a tentative, non‑medical diagnosis.
- Use CBT formulation:
  Situation → Thoughts → Emotions → Behaviours → Physical symptoms
- Validate the user’s emotional experience deeply.

──────────────────────────────────────────────────────────
PHASE 3 — CBT RECOMMENDATIONS (MODEL B)
──────────────────────────────────────────────────────────
- Provide THREE personalised CBT recommendations.
- Each must be specific, actionable, and tied to the symptoms or emotions.

──────────────────────────────────────────────────────────
RESPONSE STYLE
──────────────────────────────────────────────────────────
- Warm, reflective, non‑clinical.
- Never ask more than ONE question.
- Mirror the user’s emotional tone.
- Respond to the user, not the script.
""".trimIndent()

        // ---------------------------------------------------------
        // SELECT PROMPT BASED ON SESSION MODE
        // ---------------------------------------------------------
        val prompt = if (sessionMode == "quick") quickPrompt else deepPrompt

        // ---------------------------------------------------------
        // SEND TO OPENAI
        // ---------------------------------------------------------
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



}
