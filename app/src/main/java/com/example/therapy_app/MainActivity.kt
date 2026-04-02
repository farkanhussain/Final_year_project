package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import com.google.android.material.appbar.MaterialToolbar

class MainActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navView: NavigationView = findViewById(R.id.nav_view)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)

        // Set toolbar as ActionBar
        setSupportActionBar(toolbar)

        // Add burger icon toggle
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.Open_Drawer,
            R.string.Close_Drawer
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // -----------------------------
        // HANDLE HEADER CLICK
        // -----------------------------
        val headerView = navView.getHeaderView(0)

        headerView.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
            drawerLayout.closeDrawers()
        }

        // -----------------------------
        // HANDLE MENU ITEM CLICKS
        // -----------------------------
        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {

                R.id.nav_home -> {
                    drawerLayout.closeDrawers()
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
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            }
            true
        }
    }
}
