package com.example.natureobservation.feature.posts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private data class PostComment(val id: String, val authorId: String, val text: String)

/** Likes are keyed by user ID so a user can have only one like per post. */
@Composable
fun PostInteractions(postId: String) {
    val context = LocalContext.current
    val repository = remember(context) { ObservationRepository(context) }
    val db = remember { FirebaseFirestore.getInstance() }
    val post = remember(db, postId) { db.collection("observations").document(postId) }
    val scope = rememberCoroutineScope()
    var uid by remember(postId) { mutableStateOf<String?>(null) }
    var liked by remember(postId) { mutableStateOf(false) }
    var likeCount by remember(postId) { mutableStateOf(0) }
    var disliked by remember(postId) { mutableStateOf(false) }
    var dislikeCount by remember(postId) { mutableStateOf(0) }
    var dislikesReady by remember(postId) { mutableStateOf(false) }
    var likesReady by remember(postId) { mutableStateOf(false) }
    var changingLike by remember(postId) { mutableStateOf(false) }
    var showComments by rememberSaveable(postId) { mutableStateOf(false) }
    var draft by rememberSaveable(postId) { mutableStateOf("") }
    var sending by remember(postId) { mutableStateOf(false) }
    var error by remember(postId) { mutableStateOf<String?>(null) }
    var commentsError by remember(postId) { mutableStateOf<String?>(null) }
    var commentsLoading by remember(postId) { mutableStateOf(false) }
    var comments by remember(postId) { mutableStateOf<List<PostComment>>(emptyList()) }

    DisposableEffect(post) {
        var listener: ListenerRegistration? = null
        val job = scope.launch {
            try {
                val userId = repository.ensureSignedIn()
                uid = userId
                listener = post.collection("likes").addSnapshotListener { snapshot, failure ->
                    if (failure != null) {
                        likesReady = false
                        error = failure.localizedMessage ?: "Could not load likes."
                    } else if (snapshot != null) {
                        likeCount = snapshot.size()
                        liked = snapshot.documents.any { it.id == userId }
                        likesReady = true
                        error = null
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.localizedMessage ?: "Could not connect."
            }
        }
        onDispose { job.cancel(); listener?.remove() }
    }

    // Only listen to comments while the user has the comments section open.
    DisposableEffect(post, showComments, uid) {
        var listener: ListenerRegistration? = null
        if (showComments && uid != null) {
            commentsLoading = true
            commentsError = null
            listener = post.collection("comments")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener { snapshot, failure ->
                    commentsLoading = false
                    if (failure != null) {
                        commentsError = failure.localizedMessage ?: "Could not load comments."
                    } else if (snapshot != null) {
                        commentsError = null
                        comments = snapshot.documents.mapNotNull { document ->
                            val author = document.getString("authorId") ?: return@mapNotNull null
                            val text = document.getString("text") ?: return@mapNotNull null
                            PostComment(document.id, author, text)
                        }
                    }
                }
        }
        onDispose { listener?.remove() }
    }

    DisposableEffect(post, uid) {
        val userId = uid
        var listener: ListenerRegistration? = null
        dislikesReady = false

        if (userId != null) {
            listener = post.collection("dislikes")
                .addSnapshotListener { snapshot, failure ->
                    if (failure != null) {
                        dislikesReady = false
                        error = failure.localizedMessage
                            ?: "Could not load dislikes."
                    } else if (snapshot != null) {
                        dislikeCount = snapshot.size()
                        disliked = snapshot.documents.any {
                            it.id == userId
                        }
                        dislikesReady = true
                    }
                }
        }

        onDispose {
            listener?.remove()
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(
                enabled = uid != null && likesReady && !changingLike,
                onClick = {
                    val userId = uid ?: return@TextButton
                    changingLike = true
                    error = null
                    scope.launch {
                        try {
                            val like = post.collection("likes").document(userId)
                            db.runTransaction { transaction ->
                                if (transaction.get(like).exists()) {
                                    transaction.delete(like)
                                } else {
                                    transaction.delete(
                                        post.collection("dislikes").document(userId)
                                    )
                                    transaction.set(like, mapOf("createdAt" to FieldValue.serverTimestamp()))
                                }
                                Unit
                            }.await()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Could not change your like."
                        } finally {
                            changingLike = false
                        }
                    }
                }
            ) { Text(if (liked) "Unlike ($likeCount)" else "Like ($likeCount)") }
            TextButton(onClick = { showComments = !showComments }) {
                Text(if (showComments) "Hide comments" else "Comments")
            }
            TextButton(
                enabled = uid != null && dislikesReady && !changingLike,
                onClick = {
                    val userId = uid ?: return@TextButton
                    changingLike = true
                    error = null

                    scope.launch {
                        try {
                            val dislike = post.collection("dislikes")
                                .document(userId)
                            val like = post.collection("likes")
                                .document(userId)

                            db.runTransaction { transaction ->
                                if (transaction.get(dislike).exists()) {
                                    transaction.delete(dislike)
                                } else {
                                    transaction.delete(like)
                                    transaction.set(
                                        dislike,
                                        mapOf(
                                            "createdAt" to FieldValue.serverTimestamp()
                                        )
                                    )
                                }
                                Unit
                            }.await()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.localizedMessage
                                ?: "Could not change your dislike."
                        } finally {
                            changingLike = false
                        }
                    }
                }
            ) {
                Text(
                    if (disliked) "Undo dislike ($dislikeCount)"
                    else "Dislike ($dislikeCount)"
                )
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (showComments) {
            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= 500) draft = it },
                label = { Text("Write a comment") },
                supportingText = { Text("${draft.length}/500") },
                enabled = !sending,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = uid != null && draft.isNotBlank() && !sending,
                onClick = {
                    val userId = uid ?: return@Button
                    val text = draft.trim()
                    sending = true
                    error = null
                    val comment = post.collection("comments").document()
                    scope.launch {
                        try {
                            comment.set(mapOf(
                                "authorId" to userId,
                                "text" to text,
                                "createdAt" to FieldValue.serverTimestamp()
                            )).await()
                            draft = ""
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.localizedMessage ?: "Could not send your comment."
                        } finally {
                            sending = false
                        }
                    }
                }
            ) { Text(if (sending) "Sending..." else "Send comment") }
            commentsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (commentsLoading) Text("Loading comments...")
            if (!commentsLoading && commentsError == null && comments.isEmpty()) {
                Text("No comments yet.")
            }
            comments.forEach { comment ->
                key(comment.id) {
                    Text("${comment.authorId.take(8)}: ${comment.text}")
                }
            }
            if (comments.size == 50) Text("Showing the newest 50 comments.")
        }
    }
}
