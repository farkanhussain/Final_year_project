package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.ImageButton
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import java.util.Locale

class ChatActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()

    private val VOICE_REQUEST_CODE = 101

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

    // Placeholder for OpenAI API call
    private fun sendToAI(userMessage: String) {
        val fakeResponse = "This is where the AI response will appear."
        addMessage(fakeResponse, isUser = false)
    }
}
