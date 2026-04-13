package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Register"

        auth = FirebaseAuth.getInstance()

        // 🔐 CLEAR SAVED LOGIN DETAILS (must match LoginActivity EXACTLY)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        val prefs = EncryptedSharedPreferences.create(
            "secureLoginPrefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        prefs.edit().clear().apply()

        // 🧪 Confirm clearing worked
        Log.d("REGISTER_DEBUG", "Saved email after clearing: ${prefs.getString("savedEmail", "EMPTY")}")

        // UI Elements
        val nameEditText = findViewById<TextInputEditText>(R.id.nameRegister)
        val ageDropdown = findViewById<AutoCompleteTextView>(R.id.ageRegister)
        val genderDropdown = findViewById<AutoCompleteTextView>(R.id.genderRegister)
        val emailEditText = findViewById<EditText>(R.id.emailRegister)
        val passwordEditText = findViewById<EditText>(R.id.passwordRegister)
        val registerButton = findViewById<Button>(R.id.registerButton)

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
        ageDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, ageOptions))

        // Gender Options
        val genderOptions = listOf(
            "Prefer not to say",
            "Male",
            "Female",
            "Non-binary",
            "Other"
        )
        genderDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, genderOptions))

        registerButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val age = ageDropdown.text.toString().trim()
            val gender = genderDropdown.text.toString().trim()
            val email = emailEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            // 🧪 DEBUG LOG — SEE EXACT EMAIL USED
            Log.d("REGISTER_DEBUG", "Attempting registration with email: $email")

            // Validation
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Email and password are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!email.contains("@")) {
                Toast.makeText(this, "Invalid email address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!password.any { it.isDigit() }) {
                Toast.makeText(this, "Password must contain at least one number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(name, age, gender, email, password)
        }
    }

    private fun registerUser(name: String, age: String, gender: String, email: String, password: String) {



        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {

                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val userData = mapOf(
                            "name" to name,
                            "age" to age,
                            "gender" to gender,
                            "email" to email
                        )

                        FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(userId)
                            .set(userData)
                            .addOnSuccessListener {
                                Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()

                                val intent = Intent(this, MFAEnrollmentActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                    }

                } else {
                    Toast.makeText(
                        this,
                        "Registration Failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
