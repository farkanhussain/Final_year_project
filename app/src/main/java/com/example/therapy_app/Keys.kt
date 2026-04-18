package com.example.therapy_app

import com.example.therapy_app.BuildConfig


object Keys {
    val openAIKey: String by lazy {
        BuildConfig.OPENAI_API_KEY

    }
}
