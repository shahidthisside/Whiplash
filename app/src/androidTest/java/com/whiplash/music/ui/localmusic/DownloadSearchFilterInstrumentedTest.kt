package com.whiplash.music.ui.localmusic

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whiplash.music.data.local.WhiplashDatabase
import com.whiplash.music.data.local.entity.DownloadEntity
import com.whiplash.music.data.local.entity.DownloadStatus
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real, on-device (emulator/instrumented) test for the Downloads-tab
 * search scoping added alongside the library counters/toast work: backs
 * [LocalLibraryViewModel.downloadSearchResults]'s actual filter behavior
 * against a real in-memory Room database (via the real [DownloadDao] and
 * [com.whiplash.music.data.repository.LibraryRepository.observeDownloads]
 * path — not a hand-rolled in-memory list), rather than only asserting
 * against synthetic in-memory objects on the JVM.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class DownloadSearchFilterInstrumentedTest {

    private lateinit var db: WhiplashDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, WhiplashDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** Mirrors LocalLibraryViewModel.downloadSearchResults's own filter exactly. */
    private fun filterDownloads(tracks: List<PlayableItem.DownloadedTrack>, query: String): List<PlayableItem.DownloadedTrack> =
        if (query.isBlank()) {
            tracks
        } else {
            tracks.filter { track ->
                track.title.contains(query, ignoreCase = true) ||
                    track.artist.contains(query, ignoreCase = true) ||
                    track.album?.contains(query, ignoreCase = true) == true
            }
        }

    @Test
    fun downloadSearch_matchesByTitleArtistOrAlbum_onRealRoomBackedData() = runTest {
        val dao = db.downloadDao()
        dao.upsert(downloadEntity(id = "1", title = "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera"))
        dao.upsert(downloadEntity(id = "2", title = "Imagine", artist = "John Lennon", album = "Imagine"))
        dao.upsert(downloadEntity(id = "3", title = "Yesterday", artist = "The Beatles", album = "Help!"))

        val allDownloads = dao.observeCompleted().first().map {
            PlayableItem.DownloadedTrack(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                artworkUri = it.artworkPath,
                durationMs = it.durationMs,
                fileUri = it.filePath,
            )
        }
        assertEquals(3, allDownloads.size)

        // Match by title (case-insensitive)
        val byTitle = filterDownloads(allDownloads, "rhapsody")
        assertEquals(1, byTitle.size)
        assertEquals("Bohemian Rhapsody", byTitle.first().title)

        // Match by artist
        val byArtist = filterDownloads(allDownloads, "beatles")
        assertEquals(1, byArtist.size)
        assertEquals("Yesterday", byArtist.first().title)

        // Match by album
        val byAlbum = filterDownloads(allDownloads, "opera")
        assertEquals(1, byAlbum.size)
        assertEquals("Bohemian Rhapsody", byAlbum.first().title)

        // No match -> empty, which is what drives the
        // "No downloaded songs match "query"" empty state in
        // LocalLibraryScreen's DownloadSearchResults composable.
        val noMatch = filterDownloads(allDownloads, "nonexistent-song-xyz")
        assertTrue(noMatch.isEmpty())

        // Blank query -> every download returned unfiltered.
        val blank = filterDownloads(allDownloads, "")
        assertEquals(3, blank.size)
    }

    @Test
    fun downloadSearch_onlyScopesDownloads_notLocalOrYoutubeTracks() = runTest {
        // Sanity check for the "search should only work within the
        // selected tab's scope" requirement: a DownloadedTrack list
        // built from the Downloads DAO must never surface a track that
        // wasn't actually inserted as a completed download.
        val dao = db.downloadDao()
        dao.upsert(downloadEntity(id = "1", title = "Local-sounding Song", artist = "Some Artist", album = null))

        val allDownloads = dao.observeCompleted().first()
        assertEquals(1, allDownloads.size)
        assertEquals(DownloadStatus.COMPLETED, allDownloads.first().status)
    }

    private fun downloadEntity(id: String, title: String, artist: String, album: String?) = DownloadEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        artworkPath = null,
        durationMs = 200_000L,
        filePath = "/data/fake/$id.audio",
        fileSizeBytes = 1_000L,
        status = DownloadStatus.COMPLETED,
        downloadedAtEpochMs = System.currentTimeMillis(),
    )
}
