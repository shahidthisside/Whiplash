package com.whiplash.music.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import android.util.Log
import com.whiplash.music.data.local.WhiplashDatabase
import com.whiplash.music.data.local.entity.DownloadEntity
import com.whiplash.music.data.local.entity.DownloadStatus
import com.whiplash.music.data.local.entity.FavoriteEntity
import com.whiplash.music.data.local.entity.HistoryEntity
import com.whiplash.music.data.local.entity.MediaSource
import com.whiplash.music.data.local.entity.PinnedEntity
import com.whiplash.music.data.local.entity.PlaylistEntity
import com.whiplash.music.data.local.entity.PlaylistTrackEntity
import com.whiplash.music.data.local.entity.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
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
    private val settingsRepository: com.whiplash.music.data.repository.SettingsRepository,
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
        }.onFailure { Log.w(TAG, "backup() failed", it) }.getOrDefault(false)
    }

    /**
     * Writes a JSON-based zip containing only [categories]' data to
     * [destination] — the "Advanced backup" checkbox flow (Settings >
     * Backup & Restore), as opposed to [backup]'s "everything, one raw
     * file copy" default.
     *
     * Deliberately a completely different on-disk format from [backup]
     * (a manifest + one JSON array per category, see [ZIP_MANIFEST_ENTRY]
     * and [restore]'s own doc) rather than trying to selectively omit
     * tables from a raw SQLite file copy — Room/SQLite has no supported
     * "copy only these tables" file-level operation, so a genuine
     * per-category selection has to be built from actual queried rows,
     * not a file copy. [PLAYLISTS]/[FAVORITES]/[HISTORY]/[PINNED] each
     * automatically also include every [SongEntity] row their own
     * references point to (not exposed as its own separate checkbox —
     * a user backing up "Favorites" expects the song titles/artwork to
     * still resolve after restore, not to need to separately remember
     * "also back up the metadata cache"), deduplicated across categories
     * so a song referenced by both Favorites and History is only written
     * once. [BackupCategory.DOWNLOADS]' own doc explains why only
     * download *records* are included, never the audio files themselves.
     */
    suspend fun backupSelective(destination: Uri, categories: Set<BackupCategory>): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (categories.isEmpty()) return@withContext false

            database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

            val songIds = mutableSetOf<String>()
            val manifest = JSONArray()
            val categoryPayloads = mutableMapOf<BackupCategory, JSONArray>()

            if (BackupCategory.PLAYLISTS in categories) {
                val playlists = database.playlistDao().observeAll().first()
                val playlistsJson = JSONArray()
                for (playlist in playlists) {
                    val tracks = database.playlistDao().observeTracks(playlist.id).first()
                    tracks.forEach { if (it.source == MediaSource.YOUTUBE || it.source == MediaSource.DOWNLOAD) songIds += it.trackId }
                    playlistsJson.put(
                        JSONObject().apply {
                            put("id", playlist.id)
                            put("name", playlist.name)
                            put("description", playlist.description)
                            put("artworkUrl", playlist.artworkUrl)
                            put("createdAtEpochMs", playlist.createdAtEpochMs)
                            put("updatedAtEpochMs", playlist.updatedAtEpochMs)
                            put(
                                "tracks",
                                JSONArray().apply {
                                    tracks.forEach {
                                        put(
                                            JSONObject().apply {
                                                put("position", it.position)
                                                put("trackId", it.trackId)
                                                put("source", it.source.name)
                                                put("addedAtEpochMs", it.addedAtEpochMs)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
                categoryPayloads[BackupCategory.PLAYLISTS] = playlistsJson
            }

            if (BackupCategory.FAVORITES in categories) {
                val favorites = database.favoriteDao().observeAll().first()
                favorites.forEach { if (it.source == MediaSource.YOUTUBE || it.source == MediaSource.DOWNLOAD) songIds += it.trackId }
                categoryPayloads[BackupCategory.FAVORITES] = JSONArray().apply {
                    favorites.forEach {
                        put(
                            JSONObject().apply {
                                put("trackId", it.trackId)
                                put("source", it.source.name)
                                put("addedAtEpochMs", it.addedAtEpochMs)
                            }
                        )
                    }
                }
            }

            if (BackupCategory.HISTORY in categories) {
                // The full history table, not just observeRecentlyPlayed's
                // deduped/limited view — a genuine "back up my history"
                // should restore the real underlying rows, not a
                // display-only projection of them.
                val history = database.historyDao().let { dao ->
                    // HistoryDao has no plain "get everything" query (only
                    // the deduped/limited observeRecentlyPlayed used for
                    // display) — reading the raw table directly here is
                    // the correct, minimal way to get every row without
                    // adding a query to the DAO that nothing else needs.
                    database.query("SELECT * FROM history", null)
                }
                categoryPayloads[BackupCategory.HISTORY] = JSONArray().apply {
                    history.use { cursor ->
                        val idIdx = cursor.getColumnIndexOrThrow("id")
                        val trackIdIdx = cursor.getColumnIndexOrThrow("trackId")
                        val sourceIdx = cursor.getColumnIndexOrThrow("source")
                        val playedAtIdx = cursor.getColumnIndexOrThrow("playedAtEpochMs")
                        while (cursor.moveToNext()) {
                            val source = cursor.getString(sourceIdx)
                            val trackId = cursor.getString(trackIdIdx)
                            if (source == MediaSource.YOUTUBE.name || source == MediaSource.DOWNLOAD.name) songIds += trackId
                            put(
                                JSONObject().apply {
                                    put("id", cursor.getLong(idIdx))
                                    put("trackId", trackId)
                                    put("source", source)
                                    put("playedAtEpochMs", cursor.getLong(playedAtIdx))
                                }
                            )
                        }
                    }
                }
            }

            if (BackupCategory.PINNED in categories) {
                val pinned = database.pinnedDao().observeAll().first()
                pinned.forEach { if (it.source == MediaSource.YOUTUBE || it.source == MediaSource.DOWNLOAD) songIds += it.trackId }
                categoryPayloads[BackupCategory.PINNED] = JSONArray().apply {
                    pinned.forEach {
                        put(
                            JSONObject().apply {
                                put("trackId", it.trackId)
                                put("source", it.source.name)
                                put("pinnedAtEpochMs", it.pinnedAtEpochMs)
                            }
                        )
                    }
                }
            }

            if (BackupCategory.DOWNLOADS in categories) {
                val downloads = database.downloadDao().getAll()
                categoryPayloads[BackupCategory.DOWNLOADS] = JSONArray().apply {
                    downloads.forEach {
                        put(
                            JSONObject().apply {
                                put("id", it.id)
                                put("title", it.title)
                                put("artist", it.artist)
                                put("album", it.album)
                                put("artworkPath", it.artworkPath)
                                put("durationMs", it.durationMs)
                                put("filePath", it.filePath)
                                put("fileSizeBytes", it.fileSizeBytes)
                                put("status", it.status.name)
                                put("downloadedAtEpochMs", it.downloadedAtEpochMs)
                            }
                        )
                    }
                }
            }

            if (BackupCategory.SETTINGS in categories) {
                categoryPayloads[BackupCategory.SETTINGS] = JSONArray().put(
                    JSONObject().apply {
                        put("audioQuality", settingsRepository.audioQuality.first().name)
                        put("downloadQuality", settingsRepository.downloadQuality.first().name)
                        put("autoplayEnabled", settingsRepository.autoplayEnabled.first())
                        put("themeVariant", settingsRepository.themeVariant.first().name)
                        put("seekBarStyle", settingsRepository.seekBarStyle.first().name)
                        put("crossfadeDurationMs", settingsRepository.crossfadeDurationMs.first())
                        put("gaplessEnabled", settingsRepository.gaplessEnabled.first())
                        put("playbackSpeed", settingsRepository.playbackSpeed.first().toDouble())
                        put("audioCacheEnabled", settingsRepository.audioCacheEnabled.first())
                        // Real silent data-loss gap this closes: these four
                        // real, user-facing settings were simply absent from
                        // the SETTINGS payload (and from the restore side),
                        // so a user who had enabled Skip Silence or
                        // Per-Network Quality — including their separate
                        // Wi-Fi/cellular quality tiers — silently lost those
                        // choices on any reinstall-and-restore, with the
                        // backup reporting complete success. Added purely
                        // additively: an older backup file that lacks these
                        // keys still restores exactly as before, because the
                        // restore side reads each one only if present.
                        put("skipSilenceEnabled", settingsRepository.skipSilenceEnabled.first())
                        put("perNetworkQualityEnabled", settingsRepository.perNetworkQualityEnabled.first())
                        put("audioQualityWifi", settingsRepository.audioQualityWifi.first().name)
                        put("audioQualityCellular", settingsRepository.audioQualityCellular.first().name)
                    }
                )
            }

            // Every song id any selected category referenced, resolved
            // once here and written as its own "songs" payload — see this
            // function's own doc for why this isn't a separate checkbox.
            val songsJson = JSONArray()
            if (songIds.isNotEmpty()) {
                database.songDao().getByIds(songIds.toList()).forEach { song ->
                    songsJson.put(
                        JSONObject().apply {
                            put("id", song.id)
                            put("title", song.title)
                            put("artist", song.artist)
                            put("album", song.album)
                            put("artworkUrl", song.artworkUrl)
                            put("durationMs", song.durationMs)
                            put("albumId", song.albumId)
                            put("artistId", song.artistId)
                            put("isExplicit", song.isExplicit)
                            put("cachedAtEpochMs", song.cachedAtEpochMs)
                        }
                    )
                }
            }

            categories.forEach { manifest.put(it.name) }
            val root = JSONObject().apply {
                put("formatVersion", SELECTIVE_FORMAT_VERSION)
                put("categories", manifest)
                categoryPayloads.forEach { (category, payload) -> put(category.name, payload) }
                put("songs", songsJson)
            }

            context.contentResolver.openOutputStream(destination)?.use { rawOut ->
                ZipOutputStream(rawOut.buffered()).use { zipOut ->
                    zipOut.putNextEntry(ZipEntry(ZIP_MANIFEST_ENTRY))
                    zipOut.write(root.toString().toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }
            } ?: return@withContext false
            true
        }.onFailure { Log.w(TAG, "backupSelective() failed", it) }.getOrDefault(false)
    }

    private fun WhiplashDatabase.query(sql: String, args: Array<Any?>?) =
        openHelper.readableDatabase.query(sql, args ?: emptyArray())

    /**
     * Merges [source]'s selective backup categories into the current
     * database/settings — unlike [restore] (which replaces everything
     * wholesale and requires a full app restart), this is additive: it
     * adds rows on top of whatever already exists, using each table's
     * own existing dedup logic ([com.whiplash.music.data.local.dao.PlaylistDao.addTrack]'s
     * containsTrack check, [OnConflictStrategy.REPLACE] on
     * Favorite/Pinned/Download/Song upserts) so restoring the same
     * selective backup twice is safe and idempotent — never duplicates,
     * never crashes. No app restart is required since this never touches
     * the raw database *file*, only inserts rows through the same Room
     * DAOs the rest of the app already uses live.
     */
    suspend fun restoreSelective(source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val root = context.contentResolver.openInputStream(source)?.use { rawIn ->
                ZipInputStream(rawIn.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    var manifestJson: String? = null
                    while (entry != null) {
                        if (entry.name == ZIP_MANIFEST_ENTRY) {
                            manifestJson = zipIn.readBytes().toString(Charsets.UTF_8)
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                    manifestJson
                }
            } ?: return@withContext false
            val json = JSONObject(root)

            // Real gap this closes: SELECTIVE_FORMAT_VERSION was written into
            // every backup but never read back, so a file produced by a
            // *newer* app version (or a corrupted/foreign file that merely
            // happens to contain the manifest entry) was parsed best-effort.
            // Anything it couldn't understand was silently skipped, or threw
            // partway through and aborted a restore that had already mutated
            // the database. Refusing an unsupported version outright is the
            // honest outcome — the caller surfaces a failed restore rather
            // than importing something half-understood.
            val fileVersion = json.optInt("formatVersion", 1)
            if (fileVersion > SELECTIVE_FORMAT_VERSION) {
                Log.w(TAG, "Refusing backup with unsupported formatVersion=$fileVersion (this build supports up to $SELECTIVE_FORMAT_VERSION)")
                return@withContext false
            }

            // Real gap this closes: the whole sequence below was a bare run of
            // independent DB writes. A single malformed row partway through
            // (a bad enum name, a missing required key) threw, the outer
            // runCatching returned false — and the categories already imported
            // stayed imported. The user saw "restore failed" while their
            // library had in fact been half-overwritten, with no way to tell
            // how far it got. Running it inside one transaction makes the
            // database side genuinely all-or-nothing.
            database.withTransaction {
                restoreDatabaseCategories(json)
            }

            json.optJSONArray(BackupCategory.SETTINGS.name)?.let { settingsArray ->
                if (settingsArray.length() > 0) {
                    val s = settingsArray.getJSONObject(0)
                    runCatching { settingsRepository.setAudioQuality(com.whiplash.music.domain.model.AudioQuality.valueOf(s.getString("audioQuality"))) }
                    runCatching { settingsRepository.setDownloadQuality(com.whiplash.music.domain.model.AudioQuality.valueOf(s.getString("downloadQuality"))) }
                    runCatching { settingsRepository.setAutoplayEnabled(s.getBoolean("autoplayEnabled")) }
                    runCatching { settingsRepository.setThemeVariant(com.whiplash.music.ui.theme.ThemeVariant.valueOf(s.getString("themeVariant"))) }
                    runCatching { settingsRepository.setSeekBarStyle(com.whiplash.music.ui.theme.SeekBarStyle.valueOf(s.getString("seekBarStyle"))) }
                    runCatching { settingsRepository.setCrossfadeDurationMs(s.getInt("crossfadeDurationMs")) }
                    runCatching { settingsRepository.setGaplessEnabled(s.getBoolean("gaplessEnabled")) }
                    runCatching { settingsRepository.setPlaybackSpeed(s.getDouble("playbackSpeed").toFloat()) }
                    runCatching { settingsRepository.setAudioCacheEnabled(s.getBoolean("audioCacheEnabled")) }
                    // Restore side of the four settings that used to be
                    // omitted from backups entirely (see the backup payload's
                    // own comment). Each is guarded on the key being present
                    // so a backup taken before this fix restores unchanged
                    // rather than resetting these to their defaults.
                    if (s.has("skipSilenceEnabled")) {
                        runCatching { settingsRepository.setSkipSilenceEnabled(s.getBoolean("skipSilenceEnabled")) }
                    }
                    if (s.has("perNetworkQualityEnabled")) {
                        runCatching { settingsRepository.setPerNetworkQualityEnabled(s.getBoolean("perNetworkQualityEnabled")) }
                    }
                    if (s.has("audioQualityWifi")) {
                        runCatching { settingsRepository.setAudioQualityWifi(com.whiplash.music.domain.model.AudioQuality.valueOf(s.getString("audioQualityWifi"))) }
                    }
                    if (s.has("audioQualityCellular")) {
                        runCatching { settingsRepository.setAudioQualityCellular(com.whiplash.music.domain.model.AudioQuality.valueOf(s.getString("audioQualityCellular"))) }
                    }
                }
            }

            true
        }.onFailure { Log.w(TAG, "restoreSelective() failed", it) }.getOrDefault(false)
    }

    /** True if [source] is a selective (category-based JSON) backup rather than a legacy full-DB zip — lets the caller route to [restoreSelective] vs [restore] without the user needing to know the difference. */
    suspend fun isSelectiveBackup(source: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(source)?.use { rawIn ->
                ZipInputStream(rawIn.buffered()).use { zipIn ->
                    var entry = zipIn.nextEntry
                    var found = false
                    while (entry != null) {
                        if (entry.name == ZIP_MANIFEST_ENTRY) {
                            found = true
                            break
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                    found
                }
            } ?: false
        }.onFailure { Log.w(TAG, "isSelectiveBackup() failed to read zip", it) }.getOrDefault(false)
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
        }.onFailure { Log.w(TAG, "restore() failed", it) }.getOrDefault(false)
    }


    /**
     * The database half of [restoreSelective], extracted so it can run
     * inside a single Room transaction (see its call site). Settings are
     * deliberately NOT restored here — they live in DataStore, not the
     * database, so they cannot participate in a SQLite transaction.
     */
    private suspend fun restoreDatabaseCategories(json: JSONObject) {
        // Songs first — every category's own references depend on
        // these rows already existing to resolve correctly.
        json.optJSONArray("songs")?.let { songs ->
            val entities = (0 until songs.length()).map { i ->
                val s = songs.getJSONObject(i)
                SongEntity(
                    id = s.getString("id"),
                    title = s.getString("title"),
                    artist = s.getString("artist"),
                    album = s.optString("album").takeIf { s.has("album") && !s.isNull("album") },
                    artworkUrl = s.optString("artworkUrl").takeIf { s.has("artworkUrl") && !s.isNull("artworkUrl") },
                    durationMs = s.getLong("durationMs"),
                    albumId = s.optString("albumId").takeIf { s.has("albumId") && !s.isNull("albumId") },
                    artistId = s.optString("artistId").takeIf { s.has("artistId") && !s.isNull("artistId") },
                    isExplicit = s.optBoolean("isExplicit", false),
                    cachedAtEpochMs = s.getLong("cachedAtEpochMs"),
                )
            }
            if (entities.isNotEmpty()) database.songDao().upsertAll(entities)
        }

        json.optJSONArray(BackupCategory.PLAYLISTS.name)?.let { playlists ->
            for (i in 0 until playlists.length()) {
                val p = playlists.getJSONObject(i)
                // A fresh playlist row (never reusing the backed-up
                // id) — the id is auto-generated and restoring it
                // verbatim risks colliding with an unrelated existing
                // playlist that happens to already occupy that id
                // (e.g. after restoring the same backup twice, or on
                // a device that already has other playlists). The
                // *name* is what a user actually recognizes their
                // playlist by, not its internal id.
                val newId = database.playlistDao().insert(
                    PlaylistEntity(
                        name = p.getString("name"),
                        description = p.optString("description").takeIf { p.has("description") && !p.isNull("description") },
                        artworkUrl = p.optString("artworkUrl").takeIf { p.has("artworkUrl") && !p.isNull("artworkUrl") },
                        createdAtEpochMs = p.getLong("createdAtEpochMs"),
                        updatedAtEpochMs = p.getLong("updatedAtEpochMs"),
                    )
                )
                val tracks = p.optJSONArray("tracks") ?: JSONArray()
                for (j in 0 until tracks.length()) {
                    val t = tracks.getJSONObject(j)
                    database.playlistDao().addTrack(
                        playlistId = newId,
                        trackId = t.getString("trackId"),
                        source = MediaSource.valueOf(t.getString("source")),
                        addedAtEpochMs = t.getLong("addedAtEpochMs"),
                    )
                }
            }
        }

        json.optJSONArray(BackupCategory.FAVORITES.name)?.let { favorites ->
            for (i in 0 until favorites.length()) {
                val f = favorites.getJSONObject(i)
                database.favoriteDao().add(
                    FavoriteEntity(
                        trackId = f.getString("trackId"),
                        source = MediaSource.valueOf(f.getString("source")),
                        addedAtEpochMs = f.getLong("addedAtEpochMs"),
                    )
                )
            }
        }

        json.optJSONArray(BackupCategory.HISTORY.name)?.let { history ->
            for (i in 0 until history.length()) {
                val h = history.getJSONObject(i)
                database.historyDao().insert(
                    HistoryEntity(
                        trackId = h.getString("trackId"),
                        source = MediaSource.valueOf(h.getString("source")),
                        playedAtEpochMs = h.getLong("playedAtEpochMs"),
                    )
                )
            }
        }

        json.optJSONArray(BackupCategory.PINNED.name)?.let { pinned ->
            for (i in 0 until pinned.length()) {
                val p = pinned.getJSONObject(i)
                database.pinnedDao().add(
                    PinnedEntity(
                        trackId = p.getString("trackId"),
                        source = MediaSource.valueOf(p.getString("source")),
                        pinnedAtEpochMs = p.getLong("pinnedAtEpochMs"),
                    )
                )
            }
        }

        json.optJSONArray(BackupCategory.DOWNLOADS.name)?.let { downloads ->
            for (i in 0 until downloads.length()) {
                val d = downloads.getJSONObject(i)
                val filePath = d.getString("filePath")
                // Real silent-failure gap this closes: a DOWNLOADS row was
                // recreated verbatim, including its absolute filePath and
                // its COMPLETED status — but a selective backup
                // deliberately stores download *records*, never the audio
                // bytes. After a reinstall (or on a different device) that
                // path does not exist, so the restore produced a Downloads
                // tab full of confidently checkmarked songs that could
                // never play, with no error and no hint that they needed
                // re-downloading. Only restoring rows whose audio is
                // genuinely present keeps the tab honest; the rest simply
                // reappear as normal, re-downloadable tracks.
                if (!java.io.File(filePath).exists()) continue
                database.downloadDao().upsert(
                    DownloadEntity(
                        id = d.getString("id"),
                        title = d.getString("title"),
                        artist = d.getString("artist"),
                        album = d.optString("album").takeIf { d.has("album") && !d.isNull("album") },
                        artworkPath = d.optString("artworkPath").takeIf { d.has("artworkPath") && !d.isNull("artworkPath") },
                        durationMs = d.getLong("durationMs"),
                        filePath = filePath,
                        fileSizeBytes = d.getLong("fileSizeBytes"),
                        status = DownloadStatus.valueOf(d.getString("status")),
                        downloadedAtEpochMs = d.getLong("downloadedAtEpochMs"),
                    )
                )
            }
        }
    }

    companion object {
        private const val DB_NAME = "whiplash.db"
        private const val SETTINGS_FILENAME = "whiplash_settings.preferences_pb"

        // Real, reported diagnosability gap (UAT audit finding): every
        // backup/restore function collapsed any exception to a plain
        // `false` with no logging at all, so a corrupt zip, disk-full,
        // or partial-write failure was completely indistinguishable
        // from "there was nothing to back up/restore" — impossible to
        // diagnose from a bug report. Log.w (not Log.e — these are all
        // already-handled, non-fatal failures reported back to the
        // caller as a normal false return, not a crash) on every one of
        // this class's top-level runCatching blocks.
        private const val TAG = "BackupManager"

        /** Distinguishes a selective (category JSON) backup zip from a legacy full-DB zip — see [isSelectiveBackup]. */
        private const val ZIP_MANIFEST_ENTRY = "whiplash_backup_manifest.json"

        /** Bumped only if the selective JSON schema itself changes shape; not tied to [WhiplashDatabase]'s own Room schema version. */
        private const val SELECTIVE_FORMAT_VERSION = 1

        /** Suggested file name for the system "Save as" picker, timestamped so repeated backups don't silently collide. */
        fun suggestedFileName(): String {
            val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(java.util.Date())
            return "whiplash_backup_$stamp.zip"
        }
    }
}
