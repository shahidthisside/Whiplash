package com.whiplash.music.playback.controller
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.whiplash.music.domain.model.PlayableItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Locks in the contract behind the lock-screen "no elapsed/remaining time
 * and a frozen progress bar" bug.
 *
 * Media3's legacy bridge ([androidx.media3.session.LegacyConversions]
 * `.convertToMediaMetadataCompat`) is what the lock screen, Bluetooth/AVRCP
 * and OEM media surfaces read. It uses the player's own duration when it has
 * one, falls back to [androidx.media3.common.MediaMetadata.durationMs] when
 * it doesn't, and writes `METADATA_KEY_DURATION = -1` when neither is
 * available. ExoPlayer only knows a duration after parsing enough of the
 * container, so leaving this field unset meant every metadata publication
 * before that point sent -1 and the lock screen had no scale to draw.
 *
 * Instrumented rather than a JVM test because the mapper parses artwork URIs
 * through [android.net.Uri], which is framework-provided.
 */
@RunWith(AndroidJUnit4::class)
class PlayableItemMediaItemMapperDurationTest {

    private fun youtube(durationMs: Long) = PlayableItem.YoutubeTrack(
        id = "abc123",
        title = "295",
        artist = "Sidhu Moose Wala",
        album = null,
        artworkUri = "https://example.com/a.jpg",
        durationMs = durationMs,
    )

    @Test
    fun youtubeTrack_publishesItsKnownDuration() {
        val item = youtube(271_000L)
        val mediaItem = PlayableItemMediaItemMapper.toMediaItem(item, resolvedStreamUrl = "https://x/y")
        assertEquals(271_000L, mediaItem.mediaMetadata.durationMs)
    }

    @Test
    fun localTrack_publishesItsKnownDuration() {
        val item = PlayableItem.LocalTrack(
            id = "1",
            title = "TestSong1",
            artist = "Someone",
            album = "An Album",
            artworkUri = null,
            durationMs = 180_000L,
            mediaStoreUri = "content://media/external/audio/media/1",
        )
        assertEquals(180_000L, PlayableItemMediaItemMapper.toMediaItem(item).mediaMetadata.durationMs)
    }

    @Test
    fun downloadedTrack_publishesItsKnownDuration() {
        val item = PlayableItem.DownloadedTrack(
            id = "dl1",
            title = "Ama hem hem",
            artist = "Someone",
            album = null,
            artworkUri = "/data/data/com.whiplash.music/files/downloads/dl1.jpg",
            durationMs = 240_500L,
            fileUri = "/data/data/com.whiplash.music/files/downloads/dl1.audio",
        )
        assertEquals(240_500L, PlayableItemMediaItemMapper.toMediaItem(item).mediaMetadata.durationMs)
    }

    /**
     * An unknown duration must stay unset so the bridge keeps reporting -1
     * ("unknown"). Publishing 0 would assert a zero-length track, which pegs
     * a progress bar at its end rather than leaving it blank — worse than the
     * bug being fixed.
     */
    @Test
    fun unknownDuration_isLeftUnsetRatherThanPublishedAsZero() {
        assertNull(
            PlayableItemMediaItemMapper.toMediaItem(youtube(0L), resolvedStreamUrl = "https://x/y")
                .mediaMetadata.durationMs,
        )
    }

    @Test
    fun negativeDuration_isLeftUnset() {
        assertNull(
            PlayableItemMediaItemMapper.toMediaItem(youtube(-1L), resolvedStreamUrl = "https://x/y")
                .mediaMetadata.durationMs,
        )
    }

    /** The duration must not have displaced any metadata that already worked. */
    @Test
    fun existingMetadataIsUnchanged() {
        val item = youtube(271_000L)
        val md = PlayableItemMediaItemMapper.toMediaItem(item, resolvedStreamUrl = "https://x/y").mediaMetadata
        assertEquals("295", md.title)
        assertEquals("Sidhu Moose Wala", md.artist)
        assertEquals("https://example.com/a.jpg", md.artworkUri.toString())
    }
}
