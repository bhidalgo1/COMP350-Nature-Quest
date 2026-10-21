package com.example.naturequesthomescreen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import android.content.Intent
import com.example.naturequesthomescreen.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                var showCamera by remember { mutableStateOf(false) }//variable to hold camera screen state.
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
                            Toast.makeText(this, "Map button clicked", Toast.LENGTH_SHORT).show()
                        },
                        onUploadClick = {
                            startActivity(
                              Intent(this@MainActivity, PhotoUploadActivity::class.java)
                            )
                        }
                    )
                }
            }
        }
    }
}