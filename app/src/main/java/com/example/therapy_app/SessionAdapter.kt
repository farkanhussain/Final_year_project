package com.example.therapy_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView

class SessionAdapter(
    private var sessions: List<Session>
) : RecyclerView.Adapter<SessionAdapter.SessionViewHolder>() {

    inner class SessionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(R.id.sessionTitle)
        val notes: TextView = itemView.findViewById(R.id.sessionNotes)
        val manageIcon: ImageView = itemView.findViewById(R.id.manageSessionIcon)   // ⭐ ADD THIS
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SessionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.session_item, parent, false)
        return SessionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SessionViewHolder, position: Int) {
        val session = sessions[position]

        holder.title.text = session.title
        holder.notes.text = session.notes

        // ⭐ THIS IS WHERE THE BOTTOM SHEET IS TRIGGERED
        holder.manageIcon.setOnClickListener {
            val sheet = ManageSessionBottomSheet(session.id)

            sheet.show(
                (holder.itemView.context as AppCompatActivity).supportFragmentManager,
                "manageSession"
            )
        }
    }

    override fun getItemCount(): Int = sessions.size

    fun updateList(newList: List<Session>) {
        sessions = newList
        notifyDataSetChanged()
    }
}
