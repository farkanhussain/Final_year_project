package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        drawerLayout = findViewById(R.id.drawer_layout_profile)
        val navView: NavigationView = findViewById(R.id.nav_view_profile)
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar_profile)

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
        // NAV HEADER
        // ----------------------------------------------------
        val user = auth.currentUser
        val userId = user?.uid
        val headerView = navView.getHeaderView(0)

        val headerName = headerView.findViewById<TextView>(R.id.header_profile_name)
        val headerEmail = headerView.findViewById<TextView>(R.id.header_profile_email)

        // ----------------------------------------------------
        // PROFILE INPUT FIELDS
        // ----------------------------------------------------
        val nameInput = findViewById<TextInputEditText>(R.id.profile_name_input)
        val emailInput = findViewById<TextInputEditText>(R.id.profile_email_input)

        // ⭐ Updated to AutoCompleteTextView for dropdowns
        val genderInput = findViewById<AutoCompleteTextView>(R.id.profile_gender_input)
        val ageInput = findViewById<AutoCompleteTextView>(R.id.profile_age_input)

        val phoneInput = findViewById<TextInputEditText>(R.id.profile_phone_input)

        val saveButton = findViewById<MaterialButton>(R.id.profile_save_button)
        val logoutButton = findViewById<MaterialButton>(R.id.profile_logout_button)

        // ----------------------------------------------------
        // SETUP DROPDOWN MENUS
        // ----------------------------------------------------

        // Gender Options
        val genderOptions = listOf(
            "Prefer not to say",
            "Male",
            "Female",
            "Non-binary",
            "Other"
        )
        genderInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, genderOptions)
        )

        // Age Options
        val ageOptions = listOf(
            "Prefer not to say",
            "Under 18",
            "18–24",
            "25–34",
            "35–44",
            "45–54",
            "55–64",
            "65+"
        )
        ageInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, ageOptions)
        )

        // ----------------------------------------------------
        // LOAD USER DETAILS
        // ----------------------------------------------------
        if (userId != null) {
            db.collection("users").document(userId)
                .get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("name") ?: "Profile"
                        val email = doc.getString("email") ?: user.email ?: "Unknown"
                        val gender = doc.getString("gender") ?: ""
                        val age = doc.get("age")?.toString() ?: ""
                        val phone = doc.getString("phone") ?: ""

                        // Header
                        headerName.text = name
                        headerEmail.text = email

                        // Inputs
                        nameInput.setText(name)
                        emailInput.setText(email)
                        genderInput.setText(gender, false)
                        ageInput.setText(age, false)
                        phoneInput.setText(phone)
                    }
                }
                .addOnFailureListener {
                    Log.d("Profile", "Failed to load profile: ${it.message}")
                }
        }

        // ----------------------------------------------------
        // SAVE PROFILE CHANGES
        // ----------------------------------------------------
        saveButton.setOnClickListener {
            if (userId == null) return@setOnClickListener

            val updatedData = mapOf(
                "name" to nameInput.text.toString().trim(),
                "email" to emailInput.text.toString().trim(),
                "gender" to genderInput.text.toString().trim(),
                "age" to ageInput.text.toString().trim(),
                "phone" to phoneInput.text.toString().trim()
            )

            db.collection("users").document(userId)
                .update(updatedData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show()
                    headerName.text = updatedData["name"].toString()
                    headerEmail.text = updatedData["email"].toString()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        // ----------------------------------------------------
        // LOG OUT
        // ----------------------------------------------------
        logoutButton.setOnClickListener {
            auth.signOut()
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // ----------------------------------------------------
        // NAVIGATION MENU
        // ----------------------------------------------------
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
    }
}
