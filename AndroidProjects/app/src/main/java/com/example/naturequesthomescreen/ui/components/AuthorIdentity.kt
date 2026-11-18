package com.example.naturequesthomescreen.ui.components

import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.natureobservation.feature.posts.ObservationRepository
import com.example.useraccount.PublicProfile
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CancellationException

/** Share one active Firestore listener per author across posts and comments. */
private object AuthorDirectory {
    class Entry(val userId: String) {
        val profile = mutableStateOf(PublicProfile())
        var readers = 0
        var listener: ListenerRegistration? = null
    }
    // Accessed on the UI thread (including Firestore's default listener callbacks).
    private val entries = linkedMapOf<String, Entry>()

    fun entry(userId: String): Entry {
        entries[userId]?.let { return it }
        // Retain only a bounded set of inactive author details between screen visits.
        while (entries.size >= 100) {
            val idle = entries.entries.firstOrNull { it.value.readers == 0 } ?: break
            entries.remove(idle.key)
        }
        return Entry(userId).also { entries[userId] = it }
    }

    fun acquire(entry: Entry) {
        entry.readers++
        if (entry.readers != 1 || entry.userId.isBlank()) return
        entry.listener = FirebaseFirestore.getInstance()
            .collection("publicProfiles").document(entry.userId)
            .addSnapshotListener { snapshot, error ->
                if (error == null && snapshot != null) {
                    entry.profile.value = PublicProfile(
                        username = snapshot.getString("username")?.takeIf { it.isNotBlank() }
                            ?: "Nature explorer",
                        photoPath = snapshot.getString("photoPath").orEmpty()
                    )
                }
            }
    }

    fun release(entry: Entry) {
        entry.readers--
        if (entry.readers == 0) {
            entry.listener?.remove()
            entry.listener = null
        }
    }
}

@Composable
fun rememberAuthorProfile(authorId: String): State<PublicProfile> {
    val entry = remember(authorId) { AuthorDirectory.entry(authorId) }
    DisposableEffect(entry) {
        AuthorDirectory.acquire(entry)
        onDispose { AuthorDirectory.release(entry) }
    }
    return entry.profile
}

private val avatarImages = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
}

@Composable
fun ProfileAvatar(photoPath: String, size: Dp = 32.dp, refreshKey: Int = 0) {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val bitmap by produceState<ImageBitmap?>(
        initialValue = if (photoPath.isBlank()) null else avatarImages.get(photoPath),
        key1 = photoPath,
        key2 = repository,
        key3 = refreshKey
    ) {
        // Reset a previous path immediately, including when a picture is removed.
        value = if (photoPath.isBlank()) null else avatarImages.get(photoPath)
        value = if (photoPath.isBlank()) null else {
            avatarImages.get(photoPath) ?: try {
                repository.loadPreview(photoPath, maxDimension = 256)?.asImageBitmap()
                    ?.also { avatarImages.put(photoPath, it) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = "Profile picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Default profile picture",
                    modifier = Modifier.fillMaxSize().padding(size * 0.2f),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
fun AuthorHeader(authorId: String, prefix: String = "Posted by ", avatarSize: Dp = 32.dp) {
    val author by rememberAuthorProfile(authorId)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ProfileAvatar(author.photoPath, size = avatarSize)
        Text(
            text = prefix + author.username,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
