package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.*
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MoodTrackingActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private var selectedMood = -1

    private fun moodToEmoji(mood: Int): String {
        return when (mood) {
            1 -> "😢"
            2 -> "😕"
            3 -> "😐"
            4 -> "🙂"
            5 -> "😄"
            else -> ""
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mood_tracking)

        drawerLayout = findViewById(R.id.drawer_layout_mood_tracking)
        val navView: NavigationView = findViewById(R.id.nav_view_mood_tracking)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_mood_tracking)

        // UI Elements
        val moodLayout = findViewById<LinearLayout>(R.id.moodSelector)
        val submitBtn = findViewById<Button>(R.id.submitMood)
        val journalBtn = findViewById<Button>(R.id.goToJournal)
        val trendsBtn = findViewById<Button>(R.id.viewTrends)





        journalBtn.setOnClickListener {
            startActivity(Intent(this, JournalingActivity::class.java))
        }



        // Set toolbar
        setSupportActionBar(toolbar)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.Open_Drawer,
            R.string.Close_Drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // ----------------------------------------------------
        // EMOJI SELECTION LOGIC
        // ----------------------------------------------------
        for (i in 0 until moodLayout.childCount) {
            val emoji = moodLayout.getChildAt(i) as TextView

            emoji.setOnClickListener {
                selectedMood = i + 1

                // Reset all emojis
                for (j in 0 until moodLayout.childCount) {
                    moodLayout.getChildAt(j).alpha = 0.5f
                }

                // Highlight selected
                emoji.alpha = 1f
            }
        }

        // ----------------------------------------------------
        // SAVE MOOD TO FIRESTORE
        // ----------------------------------------------------
        submitBtn.setOnClickListener {

            val userId = FirebaseAuth.getInstance().currentUser?.uid
                ?: run {
                    Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

            if (selectedMood == -1) {
                Toast.makeText(this, "Please select a mood", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val data = hashMapOf(
                "mood" to selectedMood,
                "timestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("moods")
                .add(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Mood saved!", Toast.LENGTH_SHORT).show()
                    loadWeeklyMoodEmojis()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to save mood", Toast.LENGTH_SHORT).show()
                }
        }

        // ----------------------------------------------------
        // VIEW TRENDS BUTTON
        // ----------------------------------------------------
        trendsBtn.setOnClickListener {
            startActivity(Intent(this, MoodTrendsActivity::class.java))
        }

        // ----------------------------------------------------
        // LOAD USER DETAILS (existing code)
        // ----------------------------------------------------
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid

        val headerView = navView.getHeaderView(0)
        val nameTextView = headerView.findViewById<TextView>(R.id.header_profile_name)
        val emailTextView = headerView.findViewById<TextView>(R.id.header_profile_email)

        if (userId != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        nameTextView.text = document.getString("name") ?: "Profile"
                        emailTextView.text = document.getString("email") ?: user.email ?: "Unknown"
                    }
                }
                .addOnFailureListener {
                    nameTextView.text = "Profile"
                    emailTextView.text = user?.email ?: "Unknown"
                }
        }

        // Header click
        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // Navigation menu
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> startActivity(Intent(this, MainActivity::class.java))
                R.id.nav_therapy -> startActivity(Intent(this, TherapyActivity::class.java))
                R.id.nav_mood_tracking -> startActivity(Intent(this, MoodTrackingActivity::class.java))
                R.id.nav_journaling -> startActivity(Intent(this, JournalingActivity::class.java))
                R.id.nav_articles -> startActivity(Intent(this, ArticlesActivity::class.java))
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }

        // Load chart initially
        loadWeeklyMoodEmojis()
    }

    override fun onResume() {
        super.onResume()
        loadWeeklyMoodEmojis()
    }

    // ----------------------------------------------------
    // LOAD WEEKLY CHART DATA
    // ----------------------------------------------------
    private fun loadWeeklyMoodEmojis() {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val monMood = findViewById<TextView>(R.id.dayMonMood)
        val tueMood = findViewById<TextView>(R.id.dayTueMood)
        val wedMood = findViewById<TextView>(R.id.dayWedMood)
        val thuMood = findViewById<TextView>(R.id.dayThuMood)
        val friMood = findViewById<TextView>(R.id.dayFriMood)
        val satMood = findViewById<TextView>(R.id.daySatMood)
        val sunMood = findViewById<TextView>(R.id.daySunMood)

        val monTime = findViewById<TextView>(R.id.dayMonTimestamp)
        val tueTime = findViewById<TextView>(R.id.dayTueTimestamp)
        val wedTime = findViewById<TextView>(R.id.dayWedTimestamp)
        val thuTime = findViewById<TextView>(R.id.dayThuTimestamp)
        val friTime = findViewById<TextView>(R.id.dayFriTimestamp)
        val satTime = findViewById<TextView>(R.id.daySatTimestamp)
        val sunTime = findViewById<TextView>(R.id.daySunTimestamp)

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("moods")
            .get(com.google.firebase.firestore.Source.SERVER)
            .addOnSuccessListener { result ->

                val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))

                val moodMap = mutableMapOf<Int, Pair<Int, Long>>()
                // dayOfWeek -> (mood, timestamp)

                for (doc in result) {

                    val mood = doc.getLong("mood")?.toInt() ?: continue
                    val timestamp = doc.getLong("timestamp") ?: continue

                    calendar.timeInMillis = timestamp
                    val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)

                    val existing = moodMap[day]

                    // keep MOST RECENT entry per day
                    if (existing == null || timestamp > existing.second) {
                        moodMap[day] = Pair(mood, timestamp)
                    }
                }

                fun format(day: Int): Pair<String, String> {
                    val data = moodMap[day]
                    return if (data != null) {
                        moodToEmoji(data.first) to timeFormat.format(java.util.Date(data.second))
                    } else {
                        "" to "--"
                    }
                }

                val mon = format(java.util.Calendar.MONDAY)
                val tue = format(java.util.Calendar.TUESDAY)
                val wed = format(java.util.Calendar.WEDNESDAY)
                val thu = format(java.util.Calendar.THURSDAY)
                val fri = format(java.util.Calendar.FRIDAY)
                val sat = format(java.util.Calendar.SATURDAY)
                val sun = format(java.util.Calendar.SUNDAY)

                monMood.text = mon.first
                monTime.text = mon.second

                tueMood.text = tue.first
                tueTime.text = tue.second

                wedMood.text = wed.first
                wedTime.text = wed.second

                thuMood.text = thu.first
                thuTime.text = thu.second

                friMood.text = fri.first
                friTime.text = fri.second

                satMood.text = sat.first
                satTime.text = sat.second

                sunMood.text = sun.first
                sunTime.text = sun.second
            }
    }
}

