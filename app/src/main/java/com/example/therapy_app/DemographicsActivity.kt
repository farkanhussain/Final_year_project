package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DemographicsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_demographics)

        // ----------------------------------------------------
        // TOOLBAR + BACK BUTTON
        // ----------------------------------------------------
        val toolbar: MaterialToolbar = findViewById(R.id.demographicsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        toolbar.setNavigationOnClickListener { finish() }

        // ----------------------------------------------------
        // DROPDOWN INPUTS
        // ----------------------------------------------------
        val fullTimeInput = findViewById<AutoCompleteTextView>(R.id.fullTimeInput)
        val internationalInput = findViewById<AutoCompleteTextView>(R.id.internationalInput)

        val yesNoOptions = listOf("Yes", "No", "Prefer not to say")

        fullTimeInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, yesNoOptions)
        )

        internationalInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, yesNoOptions)
        )

        // ----------------------------------------------------
        // RACE CHECKBOXES
        // ----------------------------------------------------
        val racePakistani = findViewById<CheckBox>(R.id.racePakistani)
        val raceIndian = findViewById<CheckBox>(R.id.raceIndian)
        val raceBangladeshi = findViewById<CheckBox>(R.id.raceBangladeshi)
        val raceSriLankan = findViewById<CheckBox>(R.id.raceSriLankan)
        val raceNepali = findViewById<CheckBox>(R.id.raceNepali)
        val raceChinese = findViewById<CheckBox>(R.id.raceChinese)
        val raceBlack = findViewById<CheckBox>(R.id.raceBlack)
        val raceBritish = findViewById<CheckBox>(R.id.raceBritish)
        val raceMixed = findViewById<CheckBox>(R.id.raceMixed)
        val raceOther = findViewById<CheckBox>(R.id.raceOther)

        // ----------------------------------------------------
        // SAVE BUTTON
        // ----------------------------------------------------
        val saveButton = findViewById<MaterialButton>(R.id.saveDemographicsButton)

        saveButton.setOnClickListener {
            val userId = auth.currentUser?.uid
            if (userId == null) {
                Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fullTime = fullTimeInput.text.toString().trim()
            val international = internationalInput.text.toString().trim()

            val raceList = mutableListOf<String>()
            if (racePakistani.isChecked) raceList.add("Pakistani")
            if (raceIndian.isChecked) raceList.add("Indian")
            if (raceBangladeshi.isChecked) raceList.add("Bangladeshi")
            if (raceSriLankan.isChecked) raceList.add("Sri Lankan")
            if (raceNepali.isChecked) raceList.add("Nepali")
            if (raceChinese.isChecked) raceList.add("Chinese")
            if (raceBlack.isChecked) raceList.add("Black")
            if (raceBritish.isChecked) raceList.add("British")
            if (raceMixed.isChecked) raceList.add("Mixed ethnicity")
            if (raceOther.isChecked) raceList.add("Other")

            val data = mapOf(
                "full_time_student" to fullTime,
                "international_student" to international,
                "race" to raceList,
                "timestamp" to System.currentTimeMillis()
            )

            db.collection("users")
                .document(userId)
                .collection("health")
                .document("demographics")
                .set(data)
                .addOnSuccessListener {
                    Toast.makeText(this, "Demographics saved", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                }

                .addOnFailureListener {
                    Toast.makeText(this, "Failed: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
