package com.example.therapy_app

data class TherapySession(
    val id: String = "",
    val title: String = "",
    val tags: List<String> = emptyList(),
    val messages: List<Message> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
