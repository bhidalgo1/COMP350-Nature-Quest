package com.example.natureobservation.feature.posts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.runtime.DisposableEffect
import com.google.firebase.firestore.ListenerRegistration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Standalone screen: host app may add it to existing Compose navigation. */
@Composable
fun PhotoPostScreen(onPosted: (String) -> Unit = {}) {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val scope = rememberCoroutineScope()
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("Choose a nature photo to start a post.") }
    var availablePins by remember {
        mutableStateOf<List<ObservationPost>>(emptyList())
    }
    var selectedPinId by remember { mutableStateOf<String?>(null) }
    var loadingPins by remember { mutableStateOf(true) }

    DisposableEffect(repository) {
        var registration: ListenerRegistration? = null

        val job = scope.launch {
            try {
                val uid = repository.ensureSignedIn()

                registration = repository.observeFeed(
                    onUpdate = { posts ->
                        availablePins = posts.filter { post ->
                            post.authorId == uid &&
                                    post.latitude != null &&
                                    post.longitude != null &&
                                    post.storagePath.isBlank()
                        }
                        loadingPins = false
                    },
                    onError = { error ->
                        loadingPins = false
                        message = error.localizedMessage
                            ?: "Could not load your pins."
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loadingPins = false
                message = error.localizedMessage
                    ?: "Could not load your pins."
            }
        }

        onDispose {
            job.cancel()
            registration?.remove()
        }
    }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImage = uri }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Post an Observation", style = MaterialTheme.typography.headlineSmall)
        Text("Choose the map pin for this photo:")

        if (loadingPins) {
            Text("Loading your pins...")
        } else if (availablePins.isEmpty()) {
            Text("Create a pin on the map first, then return here.")
        }

        availablePins.forEach { pin ->
            OutlinedButton(
                onClick = {
                    selectedPinId = pin.id
                    title = pin.title
                    description = pin.description
                },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth()
            ) {
                val prefix = if (selectedPinId == pin.id) "✓ " else ""

                Text(
                    text = prefix + pin.title + "\n" +
                            String.format(
                                java.util.Locale.US,
                                "%.5f, %.5f",
                                pin.latitude,
                                pin.longitude
                            )
                )
            }
        }
        selectedImage?.let { uri ->
            val preview by rememberLocalPhotoBitmap(uri)
            if (preview != null) {
                Image(
                    bitmap = preview!!,
                    contentDescription = "Selected observation image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            }
        }
        OutlinedButton(
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose Photo") }
        OutlinedTextField(
            value = title,
            onValueChange = { if (it.length <= 100) title = it },
            label = { Text("Observation title") },
            singleLine = true,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = description,
            onValueChange = { if (it.length <= 1000) description = it },
            label = { Text("Description (optional)") },
            minLines = 3,
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val uri = selectedImage ?: return@Button
                scope.launch {
                    sending = true
                    progress = 0f
                    message = "Uploading photo..."
                    try {
                        val pinId = selectedPinId
                            ?: error("Choose a map pin first.")

                        val id = repository.publish(
                            imageUri = uri,
                            title = title,
                            description = description,
                            pinId = pinId,
                            onProgress = { progress = it }
                        )
                        message = "Observation posted successfully."
                        selectedImage = null
                        title = ""
                        description = ""
                        onPosted(id)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        message = error.localizedMessage ?: "Posting failed. Check your Firebase setup."
                    } finally {
                        sending = false
                    }
                }
            },
            enabled = selectedImage != null &&
                    title.isNotBlank() &&
                    availablePins.any { it.id == selectedPinId } &&
                    !sending,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (sending) "Posting..." else "Post Observation") }
        if (sending) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}% uploaded")
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
