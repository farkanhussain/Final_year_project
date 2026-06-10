package com.example.therapy_app

data class DiagnosisEvidence(
    val anxiety: Float,
    val depression: Float,
    val stress: Float,
    val loneliness: Float
) {




    fun strongestLabel(): String =
        mapOf(
            "Anxiety" to anxiety,
            "Depression" to depression,
            "Stress" to stress,
            "Loneliness" to loneliness
        ).maxByOrNull { it.value }?.key ?: "Unknown"

    fun confidence(): Float =
        listOf(anxiety, depression, stress, loneliness)
            .maxOrNull() ?: 0f



}

fun DiagnosisEvidence.toWeights(): Map<String, Int> {
    return mapOf(
        "anxiety" to anxiety.toInt(),
        "depression" to depression.toInt(),
        "stress" to stress.toInt(),
        "loneliness" to loneliness.toInt()
    )
}

fun DiagnosisEvidence.toWeightLabels(): Map<String, String> {

    fun label(value: Float): String {
        return when {
            value < 2f -> "Low"
            value < 4f -> "Medium"
            else -> "High"
        }
    }

    return mapOf(
        "anxiety" to label(anxiety),
        "depression" to label(depression),
        "stress" to label(stress),
        "loneliness" to label(loneliness)
    )
}








