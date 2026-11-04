package com.example.camerademo

import android.content.ContentValues
import android.provider.MediaStore
import android.widget.Toast

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.ImageCapture
import androidx.camera.core.CameraSelector
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.DisposableEffect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.camera.core.ImageCaptureException
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.camerademo.ui.theme.CameraDemoTheme

class MainActivity : ComponentActivity() {

    //request camera permission and store response in variable.
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            // We'll deal with the result later
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        //if block to check whether permission to camera has already been granted.
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            CameraDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->

                    var showCamera by remember { mutableStateOf(false) }
                    val imageCapture = remember { ImageCapture.Builder().build() }
                    if(showCamera){
                        //camera screen
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ){
                            CameraPreview(
                                lifecycleOwner = this@MainActivity,
                                imageCapture = imageCapture,
                                modifier = Modifier.fillMaxSize()
                            )
                            Button(
                                onClick = {
                                    showCamera = false
                                }, modifier = Modifier.align(Alignment.TopStart)
                            ){
                                Text("<-")
                            }
                            Button(
                                onClick = {
                                    //take picture
                                    val contentValues = ContentValues().apply {
                                        put(MediaStore.Images.Media.DISPLAY_NAME, "CameraDemo.jpg")
                                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                                    }
                                    val outputOptions = ImageCapture.OutputFileOptions.Builder(
                                        contentResolver,
                                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                        contentValues
                                    ).build()
                                    imageCapture.takePicture(
                                        outputOptions,
                                        ContextCompat.getMainExecutor(this@MainActivity),
                                        object : ImageCapture.OnImageSavedCallback{
                                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                println("PHOTO SAVED!")
                                                Toast.makeText(this@MainActivity,
                                                    "Photo Saved!",
                                                    Toast.LENGTH_SHORT).show()
                                                //photo saved successfully
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                //something went wrong :(
                                                println("PHOTO ERROR: ${exception.message}")
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ){
                                Text("Photo")
                            }
                        }
                    }
                    else{
                        //home screen
                        Box(modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.BottomCenter
                        ){
                        Button(
                            onClick = { showCamera = true }
                        ) {
                            Text("Open Camera")
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CameraPreview(lifecycleOwner: LifecycleOwner, imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    var cameraProvider: ProcessCameraProvider? = null
    AndroidView(
        factory = { context ->
            PreviewView(context).also{previewView ->

                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    val preview = CameraPreview.Builder().build()
                    preview.surfaceProvider = previewView.surfaceProvider
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, imageCapture)

                }, ContextCompat.getMainExecutor(context))
            }
        },
        modifier = modifier
    )
    //clean up process
    DisposableEffect(Unit) {
        onDispose {
            println("CAMERA PREVIEW DISPOSED")
            cameraProvider?.unbindAll()//disconnects all lifecycle binds.
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CameraDemoTheme {
        Greeting("Android")
    }
}