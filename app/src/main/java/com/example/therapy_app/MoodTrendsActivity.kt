package com.example.therapy_app

import android.os.Bundle
import android.widget.Button
import android.widget.CalendarView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class MoodTrendsActivity : AppCompatActivity() {

    private var selectedMoodDocId: String? = null
    private var startOfDay: Long = 0
    private var endOfDay: Long = 0

    private lateinit var moodText: TextView
    private lateinit var moodList: RecyclerView
    private lateinit var moodAdapter: MoodAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_trends)

        val calendarView = findViewById<CalendarView>(R.id.calendarView)
        moodText = findViewById(R.id.moodText)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_mood_trends)
        val btnEditMood = findViewById<Button>(R.id.btnEditMood)
        val btnRemoveMood = findViewById<Button>(R.id.btnRemoveMood)

        moodList = findViewById(R.id.moodList)
        moodList.layoutManager = LinearLayoutManager(this)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener {
            finish() // 🔥
        }

        // INIT ADAPTER
        moodAdapter = MoodAdapter(emptyList()) { selected ->
            selectedMoodDocId = selected.docId
            moodText.text = "Selected: ${getMoodEmoji(selected.mood)}"
        }
        moodList.adapter = moodAdapter

        // EDIT MOOD
        btnEditMood.setOnClickListener {
            val docId = selectedMoodDocId ?: return@setOnClickListener
            showMoodEditDialog(docId)
        }

        // DELETE MOOD
        btnRemoveMood.setOnClickListener {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val docId = selectedMoodDocId ?: return@setOnClickListener

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("moods")
                .document(docId)
                .delete()
                .addOnSuccessListener {
                    selectedMoodDocId = null
                    loadMoodsForSelectedDay(userId)
                }
        }

        // DATE SELECTED
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->

            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
            }

            startOfDay = selectedDate.timeInMillis
            endOfDay = startOfDay + (24 * 60 * 60 * 1000)

            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnDateChangeListener
            loadMoodsForSelectedDay(userId)
        }
    }


    override fun onResume() {
        super.onResume()

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // 🔥 IMPORTANT: ensure we re-query WITH valid date bounds
        if (startOfDay == 0L || endOfDay == 0L) {
            val today = Calendar.getInstance()
            today.set(Calendar.HOUR_OF_DAY, 0)
            today.set(Calendar.MINUTE, 0)
            today.set(Calendar.SECOND, 0)
            today.set(Calendar.MILLISECOND, 0)

            startOfDay = today.timeInMillis
            endOfDay = startOfDay + (24 * 60 * 60 * 1000)
        }

        loadMoodsForSelectedDay(userId)
    }

    // ----------------------------
    // DATA LOAD
    // ----------------------------
    private fun loadMoodsForSelectedDay(userId: String) {

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("moods")
            .whereGreaterThanOrEqualTo("timestamp", startOfDay)
            .whereLessThan("timestamp", endOfDay)
            .get()
            .addOnSuccessListener { result ->

                if (result.isEmpty) {
                    moodText.text = "No mood logged for this day"
                    selectedMoodDocId = null
                    moodAdapter.updateData(emptyList())
                    return@addOnSuccessListener
                }

                val moods = result.documents.map {
                    MoodEntry(
                        mood = it.getLong("mood")?.toInt() ?: 0,
                        timestamp = it.getLong("timestamp") ?: 0,
                        docId = it.id
                    )
                }.sortedByDescending { it.timestamp }

                val latest = moods.first()
                selectedMoodDocId = latest.docId
                val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(latest.timestamp))

                moodText.text = "Latest: ${getMoodEmoji(latest.mood)} at $time"

                moodAdapter.updateData(moods)
            }
    }

    // ----------------------------
    // EMOJI MAP
    // ----------------------------
    private fun getMoodEmoji(mood: Int): String {
        return when (mood) {
            1 -> "😢 Very Sad"
            2 -> "😕 Sad"
            3 -> "😐 Neutral"
            4 -> "🙂 Happy"
            5 -> "😄 Very Happy"
            else -> "No data"
        }
    }

    // ----------------------------
    // EDIT DIALOG (FIXED)
    // ----------------------------
    private fun showMoodEditDialog(docId: String) {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val moods = listOf(1, 2, 3, 4, 5)
        val moodEmojis = listOf("😢 Very Sad", "😕 Sad", "😐 Neutral", "🙂 Happy", "😄 Very Happy")

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Change Mood")

        builder.setSingleChoiceItems(moodEmojis.toTypedArray(), -1) { dialog, which ->

            val selectedMood = moods[which]

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("moods")
                .document(docId)
                .update(
                    mapOf(
                        "mood" to selectedMood,
                        "timestamp" to System.currentTimeMillis() // ✅ FIX: ensures refresh + sorting
                    )
                )
                .addOnSuccessListener {
                    loadMoodsForSelectedDay(userId) // 🔥 full refresh
                }

            dialog.dismiss()
        }

        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }
}