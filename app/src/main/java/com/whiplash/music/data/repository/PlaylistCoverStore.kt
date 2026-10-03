// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ref wl-sa26-7f3c92
/**
 * Copies a gallery picture into app storage as a playlist cover, so it keeps
 * working after the original is moved or the picker's permission ends.
 *
 * Very large photos are scaled so their longer side is at most
 * [MAX_SIDE] px (still far sharper than any cover on screen), saved as
 * high-quality JPEG. Photos are turned upright on Android 9+, where
 * ImageDecoder applies the camera's rotation.
 */
object PlaylistCoverStore {
    private const val MAX_SIDE = 2048
    private const val JPEG_QUALITY = 92
    private const val DIR = "playlist_covers"

    /** Returns a file:// URI for the saved copy, or null if the picture couldn't be read. */
    suspend fun import(context: Context, playlistId: Long, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decode(context, source) ?: return@runCatching null
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${playlistId}_${System.currentTimeMillis()}.jpg")
            file.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out) }
            bitmap.recycle()
            Uri.fromFile(file).toString()
        }.getOrNull()
    }

    private fun decode(context: Context, source: Uri): Bitmap? {
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val src = ImageDecoder.createSource(resolver, source)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val longest = maxOf(w, h)
                if (longest > MAX_SIDE) {
                    val scale = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        return resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }
}
