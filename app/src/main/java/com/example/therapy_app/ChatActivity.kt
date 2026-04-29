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

    private var sessionJustLoaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setDecorFitsSystemWindows(true)
        setContentView(R.layout.activity_chat)



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

        if (sessionId != null) {
            loadExistingSession()
        } else {
            addMessage("Hi, I’m here with you. What’s been on your mind today?", isUser = false)
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

        // Example symptom keyword groups
        val sadnessWords = listOf("sad", "down", "low", "depressed", "empty")
        val anxietyWords = listOf("anxious", "nervous", "on edge", "panic", "worried")
        val sleepWords = listOf("insomnia", "can't sleep", "sleeping badly", "awake all night")
        val fatigueWords = listOf("tired", "exhausted", "fatigued", "no energy")

        if (sadnessWords.any { lower.contains(it) }) symptomState[0] = 1f
        if (fatigueWords.any { lower.contains(it) }) symptomState[1] = 1f
        if (sleepWords.any { lower.contains(it) }) symptomState[2] = 1f
        if (anxietyWords.any { lower.contains(it) }) symptomState[3] = 1f
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

        // ---------------------------------------------------------
        // SESSION CONTEXT LOGIC (FIXED)
        // ---------------------------------------------------------
        val isStartOfSession = isFirstAiResponse && !sessionJustLoaded

        val sessionContextNote =
            if (isStartOfSession) {
                "This is the FIRST assistant response in a NEW session."
            } else {
                "This is a continuing conversation."
            }

        val knownSymptoms = symptomState
            .withIndex()
            .mapNotNull { (index, value) ->
                when (value) {
                    1f -> "symptom_$index"
                    0.5f -> "possible_symptom_$index"
                    else -> null
                }
            }
            .joinToString(", ")
            .ifBlank { "none reported yet" }

        val prompt = """
        Session state: $sessionContextNote
        
        The user said: "$userMessage"

        Emotion detected: $emotion
        Disorder pattern: $disorder
        Interpretation: $interpretation
        Known symptoms so far: $knownSymptoms

        You are a warm, supportive CBT-based mental health assistant.
        You do NOT diagnose.

        ────────────────────────────
        🚨 SAFETY OVERRIDE (ABSOLUTE PRIORITY)
        ────────────────────────────
        If the user shows ANY indication of:
        - suicidal thoughts
        - self-harm
        - feeling unsafe
        - severe hopelessness

        YOU MUST:
        - STOP all other steps immediately
        - DO NOT ask questions
        - DO NOT explore
        - DO NOT continue CBT flow
        - Respond only with support + resources

        Include:
        $emergencyContacts

        Keep tone calm, warm, and direct.

        ────────────────────────────
        NORMAL RESPONSE (ONLY IF SAFE)
        ────────────────────────────

        1. Brief validation (1–2 sentences max)
        2. Optional reflection (no interrogation)
        3. Optional CBT intervention (ONE tool only)
        4. End with gentle autonomy

        RULES:
        - ONE message only
        - NEVER diagnose
        - NEVER ask multiple questions
        - Safety overrides everything

        ────────────────────────────
        OPTIONAL START-OF-SESSION SUPPORT
        ────────────────────────────
        Only apply if this is the FIRST assistant response in a NEW session.

        You may include ONE short, gentle invitation encouraging the user to share how they are feeling.

        Rules:
        - Only at session start
        - One sentence only
        - Not clinical
        - Not a checklist
        - Not multiple questions
        - Warm and low pressure
    """.trimIndent()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = "You are a supportive CBT assistant."
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = prompt
                            )
                        )
                    )
                )

                val aiReply =
                    response.choices.first().message?.content ?: "I'm here with you."

                runOnUiThread {
                    addMessage(aiReply, isUser = false)

                    // ---------------------------------------------------------
                    // UPDATE SESSION STATE FLAGS (IMPORTANT FIX)
                    // ---------------------------------------------------------
                    if (isFirstAiResponse) {
                        isFirstAiResponse = false
                    }

                    sessionJustLoaded = false
                }

            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("Error: ${e.message}", isUser = false)
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
