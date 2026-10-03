// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OggOpusTaggerTest {

    private val crcTable = IntArray(256) { i ->
        var r = i shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
        r
    }
    private fun crc(d: ByteArray): Int { var c = 0; for (x in d) c = (c shl 8) xor crcTable[((c ushr 24) xor (x.toInt() and 0xFF)) and 0xFF]; return c }

    private fun page(type: Int, granule: Long, seq: Int, packets: List<ByteArray>): ByteArray {
        val segs = ArrayList<Int>(); val body = ByteArrayOutputStream()
        packets.forEach { p -> var r = p.size; while (r >= 255) { segs.add(255); r -= 255 }; segs.add(r); body.write(p) }
        val o = ByteArrayOutputStream()
        o.write("OggS".toByteArray()); o.write(0); o.write(type)
        for (i in 0 until 8) o.write(((granule ushr (8 * i)) and 0xFF).toInt())
        fun le(v: Int) { o.write(v and 0xFF); o.write(v ushr 8 and 0xFF); o.write(v ushr 16 and 0xFF); o.write(v ushr 24 and 0xFF) }
        le(1234); le(seq); le(0); o.write(segs.size); segs.forEach { o.write(it) }; o.write(body.toByteArray())
        val b = o.toByteArray(); val c = crc(b); for (i in 0 until 4) b[22 + i] = (c ushr (8 * i) and 0xFF).toByte()
        return b
    }

    private fun tags(vendor: String): ByteArray = ByteArrayOutputStream().apply {
        write("OpusTags".toByteArray()); val v = vendor.toByteArray()
        write(byteArrayOf(v.size.toByte(), 0, 0, 0)); write(v); write(byteArrayOf(0, 0, 0, 0))
    }.toByteArray()

    private val audio1 = ByteArray(300) { (it % 7).toByte() }
    private val audio2 = ByteArray(40) { 5 }

    private fun oggFile() = page(2, 0, 0, listOf("OpusHead".toByteArray() + ByteArray(11))) +
        page(0, 0, 1, listOf(tags("Android"))) +
        page(0, 960, 2, listOf(audio1)) +
        page(4, 1920, 3, listOf(audio2))

    @Test
    fun `tags are written and the audio packets are byte for byte the same`() {
        val out = OggOpusTagger.tag(oggFile(), "Blinding Lights", "The Weeknd", "After Hours", null)
        assertNotNull(out)
        val s = String(out!!, Charsets.ISO_8859_1)
        assertTrue(s.contains("TITLE=Blinding Lights") && s.contains("ARTIST=The Weeknd") && s.contains("ALBUM=After Hours"))
        assertTrue(s.contains("Android")) // vendor kept
        // Retagging the result must parse again (valid CRCs and sequence numbers).
        assertNotNull(OggOpusTagger.tag(out, "X", "Y", null, null))
        // Last two pages hold the audio unchanged.
        val tail = out.copyOfRange(out.size - (27 + 2 + audio1.size + 27 + 1 + audio2.size), out.size)
        assertArrayEquals(audio1, tail.copyOfRange(29, 29 + audio1.size))
        assertArrayEquals(audio2, tail.copyOfRange(tail.size - audio2.size, tail.size))
    }

    @Test
    fun `a large cover spanning several pages still produces a valid file`() {
        val cover = ByteArray(200_000) { (it % 251).toByte() }
        val out = OggOpusTagger.tag(oggFile(), "T", "A", null, cover)
        assertNotNull(out)
        assertTrue(String(out!!, Charsets.ISO_8859_1).contains("METADATA_BLOCK_PICTURE="))
        assertNotNull(OggOpusTagger.tag(out, "T2", "A2", null, null))
        assertEquals(true, out.size > 200_000)
    }

    @Test
    fun `a damaged file is left alone`() {
        val bad = oggFile().also { it[40] = (it[40] + 1).toByte() } // breaks a page checksum
        assertNull(OggOpusTagger.tag(bad, "T", "A", null, null))
        assertNull(OggOpusTagger.tag(ByteArray(10), "T", "A", null, null))
        assertNull(OggOpusTagger.tag("not an ogg file at all, just text".toByteArray(), "T", "A", null, null))
    }
}
