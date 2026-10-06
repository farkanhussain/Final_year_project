package com.example.therapy_app



import android.app.Application

class TherapyApp : Application() {

    companion object {
        lateinit var instance: TherapyApp
            private set
        var coldStartTimestamp: Long = 0L
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        coldStartTimestamp = System.currentTimeMillis()
    }
}
