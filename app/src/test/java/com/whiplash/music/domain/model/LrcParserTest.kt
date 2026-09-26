package com.whiplash.music.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `plain line sync parses and sorts`() {
        val lines = LrcParser.parse(
            """
            [00:12.50] second
            [00:01.00] first
            [ar:Someone]
            not a lyric line
            [00:20.00]
            """.trimIndent(),
        )
        assertEquals(listOf(1_000L, 12_500L, 20_000L), lines.map { it.timestampMs })
        assertEquals(listOf("first", "second", ""), lines.map { it.text })
        assertTrue(lines.all { it.words.isEmpty() })
    }

    @Test
    fun `time formats`() {
        assertEquals(65_000L, LrcParser.parseTime("01:05"))
        assertEquals(65_500L, LrcParser.parseTime("01:05.5"))
        assertEquals(65_120L, LrcParser.parseTime("01:05.12"))
        assertEquals(65_123L, LrcParser.parseTime("01:05.123"))
        assertEquals(65_120L, LrcParser.parseTime("01:05:12"))
        assertNull(LrcParser.parseTime("01:75.00"))
        assertNull(LrcParser.parseTime("ab:cd"))
    }

    @Test
    fun `multiple time tags repeat the line`() {
        val lines = LrcParser.parse("[00:10.00][01:20.00] chorus")
        assertEquals(listOf(10_000L, 80_000L), lines.map { it.timestampMs })
        assertEquals(listOf("chorus", "chorus"), lines.map { it.text })
    }

    @Test
    fun `enhanced word tags give word timing`() {
        val lines = LrcParser.parse(
            "[00:10.00]<00:10.00>Hello <00:10.40>big <00:10.80>world<00:11.50>\n[00:12.00] next",
        )
        val first = lines.first()
        assertEquals("Hello big world", first.text)
        assertEquals(listOf("Hello ", "big ", "world"), first.words.map { it.text })
        assertEquals(listOf(10_000L, 10_400L, 10_800L), first.words.map { it.startMs })
        assertEquals(listOf(10_400L, 10_800L, 11_500L), first.words.map { it.endMs })
    }

    @Test
    fun `last word without end tag runs to next line`() {
        val lines = LrcParser.parse("[00:10.00]<00:10.00>Hi <00:10.50>there\n[00:13.00] after")
        assertEquals(13_000L, lines.first().words.last().endMs)
    }

    @Test
    fun `last word of final line gets a default end`() {
        val lines = LrcParser.parse("[00:10.00]<00:10.00>only")
        assertEquals(11_000L, lines.single().words.single().endMs)
    }

    @Test
    fun `malformed word timing falls back to line sync`() {
        // Word times go backwards: not trustworthy.
        val lines = LrcParser.parse("[00:10.00]<00:11.00>one <00:10.00>two")
        assertEquals("one two", lines.single().text)
        assertTrue(lines.single().words.isEmpty())
    }

    @Test
    fun `offset header shifts every time`() {
        val lines = LrcParser.parse("[offset:+500]\n[00:10.00]<00:10.00>a <00:10.60>b<00:11.00>")
        val line = lines.single()
        assertEquals(9_500L, line.timestampMs)
        assertEquals(listOf(9_500L, 10_100L), line.words.map { it.startMs })
        assertEquals(listOf(10_100L, 10_500L), line.words.map { it.endMs })
    }

    @Test
    fun `active word and progress`() {
        val line = LrcParser.parse("[00:10.00]<00:10.00>a <00:11.00>b <00:12.00>c<00:13.00>").single()
        assertEquals(-1, activeWordIndex(line, 9_000))
        assertEquals(0, activeWordIndex(line, 10_000))
        assertEquals(1, activeWordIndex(line, 11_999))
        assertEquals(2, activeWordIndex(line, 20_000))
        assertEquals(0.5f, wordProgress(line.words[0], 10_500), 0.001f)
        assertEquals(1f, wordProgress(line.words[2], 14_000), 0.001f)
        assertEquals(0f, wordProgress(line.words[1], 10_000), 0.001f)
        assertEquals(-1, activeWordIndex(LyricLine(0, "x"), 5_000))
    }
}
