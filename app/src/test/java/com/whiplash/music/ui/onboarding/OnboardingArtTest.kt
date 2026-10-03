// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OnboardingArtTest {

    private val assets = File("src/main/assets")

    private fun fileFor(uri: String) = File(assets, uri.removePrefix("file:///android_asset/"))

    @Test
    fun everyGenreHasBundledArt() {
        assertEquals(OnboardingCatalog.genres.map { it.name }.toSet(), OnboardingArt.genres.keys)
    }

    @Test
    fun enoughCoversForTheCollage() {
        // The collage has 15 tiles; fewer covers would repeat within view.
        assertTrue(OnboardingArt.covers.size >= 15)
    }

    @Test
    fun everyBundledFileExists() {
        val uris = OnboardingArt.covers + OnboardingArt.genres.values + OnboardingArt.artists.values
        val missing = uris.filterNot { fileFor(it).isFile }
        assertTrue("missing files: $missing", missing.isEmpty())
    }
}
