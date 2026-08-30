package com.whiplash.music.data.backup

import android.content.Context
import android.net.Uri
import com.whiplash.music.data.local.WhiplashDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Real local backup/restore for everything Whiplash stores on-device:
 * playlists, favorites, history, pinned Speed dial entries, cached
 * metadata, and every user-personalized setting (theme, playback speed,
 * crossfade, autoplay, audio quality, cache toggle).
 *
 * ## Why this exists (real, reported problem)
 * All of Whiplash's data lives in `/data/data/com.whiplash.music/` — the
 * Room database file ([WhiplashDatabase]) and the DataStore Preferences
 * file. This is *app-private storage*, which Android unconditionally
 * deletes the instant the app is uninstalled or the user taps "Clear
 * storage/data" — there is no folder an app can create for itself, inside
 * its own sandbox or anywhere else without the user's involvement, that
 * would survive either of those. A silent "auto-backup to a local folder"
 * as originally proposed is therefore not actually possible to build in a
 * way that solves the stated problem: whatever folder the app picked for
 * itself would be wiped right along with everything else.
 *
 * The one thing that *does* survive an uninstall is a file the user
 * explicitly saves outside the app's own sandbox — Downloads, a synced
 * cloud folder, an SD card, etc. — via Android's Storage Access Framework
 * (the standard system file picker: `ACTION_CREATE_DOCUMENT` /
 * `ACTION_OPEN_DOCUMENT`). This is exactly the approach real open-source
 * apps in this same space use: verified directly against InnerTune's own
 * production source (`BackupRestoreViewModel.kt`, MIT-licensed, still
 * shipping today) — it zips the Room DB file plus the DataStore
 * preferences file together and writes that zip to a user-chosen `Uri`.
 * Whiplash's [backup]/[restore] here follow that same proven shape.
 *
 * (Separately, [Whiplash's AndroidManifest][com.whiplash.music] already
 * has `android:allowBackup="true"` with no restricting
 * `dataExtractionRules`/`fullBackupContent`, so Android's own build-in
 * Auto Backup to Google Drive already covers this data too, automatically,
 * for users who have that turned on in their Google account — but that is
 * entirely outside the app's control and not something to rely on as the
 * real fix, which is why this manual export/import exists as the actual,
 * guaranteed mechanism.)
 */
class BackupManager(
    private val context: Context,
    private val database: WhiplashDatabase,
) {

    /**
     * Writes a single zip containing the current Room database and the
     * DataStore preferences file to [destination] (a `Uri` obtained from
     * `ActivityResultContracts.CreateDocument`). Returns true on success.
     */
    suspend fun backup(destination: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // Force a WAL checkpoint so every committed write is flushed
            // into the main database file before we copy it. A raw file
            // copy of just whiplash.db would otherwise silently miss any
            // commit still sitting only in the WAL side file (confirmed
            // as a real bug during on-device testing: favorites/playlists
            // created just before a backup were missing after a restore,
            // because PRAGMA wal_checkpoint(FULL) requires the returned
            // Cursor to actually be read — SQLite does not run a PRAGMA's
            // side effect just because a Cursor for it was created and
            // then closed unread). moveToFirst() is what actually forces
            // execution here.
            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val dbFile = context.getDatabasePath(DB_NAME)
            val settingsFile = File(context.filesDir, "datastore/$SETTINGS_FILENAME")

            context.contentResolver.openOutputStream(destination)?.use { rawOut ->
                ZipOutputStream(rawOut.buffered()).use { zipOut ->
                    if (dbFile.exists()) {
                        zipOut.putNextEntry(ZipEntry(DB_NAME))
                        dbFile.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                    if (settingsFile.exists()) {
                        zipOut.putNextEntry(ZipEntry(SETTINGS_FILENAME))
                        settingsFile.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }
            } ?: return@withContext false
            true
        }.getOrDefault(false)
    }

    /**
     * Restores the database and settings from a zip previously produced
     * by [backup]. The Room database must be fully closed before its file
     * is overwritten (an open SQLite connection cannot have its backing
     * file safely replaced underneath it), so the caller is expected to
     * fully restart the app process right after this returns true — see
     * [com.whiplash.music.ui.settings.SettingsViewModel.restore].
     */
    suspend fun restore(source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dbFile = context.getDatabasePath(DB_NAME)
            val settingsFile = File(context.filesDir, "datastore/$SETTINGS_FILENAME")
            settingsFile.parentFile?.mkdirs()

            var restoredAnything = false
            context.contentResolver.openInputStream(source)?.use { rawIn ->
                ZipInputStream(rawIn.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            DB_NAME -> {
                                // Close the live connection and remove any
                                // stale WAL/SHM side files first, otherwise
                                // SQLite could try to replay an old WAL
                                // against the freshly-restored main file.
                                database.close()
                                File(dbFile.path + "-wal").delete()
                                File(dbFile.path + "-shm").delete()
                                dbFile.outputStream().use { zipIn.copyTo(it) }
                                restoredAnything = true
                            }
                            SETTINGS_FILENAME -> {
                                settingsFile.outputStream().use { zipIn.copyTo(it) }
                                restoredAnything = true
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            } ?: return@withContext false
            restoredAnything
        }.getOrDefault(false)
    }

    companion object {
        private const val DB_NAME = "whiplash.db"
        private const val SETTINGS_FILENAME = "whiplash_settings.preferences_pb"

        /** Suggested file name for the system "Save as" picker, timestamped so repeated backups don't silently collide. */
        fun suggestedFileName(): String {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
            return "whiplash_backup_$stamp.zip"
        }
    }
}
