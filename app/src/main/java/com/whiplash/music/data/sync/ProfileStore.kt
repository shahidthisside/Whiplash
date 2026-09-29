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

    /** Loads [uri] (at most ~1024 px, rotation applied) for the Adjust photo screen; null if unreadable. */
    suspend fun decodeForCrop(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { decode(context, uri) }.getOrNull()
    }

    /**
     * Saves the square [size]×[size] area of [bitmap] starting at ([left], [top]) —
     * what the user framed in the circle — as the profile photo.
     */
    suspend fun setCroppedPhoto(bitmap: Bitmap, left: Int, top: Int, size: Int): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val side = size.coerceIn(1, minOf(bitmap.width, bitmap.height))
            val x = left.coerceIn(0, bitmap.width - side)
            val y = top.coerceIn(0, bitmap.height - side)
            val square = Bitmap.createBitmap(bitmap, x, y, side, side)
            val scaled = Bitmap.createScaledBitmap(square, PHOTO_SIZE, PHOTO_SIZE, true)
            val jpeg = ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                out.toByteArray()
            }
            save(_profile.value?.name, jpeg)
            true
        }.getOrDefault(false)
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

    private fun decode(context: Context, uri: Uri): Bitmap? {
        return if (Build.VERSION.SDK_INT >= 28) {
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
            }
        }
    }

    companion object {
        private const val PREFS = "whiplash_profile"
        private const val KEY_NAME = "name"
        private const val KEY_UPDATED = "updated"
        const val MAX_NAME = 40

        /** 320 px stays sharp on the 84 dp profile circle and around 25 KB inside the sync file. */
        private const val PHOTO_SIZE = 320
        private const val DECODE_MAX = 1024
    }
}
