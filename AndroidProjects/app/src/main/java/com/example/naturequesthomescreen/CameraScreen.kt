
package com.example.naturequesthomescreen

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.widget.Toast
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.compose.ui.zIndex

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.*

@Composable
fun CameraScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    //begin
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    //end
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture = remember {
        ImageCapture.Builder().build()
    }
//==============================================================
    Box(modifier = Modifier.fillMaxSize()) {
        if(hasCameraPermission){
            CameraPreview(
                lifecycleOwner = lifecycleOwner,
                imageCapture = imageCapture,
                modifier = Modifier.fillMaxSize()
            )
            Button(
                onClick = {
                    onBack()
                          },
                modifier = Modifier.align(Alignment.TopStart).zIndex(1f).padding(24.dp)
            ) {
                Text("<- Back")
            }
            Button(
                onClick = {
                    val contentValues = ContentValues().apply {
                        put(
                            MediaStore.Images.Media.DISPLAY_NAME,
                            "NatureQuest_${System.currentTimeMillis()}.jpg"
                        )
                        put(
                            MediaStore.Images.Media.MIME_TYPE,
                            "image/jpeg"
                        )
                    }

                    val outputOptions =
                        ImageCapture.OutputFileOptions.Builder(
                            context.contentResolver,
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            contentValues
                        ).build()

                    imageCapture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(
                                outputFileResults: ImageCapture.OutputFileResults
                            ) {
                                Toast.makeText(
                                    context,
                                    "Photo Saved!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            override fun onError(
                                exception: ImageCaptureException
                            ) {
                                Toast.makeText(
                                    context,
                                    "Photo failed: ${exception.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text("Photo")
            }
        }else{
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Camera permission is required.")

                Button(
                    onClick = {
                        permissionLauncher.launch(
                            Manifest.permission.CAMERA
                        )
                    }
                ) {
                    Text("Grant Permission")
                }
            }
        }

//==========================================================



    }
}
