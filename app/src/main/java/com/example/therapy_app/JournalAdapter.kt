package com.example.therapy_app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import android.view.View
class JournalAdapter(
    initialEntries: List<JournalEntry>,
    private val onOptionsClick: (JournalEntry) -> Unit
) : RecyclerView.Adapter<JournalAdapter.ViewHolder>() {

    val entries = initialEntries.toMutableList()

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.journalTitle)
        val timestamp: TextView = view.findViewById(R.id.journalTimestamp)
        val mood: TextView = view.findViewById(R.id.journalMood)
        val tags: TextView = view.findViewById(R.id.journalTags)
        val preview: TextView = view.findViewById(R.id.journalPreview)
        val optionsIcon: View = view.findViewById(R.id.journalOptionsIcon)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_journal_entry, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val entry = entries[position]

        holder.title.text = entry.title
        holder.preview.text = entry.content

        holder.mood.text = entry.mood?.let {
            "Mood: $it"
        } ?: "Mood: N/A"

        holder.timestamp.text = java.text.SimpleDateFormat(
            "dd MMM yyyy",
            java.util.Locale.getDefault()
        ).format(java.util.Date(entry.timestamp))

        holder.tags.text = if (entry.tags.isNotEmpty()) {
            "Tags: ${entry.tags.joinToString(", ")}"
        } else {
            "Tags: None"
        }

        holder.optionsIcon.setOnClickListener {
            onOptionsClick(entry)
        }
    }

    override fun getItemCount(): Int = entries.size

    // -------------------------------
    // SAFE UPDATE METHOD (IMPORTANT)
    // -------------------------------
    fun updateList(newList: List<JournalEntry>) {
        entries.clear()
        entries.addAll(newList)
        notifyDataSetChanged()
    }

    // -------------------------------
    // SAFE REMOVE METHOD (FIXED LOCATION)
    // -------------------------------
    fun removeItem(entry: JournalEntry) {
        val index = entries.indexOfFirst { it.id == entry.id }

        if (index != -1) {
            entries.removeAt(index)
            notifyItemRemoved(index)
        }
    }
}