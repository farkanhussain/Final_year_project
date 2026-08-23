package com.example.therapy_app

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MedicalConditionsActivity : AppCompatActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_medical_conditions)

        // ----------------------------------------------------
        // TOOLBAR + BACK BUTTON
        // ----------------------------------------------------
        val toolbar: MaterialToolbar = findViewById(R.id.toolbarHealth)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Back button behaviour
        toolbar.setNavigationOnClickListener { finish() }

        // ----------------------------------------------------
        // CONTINUE BUTTON
        // ----------------------------------------------------
        val continueButton = findViewById<MaterialButton>(R.id.continueButton)

        continueButton.setOnClickListener {
            val selectedConditions = getSelectedConditions()
            saveMedicalConditions(selectedConditions)

            startActivity(Intent(this, DemographicsActivity::class.java))
        }
    }

    // --------------------------------------------------------
    // READ ALL CHECKBOXES
    // --------------------------------------------------------
    private fun getSelectedConditions(): List<String> {
        val list = mutableListOf<String>()

        fun addIfChecked(id: Int, label: String) {
            val cb = findViewById<CheckBox>(id)
            if (cb.isChecked) list.add(label)
        }

        addIfChecked(R.id.allergyCheck, "Allergy problems")
        addIfChecked(R.id.anorexiaCheck, "Anorexia")
        addIfChecked(R.id.anxietyCheck, "Anxiety Disorder")
        addIfChecked(R.id.asthmaCheck, "Asthma")
        addIfChecked(R.id.bulimiaCheck, "Bulimia")
        addIfChecked(R.id.cfsCheck, "Chronic Fatigue Syndrome")
        addIfChecked(R.id.depressionCheck, "Depression")
        addIfChecked(R.id.diabetesCheck, "Diabetes")
        addIfChecked(R.id.endometriosisCheck, "Endometriosis")
        addIfChecked(R.id.herpesCheck, "Genital Herpes")
        addIfChecked(R.id.hpvCheck, "Genital warts / HPV")
        addIfChecked(R.id.hepatitisCheck, "Hepatitis B or C")
        addIfChecked(R.id.highBpCheck, "High blood pressure")
        addIfChecked(R.id.highCholesterolCheck, "High cholesterol")
        addIfChecked(R.id.hivCheck, "HIV infection")
        addIfChecked(R.id.rsiCheck, "Repetitive stress injury")
        addIfChecked(R.id.sadCheck, "Seasonal Affective Disorder")
        addIfChecked(R.id.substanceCheck, "Substance abuse problem")
        addIfChecked(R.id.backPainCheck, "Back pain")
        addIfChecked(R.id.fractureCheck, "Broken bone / fracture")
        addIfChecked(R.id.bronchitisCheck, "Bronchitis")
        addIfChecked(R.id.chlamydiaCheck, "Chlamydia")
        addIfChecked(R.id.earInfectionCheck, "Ear infection")
        addIfChecked(R.id.gonorrheaCheck, "Gonorrhea")
        addIfChecked(R.id.monoCheck, "Mononucleosis")
        addIfChecked(R.id.pidCheck, "Pelvic Inflammatory Disease")
        addIfChecked(R.id.sinusCheck, "Sinus infection")
        addIfChecked(R.id.strepCheck, "Strep throat")
        addIfChecked(R.id.tbCheck, "Tuberculosis")

        return list
    }

    // --------------------------------------------------------
    // SAVE TO FIRESTORE
    // --------------------------------------------------------
    private fun saveMedicalConditions(conditions: List<String>) {
        val userId = auth.currentUser?.uid ?: return

        val data = mapOf(
            "conditions" to conditions,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(userId)
            .collection("health")
            .document("medical_conditions")
            .set(data)
    }
}
