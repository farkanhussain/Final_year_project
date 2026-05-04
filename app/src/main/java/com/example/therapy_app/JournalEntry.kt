package com.example.therapy_app

data class JournalEntry(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val mood: String? = null, // 🔥 better than ""
    val tags: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis() // 🔥 safer default
)