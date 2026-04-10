package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: "Unknown"

        val headerView = navView.getHeaderView(0)
        val emailTextView = headerView.findViewById<TextView>(R.id.header_profile_email)
        emailTextView.text = email

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
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }

        val recyclerView = findViewById<RecyclerView>(R.id.sessionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        allSessions = loadSessions()
        adapter = SessionAdapter(allSessions)
        recyclerView.adapter = adapter

        // ----------------------------------------------------
        // SEARCH BAR LOGIC
        // ----------------------------------------------------
        val searchInput = findViewById<TextInputEditText>(R.id.searchInput)
        val searchBar = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.searchBar)

        searchInput.clearFocus()

        // DEBUG: See if focus actually changes
        searchInput.setOnFocusChangeListener { _, hasFocus ->
            Log.d("FOCUS_DEBUG", "SearchInput focus = $hasFocus")

            if (!hasFocus) {
                searchBar.post {
                    val currentHint = searchBar.hint
                    searchBar.hint = null
                    searchBar.hint = currentHint
                }
            }
        }

        // ----------------------------------------------------
        // FORCE SEARCH BAR TO LOSE FOCUS WHEN CLICKING OUTSIDE
        // ----------------------------------------------------
        clearFocusWhenClickingOutside()

        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup)
        setupTagChips(chipGroup)

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            filterSessions(searchInput.text.toString(), getSelectedTags())
        }

        val fab = findViewById<FloatingActionButton>(R.id.newSessionFab)
        fab.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    // ----------------------------------------------------
    // FORCE CLEAR FOCUS WHEN CLICKING OUTSIDE SEARCH BAR
    // ----------------------------------------------------
    private fun clearFocusWhenClickingOutside() {
        val root = findViewById<ConstraintLayout>(R.id.root_therapy_layout)

        root.setOnClickListener {
            val searchInput = findViewById<TextInputEditText>(R.id.searchInput)
            searchInput.clearFocus()
        }
    }

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

    private fun setupTagChips(chipGroup: ChipGroup) {
        val tags = listOf("CBT", "Mindfulness", "Trauma", "Anxiety", "Depression")

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                isClickable = true

                setChipBackgroundColorResource(R.color.red_dark)
                setTextColor(resources.getColor(R.color.white, theme))
                chipStrokeColor = resources.getColorStateList(R.color.red_dark, theme)
                chipStrokeWidth = 1f
                rippleColor = null
            }

            chipGroup.addView(chip)
        }
    }

    private fun loadSessions(): MutableList<Session> {
        return mutableListOf(
            Session("1","Session 1", "Talked about anxiety", listOf("Anxiety")),
            Session("2","Session 2", "Mindfulness practice", listOf("Mindfulness")),
            Session("3","Session 3", "CBT reframing", listOf("CBT"))
        )
    }
}
