package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.useraccount.AuthenticateRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthenticateVm : ViewModel() {
    private val repository = AuthenticateRepository()
    private val authenticateState = MutableStateFlow(AuthUiState())
    val authState = authenticateState.asStateFlow()

    fun register(username: String, email: String, password: String) {
        if (authState.value.isLoading) return
        val cleanUsername = username.trim()
        val cleanEmail = email.trim()
        val error = when {
            cleanUsername.isBlank() -> "Enter a username."
            cleanEmail.isBlank() -> "Enter your email."
            password.isBlank() -> "Enter a password."
            else -> null
        }
        if (error != null) {
            authenticateState.value = AuthUiState(error = error)
            return
        }
        authenticateState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                val notice = repository.registerUser(cleanUsername, cleanEmail, password)
                authenticateState.value = AuthUiState(successful = true, notice = notice)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                authenticateState.value = AuthUiState(
                    error = failure.localizedMessage ?: "Could not create your account. Try again."
                )
            }
        }
    }

    fun login(email: String, password: String) {
        if (authState.value.isLoading) return
        val cleanEmail = email.trim()
        val error = when {
            cleanEmail.isBlank() -> "Enter your email."
            password.isBlank() -> "Enter your password."
            else -> null
        }
        if (error != null) {
            authenticateState.value = AuthUiState(error = error)
            return
        }
        authenticateState.value = AuthUiState(isLoading = true)
        viewModelScope.launch {
            try {
                repository.loginUser(cleanEmail, password)
                authenticateState.value = AuthUiState(successful = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                authenticateState.value = AuthUiState(
                    error = failure.localizedMessage ?: "Could not log in. Try again."
                )
            }
        }
    }

    // Existing guests can still reach home and upgrade through their profile.
    fun isUserLoggedIn(): Boolean = repository.currentUser() != null

    fun resetState() {
        authenticateState.value = AuthUiState()
    }

    fun logout() {
        repository.logout()
        resetState()
    }
}
