package com.example.therapy_app.utils


import android.util.Log
import com.example.therapy_app.TherapyApp
import java.io.File


object PerfLogger {

    fun log(event: String, durationMs: Long) {
        val seconds = durationMs / 1000.0
        Log.d("PERFORMANCE", "$event: $seconds seconds")
        appendToCsv(event, seconds)
    }

    private fun appendToCsv(event: String, seconds: Double) {
        try {
            val timestamp = System.currentTimeMillis()
            val line = "$timestamp,$event,$seconds\n"

            // Save to external app-specific storage
            val file = File(
                TherapyApp.instance.getExternalFilesDir(null),
                "performance_logs.csv"
            )

            file.appendText(line)
        } catch (e: Exception) {
            Log.e("PERFORMANCE", "Failed to write CSV: ${e.message}")
        }
    }
}
