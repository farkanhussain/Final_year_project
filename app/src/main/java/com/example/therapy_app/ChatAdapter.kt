package com.example.therapy_app

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private val messages: List<Message>
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val bubble: TextView = itemView.findViewById(R.id.messageBubble)
        val container: LinearLayout = itemView as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.message_item, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]

        holder.bubble.text = message.text

        if (message.isUser) {
            // User message → right side, blue bubble
            holder.bubble.setBackgroundResource(R.drawable.message_bg_user)
            holder.bubble.setTextColor(0xFFFFFFFF.toInt())

            val params = holder.bubble.layoutParams as LinearLayout.LayoutParams
            params.gravity = Gravity.END
            holder.bubble.layoutParams = params

        } else {
            // AI message → left side, grey bubble
            holder.bubble.setBackgroundResource(R.drawable.message_bg_ai)
            holder.bubble.setTextColor(0xFF000000.toInt())

            val params = holder.bubble.layoutParams as LinearLayout.LayoutParams
            params.gravity = Gravity.START
            holder.bubble.layoutParams = params
        }
    }

    override fun getItemCount(): Int = messages.size
}
