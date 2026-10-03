package com.whiplash.music

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchStackSaverTest {
    @Test fun roundTripsPagesIncludingUrlsWithColons() {
        val pages = listOf(
            'a' to "https://music.youtube.com/playlist?list=OLAK5uy_x",
            'r' to "https://www.youtube.com/channel/UC123",
            'g' to "ggMPOg1uX1",
        )
        assertEquals(pages, decodeSearchStack(encodeSearchStack(pages)))
    }

    @Test fun emptyStackStaysEmpty() {
        assertEquals(emptyList<Pair<Char, String>>(), decodeSearchStack(encodeSearchStack(emptyList())))
    }

    @Test fun damagedEntriesAreSkipped() {
        assertEquals(listOf('a' to "x"), decodeSearchStack(listOf("", "q", "a:x", "zz")))
    }
}
