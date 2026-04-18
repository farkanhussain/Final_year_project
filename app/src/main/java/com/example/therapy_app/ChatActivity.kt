package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
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

private val db = Firebase.firestore
private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    private val selectedTags = mutableListOf<String>()

    private val VOICE_REQUEST_CODE = 101
    private val presetTags = listOf("Depression", "Anxiety", "Mindfulness")

    private val openAiKey by lazy { BuildConfig.OPENAI_API_KEY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setDecorFitsSystemWindows(true)
        setContentView(R.layout.activity_chat)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_chat)
        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val voiceButton = findViewById<ImageButton>(R.id.voiceButton)
        val saveSessionButton = findViewById<Button>(R.id.saveSessionButton)

        messageInput.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        sendButton.setOnClickListener {
            val userText = messageInput.text.toString()
            if (userText.isNotBlank()) {
                addMessage(userText, isUser = true)
                messageInput.setText("")
                sendToAI(userText)
            }
        }

        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        saveSessionButton.setOnClickListener {
            showSaveDialog()
        }
    }

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
            addMessage(spokenText, isUser = true)
            sendToAI(spokenText)
        }
    }

    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(Message(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)

        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    @OptIn(BetaOpenAI::class)
    private fun sendToAI(userMessage: String) {

        val client = OpenAI(token = openAiKey)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = ChatCompletionRequest(
                    model = ModelId("gpt-4o-mini"),
                    messages = listOf(
                        ChatMessage(
                            role = ChatRole.System,
                            content = "You are a supportive CBT-based mental health assistant. Respond with empathy, clarity, and psychological safety."
                        ),
                        ChatMessage(
                            role = ChatRole.User,
                            content = userMessage
                        )
                    )
                )

                val response = client.chatCompletion(request)
                val aiReply = response.choices.first().message?.content ?: "No response."

                runOnUiThread {
                    addMessage(aiReply, isUser = false)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("Error: ${e.message}", isUser = false)
                }
            }
        }
    }

    // ---------------------------------------------------------
    // SAVE DIALOG (with direct chip styling)
    // ---------------------------------------------------------
    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tags, null)

        val titleInput = dialogView.findViewById<EditText>(R.id.titleInput)
        val chipGroup = dialogView.findViewById<ChipGroup>(R.id.tagChipGroup)
        val customTagInput = dialogView.findViewById<EditText>(R.id.customTagInput)
        val saveButton = dialogView.findViewById<Button>(R.id.saveTagsButton)

        // Clear XML chips and add dynamic ones with direct color styling
        chipGroup.removeAllViews()

        presetTags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isClickable = true

                // Background color (unchecked)
                setChipBackgroundColorResource(R.color.red_dark)

                // Text color
                setTextColor(ContextCompat.getColor(this@ChatActivity, R.color.white))

                // Stroke
                chipStrokeWidth = 2f
                chipStrokeColor = ContextCompat.getColorStateList(
                    this@ChatActivity,
                    R.color.red_dark
                )

                // Ripple effect
                rippleColor = ContextCompat.getColorStateList(
                    this@ChatActivity,
                    R.color.red
                )

                // Checked state override
                setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        setChipBackgroundColorResource(R.color.red)
                        chipStrokeColor = ContextCompat.getColorStateList(
                            this@ChatActivity,
                            R.color.red
                        )
                    } else {
                        setChipBackgroundColorResource(R.color.red_dark)
                        chipStrokeColor = ContextCompat.getColorStateList(
                            this@ChatActivity,
                            R.color.red_dark
                        )
                    }
                }
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
            if (customTag.isNotEmpty()) {
                selectedTags.add(customTag)
            }

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
    // SAVE SESSION TO FIREBASE
    // ---------------------------------------------------------
    private fun saveSession(title: String) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "You must be logged in to save sessions.", Toast.LENGTH_LONG).show()
            return
        }

        val session = TherapySession(
            id = "",
            title = title,
            tags = selectedTags.toList(),
            messages = messages.toList()
        )

        db.collection("users")
            .document(user.uid)
            .collection("sessions")
            .add(session)
            .addOnSuccessListener {
                Toast.makeText(this, "Session saved!", Toast.LENGTH_LONG).show()

                val intent = Intent(this, TherapyActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error saving session: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
