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
import java.util.concurrent.TimeUnit

class MFAEnrollmentActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var storedVerificationId: String
    private lateinit var resendToken: PhoneAuthProvider.ForceResendingToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa_enrollment)

        auth = FirebaseAuth.getInstance()

        val phoneInput = findViewById<TextInputEditText>(R.id.phoneInput)
        val sendCodeButton = findViewById<MaterialButton>(R.id.sendCodeButton)
        val codeInput = findViewById<TextInputEditText>(R.id.codeInput)
        val verifyButton = findViewById<MaterialButton>(R.id.verifyButton)
        val skipButton = findViewById<MaterialButton>(R.id.skipButton)


        sendCodeButton.setOnClickListener {
            val phone = phoneInput.text.toString().trim()
            if (phone.isEmpty()) {
                Toast.makeText(this, "Enter phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sendVerificationCode(phone)
        }

        verifyButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyCodeAndEnroll(code)
        }

        skipButton.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

    }

    private fun sendVerificationCode(phone: String) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {}

            override fun onVerificationFailed(e: FirebaseException) {
                Toast.makeText(this@MFAEnrollmentActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }

            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                storedVerificationId = verificationId
                resendToken = token
                Toast.makeText(this@MFAEnrollmentActivity, "Code sent!", Toast.LENGTH_SHORT).show()
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCodeAndEnroll(code: String) {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId, code)
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)

        val user = auth.currentUser ?: return

        user.multiFactor.enroll(assertion, "My Phone")
            .addOnSuccessListener {
                Toast.makeText(this, "Two-Step Verification Enabled!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}
