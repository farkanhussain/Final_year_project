package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class TherapyActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: SessionAdapter
    private lateinit var allSessions: MutableList<Session>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_therapy)

        drawerLayout = findViewById(R.id.drawer_layout_therapy)
        val navView: NavigationView = findViewById(R.id.nav_view_therapy)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_therapy)

        // Set toolbar as ActionBar
        setSupportActionBar(toolbar)

        // Enable burger menu toggle
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
        // SET USER EMAIL IN DRAWER HEADER (Firebase)
        // ----------------------------------------------------
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: "Unknown"

        val headerView = navView.getHeaderView(0)
        val emailTextView = headerView.findViewById<TextView>(R.id.header_profile_email)
        emailTextView.text = email

        // HEADER CLICK → Profile
        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // ----------------------------------------------------
        // NAVIGATION MENU HANDLING
        // ----------------------------------------------------
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

        // ----------------------------------------------------
        // RECYCLER VIEW SETUP
        // ----------------------------------------------------
        val recyclerView = findViewById<RecyclerView>(R.id.sessionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Load sessions (replace with DB later)
        allSessions = loadSessions()

        adapter = SessionAdapter(allSessions)
        recyclerView.adapter = adapter

        // ----------------------------------------------------
        // SEARCH BAR LOGIC
        // ----------------------------------------------------
        val searchInput = findViewById<TextInputEditText>(R.id.searchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterSessions(s.toString(), getSelectedTags())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // ----------------------------------------------------
        // TAG CHIP FILTERS
        // ----------------------------------------------------
        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup)
        setupTagChips(chipGroup)

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            filterSessions(searchInput.text.toString(), getSelectedTags())
        }

        // ----------------------------------------------------
        // FAB → ADD NEW SESSION
        // ----------------------------------------------------
        val fab = findViewById<FloatingActionButton>(R.id.newSessionFab)
        fab.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    // ----------------------------------------------------
    // FILTERING LOGIC
    // ----------------------------------------------------
    private fun filterSessions(query: String, tags: List<String>) {
        val filtered = allSessions.filter { session ->

            val matchesQuery = session.title.contains(query, ignoreCase = true) ||
                    session.notes.contains(query, ignoreCase = true)

            val matchesTags = if (tags.isEmpty()) true
            else tags.any { tag -> session.tags.contains(tag) }

            matchesQuery && matchesTags
        }

        adapter.updateList(filtered)
    }

    private fun getSelectedTags(): List<String> {
        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup)
        return chipGroup.checkedChipIds.map { id ->
            findViewById<Chip>(id).text.toString()
        }
    }

    // ----------------------------------------------------
    // TAG CHIP SETUP
    // ----------------------------------------------------
    private fun setupTagChips(chipGroup: ChipGroup) {
        val tags = listOf("CBT", "Mindfulness", "Trauma", "Anxiety", "Depression")

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
            }
            chipGroup.addView(chip)
        }
    }

    // ----------------------------------------------------
    // TEMPORARY DUMMY DATA
    // ----------------------------------------------------
    private fun loadSessions(): MutableList<Session> {
        return mutableListOf(
            Session("Session 1", "Talked about anxiety", listOf("Anxiety")),
            Session("Session 2", "Mindfulness practice", listOf("Mindfulness")),
            Session("Session 3", "CBT reframing", listOf("CBT"))
        )
    }
}

