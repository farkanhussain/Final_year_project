package com.example.therapy_app

interface AuthRepository {
    fun login(email: String, password: String): Boolean
}
