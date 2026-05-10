package com.example.therapy_app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthMultiFactorException

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()

        // 🔐 Secure encrypted SharedPreferences
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

        val prefs = EncryptedSharedPreferences.create(
            "secureLoginPrefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        val email = findViewById<EditText>(R.id.emailEditText)
        val password = findViewById<EditText>(R.id.passwordEditText)
        val loginBtn = findViewById<Button>(R.id.loginButton)
        val registerBtn = findViewById<Button>(R.id.btnRegister)
        val rememberMeCheckBox = findViewById<CheckBox>(R.id.rememberMeCheckBox)

        // Auto-fill saved login details
        val savedEmail = prefs.getString("savedEmail", "")
        val savedPassword = prefs.getString("savedPassword", "")
        val rememberMe = prefs.getBoolean("rememberMe", false)

        if (rememberMe) {
            email.setText(savedEmail)
            password.setText(savedPassword)
            rememberMeCheckBox.isChecked = true
        }

        loginBtn.setOnClickListener {
            val emailText = email.text.toString().trim()
            val passwordText = password.text.toString().trim()

            // 🔍 Input validation
            when {
                emailText.isEmpty() -> {
                    email.error = "Email is required"
                    return@setOnClickListener
                }

                !Patterns.EMAIL_ADDRESS.matcher(emailText).matches() ||
                        !emailText.contains("@") -> {
                    email.error = "Enter a valid email address"
                    return@setOnClickListener
                }

                passwordText.isEmpty() -> {
                    password.error = "Password is required"
                    return@setOnClickListener
                }

                passwordText.length < 6 -> {
                    password.error = "Password must be at least 6 characters"
                    return@setOnClickListener
                }
            }

            loginUser(emailText, passwordText, rememberMeCheckBox.isChecked, prefs)
        }

        registerBtn.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun loginUser(
        email: String,
        password: String,
        rememberMe: Boolean,
        prefs: SharedPreferences
    ) {

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {

                // 🔐 Save login info securely only if Remember Me is checked
                if (rememberMe) {
                    prefs.edit().apply {
                        putString("savedEmail", email)
                        putString("savedPassword", password)
                        putBoolean("rememberMe", true)
                        apply()
                    }
                } else {
                    prefs.edit().clear().apply()
                }

                Toast.makeText(this, "Login Successful", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->

                when (e) {
                    is FirebaseAuthMultiFactorException -> {
                        val intent = Intent(this, MfaSignInActivity::class.java)
                        intent.putExtra("resolver", e.resolver)
                        intent.putExtra("email", email)
                        intent.putExtra("password", password)
                        intent.putExtra("rememberMe", rememberMe)
                        startActivity(intent)
                    }

                    else -> {
                        val message = when {
                            e.message?.contains("password") == true ->
                                "Incorrect password"

                            e.message?.contains("no user record") == true ->
                                "No account found with this email"

                            e.message?.contains("network") == true ->
                                "Network error. Check your connection"

                            else -> "Login Failed: ${e.message}"
                        }

                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
    }
}
