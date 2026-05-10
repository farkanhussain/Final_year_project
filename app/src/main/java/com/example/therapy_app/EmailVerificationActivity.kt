package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_verification)

        auth = FirebaseAuth.getInstance()



        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_email_verification)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(null)

        val sendButton = findViewById<MaterialButton>(R.id.sendButton)
        val continueButton = findViewById<MaterialButton>(R.id.continueButton)

        // ⭐ RESEND VERIFICATION EMAIL
        sendButton.setOnClickListener {
            auth.currentUser?.sendEmailVerification()
                ?.addOnSuccessListener {
                    Toast.makeText(this, "Verification email sent!", Toast.LENGTH_LONG).show()
                }
                ?.addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        // ⭐ CHECK IF EMAIL IS VERIFIED
        continueButton.setOnClickListener {
            auth.currentUser?.reload()?.addOnSuccessListener {
                if (auth.currentUser!!.isEmailVerified) {

                    Toast.makeText(this, "Email verified!", Toast.LENGTH_LONG).show()

                    // ⭐ Save Firestore profile now
                    saveUserProfile()

                } else {
                    Toast.makeText(this, "Email not verified yet.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ⭐ Save Firestore profile AFTER verification
    private fun saveUserProfile() {

        val userId = auth.currentUser?.uid ?: return

        val userData = mapOf(
            "name" to (intent.getStringExtra("name") ?: ""),
            "age" to (intent.getStringExtra("age") ?: ""),
            "gender" to (intent.getStringExtra("gender") ?: ""),
            "email" to (intent.getStringExtra("email") ?: "")
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .set(userData)
            .addOnSuccessListener {

                Toast.makeText(this, "Registration Successful", Toast.LENGTH_SHORT).show()

                startActivity(Intent(this, MFAEnrollmentActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ⭐ Back button returns to RegisterActivity with restored fields
    override fun onSupportNavigateUp(): Boolean {
        returnToRegister()
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        returnToRegister()
    }

    private fun returnToRegister() {
        auth.currentUser?.delete()   //  delete user
        auth.signOut()               //  sign out

        val intent = Intent(this, RegisterActivity::class.java)
        intent.putExtra("name", this.intent.getStringExtra("name"))
        intent.putExtra("age", this.intent.getStringExtra("age"))
        intent.putExtra("gender", this.intent.getStringExtra("gender"))
        intent.putExtra("email", this.intent.getStringExtra("email"))
        intent.putExtra("password", this.intent.getStringExtra("password"))
        startActivity(intent)
        finish()
    }


}
