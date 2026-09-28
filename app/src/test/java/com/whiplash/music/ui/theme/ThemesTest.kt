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
    fun lightThemesReportLight() {
        assertTrue(resolvePalette(AppTheme.LIGHT, ThemeVariant.CLASSIC, CustomThemeColors()).isLight)
        assertTrue(resolvePalette(AppTheme.ROSE_PINE_DAWN, ThemeVariant.CLASSIC, CustomThemeColors()).isLight)
        assertTrue(!resolvePalette(AppTheme.NORD, ThemeVariant.CLASSIC, CustomThemeColors()).isLight)
    }
}
