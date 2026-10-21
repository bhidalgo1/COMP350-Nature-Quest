package com.example.naturequesthomescreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var showCamera by remember { mutableStateOf(false) }

                if (showCamera) {
                    CameraScreen(
                        onBack = { showCamera = false }
                    )
                } else {
                    HomeScreen(
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