package com.whiplash.music.data.sync

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The name and photo the user chose for their Whiplash profile. Google's own
 * name and photo can't be changed from here (the app only has access to its
 * Drive folder), so these are Whiplash's own and override Google's in the UI.
 * They sync with the library, so every device shows the same profile.
 */
class ProfileStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val photoFile = File(context.applicationContext.filesDir, "cloud_sync/profile_photo.jpg")

    private val _profile = MutableStateFlow(read())
    val profile: StateFlow<SyncProfile?> = _profile.asStateFlow()

    fun photoFile(): File? = photoFile.takeIf { it.exists() }

    fun setName(name: String?) {
        val current = _profile.value
        save(name?.trim()?.take(MAX_NAME)?.takeIf { it.isNotEmpty() }, current?.photoJpeg)
    }

    /** Reads [uri], crops it to a small square JPEG and saves it. False if it isn't a readable image. */
    suspend fun setPhoto(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val jpeg = runCatching { squareJpeg(context, uri) }.getOrNull() ?: return@withContext false
        save(_profile.value?.name, jpeg)
        true
    }

    fun removePhoto() = save(_profile.value?.name, null)

    /** Back to the Google name and photo. */
    fun reset() = save(null, null)

    /** Writes a profile that came from another device. */
    fun applyFromSync(profile: SyncProfile?) {
        write(profile)
    }

    private fun save(name: String?, photo: ByteArray?) {
        write(if (name == null && photo == null) SyncProfile(null, null, System.currentTimeMillis()) else SyncProfile(name, photo, System.currentTimeMillis()))
    }

    private fun write(profile: SyncProfile?) {
        val photo = profile?.photoJpeg
        if (photo != null) {
            photoFile.parentFile?.mkdirs()
            photoFile.writeBytes(photo)
        } else {
            photoFile.delete()
        }
        prefs.edit().apply {
            if (profile == null) {
                clear()
            } else {
                if (profile.name != null) putString(KEY_NAME, profile.name) else remove(KEY_NAME)
                putLong(KEY_UPDATED, profile.updatedAtEpochMs)
            }
        }.apply()
        _profile.value = profile
    }

    private fun read(): SyncProfile? {
        val updated = prefs.getLong(KEY_UPDATED, 0L)
        if (updated == 0L) return null
        val photo = photoFile.takeIf { it.exists() }?.let { runCatching { it.readBytes() }.getOrNull() }
        return SyncProfile(prefs.getString(KEY_NAME, null), photo, updated)
    }

    private fun squareJpeg(context: Context, uri: Uri): ByteArray? {
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
            val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > DECODE_MAX) {
                    val scale = DECODE_MAX.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                }
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DECODE_MAX) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
        }
        val side = minOf(bitmap.width, bitmap.height)
        if (side <= 0) return null
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, PHOTO_SIZE, PHOTO_SIZE, true)
        return ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            out.toByteArray()
        }
    }

    companion object {
        private const val PREFS = "whiplash_profile"
        private const val KEY_NAME = "name"
        private const val KEY_UPDATED = "updated"
        const val MAX_NAME = 40

        /** 256 px keeps the photo around 20 KB, small enough to ride inside the sync file. */
        private const val PHOTO_SIZE = 256
        private const val DECODE_MAX = 1024
    }
}
