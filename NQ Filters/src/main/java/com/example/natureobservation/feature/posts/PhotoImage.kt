package com.example.natureobservation.feature.posts

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gallery URI preview, with bounded bitmap decoding to avoid large allocations. */
@Composable
internal fun rememberLocalPhotoBitmap(uri: Uri): State<ImageBitmap?> {
    val context = LocalContext.current
    return produceState<ImageBitmap?>(initialValue = null, key1 = uri) {
        value = withContext(Dispatchers.IO) {
            try {
                fun open() = context.contentResolver.openInputStream(uri)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (bounds.outWidth / sample > 1200 || bounds.outHeight / sample > 1200) sample *= 2
                open()?.use {
                    BitmapFactory.decodeStream(
                        it, null,
                        BitmapFactory.Options().apply { inSampleSize = sample }
                    )?.asImageBitmap()
                }
            } catch (_: Exception) { null }
        }
    }
}

/** Downloads by authenticated Firebase Storage SDK, rather than a publicly shared URL. */
@Composable
internal fun rememberRemotePhotoBitmap(
    repository: ObservationRepository,
    storagePath: String
): State<ImageBitmap?> = produceState<ImageBitmap?>(
    initialValue = null, key1 = repository, key2 = storagePath
) {
    value = try { repository.loadPreview(storagePath)?.asImageBitmap() }
    catch (_: Exception) { null }
}
