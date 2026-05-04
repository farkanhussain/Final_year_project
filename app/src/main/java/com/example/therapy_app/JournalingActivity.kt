package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.textfield.TextInputEditText
import androidx.core.widget.addTextChangedListener


class JournalingActivity : AppCompatActivity() {

    private lateinit var journalAdapter: JournalAdapter

    private var allEntries: List<JournalEntry> = emptyList()

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_journaling)

        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(android.Manifest.permission.RECORD_AUDIO),
                100
            )
        }

        drawerLayout = findViewById(R.id.drawer_layout_journaling)
        val navView: NavigationView = findViewById(R.id.nav_view_journaling)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_journaling)

        val chipGroup: ChipGroup = findViewById(R.id.tagChipGroup)
        val fab: FloatingActionButton = findViewById(R.id.newJournalFab)

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

        // -------------------------------
        // HARD-CODED JOURNAL TAGS
        // -------------------------------
        val tags = listOf(
            "Gratitude", "Reflection", "Goals",
            "Mindfulness", "stress", "daily"
        )

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true
                setTextColor(resources.getColor(android.R.color.white, theme))
                chipBackgroundColor = resources.getColorStateList(R.color.red_dark, theme)
            }


            chip.setOnCheckedChangeListener { _, _ ->
                applyFilters()
            }

            chipGroup.addView(chip)
        }

        // -------------------------------
        // FAB → NEW ENTRY
        // -------------------------------
        fab.setOnClickListener {
            startActivity(Intent(this, JournalEntryActivity::class.java))
        }

        val recyclerView = findViewById<RecyclerView>(R.id.journalRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val searchInput = findViewById<TextInputEditText>(R.id.searchInput)

        searchInput.addTextChangedListener {
            applyFilters()
        }



// ✅ CREATE ADAPTER ONCE HERE
        journalAdapter = JournalAdapter(emptyList()) { entry ->
            openBottomSheet(entry, recyclerView)
        }

        recyclerView.adapter = journalAdapter

// THEN load data
        loadJournalEntries(recyclerView)

        // -------------------------------
        // HEADER USER INFO
        // -------------------------------
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
        }

        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // -------------------------------
        // NAV MENU
        // -------------------------------
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {

                R.id.nav_home -> startActivity(Intent(this, MainActivity::class.java))
                R.id.nav_therapy -> startActivity(Intent(this, TherapyActivity::class.java))
                R.id.nav_journaling -> startActivity(Intent(this, JournalingActivity::class.java))
                R.id.nav_mood_tracking -> startActivity(
                    Intent(
                        this,
                        MoodTrackingActivity::class.java
                    )
                )

                R.id.nav_articles -> startActivity(Intent(this, ArticlesActivity::class.java))

            }
            true
        }
    }

    // =====================================================
    // LOAD JOURNAL ENTRIES
    // =====================================================
    private fun loadJournalEntries(recyclerView: RecyclerView) {

        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("journal_entries")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->

                val entries = result.documents.map { doc ->

                    val rawTags = doc.get("tags")

                    val tags = when (rawTags) {
                        is List<*> -> rawTags.mapNotNull { it?.toString() }
                        else -> emptyList()
                    }

                    JournalEntry(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        content = doc.getString("content") ?: "",
                        mood = doc.getString("mood"),
                        timestamp = doc.getLong("timestamp") ?: 0,
                        tags = tags
                    )
                }

                allEntries = entries
                journalAdapter.updateList(entries)
            }
    }


    override fun onResume() {
        super.onResume()
        val recyclerView = findViewById<RecyclerView>(R.id.journalRecyclerView)
        loadJournalEntries(recyclerView)
    }

        // =====================================================
        // DELETE ENTRY (LOCAL UI UPDATE)
        // =====================================================
        private fun deleteEntryFromList(entry: JournalEntry, recyclerView: RecyclerView) {

            val currentList = journalAdapter.entries.toMutableList()

            val index = currentList.indexOfFirst { it.id == entry.id }

            if (index != -1) {

                currentList.removeAt(index)

                journalAdapter = JournalAdapter(currentList) { selected ->

                    val bottomSheet = ManageJournalEntryBottomSheet(
                        entryId = selected.id,
                        onDeleteEntry = {
                            deleteEntryFromList(selected, recyclerView)
                        }
                    )

                    bottomSheet.show(supportFragmentManager, "JournalSheet")
                }

                recyclerView.adapter = journalAdapter
            }
        }

    private fun openBottomSheet(entry: JournalEntry, recyclerView: RecyclerView) {

        val bottomSheet = ManageJournalEntryBottomSheet(
            entryId = entry.id,
            onDeleteEntry = {
                loadJournalEntries(recyclerView)
            },
            onUpdated = {
                recyclerView.post {
                    loadJournalEntries(recyclerView)
                }
            }
        )

        bottomSheet.show(supportFragmentManager, "JournalSheet")
    }

    private fun applyFilters() {

        val searchInput = findViewById<TextInputEditText>(R.id.searchInput)
        val chipGroup = findViewById<ChipGroup>(R.id.tagChipGroup)

        val query = searchInput.text.toString().trim().lowercase()

        // Collect selected tags
        val selectedTags = mutableListOf<String>()
        for (i in 0 until chipGroup.childCount) {
            val chip = chipGroup.getChildAt(i) as Chip
            if (chip.isChecked) selectedTags.add(chip.text.toString())
        }

        // Filter logic
        val filtered = allEntries.filter { entry ->

            val matchesSearch =
                entry.title.lowercase().contains(query) ||
                        entry.content.lowercase().contains(query)

            val matchesTags =
                selectedTags.isEmpty() || entry.tags.any { it in selectedTags }

            matchesSearch && matchesTags
        }

        journalAdapter.updateList(filtered)
    }

}
