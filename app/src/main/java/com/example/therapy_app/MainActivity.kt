package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private val openAiKey = BuildConfig.OPENAI_API_KEY

    private var latestPrompts: List<String> = emptyList()

    private var latestInsights: List<String> = emptyList()

    // Add this at the top of your Activity





    private var selectedMood: String? = null   // optional, safe default

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)

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
        // LOAD USER DETAILS INTO NAV HEADER
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

        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // ----------------------------------------------------
        // NAVIGATION MENU
        // ----------------------------------------------------
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> drawerLayout.closeDrawers()
                R.id.nav_therapy -> startActivity(Intent(this, TherapyActivity::class.java))
                R.id.nav_mood_tracking -> startActivity(Intent(this, MoodTrackingActivity::class.java))
                R.id.nav_journaling -> startActivity(Intent(this, JournalingActivity::class.java))
                R.id.nav_articles -> startActivity(Intent(this, ArticlesActivity::class.java))

            }
            true
        }

        // ----------------------------------------------------
        // LOAD AI PROMPTS INTO THE CARD
        // ----------------------------------------------------
        loadPrompts()

        loadWeeklyMoodEmojisHome()

        loadTherapyInsights()



        // ----------------------------------------------------
        // CARD CLICK → OPEN NEW JOURNAL ENTRY
        // ----------------------------------------------------
        findViewById<View>(R.id.cardJournalPrompts).setOnClickListener {
            val intent = Intent(this, JournalEntryActivity::class.java)
            intent.putStringArrayListExtra("ai_prompts", ArrayList(latestPrompts))
            startActivity(intent)
        }

        findViewById<View>(R.id.cardMoodLog).setOnClickListener {
            startActivity(Intent(this, MoodTrackingActivity::class.java))
        }

        findViewById<View>(R.id.cardTherapyInsights).setOnClickListener {
            val intent = Intent(this, ChatActivity::class.java)
            intent.putStringArrayListExtra("therapy_insights", ArrayList(latestInsights))
            intent.putExtra("from_insights_card", true)
            startActivity(intent)

        }





    }

    // ====================================================
    // 🧠 LOAD PROMPTS INTO HOME CARD
    // ====================================================
    private fun loadPrompts() {

        val container = findViewById<LinearLayout>(R.id.homePromptContainer)
        container.removeAllViews()

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            showFallbackPrompts(container)
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("sessions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(8)
            .get()
            .addOnSuccessListener { result ->

                val sessions = result.documents

                if (sessions.isEmpty()) {
                    showFallbackPrompts(container)
                } else {
                    generateAIPrompts(container, sessions)
                }
            }
            .addOnFailureListener {
                showFallbackPrompts(container)
            }

    }


    // ====================================================
    // 🧠 GENERATE PROMPTS USING OPENAI
    // ====================================================
    // ====================================================
// 🧠 GENERATE PROMPTS USING OPENAI
// ====================================================
    private fun generateAIPrompts(
        container: LinearLayout,
        sessions: List<com.google.firebase.firestore.DocumentSnapshot>
    ) {

        val sessionText = sessions.joinToString("\n") { doc ->

            val messages = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()

            messages.joinToString("\n") { message ->
                val text = message["text"] as? String ?: ""
                val isUser = message["user"] as? Boolean ?: false
                if (text.isNotBlank()) {
                    if (isUser) "User: $text" else "Therapist: $text"
                } else ""
            }
        }

        callOpenAI(
            sessionText = sessionText,
            mood = selectedMood
        ) { prompts ->

            // ⭐ CRITICAL: Save prompts so they can be passed to JournalEntryActivity
            latestPrompts = prompts

            // ⭐ Display prompts inside the card
            container.removeAllViews()
            prompts.forEach { container.addView(createPromptView(it)) }
        }
    }


    // ====================================================
    // 🧠 FALLBACK PROMPTS
    // ====================================================
    private fun showFallbackPrompts(container: LinearLayout) {

        val fallback = listOf(
            "What emotion is most present right now?",
            "What thought keeps returning today?",
            "What would self-kindness look like today?"
        )

        // ⭐ Save fallback prompts
        latestPrompts = fallback

        container.removeAllViews()
        fallback.forEach { container.addView(createPromptView(it)) }
    }

    // ====================================================
    // 🧠 OPENAI CALL
    // ====================================================
    private fun callOpenAI(
        sessionText: String,
        mood: String?,
        callback: (List<String>) -> Unit
    ) {

        val client = OpenAI(token = openAiKey)

        CoroutineScope(Dispatchers.IO).launch {

            try {
                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = """
You are a therapeutic journaling assistant.
Generate 3 short CBT-style prompts.
Max 8–10 words each.
No numbering.
""".trimIndent()
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = """
MOOD: ${mood ?: "Not provided"}
SESSION NOTES:
$sessionText
""".trimIndent()
                            )
                        ),
                        temperature = 0.8
                    )
                )

                val rawText = response.choices.first().message?.content ?: ""

                val prompts = rawText
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .map { it.replace(Regex("^\\d+\\.?\\s*"), "") }
                    .take(3)

                withContext(Dispatchers.Main) { callback(prompts) }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {
                    callback(
                        listOf(
                            "What emotion is most present right now?",
                            "What thought keeps returning today?",
                            "What would self-kindness look like today?"
                        )
                    )
                }
            }
        }
    }

    // ====================================================
    // 🧠 UI FOR EACH PROMPT
    // ====================================================
    private fun createPromptView(prompt: String): View {

        val tv = TextView(this)
        tv.text = "• $prompt"
        tv.setTextColor(resources.getColor(R.color.white, null))
        tv.textSize = 14f
        tv.setPadding(0, 6, 0, 6)

        return tv
    }

    private fun loadWeeklyMoodEmojisHome() {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val monMood = findViewById<TextView>(R.id.homeMonMood)
        val tueMood = findViewById<TextView>(R.id.homeTueMood)
        val wedMood = findViewById<TextView>(R.id.homeWedMood)
        val thuMood = findViewById<TextView>(R.id.homeThuMood)
        val friMood = findViewById<TextView>(R.id.homeFriMood)
        val satMood = findViewById<TextView>(R.id.homeSatMood)
        val sunMood = findViewById<TextView>(R.id.homeSunMood)

        val monTime = findViewById<TextView>(R.id.homeMonTime)
        val tueTime = findViewById<TextView>(R.id.homeTueTime)
        val wedTime = findViewById<TextView>(R.id.homeWedTime)
        val thuTime = findViewById<TextView>(R.id.homeThuTime)
        val friTime = findViewById<TextView>(R.id.homeFriTime)
        val satTime = findViewById<TextView>(R.id.homeSatTime)
        val sunTime = findViewById<TextView>(R.id.homeSunTime)

        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("moods")
            .get()
            .addOnSuccessListener { result ->

                val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                val moodMap = mutableMapOf<Int, Pair<Int, Long>>() // dayOfWeek -> (mood, timestamp)

                for (doc in result) {
                    val mood = doc.getLong("mood")?.toInt() ?: continue
                    val timestamp = doc.getLong("timestamp") ?: continue

                    calendar.timeInMillis = timestamp
                    val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)

                    val existing = moodMap[day]
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

                monMood.text = mon.first; monTime.text = mon.second
                tueMood.text = tue.first; tueTime.text = tue.second
                wedMood.text = wed.first; wedTime.text = wed.second
                thuMood.text = thu.first; thuTime.text = thu.second
                friMood.text = fri.first; friTime.text = fri.second
                satMood.text = sat.first; satTime.text = sat.second
                sunMood.text = sun.first; sunTime.text = sun.second
            }
    }

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

    private fun loadTherapyInsights() {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val insightsContainer = findViewById<LinearLayout>(R.id.homeTherapyInsightsContainer)

        //  If therapist messages are already cached, skip Firestore entirely
        TherapyCache.cachedTherapistMessages?.let { cached ->
            val combined = cached.joinToString("\n")

            callOpenAIInsights(combined) { insights ->
                latestInsights = insights
                insightsContainer.removeAllViews()
                insights.forEach { insightsContainer.addView(createInsightView(it)) }
            }
            return
        }

        // Clear UI once at the start
        insightsContainer.removeAllViews()

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("sessions")
            .get()
            .addOnSuccessListener { result ->

                // 2️⃣ Extract therapist-only messages efficiently
                val therapistMessages = result.documents.flatMap { doc ->
                    val messages = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()
                    messages.asSequence()
                        .filter { msg -> msg["user"] == false }
                        .map { msg -> msg["text"] as? String ?: "" }
                        .toList()
                }

                if (therapistMessages.isEmpty()) {
                    insightsContainer.addView(
                        createInsightView("No insights yet — start a therapy session to receive guidance.")
                    )
                    return@addOnSuccessListener
                }

                // 3️⃣ Cache messages globally for next time
                TherapyCache.cachedTherapistMessages = therapistMessages

                val combined = therapistMessages.joinToString("\n")

                // 4️⃣ Call OpenAI once and update UI
                callOpenAIInsights(combined) { insights ->
                    latestInsights = insights
                    insightsContainer.removeAllViews()
                    insights.forEach { insightsContainer.addView(createInsightView(it)) }
                }
            }
    }




    private fun callOpenAIInsights(
        therapistText: String,
        callback: (List<String>) -> Unit
    ) {
        val client = OpenAI(token = openAiKey)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o-mini"),
                        messages = listOf(
                            ChatMessage(
                                role = ChatRole.System,
                                content = """
You are a supportive therapeutic assistant.
Summarise the therapist’s guidance into exactly 3 short, warm bullet points.
Use simple language. No clinical terms. No long sentences.
""".trimIndent()
                            ),
                            ChatMessage(
                                role = ChatRole.User,
                                content = therapistText
                            )
                        ),
                        temperature = 0.7
                    )
                )

                val raw = response.choices.first().message?.content ?: ""
                val insights = raw.lines()
                    .map { it.trim().removePrefix("- ").removePrefix("• ") }
                    .filter { it.isNotBlank() }
                    .take(3)

                withContext(Dispatchers.Main) { callback(insights) }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback(listOf("Unable to load insights right now."))
                }
            }
        }
    }



    private fun createInsightView(text: String): View {
        val tv = TextView(this)
        tv.text = "• $text"
        tv.setTextColor(resources.getColor(R.color.white, null))
        tv.textSize = 14f
        tv.setPadding(0, 6, 0, 6)
        return tv
    }

    private fun refreshInsightsFromCache() {
        val insightsContainer = findViewById<LinearLayout>(R.id.homeTherapyInsightsContainer)

        // If no cache exists, fall back to full load
        val cached = TherapyCache.cachedTherapistMessages ?: return loadTherapyInsights()

        val combined = cached.joinToString("\n")

        callOpenAIInsights(combined) { insights ->
            latestInsights = insights
            insightsContainer.removeAllViews()
            insights.forEach { insightsContainer.addView(createInsightView(it)) }
        }
    }


    override fun onResume() {
        super.onResume()
        refreshInsightsFromCache()   // 🔥 Regenerate insights using cached messages
    }






}
