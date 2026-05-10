package com.example.therapy_app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import org.json.JSONObject
import android.util.Log
import com.github.mikephil.charting.highlight.Highlight


class ArticlesActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: ArticleAdapter

    private val allArticles = mutableListOf<Article>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_articles)

        drawerLayout = findViewById(R.id.drawer_layout_articles)
        val navView: NavigationView = findViewById(R.id.nav_view_articles)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_articles)

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
        val user = auth.currentUser
        val userId = user?.uid

        val headerView = navView.getHeaderView(0)
        val nameTextView = headerView.findViewById<TextView>(R.id.header_profile_name)
        val emailTextView = headerView.findViewById<TextView>(R.id.header_profile_email)

        if (userId != null) {
            db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        nameTextView.text = document.getString("name") ?: "Profile"
                        emailTextView.text = document.getString("email") ?: user.email ?: "Unknown"
                    }
                }
        }

        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> startActivity(Intent(this, MainActivity::class.java))
                R.id.nav_therapy -> startActivity(Intent(this, TherapyActivity::class.java))
                R.id.nav_mood_tracking -> startActivity(Intent(this, MoodTrackingActivity::class.java))
                R.id.nav_journaling -> startActivity(Intent(this, JournalingActivity::class.java))
                R.id.nav_articles -> startActivity(Intent(this, ArticlesActivity::class.java))

            }
            true
        }

        // ----------------------------------------------------
        // SETUP RECYCLER VIEW
        // ----------------------------------------------------
        val recyclerView = findViewById<RecyclerView>(R.id.articlesRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = ArticleAdapter(allArticles) { article ->
            openArticle(article.url)
        }

        recyclerView.adapter = adapter

        // ----------------------------------------------------
        // SEARCH + TAG FILTERING
        // ----------------------------------------------------
        val searchInput = findViewById<TextInputEditText>(R.id.searchInput_articles)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterArticles(s.toString(), getSelectedTags())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearFocusWhenClickingOutside()

        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup_articles)
        setupTagChips(chipGroup)

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            filterArticles(searchInput.text.toString(), getSelectedTags())
        }
    }

    override fun onResume() {
        super.onResume()


        loadArticlesFromFirestore()



    }




    // ----------------------------------------------------
    // LOAD ARTICLES FROM FIRESTORE
    // ----------------------------------------------------


    private fun loadArticlesFromFirestore() {


        db.collection("articles")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { result ->

                Log.d("ARTICLES_DEBUG", "Firestore SUCCESS")
                Log.d("ARTICLES_DEBUG", "Firestore returned ${result.size()} articles")

                allArticles.clear()

                for (doc in result) {
                    Log.d("ARTICLES_DEBUG", "Document data: ${doc.data}")
                    val article = doc.toObject(Article::class.java)
                    Log.d("ARTICLES_DEBUG", "Mapped Article: $article")
                    allArticles.add(article)
                }

                Log.d("ARTICLES_DEBUG", "Articles added to list: ${allArticles.size}")

                adapter.updateList(allArticles)
                Log.d("ARTICLES_DEBUG", "Adapter updated with ${allArticles.size} articles")

                // 🔥 AI RECOMMENDATIONS (keep commented for now)
                lifecycleScope.launch {
                    try {
                        Log.d("ARTICLES_DEBUG", "AI block started")

                        val recommended = getAIRecommendedArticles()
                        Log.d("ARTICLES_DEBUG", "AI recommended: $recommended")

                        val sorted = allArticles.sortedByDescending { it.title in recommended }
                        adapter.updateList(sorted)

                        Log.d("ARTICLES_DEBUG", "Adapter updated with sorted list")

                    } catch (e: Exception) {
                        Log.e("ARTICLES_DEBUG", "AI ERROR: ${e.message}")
                    }
                }

            }
            .addOnFailureListener { e ->
                Log.e("ARTICLES_DEBUG", "Firestore ERROR: ${e.message}")
            }
    }


    // ----------------------------------------------------
// AI RECOMMENDATIONS
// ----------------------------------------------------
    private suspend fun getAIRecommendedArticles(): List<String> {

        Log.d("ARTICLES_DEBUG", "getAIRecommendedArticles() called")

        val user = auth.currentUser ?: return emptyList()
        val userId = user.uid

        // 1. Load therapy sessions (last 5)
        val sessionsSnapshot = db.collection("users")
            .document(userId)
            .collection("sessions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .await()

        val sessionSummaries = sessionsSnapshot.documents.map { doc ->
            val messages = doc.get("messages") as? List<Map<String, Any>> ?: emptyList()
            val preview = messages.lastOrNull()?.get("text") ?: ""
            "- ${preview.toString()}"
        }.joinToString("\n")

        // 2. Load journal entries (last 5)
        val journalSnapshot = db.collection("users")
            .document(userId)
            .collection("journal_entries")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .await()

        val journalSummaries = journalSnapshot.documents.map { doc ->
            val content = doc.getString("content") ?: ""
            "- $content"
        }.joinToString("\n")

        // 3. Mood trends (last 7)
        val moodSnapshot = db.collection("users")
            .document(userId)
            .collection("moods")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(7)
            .get()
            .await()

        val moodTrends = moodSnapshot.documents.map { it.getString("mood") ?: "" }
            .joinToString(", ")

        // 4. Available articles
        val articlesList = allArticles.joinToString("\n") { article ->
            "- ${article.title} (tags: ${article.tags.joinToString()})"
        }

        // 5. Prompt
        val prompt = """
        You are an assistant inside a mental health app.
        Recommend the most relevant articles based on the user’s recent sessions,
        journal entries, and mood patterns.

        Therapy Sessions:
        $sessionSummaries

        Journal Entries:
        $journalSummaries

        Mood Trends:
        $moodTrends

        Available Articles:
        $articlesList

        Return ONLY a JSON object in this format:

        {
          "recommendations": [
            {
              "title": "",
              "reason": ""
            }
          ]
        }
    """.trimIndent()

        return try {

            // 6. OpenAI API call
            val client = OpenAI(BuildConfig.OPENAI_API_KEY)

            val request = ChatCompletionRequest(
                model = ModelId("gpt-4o-mini"),
                messages = listOf(
                    ChatMessage(
                        role = ChatRole.User,
                        content = prompt
                    )
                )
            )

            val response = client.chatCompletion(request)

            // FIX: Convert content blocks to a single string
            val json = response.choices.first().message?.content ?: ""


            Log.d("ARTICLES_DEBUG", "AI raw response: $json")

            // 7. Safe JSON parsing
            val recommendedTitles = mutableListOf<String>()

            try {
                val jsonObj = JSONObject(json)
                val arr = jsonObj.optJSONArray("recommendations")

                if (arr == null) {
                    Log.e("ARTICLES_DEBUG", "JSON ERROR: 'recommendations' array missing")
                    return emptyList()
                }

                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i)

                    if (item == null) {
                        Log.e("ARTICLES_DEBUG", "JSON ERROR: item at index $i is null")
                        continue
                    }

                    val title = item.optString("title", "")

                    if (title.isNotBlank()) {
                        recommendedTitles.add(title)
                    } else {
                        Log.e("ARTICLES_DEBUG", "JSON WARNING: missing title at index $i")
                    }
                }

            } catch (e: Exception) {
                Log.e("ARTICLES_DEBUG", "JSON PARSE EXCEPTION: ${e.message}")
                return emptyList()
            }

            recommendedTitles

        } catch (e: Exception) {
            Log.e("ARTICLES_DEBUG", "AI REQUEST ERROR: ${e.message}")
            emptyList()
        }
    }




    // ----------------------------------------------------
    // FILTER ARTICLES
    // ----------------------------------------------------
    private fun filterArticles(query: String, tags: List<String>) {
        val filtered = allArticles.filter { article ->

            val matchesQuery =
                article.title.contains(query, ignoreCase = true) ||
                        article.summary.contains(query, ignoreCase = true)

            val matchesTags =
                tags.isEmpty() || article.tags.any { it in tags }

            matchesQuery && matchesTags
        }

        adapter.updateList(filtered)
    }

    private fun getSelectedTags(): List<String> {
        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup_articles)
        return chipGroup.checkedChipIds.map { id ->
            findViewById<Chip>(id).text.toString()
        }
    }

    private fun setupTagChips(chipGroup: ChipGroup) {
        val tags = listOf("Anxiety", "CBT", "Stress", "Depression", "Mindfulness")

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isClickable = true

                setChipBackgroundColorResource(R.color.red_dark)
                setTextColor(resources.getColor(R.color.white, theme))
                chipStrokeColor = resources.getColorStateList(R.color.red_dark, theme)
                chipStrokeWidth = 1f
                rippleColor = ColorStateList.valueOf(Color.parseColor("#8B0000"))
            }

            chipGroup.addView(chip)
        }
    }

    // ----------------------------------------------------
    // OPEN ARTICLE IN BROWSER
    // ----------------------------------------------------
    private fun openArticle(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun clearFocusWhenClickingOutside() {
        val root = findViewById<ConstraintLayout>(R.id.root_articles_layout)
        root.setOnClickListener {
            findViewById<TextInputEditText>(R.id.searchInput_articles).clearFocus()
        }
    }
}
