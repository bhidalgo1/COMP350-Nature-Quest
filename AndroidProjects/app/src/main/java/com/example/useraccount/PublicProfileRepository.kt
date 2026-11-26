package com.example.useraccount

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/** Contains only fields that other signed-in app users may see. */
data class PublicProfile(
    val username: String = "Nature explorer",
    val photoPath: String = "",
    val bio: String = ""
)

class PublicProfileRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    /** Publish only the current user's name; preserve their existing avatar. */
    suspend fun syncCurrentUser(): PublicProfile = withTimeout(15_000L) {
        val user = checkNotNull(auth.currentUser) { "Log in before updating your profile." }
        val document = db.collection("publicProfiles").document(user.uid)
        val previous = document.get().await()
        val fallback = user.displayName?.takeIf { it.isNotBlank() }
            ?: previous.getString("username")?.takeIf { it.isNotBlank() }
        val username = if (user.isAnonymous) {
            "Guest"
        } else {
            val savedName = try {
                db.collection("users").document(user.uid).get().await()
                    .getString("username")?.takeIf { it.isNotBlank() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // An Auth display name is enough if private profile storage is unavailable.
                if (fallback == null) throw failure
                null
            }
            savedName ?: fallback ?: "Nature explorer"
        }
        check(auth.currentUser?.uid == user.uid) { "Your account changed. Please try again." }
        if (!previous.exists() || previous.getString("username") != username) {
            document.set(mapOf("username" to username), SetOptions.merge()).await()
        }
        PublicProfile(username, previous.getString("photoPath").orEmpty())
    }

    suspend fun changePhoto(context: Context, uri: Uri) {
        val userId = checkNotNull(auth.currentUser?.uid) { "Log in to add a profile picture." }
        val previous = syncCurrentUser()
        val jpeg = withContext(Dispatchers.IO) { preparePhoto(context, uri) }
        // Unique immutable paths prevent stale avatar caches after a picture changes.
        val path = "profile_photos/$userId/${UUID.randomUUID()}.jpg"
        withTimeout(60_000L) {
            check(auth.currentUser?.uid == userId) { "Your account changed. Please try again." }
            val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
            storage.reference.child(path).putBytes(jpeg, metadata).await()
            check(auth.currentUser?.uid == userId) { "Your account changed. Please try again." }
            // Do not point the profile at a file until its upload has succeeded.
            db.collection("publicProfiles").document(userId).update("photoPath", path).await()
        }
        // Keep the previous avatar if upload/publishing fails or is cancelled.
        // A failed/uncertain publish may leave an unused file, never a broken old avatar.
        deleteOldPhoto(userId, previous.photoPath)
    }

    suspend fun removePhoto() {
        val userId = checkNotNull(auth.currentUser?.uid) { "Log in to change your picture." }
        val previous = syncCurrentUser()
        withTimeout(15_000L) {
            check(auth.currentUser?.uid == userId) { "Your account changed. Please try again." }
            db.collection("publicProfiles").document(userId).update("photoPath", "").await()
        }
        deleteOldPhoto(userId, previous.photoPath)
    }

    private suspend fun deleteOldPhoto(userId: String, path: String) {
        if (!path.matches(Regex("profile_photos/" + Regex.escape(userId) + "/[a-zA-Z0-9-]+\\.jpg"))) return
        withTimeoutOrNull(3_000L) {
            try {
                storage.reference.child(path).delete().await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A cleanup failure does not undo an already saved profile picture.
            }
        }
    }

    /** Decode a bounded preview, respect EXIF, center-crop, and upload at most 512x512. */
    private fun preparePhoto(context: Context, uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                val ratio = min(1f, 1024f / max(info.size.width, info.size.height))
                decoder.setTargetSize(
                    max(1, (info.size.width * ratio).toInt()),
                    max(1, (info.size.height * ratio).toInt())
                )
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a readable image." }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
            val bitmap = resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: error("Could not open that image.")
            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                // Older Android releases cannot read EXIF from every image format.
                ExifInterface.ORIENTATION_NORMAL
            }
            val matrix = Matrix().apply {
                when (orientation) {
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                    ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                    ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                    ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
                    ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
                }
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        }
        var cropped: Bitmap? = null
        var scaled: Bitmap? = null
        try {
            val edge = min(decoded.width, decoded.height)
            val square = Bitmap.createBitmap(decoded, (decoded.width - edge) / 2, (decoded.height - edge) / 2, edge, edge)
            cropped = square
            val target = min(edge, 512)
            val thumbnail = Bitmap.createScaledBitmap(square, target, target, true)
            scaled = thumbnail
            val output = ByteArrayOutputStream()
            check(thumbnail.compress(Bitmap.CompressFormat.JPEG, 88, output)) { "Could not prepare that photo." }
            return output.toByteArray().also {
                require(it.isNotEmpty() && it.size <= 1024 * 1024) { "Choose a smaller profile picture." }
            }
        } finally {
            scaled?.takeIf { it !== cropped && it !== decoded }?.recycle()
            cropped?.takeIf { it !== decoded }?.recycle()
            decoded.recycle()
        }
    }
}