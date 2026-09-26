package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkPaletteTest {

    private fun solid(color: Int, w: Int = 8, h: Int = 8) = IntArray(w * h) { color }

    private fun hslOf(color: Int): FloatArray = FloatArray(3).also {
        rgbToHsl((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, it)
    }

    @Test
    fun greyscaleCover_hasNoAccent() {
        val palette = extractArtworkPalette(solid(0xFF808080.toInt()), 8, 8)
        assertNotNull(palette)
        assertNull(palette!!.accent)
        assertEquals(4, palette.mesh.size)
    }

    @Test
    fun redCover_givesRedAccentBrightEnoughForDarkUi() {
        val palette = extractArtworkPalette(solid(0xFFC0201A.toInt()), 8, 8)!!
        val hsl = hslOf(palette.accent!!)
        assertTrue("hue ${hsl[0]}", hsl[0] < 15f || hsl[0] > 345f)
        assertTrue("lightness ${hsl[2]}", hsl[2] >= 0.6f)
    }

    @Test
    fun mesh_keepsQuadrantArrangement_andStaysDark() {
        // Left half blue, right half orange.
        val w = 8; val h = 8
        val pixels = IntArray(w * h) { i -> if (i % w < w / 2) 0xFF1E50D0.toInt() else 0xFFE08020.toInt() }
        val mesh = extractArtworkPalette(pixels, w, h)!!.mesh
        val topLeftHue = hslOf(mesh[0])[0]
        val topRightHue = hslOf(mesh[1])[0]
        assertTrue("left should be blue: $topLeftHue", topLeftHue in 200f..240f)
        assertTrue("right should be orange: $topRightHue", topRightHue in 20f..40f)
        mesh.forEach { assertTrue(hslOf(it)[2] <= 0.35f) }
    }

    @Test
    fun smallColourSpeck_onBlackAndWhite_isIgnored() {
        val w = 20; val h = 20
        val pixels = IntArray(w * h) { if (it % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        pixels[0] = 0xFFFF0000.toInt()
        assertNull(extractArtworkPalette(pixels, w, h)!!.accent)
    }

    @Test
    fun transparentOrEmpty_returnsNull() {
        assertNull(extractArtworkPalette(IntArray(16), 4, 4))
        assertNull(extractArtworkPalette(IntArray(0), 0, 0))
    }

    @Test
    fun hslRoundTrip_isStable() {
        val original = 0xFF3A7BD5.toInt()
        assertEquals(original, hslToRgb(hslOf(original)))
    }
}
