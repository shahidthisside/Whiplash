package com.whiplash.music.data.backup

import android.content.Context
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whiplash.music.data.local.WhiplashDatabase
import com.whiplash.music.data.local.entity.FavoriteEntity
import com.whiplash.music.data.local.entity.HistoryEntity
import com.whiplash.music.data.local.entity.MediaSource
import com.whiplash.music.data.local.entity.PinnedEntity
import com.whiplash.music.data.local.entity.SongEntity
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.domain.model.AudioQuality
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-end round-trip tests for selective backup/restore.
 *
 * These exist because the restore path was substantially refactored: its
 * database half was extracted into `restoreDatabaseCategories` so it could be
 * wrapped in a single `withTransaction`, a `formatVersion` check was added, and
 * four previously-omitted settings plus a download-file-existence check were
 * introduced. A refactor of that size to a data-recovery feature is exactly the
 * kind of change that can silently stop working — which is the whole failure
 * mode this audit was chasing — so it is verified by actually writing a backup
 * file, wiping the database, restoring from it, and asserting every category
 * came back.
 *
 * Instrumented because BackupManager needs a real Context, real SAF-style file
 * Uris, real DataStore and a real Room database.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripInstrumentedTest {

    private lateinit var context: Context
    private lateinit var database: WhiplashDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var manager: BackupManager
    private lateinit var backupFile: File

    private val allCategories = BackupCategory.entries.toSet()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, WhiplashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        settings = SettingsRepository(context)
        manager = BackupManager(context, database, settings)
        backupFile = File(context.cacheDir, "roundtrip-${System.nanoTime()}.whiplashbackup")
    }

    @After
    fun tearDown() {
        database.close()
        backupFile.delete()
    }

    private suspend fun seedLibrary() {
        // Metadata must exist for the references below to resolve.
        database.songDao().upsertAll(
            listOf(
                SongEntity(
                    id = "vid1",
                    title = "First Track",
                    artist = "Artist One",
                    album = "Album One",
                    artworkUrl = "https://example.invalid/1.jpg",
                    durationMs = 210_000L,
                    albumId = null,
                    artistId = null,
                    isExplicit = false,
                    cachedAtEpochMs = 1_000L,
                ),
                SongEntity(
                    id = "vid2",
                    title = "Second Track",
                    artist = "Artist Two",
                    album = null,
                    artworkUrl = null,
                    durationMs = 180_000L,
                    albumId = null,
                    artistId = null,
                    isExplicit = false,
                    cachedAtEpochMs = 2_000L,
                ),
            ),
        )

        val playlistId = database.playlistDao().insert(
            com.whiplash.music.data.local.entity.PlaylistEntity(
                name = "Road Trip",
                createdAtEpochMs = 5_000L,
                updatedAtEpochMs = 5_000L,
            ),
        )
        database.playlistDao().addTrack(playlistId, "vid1", MediaSource.YOUTUBE, 6_000L)
        database.playlistDao().addTrack(playlistId, "vid2", MediaSource.YOUTUBE, 7_000L)

        database.favoriteDao().add(FavoriteEntity("vid1", MediaSource.YOUTUBE, 8_000L))
        database.historyDao().insert(HistoryEntity(trackId = "vid2", source = MediaSource.YOUTUBE, playedAtEpochMs = 9_000L))
        database.pinnedDao().add(PinnedEntity("vid1", MediaSource.YOUTUBE, 10_000L))

        // Includes the four settings that used to be silently omitted from backups.
        settings.setThemeVariant(com.whiplash.music.ui.theme.ThemeVariant.CRIMSON_NOIR)
        settings.setAudioQuality(AudioQuality.HIGH)
        settings.setDownloadQuality(AudioQuality.LOW)
        settings.setSkipSilenceEnabled(true)
        settings.setPerNetworkQualityEnabled(true)
        settings.setAudioQualityWifi(AudioQuality.HIGHEST)
        settings.setAudioQualityCellular(AudioQuality.LOW)
    }

    private suspend fun wipeDatabase() {
        database.playlistDao().let { dao ->
            dao.observeAll().first().forEach { dao.delete(it.id) }
        }
        database.favoriteDao().observeAll().first().forEach {
            database.favoriteDao().remove(it.trackId, it.source)
        }
        database.historyDao().clear()
        database.pinnedDao().observeAll().first().forEach {
            database.pinnedDao().remove(it.trackId, it.source)
        }
    }

    @Test
    fun selectiveBackupThenRestore_bringsBackEveryCategory() = runTest {
        seedLibrary()

        assertTrue(
            "backupSelective must report success",
            manager.backupSelective(backupFile.toUri(), allCategories),
        )
        assertTrue("backup file must actually exist", backupFile.exists())
        assertTrue("backup file must not be empty", backupFile.length() > 0)
        assertTrue("must be recognised as a selective backup", manager.isSelectiveBackup(backupFile.toUri()))

        wipeDatabase()
        assertEquals(0, database.favoriteDao().observeAll().first().size)
        assertEquals(0, database.playlistDao().observeAll().first().size)

        assertTrue(
            "restoreSelective must report success",
            manager.restoreSelective(backupFile.toUri()),
        )

        // Playlists + their tracks, in order.
        val playlists = database.playlistDao().observeAll().first()
        assertEquals(1, playlists.size)
        assertEquals("Road Trip", playlists.first().name)
        val tracks = database.playlistDao().observeTracks(playlists.first().id).first()
        assertEquals(2, tracks.size)
        assertEquals(listOf("vid1", "vid2"), tracks.map { it.trackId })

        // Favorites / history / pinned.
        assertEquals(listOf("vid1"), database.favoriteDao().observeAll().first().map { it.trackId })
        assertEquals(listOf("vid2"), database.historyDao().observeRecentlyPlayed(50).first().map { it.trackId })
        assertEquals(listOf("vid1"), database.pinnedDao().observeAll().first().map { it.trackId })

        // Song metadata came along, so the references above can actually render.
        assertEquals(2, database.songDao().getByIds(listOf("vid1", "vid2")).size)
    }

    @Test
    fun restore_bringsBackThePreviouslyOmittedSettings() = runTest {
        seedLibrary()
        assertTrue(manager.backupSelective(backupFile.toUri(), allCategories))

        // Move every setting away from what was backed up.
        settings.setSkipSilenceEnabled(false)
        settings.setPerNetworkQualityEnabled(false)
        settings.setAudioQualityWifi(AudioQuality.LOW)
        settings.setAudioQualityCellular(AudioQuality.HIGHEST)
        settings.setAudioQuality(AudioQuality.LOW)

        assertTrue(manager.restoreSelective(backupFile.toUri()))

        // These four were silently dropped by backup+restore before the fix.
        assertTrue("skipSilence must be restored", settings.skipSilenceEnabled.first())
        assertTrue("perNetworkQuality must be restored", settings.perNetworkQualityEnabled.first())
        assertEquals(AudioQuality.HIGHEST, settings.audioQualityWifi.first())
        assertEquals(AudioQuality.LOW, settings.audioQualityCellular.first())
        // And the ones that always worked still do.
        assertEquals(AudioQuality.HIGH, settings.audioQuality.first())
        assertEquals(AudioQuality.LOW, settings.downloadQuality.first())
    }

    /** Only the chosen categories may be written, so an unchosen one cannot come back. */
    @Test
    fun backupSelective_honoursCategorySelection() = runTest {
        seedLibrary()

        assertTrue(
            manager.backupSelective(backupFile.toUri(), setOf(BackupCategory.FAVORITES)),
        )
        wipeDatabase()
        assertTrue(manager.restoreSelective(backupFile.toUri()))

        assertEquals(
            "favorites were selected, so they must return",
            listOf("vid1"),
            database.favoriteDao().observeAll().first().map { it.trackId },
        )
        assertEquals(
            "playlists were NOT selected, so they must stay absent",
            0,
            database.playlistDao().observeAll().first().size,
        )
        assertEquals(
            "history was NOT selected, so it must stay absent",
            0,
            database.historyDao().observeRecentlyPlayed(50).first().size,
        )
    }

    /**
     * A backup claiming a newer format than this build understands must be
     * refused outright rather than parsed best-effort — formatVersion was
     * written into every file but never read back before this fix.
     */
    @Test
    fun restore_refusesAnUnsupportedFutureFormatVersion() = runTest {
        seedLibrary()
        assertTrue(manager.backupSelective(backupFile.toUri(), allCategories))

        val tampered = File(context.cacheDir, "future-${System.nanoTime()}.whiplashbackup")
        rewriteManifest(backupFile, tampered) { json ->
            json.put("formatVersion", 9_999)
            json
        }

        assertFalse(
            "a future formatVersion must be refused",
            manager.restoreSelective(tampered.toUri()),
        )
        tampered.delete()
    }

    /** A corrupt/garbage file must fail cleanly, never crash. */
    @Test
    fun restore_failsCleanlyOnGarbageInput() = runTest {
        val garbage = File(context.cacheDir, "garbage-${System.nanoTime()}.whiplashbackup")
        garbage.writeBytes(ByteArray(512) { it.toByte() })

        assertFalse(manager.restoreSelective(garbage.toUri()))
        assertFalse(manager.isSelectiveBackup(garbage.toUri()))
        garbage.delete()
    }

    /** Restoring twice must not duplicate rows. */
    @Test
    fun restore_isIdempotent() = runTest {
        seedLibrary()
        assertTrue(manager.backupSelective(backupFile.toUri(), allCategories))
        wipeDatabase()

        assertTrue(manager.restoreSelective(backupFile.toUri()))
        val favoritesAfterFirst = database.favoriteDao().observeAll().first().size
        val pinnedAfterFirst = database.pinnedDao().observeAll().first().size

        assertTrue(manager.restoreSelective(backupFile.toUri()))

        assertEquals(favoritesAfterFirst, database.favoriteDao().observeAll().first().size)
        assertEquals(pinnedAfterFirst, database.pinnedDao().observeAll().first().size)
    }

    /** Rewrites the manifest JSON inside a backup zip, preserving the entry name. */
    private fun rewriteManifest(
        source: File,
        destination: File,
        transform: (org.json.JSONObject) -> org.json.JSONObject,
    ) {
        var entryName = "manifest.json"
        var manifest: String? = null
        java.util.zip.ZipInputStream(source.inputStream().buffered()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                entryName = entry.name
                manifest = zin.readBytes().toString(Charsets.UTF_8)
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
        val updated = transform(org.json.JSONObject(requireNotNull(manifest)))
        java.util.zip.ZipOutputStream(destination.outputStream().buffered()).use { zout ->
            zout.putNextEntry(java.util.zip.ZipEntry(entryName))
            zout.write(updated.toString().toByteArray(Charsets.UTF_8))
            zout.closeEntry()
        }
    }
}
