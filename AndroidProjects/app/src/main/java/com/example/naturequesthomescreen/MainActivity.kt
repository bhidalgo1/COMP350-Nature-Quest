package com.example.naturequesthomescreen

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import android.content.Intent
import com.example.naturequesthomescreen.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                HomeScreen(
                    onCameraClick = {
                        Toast.makeText(this, "Camera button clicked", Toast.LENGTH_SHORT).show()
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