package com.example.viewmodel

data class AuthUiState(
    val isLoading: Boolean = false,
    val successful: Boolean = false,
    val error: String? = null
)
