package com.example.therapy_app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MoodAdapter(
    private var moods: List<MoodEntry>,
    private val onClick: (MoodEntry) -> Unit
) : RecyclerView.Adapter<MoodAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emoji: TextView = view.findViewById(R.id.moodEmoji)
        val time: TextView = view.findViewById(R.id.moodTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mood, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = moods.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val moodEntry = moods[position]

        holder.emoji.text = getMoodEmoji(moodEntry.mood)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeText = timeFormat.format(Date(moodEntry.timestamp))

        holder.time.text = timeText

        holder.itemView.setOnClickListener {
            onClick(moodEntry)
        }
    }

    // ✅ OPTION B: safe dataset refresh (no adapter recreation)
    fun updateData(newMoods: List<MoodEntry>) {
        moods = newMoods.sortedByDescending { it.timestamp }
        notifyDataSetChanged()
    }

    private fun getMoodEmoji(mood: Int): String {
        return when (mood) {
            1 -> "😢"
            2 -> "😕"
            3 -> "😐"
            4 -> "🙂"
            5 -> "😄"
            else -> "-"
        }
    }
}