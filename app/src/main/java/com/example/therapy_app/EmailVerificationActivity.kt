package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth

class EmailVerificationActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_email_verification)

        auth = FirebaseAuth.getInstance()

        // ⭐ Set toolbar as ActionBar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_email_verification)
        setSupportActionBar(toolbar)

        // ⭐ Enable built-in back arrow
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(null) // use default arrow

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
                    startActivity(Intent(this, MFAEnrollmentActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Email not verified yet.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // ⭐ Handle toolbar back arrow
    override fun onSupportNavigateUp(): Boolean {
        startActivity(Intent(this, MFAEnrollmentActivity::class.java))
        finish()
        return true
    }
}
