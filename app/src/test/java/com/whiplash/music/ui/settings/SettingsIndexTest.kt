package com.whiplash.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {

    @Test
    fun `blank query shows everything`() {
        assertEquals(SettingsSection.entries, visibleSections(""))
        assertEquals(SettingsSection.entries, visibleSections("   "))
    }

    @Test
    fun `matches title, keywords and section, any case`() {
        assertTrue(settingMatches(SettingEntry.CROSSFADE, "CROSS"))
        assertTrue(settingMatches(SettingEntry.EQUALIZER, "bass"))
        assertTrue(settingMatches(SettingEntry.LYRICS_BLUR, "lyrics"))
        assertFalse(settingMatches(SettingEntry.THEME, "bass"))
    }

    @Test
    fun `every word must match`() {
        assertTrue(settingMatches(SettingEntry.DOWNLOAD_WIFI, "wifi download"))
        assertFalse(settingMatches(SettingEntry.DOWNLOAD_QUALITY, "wifi download"))
    }

    @Test
    fun `sections filter down`() {
        assertEquals(listOf(SettingsSection.LYRICS), visibleSections("lrclib"))
        assertEquals(emptyList<SettingsSection>(), visibleSections("zzzz"))
        assertTrue(SettingsSection.AUDIO_QUALITY in visibleSections("wifi"))
        assertTrue(SettingsSection.DOWNLOADS in visibleSections("wifi"))
    }

    @Test
    fun `every section has entries`() {
        SettingsSection.entries.forEach { s -> assertTrue(s.name, SettingEntry.entries.any { it.section == s }) }
    }

    @Test
    fun `every section is in exactly one folder group`() {
        val grouped = SettingsGroup.entries.flatMap { it.sections }
        assertEquals(SettingsSection.entries.size, grouped.size)
        assertEquals(SettingsSection.entries.toSet(), grouped.toSet())
    }
}
