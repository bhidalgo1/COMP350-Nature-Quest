package com.example.natureobservation.feature.posts

import com.example.naturequesthomescreen.ui.components.AuthorHeader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import android.content.Intent
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import com.example.naturequesthomescreen.MapActivity
import com.google.android.gms.maps.model.LatLng
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

/** Live Firestore feed. The host app can display this anywhere in its navigation. */
@Composable
fun PhotoFeedScreen(
    focusPostId: String? = null,
    organismFilters: List<String> = emptyList(),
    ownPostsOnly: Boolean = false,
    authorId: String? = null,
    showPosts: Boolean = true,
    profileHeader: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val scope = rememberCoroutineScope()
    val posts = remember { mutableStateListOf<ObservationPost>() }
    val listState = rememberLazyListState()
    var focusedPostId by remember { mutableStateOf<String?>(null) }
    val focusIndex = posts.indexOfFirst { it.id == focusPostId }.let { index ->
        if (index >= 0 && profileHeader != null) index + 1 else index
    }

    LaunchedEffect(focusPostId, focusIndex) {
        if (focusPostId != null && focusIndex >= 0 && focusedPostId != focusPostId) {
            listState.scrollToItem(focusIndex)
            focusedPostId = focusPostId
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    DisposableEffect(repository, organismFilters, ownPostsOnly, authorId, showPosts) {
        loading = true
        error = null
        posts.clear()
        var registration: ListenerRegistration? = null
        if (!showPosts) return@DisposableEffect onDispose {}
        val job = scope.launch {
            try {
                if (!ownPostsOnly && authorId == null) repository.ensureSignedIn()
                registration = repository.observeFeed(
                    organisms = organismFilters,
                    ownPostsOnly = ownPostsOnly,
                    authorId = authorId,
                    onUpdate = { items ->
                        posts.clear()
                        posts.addAll(items)
                        error = null
                        loading = false
                    },
                    onError = { failure ->
                        if (authorId != null || ownPostsOnly) posts.clear()
                        error = failure.localizedMessage ?: "Could not load observations."
                        loading = false
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.localizedMessage ?: "Could not connect to Firebase."
                loading = false
            }
        }
        onDispose { job.cancel(); registration?.remove() }
    }

    val feedHeading: @Composable () -> Unit = {
        Text(
            when {
                ownPostsOnly -> "My posts"
                authorId != null -> "Posts"
                organismFilters.isEmpty() -> "Nature Observations"
                else -> "Filtered observations"
            },
            style = MaterialTheme.typography.headlineSmall
        )
        if (organismFilters.isNotEmpty()) {
            Text(organismFilters.joinToString(", "), style = MaterialTheme.typography.bodySmall)
        }
        if (loading) Text("Loading posts...")
        error?.let { Text("Feed error: $it", color = MaterialTheme.colorScheme.error) }
        if (!loading && posts.isEmpty() && error == null) {
            Text(when {
                ownPostsOnly -> "You haven’t posted any observations yet."
                authorId != null -> "No posts yet."
                organismFilters.isEmpty() -> "No observations posted yet."
                else -> "No posts match these categories yet."
            })
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .then(if (profileHeader == null) Modifier.padding(16.dp) else Modifier)
    ) {
        if (profileHeader == null && showPosts) feedHeading()
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (profileHeader != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        profileHeader()
                        if (showPosts) Column { feedHeading() }
                    }
                }
            }
            items(items = if (showPosts) posts else emptyList(), key = { it.id }) { post ->
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
                                        val intent = Intent(context, MapActivity::class.java)
                                            .putExtra("pin_id", post.id)
                                        if (post.latitude != null && post.longitude != null) {
                                            MapActivity.putLocation(
                                                intent,
                                                LatLng(post.latitude, post.longitude)
                                            )
                                        }
                                        context.startActivity(intent)
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
                        if (post.organism.isNotBlank()) {
                            Text("Category: ${post.organism}", style = MaterialTheme.typography.labelSmall)
                        }

                        AuthorHeader(authorId = post.authorId)
                        PostInteractions(observation = post)
                    }
                }
            }
        }
    }
}