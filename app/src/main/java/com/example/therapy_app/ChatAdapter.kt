package com.example.therapy_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: MutableList<Message>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_USER = 1
        private const val TYPE_AI = 2
    }

    // ---------------- VIEW HOLDERS ----------------

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.messageBubble)
    }

    class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.messageBubble)
    }

    // ---------------- VIEW TYPE ----------------

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].user) TYPE_USER else TYPE_AI
    }

    // ---------------- CREATE ----------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        return if (viewType == TYPE_USER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.message_item_user, parent, false)
            UserViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.message_item_ai, parent, false)
            AiViewHolder(view)
        }
    }

    // ---------------- BIND ----------------

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {

        val message = messages[position]

        when (holder) {

            is UserViewHolder -> {
                holder.bubble.text = message.text
            }

            is AiViewHolder -> {
                holder.bubble.text = message.text
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}