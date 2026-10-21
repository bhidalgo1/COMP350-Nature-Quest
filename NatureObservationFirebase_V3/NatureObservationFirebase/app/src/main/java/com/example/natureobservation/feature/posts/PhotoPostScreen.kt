package com.example.natureobservation.feature.posts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> selectedImage = uri }

    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Post an Observation", style = MaterialTheme.typography.headlineSmall)
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
                        val id = repository.publish(uri, title, description) { progress = it }
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
            enabled = selectedImage != null && title.isNotBlank() && !sending,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (sending) "Posting..." else "Post Observation") }
        if (sending) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text("${(progress * 100).toInt()}% uploaded")
        }
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
