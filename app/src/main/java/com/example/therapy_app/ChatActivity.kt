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
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()
    private val selectedTags = mutableListOf<String>()

    private val VOICE_REQUEST_CODE = 101

    private val presetTags = listOf("Depression", "Anxiety", "Mindfulness")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setDecorFitsSystemWindows(true)
        setContentView(R.layout.activity_chat)

        // -----------------------------
        // FIND VIEWS
        // -----------------------------
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_chat)
        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        val messageInput = findViewById<EditText>(R.id.messageInput)
        val sendButton = findViewById<ImageButton>(R.id.sendButton)
        val voiceButton = findViewById<ImageButton>(R.id.voiceButton)

        val saveSessionButton = findViewById<Button>(R.id.saveSessionButton)
        val addTagsButton = findViewById<Button>(R.id.addTagsButton)

        // Set message input text color to white
        messageInput.setTextColor(ContextCompat.getColor(this, android.R.color.white))

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // -----------------------------
        // BACK BUTTON
        // -----------------------------
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // -----------------------------
        // RECYCLER VIEW SETUP
        // -----------------------------
        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // -----------------------------
        // SEND TEXT MESSAGE
        // -----------------------------
        sendButton.setOnClickListener {
            val userText = messageInput.text.toString()
            if (userText.isNotBlank()) {
                addMessage(userText, isUser = true)
                messageInput.setText("")
                sendToAI(userText)
            }
        }

        // -----------------------------
        // VOICE INPUT
        // -----------------------------
        voiceButton.setOnClickListener {
            startVoiceRecognition()
        }

        // -----------------------------
        // ADD TAGS BUTTON
        // -----------------------------
        addTagsButton.setOnClickListener {
            showTagDialog()
        }

        // -----------------------------
        // SAVE SESSION BUTTON
        // -----------------------------
        saveSessionButton.setOnClickListener {
            saveSession()
        }
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
            addMessage(spokenText, isUser = true)
            sendToAI(spokenText)
        }
    }

    // ---------------------------------------------------------
    // ADD MESSAGE TO CHAT
    // ---------------------------------------------------------
    private fun addMessage(text: String, isUser: Boolean) {
        messages.add(Message(text, isUser))
        adapter.notifyItemInserted(messages.size - 1)

        val recyclerView = findViewById<RecyclerView>(R.id.chatRecyclerView)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    // ---------------------------------------------------------
    // AI RESPONSE (placeholder)
    // ---------------------------------------------------------
    private fun sendToAI(userMessage: String) {
        val fakeResponse = "This is where the AI response will appear."
        addMessage(fakeResponse, isUser = false)
    }

    // ---------------------------------------------------------
    // TAG SELECTION DIALOG (XML-based, fully working)
    // ---------------------------------------------------------
    private fun showTagDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_tags, null)

        val listView = dialogView.findViewById<ListView>(R.id.tagListView)
        val customTagInput = dialogView.findViewById<EditText>(R.id.customTagInput)
        val saveTagsButton = dialogView.findViewById<Button>(R.id.saveTagsButton)

        // Set up list with preset tags
        listView.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_multiple_choice,
            presetTags
        )

        // Restore previously selected tags
        for (i in presetTags.indices) {
            listView.setItemChecked(i, selectedTags.contains(presetTags[i]))
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Select Tags")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        saveTagsButton.setOnClickListener {
            selectedTags.clear()

            // Add checked preset tags
            for (i in presetTags.indices) {
                if (listView.isItemChecked(i)) {
                    selectedTags.add(presetTags[i])
                }
            }

            // Add custom tag
            val customTag = customTagInput.text.toString().trim()
            if (customTag.isNotEmpty()) {
                selectedTags.add(customTag)
            }

            Toast.makeText(
                this,
                "Tags saved: ${selectedTags.joinToString(", ")}",
                Toast.LENGTH_SHORT
            ).show()

            dialog.dismiss()
        }

        dialog.show()
    }

    // ---------------------------------------------------------
    // SAVE SESSION
    // ---------------------------------------------------------
    private fun saveSession() {
        val tagList = if (selectedTags.isEmpty()) "No tags" else selectedTags.joinToString(", ")

        Toast.makeText(
            this,
            "Session saved with ${messages.size} messages and tags: $tagList",
            Toast.LENGTH_LONG
        ).show()

        // TODO: Replace with Room or Firebase persistence
    }
}

