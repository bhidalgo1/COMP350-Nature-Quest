package com.example.naturequesthomescreen

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.example.naturequesthomescreen.ui.theme.NatureQuestHomeScreenTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NatureQuestHomeScreenTheme {
                var showCamera by remember { mutableStateOf(false) }
                var showLikedPosts by remember { mutableStateOf(false) }

                if(showLikedPosts){
                    LikedPostsScreen(
                        onBack = { showLikedPosts = false }
                    )
                }else if (showCamera) {
                    CameraScreen(
                        onBack = { showCamera = false }
                    )
                } else {
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