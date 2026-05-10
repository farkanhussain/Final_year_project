package com.example.therapy_app

object InputValidator {

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

    fun validateLogin(email: String, password: String): String? {
        return when {
            email.isEmpty() ->
                "Email is required"

            !emailRegex.matches(email) ->
                "Enter a valid email address"

            password.isEmpty() ->
                "Password is required"

            password.length < 6 ->
                "Password must be at least 6 characters"

            else -> null
        }
    }
}
