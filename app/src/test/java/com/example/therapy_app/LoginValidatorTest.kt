package com.example.therapy_app

import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LoginValidatorTest {

    // ---------------------------------------------------------
    // AUTHENTICATION TESTS
    // ---------------------------------------------------------

    @Test
    fun validLogin_returnsTrue() {
        val repo = mock<AuthRepository>()
        whenever(repo.login("test@example.com", "password123"))
            .thenReturn(true)

        val validator = LoginValidator(repo)
        val result = validator.login("test@example.com", "password123")

        assertTrue(result)
    }

    @Test
    fun invalidLogin_returnsFalse() {
        val repo = mock<AuthRepository>()
        whenever(repo.login("test@example.com", "wrongpass"))
            .thenReturn(false)

        val validator = LoginValidator(repo)
        val result = validator.login("test@example.com", "wrongpass")

        assertFalse(result)
    }

    // ---------------------------------------------------------
    // INPUT VALIDATION TESTS
    // ---------------------------------------------------------

    @Test
    fun invalidEmail_returnsError() {
        val result = InputValidator.validateLogin("invalidEmail", "password123")
        assertEquals("Enter a valid email address", result)
    }

    @Test
    fun invalidPassword_returnsError() {
        val result = InputValidator.validateLogin("test@example.com", "123")
        assertEquals("Password must be at least 6 characters", result)
    }
}
