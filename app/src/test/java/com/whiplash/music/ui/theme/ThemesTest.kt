// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ThemesTest {

    private fun assertReadable(p: GlassPalette, label: String) {
        val surfaces = listOf(p.background, p.surfaceGlass, p.surfaceElevated, p.surfaceSheet)
        for (s in surfaces) {
            // Small tolerance for the binary search's final step.
            assertTrue("$label textPrimary", contrastRatio(p.textPrimary, s) >= 6.9f)
            assertTrue("$label textSecondary", contrastRatio(p.textSecondary, s) >= 4.4f)
            assertTrue("$label textTertiary", contrastRatio(p.textTertiary, s) >= 2.9f)
            assertTrue("$label accent", contrastRatio(p.accent, s) >= 2.9f)
        }
        assertTrue("$label onAccent", contrastRatio(p.onAccent, p.accent) >= 4.4f)
    }

    @Test
    fun everyThemeWithEveryAccentIsReadable() {
        for (theme in AppTheme.entries) for (accent in ThemeVariant.entries) {
            assertReadable(resolvePalette(theme, accent, CustomThemeColors()), "$theme/$accent")
        }
    }

    @Test
    fun randomCustomThemesAreReadable() {
        val rnd = Random(42)
        repeat(500) {
            val custom = CustomThemeColors(
                background = Color(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()),
                accent = Color(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat()),
            )
            assertReadable(resolvePalette(AppTheme.CUSTOM, ThemeVariant.CLASSIC, custom), "custom $custom")
        }
    }

    @Test
    fun randomCustomAccentsAreReadableOnEveryAccentTheme() {
        val rnd = Random(7)
        val themes = AppTheme.entries.filter { it.usesAccentChoice }
        repeat(300) {
            val accent = Color(rnd.nextFloat(), rnd.nextFloat(), rnd.nextFloat())
            for (theme in themes) for (bg in GlassBackground.entries) {
                val c = CustomThemeColors(accentColor = accent, glassBackground = bg)
                val p = resolvePalette(theme, ThemeVariant.CUSTOM, c)
                assertReadable(p, "custom accent $accent on $theme/$bg")
            }
        }
        // Actually uses the picked colour when it's already readable.
        val blue = Color(0xFF5AC8FA)
        assertTrue(resolvePalette(AppTheme.DARK, ThemeVariant.CUSTOM, CustomThemeColors(accentColor = blue)).accent == blue)
    }

    @Test
    fun everyGlassBackgroundIsReadable() {
        for (bg in GlassBackground.entries) for (accent in ThemeVariant.entries) {
            val c = CustomThemeColors(glassBackground = bg, glassColor = Color(0xFF3060FF))
            assertReadable(resolvePalette(AppTheme.LIQUID_GLASS, accent, c), "glass $bg/$accent")
        }
        assertTrue(resolvePalette(AppTheme.LIQUID_GLASS, ThemeVariant.CLASSIC, CustomThemeColors(glassBackground = GlassBackground.PEARL)).isLight)
    }

    @Test
    fun lightThemesReportLight() {
        assertTrue(resolvePalette(AppTheme.LIGHT, ThemeVariant.CLASSIC, CustomThemeColors()).isLight)
        assertTrue(resolvePalette(AppTheme.ROSE_PINE_DAWN, ThemeVariant.CLASSIC, CustomThemeColors()).isLight)
        assertTrue(!resolvePalette(AppTheme.NORD, ThemeVariant.CLASSIC, CustomThemeColors()).isLight)
    }
}
