package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.view.LayoutInflater
import android.widget.Toast
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText


class JournalEntryActivity : AppCompatActivity() {

    private val openAiKey by lazy { BuildConfig.OPENAI_API_KEY }

    private var originalMood: String? = null

    private var loadedTitle: String? = null
    private var loadedTags: List<String> = emptyList()





    private lateinit var journalText: EditText
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent

    // 🧠 NEW: mood tracking
    private var selectedMood: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journal_entry)

        journalText = findViewById(R.id.journalText)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_journal_entry)
        val micButton = findViewById<FloatingActionButton>(R.id.micButton)
        val saveButton = findViewById<MaterialButton>(R.id.saveButton)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val incomingPrompts = intent.getStringArrayListExtra("ai_prompts")
        val promptContainer = findViewById<LinearLayout>(R.id.promptContainer)

        if (incomingPrompts != null && incomingPrompts.isNotEmpty()) {
            // Use prompts from Home page
            promptContainer.removeAllViews()
            incomingPrompts.forEach { promptContainer.addView(createPromptView(it)) }
        } else {
            // No prompts passed → generate new ones
            loadPrompts()
        }


        // ----------------------------------------------------
// LOAD EXISTING ENTRY IF EDITING
// ----------------------------------------------------
        val entryId = intent.getStringExtra("ENTRY_ID")
        if (entryId != null) {
            loadExistingEntry(entryId)
        }


        // ----------------------------------------------------
        // BACK BUTTON
        // ----------------------------------------------------
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // ----------------------------------------------------
        // SPEECH TO TEXT
        // ----------------------------------------------------
        setupSpeechToText()

        micButton.setOnClickListener {
            startSpeechToText()
        }

        // ----------------------------------------------------
        // LOAD AI PROMPTS
        // ----------------------------------------------------
        loadPrompts()

        // ----------------------------------------------------
        // MOOD SELECTOR SETUP
        // ----------------------------------------------------
        setupMoodSelector()

        // ----------------------------------------------------
        // SAVE BUTTON
        // ----------------------------------------------------
        saveButton.setOnClickListener {
            showSaveDialog(loadedTitle, loadedTags)
        }
    }

    // ====================================================
    // 🧠 AI PROMPTS (TOP SECTION)
    // ====================================================
    private fun loadPrompts() {

        val container = findViewById<LinearLayout>(R.id.promptContainer)

        // Always clear existing prompts first
        container.removeAllViews()

        // ----------------------------------------------------
        // 1️⃣ If prompts were passed from Home → use them
        // ----------------------------------------------------
        val incomingPrompts = intent.getStringArrayListExtra("ai_prompts")

        if (incomingPrompts != null && incomingPrompts.isNotEmpty()) {
            Log.d("DEBUG_PROMPTS", "Using prompts passed from HomeActivity: $incomingPrompts")

            incomingPrompts.forEach { prompt ->
                container.addView(createPromptView(prompt))
            }

            return  // ⬅️ Stop here — do NOT regenerate prompts
        }

        // ----------------------------------------------------
        // 2️⃣ Otherwise, generate new prompts (fallback behaviour)
        // ----------------------------------------------------
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("DEBUG", "UserId: $userId")

        if (userId == null) {
            showFallbackPrompts(container)
            return
        }

        Log.d("DEBUG_FIRESTORE", "Querying therapy_sessions for userId: $userId")

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("sessions")
            .get()
            .addOnSuccessListener { result ->

                Log.d("DEBUG_FIRESTORE", "Query successful")
                Log.d("DEBUG_FIRESTORE", "Sessions found: ${result.size()}")

                val sessions = result.documents

                if (sessions.isEmpty()) {
                    Log.d("DEBUG_FIRESTORE", "No sessions → showing fallback")
                    showFallbackPrompts(container)
                } else {
                    Log.d("DEBUG_FIRESTORE", "Sessions exist → generating AI prompts")
                    generateAIPrompts(container, sessions)
                }
            }
            .addOnFailureListener { e ->

                Log.e("DEBUG_FIRESTORE", "Firestore FAILED: ${e.message}", e)
                showFallbackPrompts(container)
            }
    }

    private fun generateAIPrompts(
        container: LinearLayout,
        sessions: List<com.google.firebase.firestore.DocumentSnapshot>
    ) {

        Log.d("DEBUG", "generateAIPrompts called with ${sessions.size} sessions")

        sessions.forEachIndexed { index, doc ->
            Log.d("DEBUG_DOC", "Session[$index] raw data: ${doc.data}")
        }

        // ✅ FIXED SESSION TEXT BUILDER (uses messages array properly)
        val sessionText = sessions.joinToString("\n") { doc ->

            val messages = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()

            val parsedSession = messages.joinToString("\n") { message ->

                val text = message["text"] as? String ?: ""
                val isUser = message["user"] as? Boolean ?: false

                if (text.isNotBlank()) {
                    if (isUser) "User: $text" else "Therapist: $text"
                } else {
                    ""
                }
            }

            Log.d("DEBUG", "Parsed session block:\n$parsedSession")

            parsedSession
        }

        Log.d("DEBUG", "FINAL sessionText sent to OpenAI:\n$sessionText")

        callOpenAI(
            sessionText = sessionText,
            mood = selectedMood
        ) { prompts ->

            Log.d("DEBUG", "AI returned ${prompts.size} prompts: $prompts")

            prompts.forEach {
                container.addView(createPromptView(it))
            }
        }
    }

    private fun showFallbackPrompts(container: LinearLayout) {

        Log.d("DEBUG_FALLBACK", "showFallbackPrompts triggered")

        val fallback = listOf(
            "What is one thought you want to release today?",
            "What emotion is most present right now?",
            "What helped you cope recently?"
        )

        Log.d("DEBUG_FALLBACK", "Fallback prompts count: ${fallback.size}")

        fallback.forEachIndexed { index, prompt ->

            Log.d("DEBUG_FALLBACK", "Adding fallback[$index]: $prompt")

            container.addView(createPromptView(prompt))
        }

        Log.d("DEBUG_FALLBACK", "Fallback prompts successfully added to UI")
    }

    private fun callOpenAI(
        sessionText: String,
        mood: String?,
        callback: (List<String>) -> Unit
    ) {

        val client = OpenAI(token = openAiKey)

        Log.d("DEBUG_OPENAI", "callOpenAI triggered")
        Log.d("DEBUG_OPENAI", "Mood input: $mood")
        Log.d("DEBUG_OPENAI", "Session text length: ${sessionText.length}")
        Log.d("DEBUG_OPENAI", "Session text preview: ${sessionText.take(300)}")

        CoroutineScope(Dispatchers.IO).launch {

            try {

                val safeMood = mood ?: "Not provided"

                val safeSessionText = if (sessionText.isBlank()) {
                    "No therapy notes available."
                } else {
                    sessionText
                }

                Log.d("DEBUG_OPENAI", "safeMood = $safeMood")
                Log.d("DEBUG_OPENAI", "safeSessionText length = ${safeSessionText.length}")

                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = """
You are a therapeutic journaling assistant.

Generate 3 VERY SHORT CBT-style journaling prompts.

Rules:
- Maximum 8–10 words each
- Prefer short phrases over full sentences
- No numbering
- No explanations
- Keep each prompt concise and punchy
- Gentle, safe, non-clinical tone
""".trimIndent()
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = """
MOOD:
$safeMood

SESSION NOTES:
$safeSessionText

TASK:
Generate 3 reflective journaling prompts tailored to the user's emotional state.

If context is missing, still generate general supportive prompts.
""".trimIndent()
                            )
                        ),
                        temperature = 0.8
                    )
                )

                Log.d("DEBUG_OPENAI", "Raw response received: $response")

                val rawText = response.choices.first().message?.content ?: ""

                Log.d("DEBUG_OPENAI", "Raw OpenAI text: $rawText")

                val prompts = rawText
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { it.replace(Regex("^\\d+\\.?\\s*"), "") }
                    .take(3)

                Log.d("DEBUG_OPENAI", "Parsed prompts: $prompts")

                CoroutineScope(Dispatchers.Main).launch {
                    Log.d("DEBUG_OPENAI", "Returning prompts to UI thread")
                    callback(prompts)
                }

            } catch (e: Exception) {

                Log.e("DEBUG_OPENAI", "OpenAI error: ${e.message}", e)

                CoroutineScope(Dispatchers.Main).launch {

                    Log.d("DEBUG_OPENAI", "Falling back to default prompts")

                    callback(
                        listOf(
                            "What emotion is most present right now?",
                            "What thought keeps coming back today?",
                            "What would self-compassion look like today?"
                        )
                    )
                }
            }
        }
    }

    // ====================================================
    // 😊 MOOD SELECTOR
    // ====================================================
    private fun setupMoodSelector() {

        val moodSelector = findViewById<LinearLayout>(R.id.moodSelector)

        Log.d("DEBUG_MOOD", "Mood selector child count: ${moodSelector.childCount}")

        for (i in 0 until moodSelector.childCount) {

            val moodView = moodSelector.getChildAt(i) as TextView

            Log.d("DEBUG_MOOD", "Found mood option[$i]: ${moodView.text}")

            moodView.setOnClickListener {

                Log.d("DEBUG_MOOD", "Mood clicked: ${moodView.text}")

                for (j in 0 until moodSelector.childCount) {

                    val child = moodSelector.getChildAt(j)

                    child.alpha = 0.4f
                }

                moodView.alpha = 1.0f

                selectedMood = moodView.text.toString()

                Log.d("DEBUG_MOOD", "selectedMood updated → $selectedMood")
            }
        }
    }
    // ====================================================
    // 🎤 SPEECH TO TEXT
    // ====================================================
    private fun setupSpeechToText() {

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        }

        speechRecognizer.setRecognitionListener(object : android.speech.RecognitionListener {

            override fun onResults(results: Bundle) {
                val matches =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (!matches.isNullOrEmpty()) {
                    journalText.append(" " + matches[0])
                }
            }

            override fun onError(error: Int) {
                Toast.makeText(this@JournalEntryActivity,
                    "Speech error", Toast.LENGTH_SHORT).show()
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun startSpeechToText() {
        try {
            speechRecognizer.startListening(recognizerIntent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Speech not supported", Toast.LENGTH_SHORT).show()
        }
    }

    // ====================================================
    // 💾 SAVE DIALOG
    // ====================================================
    private fun showSaveDialog(
        currentTitle: String?,
        currentTags: List<String>?
    ) {

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_save_journal, null)

        val titleInput =
            dialogView.findViewById<TextInputEditText>(R.id.titleInput)

        val chipGroup =
            dialogView.findViewById<ChipGroup>(R.id.tagChipGroup)

        val customTagInput =
            dialogView.findViewById<TextInputEditText>(R.id.customTagInput)

        // ⭐ Pre-fill title
        titleInput.setText(currentTitle ?: "")

        // ⭐ Convert tags to a mutable set for easy editing
        val selectedTags = currentTags?.toMutableSet() ?: mutableSetOf()

        // Hard-coded suggested tags
        val suggestedTags = listOf("Gratitude", "Reflection", "Goals", "Mindfulness", "stress", "daily")

        chipGroup.removeAllViews()

        // -------------------------------
        // Suggested tags (check if selected)
        // -------------------------------
        suggestedTags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isChecked = selectedTags.contains(tag)
                setTextColor(resources.getColor(android.R.color.white, theme))
                chipBackgroundColor = resources.getColorStateList(R.color.red_dark, theme)
            }

            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) selectedTags.add(tag)
                else selectedTags.remove(tag)
            }

            chipGroup.addView(chip)
        }

        // -------------------------------
        // Custom tags (tags not in suggested list)
        // -------------------------------
        selectedTags
            .filter { it !in suggestedTags }
            .forEach { customTag ->

                val chip = Chip(this).apply {
                    text = customTag
                    isCheckable = false
                    isCloseIconVisible = true
                    setTextColor(resources.getColor(android.R.color.white, theme))
                    chipBackgroundColor = resources.getColorStateList(R.color.red_dark, theme)
                }

                chip.setOnCloseIconClickListener {
                    selectedTags.remove(customTag)
                    chipGroup.removeView(chip)
                }

                chipGroup.addView(chip)
            }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // -------------------------------
        // SAVE BUTTON
        // -------------------------------
        dialogView.findViewById<MaterialButton>(R.id.saveTagsButton).setOnClickListener {

            // Add typed custom tag
            val typedTag = customTagInput.text.toString().trim()
            if (typedTag.isNotEmpty()) {
                selectedTags.add(typedTag)
            }

            saveJournalEntry(
                titleInput.text.toString(),
                selectedTags.toList(),
                journalText.text.toString(),
                selectedMood
            )

            dialog.dismiss()
            finish()
        }

        dialog.show()
    }


    // ====================================================
    // ☁️ FIREBASE SAVE
    // ====================================================
    private fun saveJournalEntry(
        title: String,
        tags: List<String>,
        content: String,
        mood: String?
    ) {

        if (content.isBlank()) return

        val data = hashMapOf(
            "title" to title,
            "tags" to tags,
            "content" to content,
            "mood" to mood,
            "timestamp" to System.currentTimeMillis()
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("journal_entries")
            .add(data)
            .addOnSuccessListener {


                val moodInt = emojiToMoodInt(mood)

                if (moodInt != -1) {
                    saveMoodLog(moodInt)
                }
            }
    }




    private fun createPromptView(prompt: String): TextView {
        return TextView(this).apply {
            text = prompt

            textSize = 16f



            setPadding(32, 24, 32, 24)

            setTextColor(resources.getColor(android.R.color.white, theme))
            background = resources.getDrawable(R.drawable.prompt_background, theme)

            setOnClickListener {
                journalText.append("\n\n" + prompt)
            }
        }
    }

    private fun loadExistingEntry(entryId: String) {

        val user = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("journal_entries")
            .document(entryId)
            .get()
            .addOnSuccessListener { doc ->

                if (!doc.exists()) {
                    Toast.makeText(this, "Entry not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val title = doc.getString("title") ?: ""
                val content = doc.getString("content") ?: ""
                val mood = doc.getString("mood") ?: ""
                val tags = doc.get("tags") as? List<String> ?: emptyList()

                // ⭐ STORE ORIGINAL MOOD FOR COMPARISON WHEN SAVING
                originalMood = mood

                // ⭐ STORE TITLE + TAGS FOR THE SAVE DIALOG
                loadedTitle = title
                loadedTags = tags

                // 📝 Fill UI
                findViewById<TextView>(R.id.journalTitle)?.text = title
                journalText.setText(content)

                selectedMood = mood
                highlightSelectedMood(mood)

                findViewById<TextView>(R.id.journalTags)?.text =
                    tags.joinToString(", ")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load entry", Toast.LENGTH_SHORT).show()
            }
    }



    private fun highlightSelectedMood(mood: String?) {
        val moodSelector = findViewById<LinearLayout>(R.id.moodSelector)

        for (i in 0 until moodSelector.childCount) {
            val moodView = moodSelector.getChildAt(i) as TextView
            moodView.alpha = if (moodView.text.toString() == mood) 1f else 0.4f
        }
    }

    private fun emojiToMoodInt(emoji: String?): Int {
        return when (emoji) {
            "😢" -> 1
            "😕" -> 2
            "😐" -> 3
            "🙂" -> 4
            "😄" -> 5
            else -> -1
        }
    }

    private fun saveMoodLog(moodInt: Int) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val moodData = mapOf(
            "mood" to moodInt,
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("moods")
            .add(moodData)
    }







}