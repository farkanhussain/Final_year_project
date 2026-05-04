package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.MultiFactorSession
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import android.util.Log
import java.util.concurrent.TimeUnit

class MFAEnrollmentActivity : AppCompatActivity() {

    private var enteredPhoneNumber: String? = null

    private lateinit var auth: FirebaseAuth
    private lateinit var storedVerificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa_enrollment)

        auth = FirebaseAuth.getInstance()

        // ⭐ BLOCK MFA IF EMAIL IS NOT VERIFIED
        val currentUser = auth.currentUser
        if (currentUser != null && !currentUser.isEmailVerified) {
            Toast.makeText(
                this,
                "Please verify your email before enabling Two-Step Verification.",
                Toast.LENGTH_LONG
            ).show()

            startActivity(Intent(this, EmailVerificationActivity::class.java))
            finish()
            return
        }

        val phoneInput = findViewById<TextInputEditText>(R.id.phoneInput)
        val sendCodeButton = findViewById<MaterialButton>(R.id.sendCodeButton)
        val codeInput = findViewById<TextInputEditText>(R.id.codeInput)
        val verifyButton = findViewById<MaterialButton>(R.id.verifyButton)
        val skipButton = findViewById<MaterialButton>(R.id.skipButton)

        // ⭐ SEND CODE BUTTON
        sendCodeButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            enteredPhoneNumber = phone

            val user = auth.currentUser ?: return@setOnClickListener

            // ⭐ REQUIRED FOR MFA ENROLLMENT
            user.multiFactor.getSession()
                .addOnSuccessListener { session ->
                    Log.d("MFA_DEBUG", "MFA session created")
                    sendVerificationCode(phone, session)
                }
                .addOnFailureListener { e ->
                    Log.d("MFA_DEBUG", "Failed to create MFA session: ${e.message}")
                    Toast.makeText(this, "Failed to start MFA session", Toast.LENGTH_LONG).show()
                }
        }

        // ⭐ VERIFY CODE BUTTON
        verifyButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyCodeAndEnroll(code)
        }

        // ⭐ SKIP BUTTON
        skipButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // ⭐ SEND VERIFICATION CODE WITH MFA SESSION
    private fun sendVerificationCode(phone: String, session: MultiFactorSession) {

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d("MFA_DEBUG", "Auto verification completed")
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.d("MFA_DEBUG", "Verification failed: ${e.message}")
                Toast.makeText(this@MFAEnrollmentActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                resendToken = token

                Log.d("MFA_DEBUG", "Code sent. VerificationId: $verificationId")
                Toast.makeText(this@MFAEnrollmentActivity, "Code sent!", Toast.LENGTH_SHORT).show()
            }
        }

        val options = PhoneAuthOptions.newBuilder()
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .setMultiFactorSession(session)   // ⭐ REQUIRED FOR MFA
            .build()

        Log.d("MFA_DEBUG", "Requesting SMS code for MFA enrollment…")
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // ⭐ VERIFY CODE + ENROLL MFA
    private fun verifyCodeAndEnroll(code: String) {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId, code)
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)

        val user = auth.currentUser ?: return

        user.multiFactor.enroll(assertion, "My Phone")
            .addOnSuccessListener {
                savePhoneNumberToFirestore()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ⭐ SAVE PHONE NUMBER TO FIRESTORE
    private fun savePhoneNumberToFirestore() {
        val user = auth.currentUser ?: return

        Log.d("MFA_DEBUG", "savePhoneNumberToFirestore() called")

        val phone = enteredPhoneNumber
        Log.d("MFA_DEBUG", "Phone to save: $phone")

        if (phone.isNullOrEmpty()) {
            Log.d("MFA_DEBUG", "ERROR: enteredPhoneNumber is NULL or EMPTY — Firestore will NOT be updated")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val profileRef = db.collection("users").document(user.uid)

        profileRef.set(
            mapOf("phone" to phone),
            SetOptions.merge()
        )
            .addOnSuccessListener {
                Log.d("MFA_DEBUG", "Firestore write SUCCESS. Saved phone: $phone")

                Toast.makeText(this, "Two-Step Verification Enabled!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Log.d("MFA_DEBUG", "Firestore write FAILED: ${e.message}")
                Toast.makeText(this, "MFA enabled but phone not saved", Toast.LENGTH_LONG).show()
            }
    }
}
