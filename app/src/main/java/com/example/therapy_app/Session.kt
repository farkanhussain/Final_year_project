package com.example.therapy_app

data class Session(
    val id: String,
    val title: String,
    val notes: String,
    val tags: List<String>
)
