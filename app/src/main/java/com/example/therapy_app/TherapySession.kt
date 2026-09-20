package com.example.therapy_app

data class TherapySession(
    val id: String = "",
    val title: String = "",
    val tags: List<String> = emptyList(),
    val messages: List<Message> = emptyList(),
    val symptomsVector: List<Float> = emptyList(), // ⭐ NEW — used for DBT selection
    val diagnosis: String = "",                        // ⭐ NEW — optional but useful
    val phq9Score: Int? = null,                        // ⭐ NEW — optional
    val gad7Score: Int? = null,                        // ⭐ NEW — optional
    val timestamp: Long = System.currentTimeMillis()
)
