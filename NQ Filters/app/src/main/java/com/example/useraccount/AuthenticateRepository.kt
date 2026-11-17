package com.example.useraccount

import com.example.useraccount.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthenticateRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun registerUser(
        username: String,
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    )
    {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid != null) {
                    saveUserToFirestore(uid, username, email, onResult)
                } else {
                    onResult(false, "Failed to create user account")
                }
            }
            .addOnFailureListener { exception ->
                onResult(false, exception.message)
            }
    }

    fun loginUser(
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { exception ->
                onResult(false, exception.message)
            }
    }

    fun logout() {
        auth.signOut()
    }

    fun currentUser() = auth.currentUser

    private fun saveUserToFirestore(
        uid: String,
        username: String,
        email: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = User(
            uid = uid,
            username = username,
            email = email,
            timeCreated = System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(uid)
            .set(user)
            .addOnSuccessListener {
                onResult(true, null)
            }
            .addOnFailureListener { exception ->
                onResult(false, exception.message)
            }
    }
}