// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.whiplash.music.data.local.dao.DownloadDao
import com.whiplash.music.data.local.entity.DownloadEntity
import com.whiplash.music.data.local.entity.DownloadStatus
import com.whiplash.music.ui.common.ToastController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileInputStream
import java.io.IOException

/**
 * Copies downloaded songs out of the app's private storage into the phone's
 * public Download/Whiplash folder, so they can be used in other players, a
 * file manager or on a computer. The audio is copied exactly (never
 * re-encoded): M4A downloads as they are, WebM/Opus ones repackaged as Ogg
 * Opus ([OpusRemuxer]), with title, artist, album and cover embedded.
 *
 * - Android 10+ writes through MediaStore's Downloads collection, which needs
 *   no permission. The row stays "pending" (invisible to other apps) until
 *   the copy completes, and is deleted if the copy fails, so a half-written
 *   file is never left behind.
 * - Android 8–9 writes the file directly, which needs WRITE_EXTERNAL_STORAGE
 *   (requested by the UI first, see [needsLegacyPermission]). It copies to a
 *   temp name and renames at the end for the same reason.
 *
 * The app's own copy is never touched, so the song keeps playing offline in
 * Whiplash. A song already saved under the same name is skipped rather than
 * duplicated. Runs on its own scope, so leaving the screen doesn't stop a
 * "Save all" halfway; a mutex keeps two saves from interleaving.
 */
class DeviceExporter(
    context: Context,
    private val downloadDao: DownloadDao,
) {
    private val appContext = context.applicationContext
    // A failure never escapes to crash the app; each song's own errors are
    // already counted, this only guards against the unexpected.
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "Save to device failed", e)
            ToastController.show("Couldn't save to the device")
        },
    )
    private val mutex = Mutex()
    private val active = java.util.concurrent.atomic.AtomicInteger(0)

    private val _isSaving = MutableStateFlow(false)
    /** True while a save is running, so "Save all" can't be started twice. */
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /** Android 8–9 only: whether the storage permission must be requested before [save]. */
    fun needsLegacyPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    /** Saves the given downloaded songs (by id) and reports the outcome in a toast. */
    fun save(ids: List<String>) {
        if (ids.isEmpty()) return
        // Set before the work starts, so a double tap on "Save all" can't
        // queue the same batch twice.
        if (ids.size > 1 && active.get() > 0) {
            ToastController.show("Already saving to $FOLDER_LABEL…")
            return
        }
        active.incrementAndGet()
        _isSaving.value = true
        if (ids.size > 1) ToastController.show("Saving ${ids.size} songs to $FOLDER_LABEL…")
        scope.launch {
            mutex.withLock {
                try {
                    var saved = 0
                    var already = 0
                    var failed = 0
                    for (id in ids.distinct()) {
                        val entity = runCatching { downloadDao.getById(id) }.getOrNull()
                        if (entity == null || entity.status != DownloadStatus.COMPLETED) {
                            failed++
                            continue
                        }
                        when (runCatching { export(entity) }.getOrElse {
                            Log.w(TAG, "Saving ${entity.id} to device failed", it)
                            Outcome.FAILED
                        }) {
                            Outcome.SAVED -> saved++
                            Outcome.ALREADY_SAVED -> already++
                            Outcome.FAILED -> failed++
                        }
                    }
                    ToastController.show(summary(ids.size, saved, already, failed))
                } finally {
                    if (active.decrementAndGet() == 0) _isSaving.value = false
                }
            }
        }
    }

    private enum class Outcome { SAVED, ALREADY_SAVED, FAILED }

    private fun export(entity: DownloadEntity): Outcome {
        val source = File(entity.filePath)
        if (!source.isFile || source.length() == 0L) return Outcome.FAILED
        val format = detectFormat(source) ?: return Outcome.FAILED
        // The audio is never re-encoded, so the saved file is exactly the
        // downloaded sound, and saving takes about a second:
        // - M4A (AAC) downloads are copied as they are (already tagged).
        // - WebM/Opus downloads have their Opus audio moved, untouched, into
        //   an Ogg Opus (.opus) file with the tags in its header. WebM itself
        //   is filed as video by Android, so music players would skip it.
        val out = when (format) {
            Format.MP4 -> Target(source, "m4a", M4A_MIME, temp = false)
            Format.WEBM -> Target(File(appContext.cacheDir, "export-${entity.id}.opus"), "opus", OPUS_MIME, temp = true)
        }
        try {
            if (out.temp) {
                val started = android.os.SystemClock.elapsedRealtime()
                OpusRemuxer.toOgg(
                    source, out.file, entity.title, entity.artist, entity.album,
                    cover = entity.artworkPath?.let { File(it) }?.takeIf { it.isFile && it.length() < MAX_COVER_BYTES }?.readBytes(),
                )
                Log.i(TAG, "${entity.id}: prepared .${out.extension} in ${android.os.SystemClock.elapsedRealtime() - started} ms")
            }
            // "Artist - Title.ext". If that name is taken by a file of the same
            // size, it's this song already saved; a different size means a
            // different song with the same name (another version, say), so
            // this one is saved under a name that also carries its id.
            val base = fileBaseName(entity)
            val size = out.file.length()
            val candidates = listOf("$base.${out.extension}") +
                (1..MAX_NAME_ATTEMPTS).map { n -> "$base (${entity.id}${if (n > 1) " $n" else ""}).${out.extension}" }
            var displayName: String? = null
            for (name in candidates) {
                when (existingSize(name)) {
                    null -> { displayName = name; break }
                    size -> return Outcome.ALREADY_SAVED
                }
            }
            if (displayName == null) return Outcome.FAILED
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportViaMediaStore(out.file, displayName, out.mimeType)
            } else {
                exportLegacy(out.file, displayName, out.mimeType)
            }
        } finally {
            if (out.temp) runCatching { out.file.delete() }
        }
    }

    private class Target(val file: File, val extension: String, val mimeType: String, val temp: Boolean)

    /** Size of the file already saved under [displayName] in Download/Whiplash, or null if there is none. */
    private fun existingSize(displayName: String): Long? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appContext.contentResolver.query(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                arrayOf(MediaStore.MediaColumns.SIZE),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                arrayOf(displayName, RELATIVE_PATH),
                null,
            )?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } else {
            @Suppress("DEPRECATION")
            File(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER), displayName)
                .takeIf { it.isFile }?.length()
        }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun exportViaMediaStore(source: File, displayName: String, mimeType: String): Outcome {
        val resolver = appContext.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values) ?: return Outcome.FAILED
        return try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("No output stream for $uri")
            out.use { output -> FileInputStream(source).use { it.copyTo(output) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            Outcome.SAVED
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
    }

    @Suppress("DEPRECATION") // getExternalStoragePublicDirectory is the API on Android 8–9.
    private fun exportLegacy(source: File, displayName: String, mimeType: String): Outcome {
        if (needsLegacyPermission()) return Outcome.FAILED
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) return Outcome.FAILED
        val target = File(dir, displayName)
        val temp = File(dir, "$displayName.part")
        try {
            source.copyTo(temp, overwrite = true)
            if (target.exists()) throw IOException("${target.name} already exists") // never overwrite the user's files
            if (!temp.renameTo(target)) throw IOException("Can't rename to ${target.name}")
        } catch (t: Throwable) {
            runCatching { temp.delete() }
            throw t
        }
        // Make it show up in other apps and over USB straight away.
        MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(mimeType), null)
        return Outcome.SAVED
    }

    private enum class Format { MP4, WEBM }

    /** Downloads are stored as ".audio"; the real container is read from the file header. */
    private fun detectFormat(file: File): Format? {
        val header = ByteArray(12)
        val read = FileInputStream(file).use { it.read(header) }
        if (read < 8) return null
        val isMp4 = header[4] == 'f'.code.toByte() && header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() && header[7] == 'p'.code.toByte()
        val isWebm = header[0] == 0x1A.toByte() && header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() && header[3] == 0xA3.toByte()
        return when {
            isMp4 -> Format.MP4
            isWebm -> Format.WEBM
            else -> null
        }
    }

    /** "Artist - Title", with characters that aren't allowed in file names removed. */
    private fun fileBaseName(entity: DownloadEntity): String {
        val raw = listOf(entity.artist, entity.title).filter { it.isNotBlank() }.joinToString(" - ")
        val clean = raw
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')
            .take(MAX_NAME_CHARS)
            // Most volumes (ext4/exFAT/FAT32) cap a file name at 255 *bytes*,
            // not chars, so 120 CJK/emoji chars (up to 4 bytes each) would
            // overflow and the save would fail. Cap by UTF-8 bytes too,
            // leaving headroom for " (id).ext" so the disambiguated name fits.
            .let { takeUtf8Bytes(it, MAX_NAME_BYTES) }
            .trim()
        return clean.ifBlank { entity.id }
    }

    /** [s] truncated so its UTF-8 encoding is at most [maxBytes], never splitting a character. */
    private fun takeUtf8Bytes(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        var bytes = 0
        val sb = StringBuilder(s.length)
        for (cp in s.codePoints()) {
            val w = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
            if (bytes + w > maxBytes) break
            bytes += w
            sb.appendCodePoint(cp)
        }
        return sb.toString()
    }

    private fun summary(requested: Int, saved: Int, already: Int, failed: Int): String {
        if (requested == 1) {
            return when {
                saved == 1 -> "Saved to $FOLDER_LABEL"
                already == 1 -> "Already saved in $FOLDER_LABEL"
                else -> "Couldn't save this song to the device"
            }
        }
        val parts = buildList {
            if (saved > 0) add("Saved ${songs(saved)} to $FOLDER_LABEL")
            if (already > 0) add("${songs(already)} already saved")
            if (failed > 0) add("${songs(failed)} couldn't be saved")
        }
        return parts.joinToString(" · ").ifBlank { "Nothing to save" }
    }

    private fun songs(n: Int) = if (n == 1) "1 song" else "$n songs"

    private companion object {
        const val TAG = "DeviceExporter"
        const val FOLDER = "Whiplash"
        const val FOLDER_LABEL = "Download/Whiplash"
        const val M4A_MIME = "audio/mp4"
        const val OPUS_MIME = "audio/ogg"
        const val MAX_COVER_BYTES = 2L * 1024 * 1024
        const val MAX_NAME_ATTEMPTS = 5
        val RELATIVE_PATH = "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER/"
        const val MAX_NAME_CHARS = 120
        // Filesystem name limit is 255 bytes; leave room for " (id).ext".
        const val MAX_NAME_BYTES = 200
    }
}
