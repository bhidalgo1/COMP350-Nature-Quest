package com.example.natureobservation.feature.posts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import android.content.Intent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import com.example.naturequesthomescreen.MapActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import java.util.Locale

/** Live Firestore feed. The host app can display this anywhere in its navigation. */
@Composable
fun PhotoFeedScreen(focusPostId: String? = null) {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<ObservationPost>() }
    val listState = rememberLazyListState()
    var focusedPostId by remember { mutableStateOf<String?>(null) }
    val focusIndex = posts.indexOfFirst { it.id == focusPostId }

    LaunchedEffect(focusPostId, focusIndex) {
        if (focusPostId != null && focusIndex >= 0 && focusedPostId != focusPostId) {
            listState.scrollToItem(focusIndex)
            focusedPostId = focusPostId
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(repository) {
        var registration: ListenerRegistration? = null
        val job = scope.launch {
            try {
                repository.ensureSignedIn()
                registration = repository.observeFeed(
                    onUpdate = { items ->
                        posts.clear()
                        posts.addAll(items)
                        error = null
                        loading = false
                    },
                    onError = { failure ->
                        error = failure.localizedMessage ?: "Could not load observations."
                        loading = false
                    }
                )
            } catch (failure: Exception) {
                error = failure.localizedMessage ?: "Could not connect to Firebase."
                loading = false
            }
        }
        onDispose { job.cancel(); registration?.remove() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Nature Observations", style = MaterialTheme.typography.headlineSmall)
        if (loading) Text("Loading posts...")
        error?.let { Text("Feed error: $it", color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error == null) Text("No observations posted yet.")
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items = posts, key = { it.id }) { post ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (post.isMapPin || post.latitude != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        enabled = post.latitude != null &&
                                                post.longitude != null
                                    ) {
                                        context.startActivity(
                                            Intent(context, MapActivity::class.java)
                                                .putExtra("pin_id", post.id)
                                        )
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "📍 View on map",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    if (post.latitude != null && post.longitude != null) {
                                        Text(
                                            text = String.format(
                                                Locale.US,
                                                "%.4f, %.4f",
                                                post.latitude,
                                                post.longitude
                                            ),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        if (post.storagePath.isNotBlank()) {
                            val photo by rememberRemotePhotoBitmap(repository, post.storagePath)
                            if (photo != null) {
                                val bitmap = photo!!
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "Photo for ${post.title}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat())
                                )
                            } else {
                                Text("Loading photo...", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Text(post.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (post.description.isNotBlank()) {
                            var expanded by remember(post.id, post.description) { mutableStateOf(false) }
                            var hasOverflow by remember(post.id, post.description) { mutableStateOf(false) }

                            Text(
                                text = post.description,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (expanded) Int.MAX_VALUE else 3,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { layout ->
                                    if (!expanded) {
                                        hasOverflow = layout.hasVisualOverflow
                                    }
                                }
                            )

                            if (hasOverflow || expanded) {
                                TextButton(onClick = { expanded = !expanded }) {
                                    Text(if (expanded) "Show less" else "Show more")
                                }
                            }
                        }

                        Text(
                            text = "Posted by ${post.authorId.take(8)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        PostInteractions(observation = post)
                    }
                }
            }
        }
    }
}