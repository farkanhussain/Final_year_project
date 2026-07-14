package com.example.therapy_app

data class DeepSessionResult(
    val phq9: Float,
    val gad7: Float,
    val achaRisk: Float,
    val generalDiagnosis: Int,
    val emotionVector: FloatArray
)
