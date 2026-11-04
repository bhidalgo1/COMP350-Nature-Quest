package com.example.natureobservation.data

import android.content.Context
import android.net.Uri
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Uploads photos to Firebase; keeps a local preview after a successful upload. */
class FirebasePhotoRepository(private val context: Context) {
    private val preferences = context.getSharedPreferences("firebase_photos", Context.MODE_PRIVATE)

    fun getLastSavedPhoto(): File? = preferences.getString("preview", null)
        ?.let { File(it).takeIf { file -> file.isFile } }

    fun getLastStoragePath(): String? = preferences.getString("storage_path", null)

    suspend fun uploadPhoto(uri: Uri, onProgress: (Float) -> Unit): File {
        check(FirebaseApp.initializeApp(context) != null) {
            "Add your Firebase google-services.json to the app folder, sync, and run again."
        }
        val preview = preparePhoto(uri)
        var successful = false
        try {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
                ?: error("Firebase sign-in failed. Enable Anonymous sign-in in Firebase Authentication.")
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val path = "observation_photos/${user.uid}/${preview.name}"
            val reference = FirebaseStorage.getInstance().reference.child(path)
            val metadata = StorageMetadata.Builder().setContentType(mimeType).build()
            val upload = reference.putFile(Uri.fromFile(preview), metadata)
            val listener = com.google.firebase.storage.OnProgressListener<com.google.firebase.storage.UploadTask.TaskSnapshot> { snapshot ->
                if (snapshot.totalByteCount > 0L) {
                    onProgress((snapshot.bytesTransferred.toFloat() / snapshot.totalByteCount).coerceIn(0f, 1f))
                }
            }
            upload.addOnProgressListener(listener)
            try {
                upload.await()
            } catch (cancelled: CancellationException) {
                upload.cancel()
                throw cancelled
            } finally {
                upload.removeOnProgressListener(listener)
            }
            val oldPreview = getLastSavedPhoto()
            preferences.edit().putString("preview", preview.absolutePath)
                .putString("storage_path", path).apply()
            successful = true
            oldPreview?.delete()
            return preview
        } finally {
            if (!successful) preview.delete()
        }
    }

    /** Enforces the same 10 MiB limit as storage.rules, without loading the file into memory. */
    private suspend fun preparePhoto(uri: Uri): File = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri) ?: error("Could not determine the photo type. Choose another image.")
        require(mimeType.startsWith("image/")) { "Please choose an image file." }
        val folder = File(context.filesDir, "firebase_previews").apply { mkdirs() }
        val extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "img"
        val file = File(folder, "${UUID.randomUUID()}.$extension")
        try {
            resolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 10L * 1024 * 1024) { "Choose a photo that is 10 MB or smaller." }
                        output.write(buffer, 0, count)
                    }
                    require(total > 0) { "The selected photo is empty." }
                }
            } ?: error("Android could not open the selected photo. Select it again.")
            file
        } catch (error: Exception) {
            file.delete()
            throw error
        }
    }
}

fun firebaseUploadError(error: Exception): String = when {
    error is StorageException -> when (error.errorCode) {
        StorageException.ERROR_NOT_AUTHORIZED -> "Upload denied. Publish storage.rules and check Anonymous sign-in."
        StorageException.ERROR_NOT_AUTHENTICATED -> "Sign-in required. Enable Anonymous sign-in in Firebase Authentication."
        StorageException.ERROR_BUCKET_NOT_FOUND -> "Storage bucket not found. Create it in Firebase and download a new google-services.json."
        StorageException.ERROR_QUOTA_EXCEEDED -> "Firebase storage quota exceeded. Check your project's billing and storage usage."
        StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "Upload timed out. Check your internet connection and try again."
        else -> "Upload failed: ${error.localizedMessage ?: "Check your connection and Firebase setup."}"
    }
    error is com.google.firebase.FirebaseNetworkException -> "Could not connect to Firebase. Check your internet connection and try again."
    error is com.google.firebase.auth.FirebaseAuthException -> "Firebase sign-in failed. Enable Anonymous sign-in and check your Firebase project. (${error.errorCode})"
    else -> "Could not upload photo: ${error.localizedMessage ?: "Unknown error"}"
}
