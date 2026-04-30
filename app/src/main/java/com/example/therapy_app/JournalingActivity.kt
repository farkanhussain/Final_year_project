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

class JournalingActivity : AppCompatActivity() {

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

        // ----------------------------------------------------
        // HARD-CODED JOURNAL TAGS
        // ----------------------------------------------------
        val tags = listOf("CBT", "Trauma", "Mindfulness", "Anxiety", "Depression")

        tags.forEach { tag ->
            val chip = Chip(this).apply {
                text = tag
                isCheckable = true

                // Styling to match your theme
                setTextColor(resources.getColor(android.R.color.white, theme))
                chipBackgroundColor = resources.getColorStateList(R.color.red_dark, theme)
            }

            chipGroup.addView(chip)
        }

        // ----------------------------------------------------
        // FAB → OPEN NEW JOURNAL ENTRY
        // ----------------------------------------------------
        fab.setOnClickListener {
            val intent = Intent(this, JournalEntryActivity::class.java)
            startActivity(intent)
        }

        // ----------------------------------------------------
        // FIREBASE USER HEADER (unchanged)
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

                        val name = document.getString("name") ?: "Profile"
                        val email = document.getString("email") ?: user.email ?: "Unknown"

                        nameTextView.text = name
                        emailTextView.text = email
                    }
                }
                .addOnFailureListener {
                    nameTextView.text = "Profile"
                    emailTextView.text = user?.email ?: "Unknown"
                }
        }

        // ----------------------------------------------------
        // HEADER CLICK
        // ----------------------------------------------------
        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // ----------------------------------------------------
        // NAVIGATION MENU
        // ----------------------------------------------------
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {

                R.id.nav_home -> startActivity(Intent(this, MainActivity::class.java))

                R.id.nav_therapy -> startActivity(Intent(this, TherapyActivity::class.java))

                R.id.nav_journaling -> startActivity(Intent(this, JournalingActivity::class.java))

                R.id.nav_mood_tracking -> startActivity(Intent(this, MoodTrackingActivity::class.java))

                R.id.nav_articles -> startActivity(Intent(this, ArticlesActivity::class.java))

                R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
    }
}