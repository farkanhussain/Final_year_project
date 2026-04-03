package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import android.widget.TextView

class SettingsActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        drawerLayout = findViewById(R.id.drawer_layout_settings)
        val navView: NavigationView = findViewById(R.id.nav_view_settings)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_settings)

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

                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }

                R.id.nav_therapy -> {
                    startActivity(Intent(this, TherapyActivity::class.java))
                }

                R.id.nav_mood_tracking -> {
                    startActivity(Intent(this, MoodTrackingActivity::class.java))
                }

                R.id.nav_journaling -> {
                    startActivity(Intent(this, JournalingActivity::class.java))
                }

                R.id.nav_articles -> {
                    startActivity(Intent(this, ArticlesActivity::class.java))
                }

                R.id.nav_settings -> {
                    drawerLayout.closeDrawers() // already here
                }
            }
            true
        }
    }
}
