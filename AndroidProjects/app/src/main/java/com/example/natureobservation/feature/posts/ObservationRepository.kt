package com.example.natureobservation.feature.posts

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.graphics.Matrix
import android.media.ExifInterface
import android.webkit.MimeTypeMap
import kotlinx.coroutines.NonCancellable
import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
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

    companion object {
        private val localPosts = mutableListOf<ObservationPost>()
        private val feedListeners = mutableListOf<() -> Unit>()

        fun addLocalPost(post: ObservationPost) {
            synchronized(localPosts) {
                localPosts.removeAll { it.id == post.id }
                localPosts.add(0, post)
            }
            feedListeners.toList().forEach { it.invoke() }
        }

        fun getLocalPosts(): List<ObservationPost> {
            return synchronized(localPosts) { localPosts.toList() }
        }
    }

    // load the currently signed-in user's liked posts.
    suspend fun getLikedPosts(): List<ObservationPost> {
        val userId = ensureSignedIn()
        val observations = db.collection("observations").get().await()
        val likedPosts = mutableListOf<ObservationPost>()

        for (document in observations.documents) {
            val like = document.reference
                .collection("likes")
                .document(userId)
                .get()
                .await()

            if (!like.exists()) continue

            val authorId = document.getString("authorId") ?: continue
            val title = document.getString("title") ?: continue
            val latitude = document.getDouble("latitude")
            val longitude = document.getDouble("longitude")

            likedPosts.add(
                ObservationPost(
                    id = document.id,
                    authorId = authorId,
                    title = title,
                    description = document.getString("description").orEmpty(),
                    storagePath = document.getString("storagePath").orEmpty(),
                    createdAt = document.getTimestamp("createdAt"),
                    latitude = latitude,
                    longitude = longitude,
                    isMapPin = document.getBoolean("isMapPin")
                        ?: (latitude != null && longitude != null)
                )
            )
        }

        return likedPosts.sortedByDescending {
            it.createdAt?.seconds ?: 0L
        }
    }
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

            val removed = synchronized(localPosts) {
                localPosts.removeAll { it.id == post.id }
            }
            if (removed) feedListeners.toList().forEach { it.invoke() }
        }
    }
    /** Publish a custom Map Pin observation. */
    suspend fun publishPin(
        title: String,
        description: String,
        latitude: Double,
        longitude: Double
    ): String {
        val cleanTitle = title.trim().ifBlank { "Map Pin Observation" }
        val cleanDescription = description.trim()
        val uid = try { ensureSignedIn() } catch (_: Exception) { "user_${UUID.randomUUID().toString().take(8)}" }
        val postId = UUID.randomUUID().toString()

        val post = ObservationPost(
            id = postId,
            authorId = uid,
            title = cleanTitle,
            description = cleanDescription,
            storagePath = "",
            createdAt = Timestamp.now(),
            latitude = latitude,
            longitude = longitude,
            isMapPin = true
        )

        addLocalPost(post)

        try {
            val record = mapOf(
                "authorId" to uid,
                "title" to cleanTitle,
                "description" to cleanDescription,
                "storagePath" to "",
                "latitude" to latitude,
                "longitude" to longitude,
                "isMapPin" to true,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection("observations").document(postId).set(record).await()
        } catch (_: Exception) {
            // Firestore write optional if offline
        }
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

// Update the local pin too, so it shows the attached photo.
            getLocalPosts().firstOrNull { it.id == pinId }?.let { pin ->
                addLocalPost(
                    pin.copy(
                        title = cleanTitle,
                        description = cleanDescription,
                        storagePath = storagePath
                    )
                )
            }

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
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        var lastRemotePosts = emptyList<ObservationPost>()

        fun emitCombined() {
            val local = getLocalPosts()
            val combined = (local + lastRemotePosts)
                .distinctBy { it.id }
                .sortedByDescending { it.createdAt?.seconds ?: Long.MAX_VALUE }
            onUpdate(combined)
        }

        val listener = {
            emitCombined()
        }
        synchronized(localPosts) {
            feedListeners.add(listener)
        }

        emitCombined()

        val firestoreRegistration = try {
            db.collection("observations")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(40)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        onError(error)
                        emitCombined()
                    } else if (snapshot != null) {
                        lastRemotePosts = snapshot.documents.mapNotNull { document ->
                            val author = document.getString("authorId") ?: return@mapNotNull null
                            val title = document.getString("title") ?: return@mapNotNull null
                            val lat = document.getDouble("latitude")
                            val lng = document.getDouble("longitude")
                            val isPin = document.getBoolean("isMapPin") ?: (lat != null && lng != null)
                            ObservationPost(
                                id = document.id,
                                authorId = author,
                                title = title,
                                description = document.getString("description").orEmpty(),
                                storagePath = document.getString("storagePath").orEmpty(),
                                createdAt = document.getTimestamp("createdAt"),
                                latitude = lat,
                                longitude = lng,
                                isMapPin = isPin
                            )
                        }
                        emitCombined()
                    }
                }
        } catch (_: Exception) {
            null
        }

        return object : ListenerRegistration {
            override fun remove() {
                firestoreRegistration?.remove()
                synchronized(localPosts) {
                    feedListeners.remove(listener)
                }
            }
        }
    }

    /** Storage SDK enforces authenticated reads; temp files are deleted after decoding. */
    suspend fun loadPreview(storagePath: String): Bitmap? = withContext(Dispatchers.IO) {
        if (storagePath.isBlank()) return@withContext null
        val temp = File.createTempFile("observation_", ".img", appContext.cacheDir)
        try {
            storage.reference.child(storagePath).getFile(temp).await()
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(temp.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
            val bitmap = android.graphics.BitmapFactory.decodeFile(
                temp.absolutePath,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@withContext null

            orientBitmap(bitmap, temp)
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
