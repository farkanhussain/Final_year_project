package com.example.therapy_app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.therapy_app.ChatAdapter
import com.example.therapy_app.Message
import com.example.therapy_app.TherapySession

import com.example.therapy_app.R
import com.google.android.material.appbar.MaterialToolbar


class ChatHistoryActivity : AppCompatActivity() {

    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<Message>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_history)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_history)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val recyclerView = findViewById<RecyclerView>(R.id.chatHistoryRecyclerView)
        adapter = ChatAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val sessionId = intent.getStringExtra("SESSION_ID")
        if (sessionId != null) loadSession(sessionId)
    }

    private fun loadSession(id: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .collection("sessions")
            .document(id)
            .get()
            .addOnSuccessListener { doc ->
                val session = doc.toObject(TherapySession::class.java) ?: return@addOnSuccessListener

                messages.clear()
                messages.addAll(session.messages)
                adapter.notifyDataSetChanged()
            }
    }
}
