package com.example.viewmodel

import androidx.lifecycle.ViewModel
import com.example.useraccount.AuthenticateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AuthenticateVm : ViewModel() {
    private val repository = AuthenticateRepository()

    private val authenticateState = MutableStateFlow(AuthUiState())

    val authState = authenticateState.asStateFlow()

    fun register(username: String, email: String, password: String) {
        if (username.isBlank()) {
            authenticateState.value = AuthUiState(error = "Username field is blank")
            return
        }
        if (email.isBlank()) {
            authenticateState.value = AuthUiState(error = "Email field is blank")
            return
        }
        if (password.isBlank()) {
            authenticateState.value = AuthUiState(error = "Password field is blank")
            return
        }

        authenticateState.value = AuthUiState(isLoading = true)

        repository.registerUser(username, email, password) { success, message ->
            authenticateState.value = if (success) {
                //Analytics.logSignUp()
                AuthUiState(successful = true)
            } else {
                AuthUiState(error = message)
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank()) {
            authenticateState.value = AuthUiState(error = "Username field is blank")
            return
        }
        if (email.isBlank()) {
            authenticateState.value = AuthUiState(error = "Email field is blank")
            return
        }

        authenticateState.value = AuthUiState(isLoading = true)

        repository.loginUser(email, password) { success, message ->
            authenticateState.value = if (success) {
                //Analytics.logLogin()
                AuthUiState(successful = true)
            } else {
                AuthUiState(error = message)
            }
        }
    }

    fun isUserLoggedIn(): Boolean {
        return repository.currentUser() != null
    }

    fun resetState() {
        authenticateState.value = AuthUiState()
    }

    fun logout() {
        repository.logout()
    }
}