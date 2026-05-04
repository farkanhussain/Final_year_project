package com.example.therapy_app

import com.google.firebase.Timestamp

data class Article(
    val title: String = "",
    val summary: String = "",
    val url: String = "",
    val tags: List<String> = emptyList(),
    val imageUrl: String = "",
    val createdAt: Timestamp? = null
)
