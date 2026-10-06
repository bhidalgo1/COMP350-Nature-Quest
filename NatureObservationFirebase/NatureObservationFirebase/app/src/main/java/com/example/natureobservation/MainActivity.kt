package com.example.natureobservation

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.natureobservation.data.FirebasePhotoRepository
import com.example.natureobservation.data.firebaseUploadError
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FirebasePhotoScreen()
                }
            }
        }
    }
}

/**
 * User story:
 * "As a user, I want to upload a photo so that I can include a picture with my nature observation."
 *
 * Uploads the selected photo to Firebase Storage and remembers its object path.
 * A local preview remains available after the app restarts.
 */
@Composable
fun FirebasePhotoScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { FirebasePhotoRepository(context.applicationContext) }

    val previousPhoto = remember { repository.getLastSavedPhoto() }

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var savedPhoto by remember { mutableStateOf(previousPhoto) }
    var isSaving by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf(0f) }
    var storagePath by remember { mutableStateOf(repository.getLastStoragePath()) }
    var statusMessage by remember {
        mutableStateOf(
            if (previousPhoto != null) {
                "Your last photo was uploaded to Firebase."
            } else {
                "Choose a photo to include with your nature observation."
            }
        )
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
                statusMessage = "Photo selected. Tap Upload Photo to send it to Firebase."
            }
        }
    )

    val previewBitmap by rememberPreviewBitmap(
        selectedUri = selectedPhotoUri,
        savedFile = if (selectedPhotoUri == null) savedPhoto else null
    )

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Nature Observation",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Add a picture to your observation by uploading it to Firebase.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap!!,
                            contentDescription = "Nature observation photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = "No photo selected",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (selectedPhotoUri == null) "Choose Photo" else "Choose Different Photo")
            }

            Button(
                onClick = {
                    val uri = selectedPhotoUri ?: return@Button

                    scope.launch {
                        isSaving = true
                        uploadProgress = 0f
                        statusMessage = "Signing in and uploading photo..."

                        try {
                            val file = repository.uploadPhoto(uri) { progress -> uploadProgress = progress }
                            storagePath = repository.getLastStoragePath()
                            savedPhoto = file
                            selectedPhotoUri = null
                            statusMessage = "Photo uploaded to Firebase. Its storage path is ready to attach to your observation."
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            statusMessage = firebaseUploadError(error)
                        } finally {
                            isSaving = false
                        }
                    }
                },
                enabled = selectedPhotoUri != null && !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isSaving) "Uploading..." else "Upload Photo")
            }

            if (isSaving) {
                LinearProgressIndicator(progress = { uploadProgress }, modifier = Modifier.fillMaxWidth())
                Text("${(uploadProgress * 100).toInt()}% uploaded")
            }

            Text(
                text = statusMessage,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            storagePath?.let { path ->
                Text(
                    text = "Firebase path: $path",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/**
 * Loads a preview either from the newly selected photo URI or from the last photo
 * copied into the app's local internal-storage folder.
 */
@Composable
private fun rememberPreviewBitmap(
    selectedUri: Uri?,
    savedFile: File?
): androidx.compose.runtime.State<ImageBitmap?> {
    val context = LocalContext.current

    return produceState<ImageBitmap?>(
        initialValue = null,
        key1 = selectedUri,
        key2 = savedFile?.absolutePath
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                fun openStream() = when {
                    selectedUri != null -> context.contentResolver.openInputStream(selectedUri)
                    savedFile?.isFile == true -> savedFile.inputStream()
                    else -> null
                }
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                openStream()?.use { BitmapFactory.decodeStream(it, null, options) }
                var sample = 1
                while (options.outWidth / sample > 1200 || options.outHeight / sample > 1200) sample *= 2
                options.inJustDecodeBounds = false
                options.inSampleSize = sample
                openStream()?.use { BitmapFactory.decodeStream(it, null, options)?.asImageBitmap() }
            } catch (error: java.io.IOException) {
                null
            } catch (error: SecurityException) {
                null
            }
        }
    }
}
