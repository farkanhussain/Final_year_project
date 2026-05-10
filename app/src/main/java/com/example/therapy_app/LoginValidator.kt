package com.example.therapy_app

class LoginValidator(private val repo: AuthRepository) {
    fun login(email: String, password: String): Boolean {
        return repo.login(email, password)
    }
}
