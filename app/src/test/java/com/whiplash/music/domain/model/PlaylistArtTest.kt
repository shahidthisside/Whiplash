// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistArtTest {

    @Test
    fun nullAndBlankAreAuto() {
        assertEquals(PlaylistArt.Auto, PlaylistArt.parse(null))
        assertEquals(PlaylistArt.Auto, PlaylistArt.parse("  "))
        assertNull(PlaylistArt.encode(PlaylistArt.Auto))
    }

    @Test
    fun songsRoundTripAndCapAtFour() {
        val art = PlaylistArt.Songs(listOf("https://a/1.jpg", "https://a/2.jpg", "file:///x/3.jpg"))
        assertEquals(art, PlaylistArt.parse(PlaylistArt.encode(art)))
        val five = PlaylistArt.Songs((1..5).map { "https://a/$it.jpg" })
        assertEquals(4, (PlaylistArt.parse(PlaylistArt.encode(five)) as PlaylistArt.Songs).artworks.size)
    }

    @Test
    fun emptySongsBecomeAuto() {
        assertNull(PlaylistArt.encode(PlaylistArt.Songs(emptyList())))
        assertEquals(PlaylistArt.Auto, PlaylistArt.parse("songs:\n\n"))
    }

    @Test
    fun imageRoundTrip() {
        val art = PlaylistArt.Image("file:///data/user/0/app/files/playlist_covers/7_1.jpg")
        assertEquals(art, PlaylistArt.parse(PlaylistArt.encode(art)))
    }

    @Test
    fun bareUrlIsImageAndJunkIsAuto() {
        assertEquals(PlaylistArt.Image("https://i.ytimg.com/x.jpg"), PlaylistArt.parse("https://i.ytimg.com/x.jpg"))
        assertEquals(PlaylistArt.Auto, PlaylistArt.parse("something else"))
    }
}
