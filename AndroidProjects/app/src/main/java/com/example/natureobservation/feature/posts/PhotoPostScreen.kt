package com.example.natureobservation.feature.posts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import android.app.Activity
import android.content.Intent
import androidx.compose.material3.Slider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.naturequesthomescreen.MapActivity
import com.google.android.gms.maps.model.LatLng
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
fun PhotoPostScreen(
    initialLocation: LatLng? = null,
    onPosted: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val scope = rememberCoroutineScope()
    var selectedImage by remember { mutableStateOf<Uri?>(null) }
    var rotationDegrees by rememberSaveable { mutableStateOf(0) }
    var photoZoom by rememberSaveable { mutableStateOf(1f) }
    var photoPanX by rememberSaveable { mutableStateOf(0f) }
    var photoPanY by rememberSaveable { mutableStateOf(0f) }
    var location by rememberSaveable { mutableStateOf(initialLocation) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf("Choose a nature photo to start a post.") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            selectedImage = uri
            rotationDegrees = 0
            photoZoom = 1f
            photoPanX = 0f
            photoPanY = 0f
        }
    }
    val locationPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let { data ->
                MapActivity.readLocation(data)?.let { point -> location = point }
            }
        }
    }
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Post an Observation", style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(
            onClick = {
                val mapIntent = Intent(context, MapActivity::class.java)
                    .putExtra(MapActivity.EXTRA_PICK_LOCATION, true)
                location?.let { point -> MapActivity.putLocation(mapIntent, point) }
                locationPicker.launch(mapIntent)
            },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (location == null) "Add map location (optional)"
                else "Change map location"
            )
        }

        if (location != null) {
            Text("This post will appear on the map.")
        }
        selectedImage?.let { uri ->
            val preview by produceState<android.graphics.Bitmap?>(null, uri) {
                value = repository.loadDraftPreview(uri)
            }

            preview?.let { bitmap ->
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().height(220.dp).clipToBounds(),
                    contentAlignment = Alignment.Center
                ) {
                    val quarterTurn = rotationDegrees % 180 != 0
                    val ratio = if (quarterTurn) {
                        bitmap.height.toFloat() / bitmap.width
                    } else {
                        bitmap.width.toFloat() / bitmap.height
                    }
                    val frameWidth = minOf(maxWidth, maxHeight * ratio)
                    val frameHeight = frameWidth / ratio
                    val imageWidth = if (quarterTurn) frameHeight else frameWidth
                    val imageHeight = if (quarterTurn) frameWidth else frameHeight

                    Box(
                        modifier = Modifier
                            .requiredSize(frameWidth, frameHeight)
                            .clipToBounds()
                            .pointerInput(photoZoom, rotationDegrees, sending, uri) {
                                if (!sending && photoZoom > 1f) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()

                                        val maxX = size.width * (photoZoom - 1f) / 2f
                                        val maxY = size.height * (photoZoom - 1f) / 2f

                                        if (maxX > 0f && maxY > 0f) {
                                            photoPanX = (photoPanX + dragAmount.x / maxX)
                                                .coerceIn(-1f, 1f)
                                            photoPanY = (photoPanY + dragAmount.y / maxY)
                                                .coerceIn(-1f, 1f)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = photoZoom
                                    scaleY = photoZoom
                                    translationX = photoPanX * size.width * (photoZoom - 1f) / 2f
                                    translationY = photoPanY * size.height * (photoZoom - 1f) / 2f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Selected observation photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .rotate(rotationDegrees.toFloat())
                                    .requiredSize(imageWidth, imageHeight)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { rotationDegrees = (rotationDegrees + 270) % 360 },
                        enabled = !sending
                    ) { Text("Rotate left") }

                    OutlinedButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        enabled = !sending
                    ) { Text("Rotate right") }
                }

                Text("Zoom / crop")
                Slider(
                    value = photoZoom,
                    onValueChange = { photoZoom = it },
                    valueRange = 1f..3f,
                    enabled = !sending
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


                        val id = repository.publish(
                            imageUri = uri,
                            title = title,
                            description = description,
                            location = location,
                            rotationDegrees = rotationDegrees,
                            photoZoom = photoZoom,
                            photoPanX = photoPanX,
                            photoPanY = photoPanY,
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
