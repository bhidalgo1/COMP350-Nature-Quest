package com.example.useraccount

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val timeCreated: Long = System.currentTimeMillis()
)