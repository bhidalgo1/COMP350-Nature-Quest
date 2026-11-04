package com.example.natureobservation.feature.posts

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Isolated Firebase API for publishing and reading nature observations. */
class ObservationRepository(context: Context) {
    private val appContext = context.applicationContext
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    suspend fun ensureSignedIn(): String {
        check(FirebaseApp.initializeApp(appContext) != null) {
            "Firebase is not configured. Add the matching google-services.json to app/."
        }
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
            ?: error("Anonymous Firebase Authentication is not enabled.")
    }

    /** Upload first; only create a visible Firestore post when upload succeeds. */
    suspend fun publish(
        imageUri: Uri,
        title: String,
        description: String,
        onProgress: (Float) -> Unit
    ): String {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) {
            "Enter a title (up to 100 characters)."
        }
        require(cleanDescription.length <= 1000) {
            "Keep the description under 1,000 characters."
        }

        val uid = ensureSignedIn()
        val postId = UUID.randomUUID().toString()
        val (photo, mimeType) = copySelectedImage(imageUri)
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) } ?: "jpg"
        val storagePath = "observation_photos/$uid/$postId.$ext"
        val photoRef = storage.reference.child(storagePath)
        var uploaded = false
        try {
            val task: UploadTask = photoRef.putFile(
                Uri.fromFile(photo),
                StorageMetadata.Builder().setContentType(mimeType).build()
            )
            val progressListener = com.google.firebase.storage.OnProgressListener<UploadTask.TaskSnapshot> { snapshot ->
                if (snapshot.totalByteCount > 0) {
                    onProgress((snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount).coerceIn(0f, 1f))
                }
            }
            task.addOnProgressListener(progressListener)
            try {
                task.await()
                uploaded = true
            } catch (cancelled: CancellationException) {
                task.cancel()
                throw cancelled
            } finally {
                task.removeOnProgressListener(progressListener)
            }

            val record = mapOf(
                "authorId" to uid,
                "title" to cleanTitle,
                "description" to cleanDescription,
                "storagePath" to storagePath,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("observations").document(postId).set(record).await()
            return postId
        } catch (error: Exception) {
            // Avoid leaving an unreferenced picture if the Firestore write fails.
            if (uploaded) try { photoRef.delete().await() } catch (_: Exception) { }
            throw error
        } finally {
            photo.delete()
        }
    }

    /** Feed updates automatically whenever an observation is published. */
    fun observeFeed(
        onUpdate: (List<ObservationPost>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration = db.collection("observations")
        .orderBy("createdAt", Query.Direction.DESCENDING)
        .limit(40)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
            } else if (snapshot != null) {
                onUpdate(snapshot.documents.mapNotNull { document ->
                    val author = document.getString("authorId") ?: return@mapNotNull null
                    val title = document.getString("title") ?: return@mapNotNull null
                    val path = document.getString("storagePath") ?: return@mapNotNull null
                    ObservationPost(
                        id = document.id,
                        authorId = author,
                        title = title,
                        description = document.getString("description").orEmpty(),
                        storagePath = path,
                        createdAt = document.getTimestamp("createdAt")
                    )
                })
            }
        }

    /** Storage SDK enforces authenticated reads; temp files are deleted after decoding. */
    suspend fun loadPreview(storagePath: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("observation_", ".img", appContext.cacheDir)
        try {
            storage.reference.child(storagePath).getFile(temp).await()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temp.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
            android.graphics.BitmapFactory.decodeFile(
                temp.absolutePath,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            )
        } finally {
            temp.delete()
        }
    }

    /** Copies streams with a 10 MiB ceiling instead of loading an entire image in memory. */
    private suspend fun copySelectedImage(uri: Uri): Pair<File, String> = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: error("Choose an image with a supported file type.")
        require(mime.startsWith("image/")) { "Please select an image." }
        val temp = File.createTempFile("upload_", ".img", appContext.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { output ->
                    var size = 0L
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        size += count
                        require(size <= 10L * 1024 * 1024) { "Choose a photo smaller than 10 MB." }
                        output.write(buffer, 0, count)
                    }
                    require(size > 0) { "The photo is empty." }
                }
            } ?: error("Couldn't open the selected photo.")
            temp to mime
        } catch (error: Exception) {
            temp.delete()
            throw error
        }
    }
}
