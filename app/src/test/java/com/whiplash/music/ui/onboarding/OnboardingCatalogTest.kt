// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingCatalogTest {

    @Test
    fun `nothing picked suggests an English and Hindi mix`() {
        val s = OnboardingCatalog.suggestedArtists(emptyList(), emptyList())
        assertTrue("Taylor Swift" in s.take(2))
        assertTrue("Arijit Singh" in s.take(2))
    }

    @Test
    fun `every pick is represented near the top and nothing repeats`() {
        val s = OnboardingCatalog.suggestedArtists(listOf("Tamil", "Korean"), listOf("EDM"))
        assertEquals(s.size, s.distinct().size)
        val top = s.take(3)
        assertTrue(top.contains("Anirudh Ravichander"))
        assertTrue(top.contains("BTS"))
        assertTrue(top.contains("Alan Walker"))
    }

    @Test
    fun `suggestions are capped`() {
        val all = OnboardingCatalog.languages.map { it.name }
        assertEquals(10, OnboardingCatalog.suggestedArtists(all, emptyList(), max = 10).size)
    }

    @Test
    fun `a small language is topped up with well-known artists`() {
        val s = OnboardingCatalog.suggestedArtists(listOf("Marathi"), emptyList())
        assertEquals(24, s.size)
        assertEquals(listOf("Ajay-Atul", "Shankar Mahadevan", "Avadhoot Gupte"), s.take(3))
        assertTrue("Arijit Singh" in s)
        assertEquals(s.size, s.distinct().size)
    }

    @Test
    fun `every suggestion has a bundled photo`() {
        val all = OnboardingCatalog.suggestedArtists(OnboardingCatalog.languages.map { it.name }, OnboardingCatalog.genres.map { it.name }, max = 500)
        assertEquals(emptyList<String>(), all.filter { it !in OnboardingArt.artists })
    }

    @Test
    fun `every genre has a search query`() {
        OnboardingCatalog.genres.forEach { assertTrue(it.query.isNotBlank()) }
        assertEquals(OnboardingCatalog.genres.size, OnboardingCatalog.genres.map { it.name }.distinct().size)
    }
}
