package com.example.therapy_app

object CrisisDetector {

    private val crisisKeywords = listOf(
        "kill myself",
        "suicide",
        "end my life",
        "want to die",
        "don't want to live",
        "hurt myself",
        "self harm",
        "no reason to live",
        "i'm done",
        "life is pointless",
        "I can't go on",
        "I don't see a point",
        "life is too much",
        "i can't do this anymore",
        "i want everything to stop"
    )

    fun isCrisisMessage(text: String): Boolean {
        val lower = text.lowercase()
        return crisisKeywords.any { lower.contains(it) }
    }
}