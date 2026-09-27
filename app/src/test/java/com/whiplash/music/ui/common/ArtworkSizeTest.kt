package com.whiplash.music.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkSizeTest {
    @Test fun enlargesWidthHeight() {
        assertEquals(
            "https://yt3.googleusercontent.com/abc=w544-h544-l90-rj",
            artworkAtSize("https://yt3.googleusercontent.com/abc=w120-h120-l90-rj", 544),
        )
    }

    @Test fun enlargesSquareSize() {
        assertEquals("https://lh3.googleusercontent.com/x=s544", artworkAtSize("https://lh3.googleusercontent.com/x=s88", 544))
    }

    @Test fun neverShrinks() {
        val big = "https://lh3.googleusercontent.com/x=w1200-h1200-l90-rj"
        assertEquals(big, artworkAtSize(big, 544))
    }

    @Test fun leavesOtherHostsAlone() {
        val yt = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=-oay=w120-h120"
        assertEquals(yt, artworkAtSize(yt, 544))
    }

    @Test fun nullStaysNull() {
        assertNull(artworkAtSize(null, 544))
    }
}
