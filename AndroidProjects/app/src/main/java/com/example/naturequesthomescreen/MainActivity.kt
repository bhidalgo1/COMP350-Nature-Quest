package com.example.naturequesthomescreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.naturequesthomescreen.ui.theme.NatureQuestHomeScreenTheme
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.example.viewmodel.AuthenticateVm
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val firestore = FirebaseFirestore.getInstance()
        val settings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(
                PersistentCacheSettings.newBuilder()
                    .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
            )
            .build()
        firestore.firestoreSettings = settings
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            FirebaseCrashlytics.getInstance().setUserId(currentUser.uid)
        }

        setContent {
            NatureQuestHomeScreenTheme {
                var showCamera by remember { mutableStateOf(false) }
                var showLikedPosts by remember { mutableStateOf(false) }
                var toRegisterScreen by remember { mutableStateOf(false) }
                var toLoginScreen by remember { mutableStateOf(false) }
                var toHomeScreen by remember { mutableStateOf(false) }
                val authVm: AuthenticateVm = viewModel()

                NavigateToScreen()
                //if (authVm.isUserLoggedIn()) toHomeScreen = true else toLoginScreen = true

                if (showLikedPosts) {
                    LikedPostsScreen(
                        onBack = { showLikedPosts = false }
                    )
                } else if (showCamera) {
                    CameraScreen(
                        onBack = { showCamera = false }
                    )
                } else if (toLoginScreen) {
                    LoginScreen(
                        onRegisterClick = {
                            toRegisterScreen = true
                        },
                        onLoginSuccess = {
                            toHomeScreen = true
                        }
                    )
                } else if (toRegisterScreen) {
                    RegisterScreen(
                        onLoginClick = {
                            toLoginScreen = true
                        },
                        onRegisterSuccess = {
                            toHomeScreen = true
                        }
                    )
                } else if (toHomeScreen) {
                    HomeScreen(
                        onLikedPostsClick = {
                            showLikedPosts = true
                        },
                        onCameraClick = {
                            showCamera = true
                        },
                        onMapClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    MapActivity::class.java
                                )
                            )
                        },
                        onUploadClick = {
                            startActivity(
                                Intent(
                                    this@MainActivity,
                                    PhotoUploadActivity::class.java
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}
