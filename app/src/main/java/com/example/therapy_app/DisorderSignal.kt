package com.example.therapy_app

data class DisorderSignal(
    val stress: Float = 0f,
    val anxiety: Float = 0f,
    val depression: Float = 0f,
    val loneliness: Float = 0f
) {
    fun strongest(): String {
        return mapOf(
            "Stress" to stress,
            "Anxiety" to anxiety,
            "Depression" to depression,
            "Loneliness" to loneliness
        ).maxByOrNull { it.value }?.key ?: "Stress"
    }
}