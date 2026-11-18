package com.example.useraccount

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class AuthenticateRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /** Returns a warning if the account was created but profile setup was incomplete. */
    suspend fun registerUser(username: String, email: String, password: String): String? {
        val current = auth.currentUser
        check(current == null || current.isAnonymous) {
            "Log out before creating another account."
        }
        val result = if (current?.isAnonymous == true) {
            // Upgrade the guest account without changing ownership of posts and likes.
            current.linkWithCredential(EmailAuthProvider.getCredential(email, password)).await()
        } else {
            auth.createUserWithEmailAndPassword(email, password).await()
        }
        val user = checkNotNull(result.user) { "Could not finish creating the account." }

        // Authentication has succeeded. Profile-storage failure must not ask the
        // user to create the same account again or wait indefinitely while offline.
        val displayNameSaved = saveProfileDetail {
            user.updateProfile(
                UserProfileChangeRequest.Builder().setDisplayName(username).build()
            ).await()
        }
        val documentSaved = saveProfileDetail {
            firestore.collection("users").document(user.uid).set(
                User(
                    uid = user.uid,
                    username = username,
                    email = email,
                    timeCreated = System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        }
        return when {
            documentSaved -> null
            displayNameSaved -> "Your account was created. Your username is saved, but the " +
                    "profile document could not be saved yet. You can still log in."
            else -> "Your account was created, but your username could not be saved. " +
                    "You can still log in with your email and password."
        }
    }

    private suspend fun saveProfileDetail(save: suspend () -> Unit): Boolean {
        return withTimeoutOrNull(10_000L) {
            try {
                save()
                true
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
        } ?: false
    }

    suspend fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    suspend fun loadUsername(userId: String): String? = withTimeout(15_000L) {
        firestore.collection("users").document(userId).get().await()
            .getString("username")?.takeIf { it.isNotBlank() }
    }

    /** Counts saved observations, including map pins, without downloading posts. */
    suspend fun countPosts(userId: String): Long = withTimeout(15_000L) {
        firestore.collection("observations")
            .whereEqualTo("authorId", userId)
            .count()
            .get(AggregateSource.SERVER)
            .await()
            .count
    }

    fun logout() {
        auth.signOut()
    }

    fun currentUser() = auth.currentUser
}
