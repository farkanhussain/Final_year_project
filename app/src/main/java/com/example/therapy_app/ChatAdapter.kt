package com.example.therapy_app

import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.util.Log
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
        private const val TYPE_SUMMARY = 3
    }

    // ---------------- VIEW HOLDERS ----------------

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.messageBubble)
    }

    class AiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.messageBubble)
    }

    class SummaryViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.messageBubble)
    }

    // ---------------- VIEW TYPE ----------------

    override fun getItemViewType(position: Int): Int {
        val msg = messages[position]

        return when (msg.type) {
            MessageType.SUMMARY -> TYPE_SUMMARY
            MessageType.NORMAL -> if (msg.user) TYPE_USER else TYPE_AI
            else -> TYPE_AI
        }
    }

    // ---------------- CREATE ----------------

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_USER -> {
                val view = inflater.inflate(R.layout.message_item_user, parent, false)
                UserViewHolder(view)
            }
            TYPE_AI -> {
                val view = inflater.inflate(R.layout.message_item_ai, parent, false)
                AiViewHolder(view)
            }
            TYPE_SUMMARY -> {
                val view = inflater.inflate(R.layout.message_item_ai, parent, false)
                SummaryViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.message_item_ai, parent, false)
                AiViewHolder(view)
            }
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

                // ⭐ Make URLs clickable
                holder.bubble.autoLinkMask = Linkify.WEB_URLS
                holder.bubble.linksClickable = true
                holder.bubble.movementMethod = LinkMovementMethod.getInstance()
            }

            is SummaryViewHolder -> {
                holder.bubble.text = message.text
            }

            else -> {
                Log.w("ChatAdapter", "Unhandled ViewHolder type at position $position")
            }
        }
    }

    override fun getItemCount(): Int = messages.size
}
