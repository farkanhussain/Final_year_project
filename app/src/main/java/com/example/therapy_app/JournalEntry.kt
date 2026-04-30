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

class JournalEntryActivity : AppCompatActivity() {

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
            showSaveDialog()
        }
    }

    // ====================================================
    // 🧠 AI PROMPTS (TOP SECTION)
    // ====================================================
    private fun loadPrompts() {

        val container = findViewById<LinearLayout>(R.id.promptContainer)

        container.removeAllViews()

        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        if (userId == null) {
            showFallbackPrompts(container)
            return
        }

        FirebaseFirestore.getInstance()
            .collection("therapy_sessions")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { result ->

                val sessions = result.documents

                if (sessions.isEmpty()) {
                    showFallbackPrompts(container)
                } else {
                    generateAIPrompts(container, sessions)
                }
            }
            .addOnFailureListener {
                showFallbackPrompts(container)
            }
    }

    private fun generateAIPrompts(
        container: LinearLayout,
        sessions: List<com.google.firebase.firestore.DocumentSnapshot>
    ) {

        val sessionText = sessions.joinToString("\n") {
            it.getString("notes") ?: ""
        }

        val prompt = """
            Based on therapy sessions:
            $sessionText
            
            Generate 3 short reflective journaling prompts.
        """.trimIndent()

        callOpenAI(prompt) { prompts ->

            prompts.forEach {
                container.addView(createPromptView(it))
            }
        }
    }

    private fun showFallbackPrompts(container: LinearLayout) {

        val fallback = listOf(
            "What is one thought you want to release today?",
            "What emotion is most present right now?",
            "What helped you cope recently?"
        )

        fallback.forEach {
            container.addView(createPromptView(it))
        }
    }

    private fun callOpenAI(prompt: String, callback: (List<String>) -> Unit) {

        // 🔌 Replace with real API call later
        val fake = listOf(
            "What patterns are you noticing in your thoughts?",
            "What feels unresolved right now?",
            "What would compassion look like today?"
        )

        callback(fake)
    }

    private fun createPromptView(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 14f
            setPadding(12, 12, 12, 12)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
    }

    // ====================================================
    // 😊 MOOD SELECTOR
    // ====================================================
    private fun setupMoodSelector() {

        val moodSelector = findViewById<LinearLayout>(R.id.moodSelector)

        for (i in 0 until moodSelector.childCount) {

            val moodView = moodSelector.getChildAt(i) as TextView

            moodView.setOnClickListener {

                for (j in 0 until moodSelector.childCount) {
                    moodSelector.getChildAt(j).alpha = 0.4f
                }

                moodView.alpha = 1.0f
                selectedMood = moodView.text.toString()
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
    private fun showSaveDialog() {

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_save_journal, null)

        val titleInput =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                R.id.titleInput
            )

        val chipGroup =
            dialogView.findViewById<ChipGroup>(R.id.tagChipGroup)

        val customTagInput =
            dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(
                R.id.customTagInput
            )

        val tags = listOf("CBT", "Trauma", "Mindfulness", "Anxiety", "Depression")

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                setTextColor(resources.getColor(android.R.color.white, theme))
                chipBackgroundColor =
                    resources.getColorStateList(R.color.red_dark, theme)
            }
            chipGroup.addView(chip)
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<MaterialButton>(R.id.saveTagsButton).setOnClickListener {

            val selectedTags = mutableListOf<String>()

            for (i in 0 until chipGroup.childCount) {
                val chip = chipGroup.getChildAt(i) as Chip
                if (chip.isChecked) selectedTags.add(chip.text.toString())
            }

            val customTag = customTagInput.text.toString().trim()
            if (customTag.isNotEmpty()) selectedTags.add(customTag)

            saveJournalEntry(
                titleInput.text.toString(),
                selectedTags,
                journalText.text.toString(),
                selectedMood // 🧠 NOW INCLUDED
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
            "mood" to mood, // 🧠 optional
            "timestamp" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("journal_entries")
            .add(data)
    }
}