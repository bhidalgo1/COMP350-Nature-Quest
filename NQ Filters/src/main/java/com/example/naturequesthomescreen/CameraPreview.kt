package com.example.naturequesthomescreen


import androidx.core.content.ContextCompat
import androidx.camera.view.PreviewView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.core.ImageCapture
import androidx.camera.core.CameraSelector
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier

@Composable
fun CameraPreview(lifecycleOwner: LifecycleOwner, imageCapture: ImageCapture, modifier: Modifier = Modifier) {
    var cameraProvider: ProcessCameraProvider? = null
    AndroidView(
        factory = { context ->
            PreviewView(context).also{previewView ->
                previewView.implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE

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