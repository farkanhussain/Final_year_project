package com.example.therapy_app

class SymptomCollectionEngine(
    private val featureIndexMap: Map<String, Int>,
    private val featureCols: List<String>
) {

    // -----------------------------------------------------
    // STATE
    // -----------------------------------------------------
    private val symptoms = FloatArray(featureCols.size) { 0f }
    private var symptomCount = 0

    var anxiety: Float = 0f
    var depression: Float = 0f
    var stress: Float = 0f
    var loneliness: Float = 0f


    // -----------------------------------------------------
    // RESET
    // -----------------------------------------------------
    fun reset() {
        for (i in symptoms.indices) symptoms[i] = 0f
        symptomCount = 0
    }

    fun getDiagnosisEvidence(): DiagnosisEvidence {
        return DiagnosisEvidence(
            anxiety = anxiety,
            depression = depression,
            stress = stress,
            loneliness = loneliness
        )
    }


    // -----------------------------------------------------
    // MAIN ENTRY (checkbox-based)
    // -----------------------------------------------------
    fun updateFromCheckboxSelections(selectedSymptoms: List<String>): FloatArray {

        // Reset evidence before scoring
        anxiety = 0f
        depression = 0f
        stress = 0f
        loneliness = 0f

        for (symptomName in selectedSymptoms) {

            // Update the 24‑feature vector
            val index = featureIndexMap[symptomName] ?: continue

            if (symptoms[index] == 0f) {
                symptoms[index] = 1f
                symptomCount++
            }

            // 🔥 Add disorder evidence scoring here
            when (symptomName) {

                // Anxiety
                "feeling.nervous",
                "panic",
                "breathing.rapidly",
                "sweating",
                "trouble.in.concentration",
                "having.trouble.in.sleeping" ->
                    anxiety += 1f

                // Depression
                "hopelessness",
                "feeling.negative",
                "feeling.tired",
                "blamming.yourself",
                "loss.of.interest",
                "suicidal.thought" ->
                    depression += 1.5f

                // Stress

                "over.react",
                "anger",
                "having.trouble.with.work",
                "change.in.eating",
                "popping.up.stressful.memory",
                "having.nightmares",
                "social.media.addiction",
                "weight.gain",
                "material.possessions" ->
                    stress += 1f

                // Loneliness
                "close.friend",
                "avoids.people.or.activities",
                "introvert" ->
                    loneliness += 1.5f
            }

        }

        return symptoms
    }


    // -----------------------------------------------------
    // ACCESSORS
    // -----------------------------------------------------
    fun getSymptoms(): FloatArray = symptoms

    fun getSymptomCount(): Int = symptomCount

    fun getConfirmedSymptoms(): List<String> {
        return featureCols.filterIndexed { index, _ ->
            symptoms[index] >= 0.5f
        }
    }

    fun getConfirmedSymptomsText(): String {
        return getConfirmedSymptoms()
            .joinToString(", ")
            .ifEmpty { "None confirmed yet" }
    }




    // -----------------------------------------------------
    // DIAGNOSIS READINESS
    // -----------------------------------------------------
    fun isReadyForDiagnosis(): Boolean {
        return symptomCount >= 3
    }
}
