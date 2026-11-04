package com.example.natureobservation.feature.posts

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/** Live Firestore feed. The host app can display this anywhere in its navigation. */
@Composable
fun PhotoFeedScreen() {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<ObservationPost>() }
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items = posts, key = { it.id }) { post ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val photo by rememberRemotePhotoBitmap(repository, post.storagePath)
                        if (photo != null) {
                            Image(
                                bitmap = photo!!,
                                contentDescription = "Photo for ${post.title}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(200.dp)
                            )
                        } else {
                            Text("Loading photo...", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(post.title, style = MaterialTheme.typography.titleMedium)
                        if (post.description.isNotBlank()) Text(post.description)
                        Text("Posted by ${post.authorId.take(8)}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
