package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView

class ArticlesActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_articles)

        drawerLayout = findViewById(R.id.drawer_layout_articles)
        val navView: NavigationView = findViewById(R.id.nav_view_articles)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_articles)

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
        // LOAD USER DETAILS FROM FIRESTORE (name, email, age, gender)
        // ----------------------------------------------------
        val user = FirebaseAuth.getInstance().currentUser
        val userId = user?.uid

        val headerView = navView.getHeaderView(0)

        // Existing header fields
        val nameTextView = headerView.findViewById<TextView>(R.id.header_profile_name)
        val emailTextView = headerView.findViewById<TextView>(R.id.header_profile_email)

        // Future fields (not in XML yet)
        // val ageTextView = headerView.findViewById<TextView>(R.id.header_profile_age)
        // val genderTextView = headerView.findViewById<TextView>(R.id.header_profile_gender)

        if (userId != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {

                        val name = document.getString("name") ?: "Profile"
                        val email = document.getString("email") ?: user.email ?: "Unknown"
                        val age = document.getString("age") ?: "Not set"
                        val gender = document.getString("gender") ?: "Not set"

                        // Set existing header fields
                        nameTextView.text = name
                        emailTextView.text = email

                        // These will work once you add the TextViews
                        // ageTextView.text = age
                        // genderTextView.text = gender
                    }
                }
                .addOnFailureListener {
                    nameTextView.text = "Profile"
                    emailTextView.text = user?.email ?: "Unknown"
                }
        }

        // ----------------------------------------------------
        // HANDLE HEADER CLICK
        // ----------------------------------------------------
        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // ----------------------------------------------------
        // HANDLE MENU ITEM CLICKS
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
    }
}
