package com.example.naturequesthomescreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.ObservationPost
import com.example.natureobservation.feature.posts.ObservationRepository
import com.example.natureobservation.feature.posts.PostInteractions
import com.example.natureobservation.feature.posts.rememberRemotePhotoBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.selects.select

@Composable
fun FilteredPostsScreen(
    selectedFilters: List<Boolean>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember(context) {
        ObservationRepository(context)
    }

    var posts by remember { mutableStateOf<List<ObservationPost>>(emptyList()) }
    var plantPosts by remember { mutableStateOf<List<ObservationPost>>(emptyList()) }
    var mammalPosts by remember { mutableStateOf<List<ObservationPost>>(emptyList()) }
    var birdPosts by remember { mutableStateOf<List<ObservationPost>>(emptyList()) }
    var reptilePosts by remember { mutableStateOf<List<ObservationPost>>(emptyList()) }
    var insectPosts by remember { mutableStateOf<List<ObservationPost>>(emptyList()) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(repository, reloadKey) {
        loading = true
        error = null

        try {
            if (selectedFilters[0]) plantPosts = (repository.getFilteredPosts("Plant"))
            if (selectedFilters[1]) mammalPosts = repository.getFilteredPosts("Mammal")
            if (selectedFilters[2]) birdPosts = repository.getFilteredPosts("Bird")
            if (selectedFilters[3]) reptilePosts = repository.getFilteredPosts("Reptile")
            if (selectedFilters[4]) insectPosts = repository.getFilteredPosts("Insect")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.localizedMessage
                ?: "Could not load liked posts."
        } finally {
            loading = false
        }
    }

    posts = plantPosts + mammalPosts + birdPosts + reptilePosts + insectPosts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onBack) {
                Text("Back")
            }

            TextButton(
                enabled = !loading,
                onClick = { reloadKey++ }
            ) {
                Text("Refresh")
            }
        }

        Text(
            text = "Filtered posts",
            style = MaterialTheme.typography.headlineMedium
        )

        when {
            loading -> {
                CircularProgressIndicator()
            }

            error != null -> {
                Text(
                    text = "Couldn't load posts: $error",
                    color = MaterialTheme.colorScheme.error
                )
                Button(onClick = { reloadKey++ }) {
                    Text("Try again")
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = posts,
                        key = { it.id }
                    ) { post ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (post.storagePath.isNotBlank()) {
                                    val photo by rememberRemotePhotoBitmap(
                                        repository,
                                        post.storagePath
                                    )

                                    val bitmap = photo
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = "Photo for ${post.title}",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(200.dp)
                                        )
                                    } else {
                                        Text("Loading photo...")
                                    }
                                }

                                Text(
                                    text = post.title,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                if (post.description.isNotBlank()) {
                                    Text(post.description)
                                }

                                Text(
                                    text = "Posted by ${post.authorId.take(8)}",
                                    style = MaterialTheme.typography.labelSmall
                                )

                                PostInteractions(
                                    observation = post,
                                    onDeleted = {
                                        posts = posts.filterNot { it.id == post.id }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}