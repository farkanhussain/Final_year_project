package com.example.therapy_app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

suspend fun fetchWebsiteText(url: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val request = Request.Builder()
                .url(url)
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Safari/537.36"
                )
                .header("Accept", "text/html,application/xhtml+xml")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Connection", "keep-alive")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext "ERROR_FETCHING_CONTENT"

            // Parse HTML with Jsoup
            val doc = Jsoup.parse(body)
            doc.text()

        } catch (e: Exception) {
            "ERROR_FETCHING_CONTENT"
        }
    }
}
