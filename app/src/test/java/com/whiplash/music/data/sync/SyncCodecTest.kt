package com.whiplash.music.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class SyncCodecTest {

    @Test
    fun `round trip keeps every field`() {
        val snapshot = SyncSnapshot(
            songs = mapOf("a" to SyncSong("Skyfall", "Adele", "Skyfall", "https://i/a.jpg", 286000, "alb", "art", true)),
            favorites = mapOf("a" to 5L),
            pinned = mapOf("a" to 6L),
            history = mapOf(SyncSnapshot.historyKey("a", 7L) to 7L),
            playlists = mapOf(
                "c1" to SyncPlaylist("Mix", "desc", "songs:https://x", 1L, 2L, 3L, listOf(SyncTrack("a", 4L))),
                "c9" to SyncPlaylist(name = "Plain", createdAtEpochMs = 9L, updatedAtEpochMs = 9L),
            ),
            replay = mapOf("2026-09|a" to SyncReplay("Skyfall", "Adele", null, 286000, 3, 900L, 8L)),
            settings = mapOf("appTheme" to "DARK", "playbackSpeed" to "1.25", "gaplessEnabled" to "true"),
            lyricOffsets = mapOf("a" to -250L),
            profile = SyncProfile("Sam", byteArrayOf(-1, 0, 42), 77L),
        )
        assertEquals(snapshot, SyncCodec.decode(SyncCodec.encode(snapshot, "Pixel", 100L)))
        assertNull(SyncCodec.decode(SyncCodec.encode(snapshot, "Pixel", 100L)).playlists.getValue("c9").pinnedAtEpochMs)
    }

    @Test(expected = SyncCodec.UnsupportedVersion::class)
    fun `a newer file format is refused`() {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(JSONObject().put("v", SyncCodec.FORMAT_VERSION + 1).toString().toByteArray()) }
        SyncCodec.decode(out.toByteArray())
    }

    @Test
    fun `history key survives ids containing the separator`() {
        val key = SyncSnapshot.historyKey("we@ird", 42L)
        assertEquals("we@ird", SyncSnapshot.historyTrackId(key))
    }
}
