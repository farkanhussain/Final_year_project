package com.example.therapy_app
import com.google.gson.Gson


data class DbtExercise(
    val name: String,
    val description: String,
    val url: String,
    val steps: List<String>
) {
    companion object {
        fun toJsonList(list: List<DbtExercise>?): String {
            return Gson().toJson(list ?: emptyList<DbtExercise>())
        }

        fun fromJsonList(json: String): List<DbtExercise> {
            return Gson().fromJson(json, Array<DbtExercise>::class.java).toList()
        }
    }
}
