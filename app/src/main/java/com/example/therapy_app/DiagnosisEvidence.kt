package com.example.therapy_app

data class DiagnosisEvidence(
    val phq9Score: Float,
    val phq9Severity: String,
    val gad7Score: Float,
    val gad7Severity: String
) {




    fun DiagnosisEvidence.strongestCondition(): String {
        return when {
            phq9Score >= 15 && gad7Score >= 15 -> "Severe mixed anxiety‑depression"
            phq9Score > gad7Score -> "Depression"
            gad7Score > phq9Score -> "Anxiety"
            else -> "Unclear"
        }
    }


    fun DiagnosisEvidence.confidence(): Float {
        val dep = phq9Score / 27f
        val anx = gad7Score / 21f
        return maxOf(dep, anx)
    }

    fun DiagnosisEvidence.toSeverityLabels(): Map<String, String> {
        return mapOf(
            "phq9" to phq9Severity,
            "gad7" to gad7Severity
        )
    }



}








