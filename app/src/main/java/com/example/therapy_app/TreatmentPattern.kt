package com.example.therapy_app

@kotlinx.serialization.Serializable
data class TreatmentPattern(
    val cluster: String,
    val focus: String,
    val methods: List<String>
)
