package com.example.natureobservation.feature.posts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Matrix
import android.media.ExifInterface
import android.webkit.MimeTypeMap
import kotlinx.coroutines.NonCancellable
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.FirebaseFirestoreException
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

    suspend fun isLiked(postId: String, uid: String): Boolean =
        db.collection("observations").document(postId).collection("likes").document(uid)
            .get(com.google.firebase.firestore.Source.SERVER).await().exists()

    suspend fun ensureSignedIn(): String {
        check(FirebaseApp.initializeApp(appContext) != null) {
            "Firebase is not configured. Add the matching google-services.json to app/."
        }
        return auth.currentUser?.uid ?: auth.signInAnonymously().await().user?.uid
        ?: error("Anonymous Firebase Authentication is not enabled.")
    }

    suspend fun deletePost(post: ObservationPost) {
        val userId = auth.currentUser?.uid
        check(userId != null && userId == post.authorId) {
            "You can only delete your own posts."
        }

        val postRef = db.collection("observations").document(post.id)

        withContext(NonCancellable) {
            db.runTransaction { transaction ->
                val savedPost = transaction.get(postRef)
                if (savedPost.exists()) {
                    check(savedPost.getString("authorId") == userId) {
                        "You can only delete your own posts."
                    }
                    transaction.delete(postRef)
                }
                Unit
            }.await()
        }
    }
    /** Publish a custom Map Pin observation. */
    suspend fun publishPin(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double,
        organism: String = ""
    ): String {
        val cleanTitle = title.trim().ifBlank { "Map Pin Observation" }
        val cleanDescription = description.trim()
        require(organism.isEmpty() || organism in ORGANISM_CATEGORIES) { "Choose a supported category." }
        val uid = ensureSignedIn()
        val postId = UUID.randomUUID().toString()
        val record = mapOf(
            "authorId" to uid,
            "title" to cleanTitle,
            "description" to cleanDescription,
            "organism" to organism,
            "storagePath" to "",
            "latitude" to latitude,
            "longitude" to longitude,
            "isMapPin" to true,
            "createdAt" to FieldValue.serverTimestamp()
        )
        db.collection("observations").document(postId).set(record).await()
        return postId
    }

    /** Upload first; only create a visible Firestore post when upload succeeds. */
    suspend fun publish(
        imageUri: Uri,
        title: String,
        description: String,
        pinId: String? = null,
        location: com.google.android.gms.maps.model.LatLng? = null,
        rotationDegrees: Int = 0,
        photoZoom: Float = 1f,
        photoPanX: Float = 0f,
        photoPanY: Float = 0f,
        organism: String = "",
        onProgress: (Float) -> Unit
    ): String {
        val cleanTitle = title.trim()
        val cleanDescription = description.trim()
        require(organism.isEmpty() || organism in ORGANISM_CATEGORIES) { "Choose a supported category." }
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 100) {
            "Enter a title (up to 100 characters)."
        }
        require(cleanDescription.length <= 1000) {
            "Keep the description under 1,000 characters."
        }

        val uid = ensureSignedIn()
        val postId = UUID.randomUUID().toString()
        val (photo, mimeType) = withContext(Dispatchers.IO) {
            val (file, originalMimeType) = copySelectedImage(imageUri)

            try {
                if (rotationDegrees % 360 == 0 && photoZoom == 1f) {
                    file to originalMimeType
                } else {
                    val rotated = decodeDraftPhoto(file, rotationDegrees)
                    val zoom = photoZoom.coerceIn(1f, 3f)
                    val width = (rotated.width / zoom).toInt().coerceAtLeast(1)
                    val height = (rotated.height / zoom).toInt().coerceAtLeast(1)

                    val maxLeft = rotated.width - width
                    val maxTop = rotated.height - height

                    val cropLeft = (maxLeft * (1f - photoPanX.coerceIn(-1f, 1f)) / 2f)
                        .toInt()
                    val cropTop = (maxTop * (1f - photoPanY.coerceIn(-1f, 1f)) / 2f)
                        .toInt()

                    val edited = Bitmap.createBitmap(rotated, cropLeft, cropTop, width, height)

                    val hasAlpha = edited.hasAlpha()
                    val format = if (hasAlpha) {
                        Bitmap.CompressFormat.PNG
                    } else {
                        Bitmap.CompressFormat.JPEG
                    }

                    file.outputStream().use { output ->
                        check(edited.compress(format, 92, output)) {
                            "Could not save the rotated photo."
                        }
                    }
                    require(file.length() <= 10L * 1024 * 1024) {
                        "The rotated photo is too large. Choose a smaller photo."
                    }

                    file to if (hasAlpha) "image/png" else "image/jpeg"
                }
            } catch (failure: Exception) {
                file.delete()
                throw failure
            }
        }
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            ?.takeIf { it.matches(Regex("[a-zA-Z0-9]{1,8}")) } ?: "jpg"
        val photoId = UUID.randomUUID().toString()
        val storagePath = "observation_photos/$uid/$photoId.$ext"
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

            if (pinId == null) {
                val record = mutableMapOf<String, Any>(
                    "authorId" to uid,
                    "title" to cleanTitle,
                    "description" to cleanDescription,
                    "organism" to organism,
                    "storagePath" to storagePath,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                if (location != null) {
                    record["latitude"] = location.latitude
                    record["longitude"] = location.longitude
                    record["isMapPin"] = true
                }

                db.collection("observations")
                    .document(postId)
                    .set(record)
                    .await()

                return postId
            }

            require(pinId.isNotBlank()) {
                "Choose a map pin."
            }

            val pinRef = db.collection("observations").document(pinId)

            db.runTransaction { transaction ->
                val pin = transaction.get(pinRef)

                check(pin.exists()) {
                    "This pin no longer exists."
                }

                check(pin.getString("authorId") == uid) {
                    "Choose one of your own pins."
                }

                check(
                    pin.getDouble("latitude") != null &&
                            pin.getDouble("longitude") != null
                ) {
                    "This observation does not have a map location."
                }

                check(pin.getString("storagePath").isNullOrBlank()) {
                    "This pin already has a photo."
                }

                transaction.update(
                    pinRef,
                    mapOf(
                        "title" to cleanTitle,
                        "description" to cleanDescription,
                        "storagePath" to storagePath
                    )
                )
            }.await()

            return pinId
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
        onError: (Exception) -> Unit,
        organisms: List<String> = emptyList(),
        allPosts: Boolean = false,
        ownPostsOnly: Boolean = false,
        authorId: String? = null
    ): ListenerRegistration {
        val uid = checkNotNull(auth.currentUser?.uid)
        val selected = normalizeOrganisms(organisms)
        val requestedAuthor = if (ownPostsOnly) uid else authorId
        if (requestedAuthor != null) {
            return db.collection("observations").whereEqualTo("authorId", requestedAuthor)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, failure ->
                    if (failure != null) {
                        onError(failure)
                    } else if (snapshot != null && !snapshot.metadata.isFromCache) {
                        onUpdate(snapshot.documents.mapNotNull { it.toObservation(requestedAuthor) }
                            .filter { selected.isEmpty() || it.organism in selected }
                            .sortedByDescending { it.createdAt })
                    }
                }
        }

        val social = com.example.useraccount.SocialRepository(uid)
        val rows = mutableMapOf<String, List<ObservationPost>>()
        val subscriptions = mutableMapOf<String, ListenerRegistration>()
        var authors: Set<String>? = null
        var blocked: Set<String>? = null
        var blockedBy: Set<String>? = null
        var closed = false

        fun emit() {
            val ordered = rows.values.flatten()
                .filter { selected.isEmpty() || it.organism in selected }
                .sortedByDescending { it.createdAt }
            onUpdate(if (selected.isEmpty() && !allPosts) ordered.take(40) else ordered)
        }

        fun reconcile() {
            if (closed) return
            val allowed = if (authors == null || blocked == null || blockedBy == null) emptySet()
            else (authors.orEmpty() + uid) - blocked.orEmpty() - blockedBy.orEmpty()
            for (author in subscriptions.keys.toSet() - allowed) {
                subscriptions.remove(author)?.remove()
                rows.remove(author)
            }
            emit()
            for (author in allowed - subscriptions.keys) {
                val query = db.collection("observations").whereEqualTo("authorId", author)
                subscriptions[author] = query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, failure ->
                    if (closed || author !in subscriptions) return@addSnapshotListener
                    if (failure != null) {
                        rows.remove(author)
                        emit()
                        if (failure.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) onError(failure)
                    } else if (snap != null && !snap.metadata.isFromCache) {
                        rows[author] = snap.documents.mapNotNull { it.toObservation(author) }
                        emit()
                    }
                }
            }
        }

        fun track(query: Query, field: String?, assign: (Set<String>?) -> Unit): ListenerRegistration =
            query.addSnapshotListener(MetadataChanges.INCLUDE) { snap, failure ->
                if (closed) return@addSnapshotListener
                if (failure != null) {
                    assign(null)
                    reconcile()
                    onError(failure)
                } else if (snap != null && !snap.metadata.isFromCache) {
                    assign(snap.documents.mapNotNull { if (field == null) it.id else it.getString(field) }.toSet())
                    reconcile()
                }
            }

        val controls = listOf(track(social.people, null) { authors = it },
            track(social.edges(uid, "blocks"), null) { blocked = it },
            track(social.incoming("blocks"), "source") { blockedBy = it })
        return ListenerRegistration {
            closed = true
            controls.forEach { it.remove() }
            subscriptions.values.forEach { it.remove() }
        }
    }

    private fun DocumentSnapshot.toObservation(author: String): ObservationPost? {
        val title = getString("title") ?: return null
        val latitude = getDouble("latitude")
        val longitude = getDouble("longitude")
        return ObservationPost(
            id = id,
            authorId = author,
            title = title,
            description = getString("description").orEmpty(),
            storagePath = getString("storagePath").orEmpty(),
            createdAt = getTimestamp("createdAt"),
            latitude = latitude,
            longitude = longitude,
            isMapPin = getBoolean("isMapPin") ?: (latitude != null && longitude != null),
            organism = getString("organism").orEmpty()
        )
    }

    /** Storage SDK enforces authenticated reads; temp files are deleted after decoding. */
    suspend fun loadPreview(storagePath: String, maxDimension: Int = 1200): Bitmap? = withContext(Dispatchers.IO) {
        require(maxDimension in 64..1200)
        if (storagePath.isBlank()) return@withContext null
        val temp = File.createTempFile("observation_", ".img", appContext.cacheDir)
        try {
            if (storagePath.startsWith("observation_photos/")) {
                val project = checkNotNull(FirebaseApp.getInstance().options.projectId)
                val token = checkNotNull(auth.currentUser?.getIdToken(false)?.await()?.token)
                val endpoint = Uri.Builder().scheme("https")
                    .authority("us-central1-${project}.cloudfunctions.net")
                    .appendPath("readObservationPhoto").appendQueryParameter("path", storagePath).build()
                val connection = java.net.URL(endpoint.toString()).openConnection() as javax.net.ssl.HttpsURLConnection
                try {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                    connection.instanceFollowRedirects = false
                    connection.connectTimeout = 15000
                    connection.readTimeout = 30000
                    check(connection.responseCode == 200)
                    connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
                } finally {
                    connection.disconnect()
                }
            } else {
                storage.reference.child(storagePath).getFile(temp).await()
            }
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temp.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
            val bitmap = android.graphics.BitmapFactory.decodeFile(
                temp.absolutePath,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@withContext null

            orientBitmap(bitmap, temp)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }
    private fun orientBitmap(
        bitmap: Bitmap,
        file: File,
        rotationDegrees: Int = 0
    ): Bitmap {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: java.io.IOException) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        }

        matrix.postRotate(rotationDegrees.toFloat())

        return if (matrix.isIdentity) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
    private fun decodeDraftPhoto(file: File, rotationDegrees: Int): Bitmap {
        val bounds = android.graphics.BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)

        var sample = 1
        while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) {
            sample *= 2
        }

        val bitmap = android.graphics.BitmapFactory.decodeFile(
            file.absolutePath,
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: error("Could not open the selected photo.")

        return orientBitmap(bitmap, file, rotationDegrees)
    }

    suspend fun loadDraftPreview(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val (file, _) = copySelectedImage(uri)
            try {
                decodeDraftPhoto(file, 0)
            } finally {
                file.delete()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
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