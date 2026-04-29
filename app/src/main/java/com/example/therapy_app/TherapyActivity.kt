package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import android.widget.Toast
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
import com.google.firebase.firestore.FirebaseFirestore
import android.content.res.ColorStateList
import android.graphics.Color

class TherapyActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var adapter: SessionAdapter

    private val allSessions = mutableListOf<TherapySession>()
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

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
                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }

        // ----------------------------------------------------
        // SETUP RECYCLER VIEW
        // ----------------------------------------------------
        val recyclerView = findViewById<RecyclerView>(R.id.sessionRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        adapter = SessionAdapter(allSessions) { sessionId ->
            showManageBottomSheet(sessionId)
        }

        recyclerView.adapter = adapter

        // ----------------------------------------------------
        // SEARCH + TAG FILTERING
        // ----------------------------------------------------
        val searchInput = findViewById<TextInputEditText>(R.id.searchInput)

        searchInput.clearFocus()

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterSessions(s.toString(), getSelectedTags())
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        clearFocusWhenClickingOutside()

        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup)
        setupTagChips(chipGroup)

        chipGroup.setOnCheckedStateChangeListener { _, _ ->
            filterSessions(searchInput.text.toString(), getSelectedTags())
        }

        // ----------------------------------------------------
        // NEW SESSION BUTTON
        // ----------------------------------------------------
        val fab = findViewById<FloatingActionButton>(R.id.newSessionFab)
        fab.setOnClickListener {
            startActivity(Intent(this, ChatActivity::class.java))
        }
    }

    // ----------------------------------------------------
    // OPEN BOTTOM SHEET
    // ----------------------------------------------------
    private fun showManageBottomSheet(sessionId: String) {
        val bottomSheet = ManageSessionBottomSheet(
            sessionId = sessionId,
            onDeleteSession = { id ->
                deleteSession(id)
            }
        )
        bottomSheet.show(supportFragmentManager, "ManageSessionBottomSheet")
    }

    // ----------------------------------------------------
    // DELETE SESSION FROM FIRESTORE
    // ----------------------------------------------------
    private fun deleteSession(sessionId: String) {
        val user = auth.currentUser ?: return

        db.collection("users")
            .document(user.uid)
            .collection("sessions")
            .document(sessionId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Session deleted", Toast.LENGTH_SHORT).show()
                loadSessionsFromFirestore()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ----------------------------------------------------
    // REFRESH SESSIONS WHEN RETURNING TO THIS SCREEN
    // ----------------------------------------------------
    override fun onResume() {
        super.onResume()
        loadSessionsFromFirestore()
    }

    // ----------------------------------------------------
    // LOAD SESSIONS FROM FIRESTORE
    // ----------------------------------------------------
    private fun loadSessionsFromFirestore() {
        val user = auth.currentUser ?: return

        db.collection("users")
            .document(user.uid)
            .collection("sessions")
            .get()
            .addOnSuccessListener { result ->
                allSessions.clear()

                for (doc in result) {
                    val session = doc.toObject(TherapySession::class.java)
                        .copy(id = doc.id)

                    allSessions.add(session)
                }

                allSessions.sortByDescending { it.timestamp }

                adapter.updateList(allSessions)
            }
    }

    private fun clearFocusWhenClickingOutside() {
        val root = findViewById<ConstraintLayout>(R.id.root_therapy_layout)
        root.setOnClickListener {
            findViewById<TextInputEditText>(R.id.searchInput).clearFocus()
        }
    }

    private fun filterSessions(query: String, tags: List<String>): List<TherapySession> {
        val filtered = allSessions.filter { session ->

            val matchesQuery =
                session.tags.any { it.contains(query, ignoreCase = true) } ||
                        session.messages.any { it.text.contains(query, ignoreCase = true) }

            val matchesTags = if (tags.isEmpty()) true
            else tags.any { tag -> session.tags.contains(tag) }

            matchesQuery && matchesTags
        }

        adapter.updateList(filtered)
        return filtered
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

                // ✅ ripple must be inside here
                rippleColor = ColorStateList.valueOf(
                    Color.parseColor("#8B0000")
                )
            }

            chipGroup.addView(chip) // if you're using a ChipGroup
        }
    }
}
