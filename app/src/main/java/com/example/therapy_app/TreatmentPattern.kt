package com.example.therapy_app

import android.annotation.SuppressLint


@SuppressLint("UnsafeOptInUsageError")
@kotlinx.serialization.Serializable
data class TreatmentPattern(
    val cluster: String,
    val focus: String,
    val methods: List<String>
) {
    companion object {
        fun empty(): TreatmentPattern {
            return TreatmentPattern(
                cluster = "none",
                focus = "none",
                methods = emptyList()
            )
        }
    }
}