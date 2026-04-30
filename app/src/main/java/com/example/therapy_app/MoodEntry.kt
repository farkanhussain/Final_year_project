package com.example.therapy_app

data class MoodEntry(
    val mood: Int = 0,
    val timestamp: Long = 0,
    val docId: String = ""
)