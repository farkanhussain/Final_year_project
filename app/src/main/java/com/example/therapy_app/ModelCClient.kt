package com.example.therapy_app

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.call.body
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// ---------------------------------------------------------
// JSON INSTANCE
// ---------------------------------------------------------
private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
}

// ---------------------------------------------------------
// REQUEST BODY FOR MODEL C
// ---------------------------------------------------------
@Serializable
data class ModelCRequest(
    val disorder: String,
    val symptoms: List<Float>,
    val mood: Float,
    val sleep: Float,
    val activity: Float,
    val stress: Float,
    val progress: Float,
    val adherence: Float,
    val emotion: String
)

class ModelCClient(private val http: HttpClient) {

    suspend fun predictTreatmentPattern(
        disorder: String,
        symptoms: FloatArray,
        mood: Float,
        sleep: Float,
        activity: Float,
        stress: Float,
        progress: Float,
        adherence: Float,
        emotion: String
    ): TreatmentPattern {

        // Build request
        val request = ModelCRequest(
            disorder = disorder,
            symptoms = symptoms.toList(),
            mood = mood,
            sleep = sleep,
            activity = activity,
            stress = stress,
            progress = progress,
            adherence = adherence,
            emotion = emotion
        )

        // Log outgoing JSON
        val payloadJson = json.encodeToString(request)
        Log.e("ModelC", "Sending to Model C:\n$payloadJson")

        // Send request
        val responseText: String = http.post("http://192.168.244.1:8000/predict_model_c") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()

        // Log raw response
        Log.e("ModelC", "Raw response:\n$responseText")

        // Decode into updated TreatmentPattern
        return json.decodeFromString<TreatmentPattern>(responseText)
    }
}

