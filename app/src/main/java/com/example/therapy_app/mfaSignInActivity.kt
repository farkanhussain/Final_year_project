package com.example.therapy_app

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.FirebaseException
import com.google.firebase.auth.MultiFactorResolver
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneMultiFactorGenerator
import com.google.firebase.auth.PhoneMultiFactorInfo
import java.util.concurrent.TimeUnit

class MfaSignInActivity : AppCompatActivity() {

    private lateinit var resolver: MultiFactorResolver
    private lateinit var storedVerificationId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mfa_signin)

        resolver = intent.getParcelableExtra("resolver")!!

        val codeInput = findViewById<TextInputEditText>(R.id.codeInput)
        val verifyButton = findViewById<MaterialButton>(R.id.verifyButton)

        // 🔐 Load encrypted SharedPreferences (same file as LoginActivity)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val prefs = EncryptedSharedPreferences.create(
            "secureLoginPrefs",
            masterKeyAlias,
            this,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        // 🔄 Retrieve email + password passed from LoginActivity
        val email = intent.getStringExtra("email") ?: ""
        val password = intent.getStringExtra("password") ?: ""
        val rememberMe = intent.getBooleanExtra("rememberMe", false)

        sendMfaCode()

        verifyButton.setOnClickListener {
            val code = codeInput.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "Enter verification code", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            completeMfaSignIn(code, email, password, rememberMe, prefs)
        }
    }

    private fun sendMfaCode() {

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                // Auto-retrieval is rare for MFA
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Toast.makeText(
                    this@MfaSignInActivity,
                    "Failed: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                storedVerificationId = verificationId
                Toast.makeText(
                    this@MfaSignInActivity,
                    "Code sent!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val phoneInfo = resolver.hints[0] as PhoneMultiFactorInfo

        val options = PhoneAuthOptions.newBuilder()
            .setActivity(this)
            .setMultiFactorSession(resolver.session)
            .setMultiFactorHint(phoneInfo)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun completeMfaSignIn(
        code: String,
        email: String,
        password: String,
        rememberMe: Boolean,
        prefs: SharedPreferences
    ) {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId, code)
        val assertion = PhoneMultiFactorGenerator.getAssertion(credential)

        resolver.resolveSignIn(assertion)
            .addOnSuccessListener {

                // 🔐 Save Remember Me credentials AFTER MFA succeeds
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

                Toast.makeText(this, "Signed in successfully!", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }
}

