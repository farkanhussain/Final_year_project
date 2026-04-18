package com.example.therapy_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionAdapter(
    private var sessions: List<TherapySession>
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.sessionTitle)
        val tags: TextView = itemView.findViewById(R.id.sessionTags)
        val preview: TextView = itemView.findViewById(R.id.sessionPreview)
        val timestamp: TextView = itemView.findViewById(R.id.sessionTimestamp)
        val manageIcon: ImageView = itemView.findViewById(R.id.manageSessionIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.session_item, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        // ⭐ Title (if you added title to your model)
        holder.title.text = session.title.ifBlank { "Untitled Session" }

        // ⭐ Tags
        holder.tags.text =
            if (session.tags.isNotEmpty()) "Tags: " + session.tags.joinToString(", ")
            else "Tags: None"

        // ⭐ Conversation preview (first message)
        val previewText = session.messages.firstOrNull()?.text ?: "No messages yet"
        holder.preview.text = previewText

        // ⭐ Timestamp → formatted date
        val date = Date(session.timestamp)
        val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        holder.timestamp.text = formatter.format(date)

        // ⭐ Manage session bottom sheet
        holder.manageIcon.setOnClickListener {
            val sheet = ManageSessionBottomSheet(session.id)

            sheet.show(
                (holder.itemView.context as AppCompatActivity).supportFragmentManager,
                "manageSession"
            )
        }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateList(newList: List<TherapySession>) {
        sessions = newList
        notifyDataSetChanged()
    }
}
