package com.example.naturequesthomescreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.naturequesthomescreen.ui.theme.NatureQuestHomeScreenTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

class MainActivity : ComponentActivity() {
    companion object {
        // Configure the shared instance once, including across activity recreation.
        private val configureFirestore: Unit by lazy {
            FirebaseFirestore.getInstance().firestoreSettings =
                FirebaseFirestoreSettings.Builder()
                    .setLocalCacheSettings(
                        PersistentCacheSettings.newBuilder()
                            .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            .build()
                    )
                    .build()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureFirestore

        FirebaseAuth.getInstance().currentUser?.let { user ->
            FirebaseCrashlytics.getInstance().setUserId(user.uid)
        }

        setContent {
            NatureQuestHomeScreenTheme {
                NavigateToScreen(
                    onMapClick = {
                        startActivity(Intent(this@MainActivity, MapActivity::class.java))
                    },
                    onUploadClick = {
                        startActivity(Intent(this@MainActivity, PhotoUploadActivity::class.java))
                    }
                )
            }
        }
    }
}
