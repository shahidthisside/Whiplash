package com.whiplash.music.data.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * Covers [Mp4Tagger] against real, hand-constructed MP4 box structures —
 * not just "doesn't throw." Every assertion here parses the *output*'s own
 * box structure back apart (rather than merely checking the tag strings
 * appear as a raw substring somewhere in the byte array) to catch a
 * tagger that produces bytes an actual player would reject as malformed
 * (wrong box size, `mdat` corrupted, `moov` box size not updated).
 */
class Mp4TaggerTest {

    private fun u32(value: Long): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    private fun box(type: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(u32(content.size + 8L))
        out.write(type.toByteArray(Charsets.US_ASCII))
        out.write(content)
        return out.toByteArray()
    }

    /** A minimal but structurally real MP4: ftyp + moov(mvhd-stub) + mdat(fake audio bytes). */
    private fun fakeMp4(mdatContent: ByteArray = byteArrayOf(1, 2, 3, 4, 5), moovExtra: ByteArray = ByteArray(0)): ByteArray {
        val ftyp = box("ftyp", "isom".toByteArray(Charsets.US_ASCII) + u32(0) + "isomiso2mp41".toByteArray(Charsets.US_ASCII))
        val mvhd = box("mvhd", ByteArray(100)) // stub, content shape doesn't matter for these tests
        val moov = box("moov", mvhd + moovExtra)
        val mdat = box("mdat", mdatContent)
        return ftyp + moov + mdat
    }

    /** Parses top-level boxes of an arbitrary MP4 byte array the same way a real reader would, for assertions. */
    private data class ParsedBox(val type: String, val content: ByteArray)

    private fun parseTopLevel(bytes: ByteArray): List<ParsedBox> {
        val result = mutableListOf<ParsedBox>()
        var pos = 0
        while (pos < bytes.size) {
            val size = ((bytes[pos].toLong() and 0xFF) shl 24) or
                ((bytes[pos + 1].toLong() and 0xFF) shl 16) or
                ((bytes[pos + 2].toLong() and 0xFF) shl 8) or
                (bytes[pos + 3].toLong() and 0xFF)
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            val content = bytes.copyOfRange(pos + 8, (pos + size).toInt())
            result.add(ParsedBox(type, content))
            pos += size.toInt()
        }
        return result
    }

    @Test
    fun `tagging a real MP4 preserves ftyp and mdat exactly, byte for byte`() {
        val mdatContent = byteArrayOf(10, 20, 30, 40, 50, 60)
        val original = fakeMp4(mdatContent = mdatContent)
        val tagged = Mp4Tagger.tag(original, title = "Song", artist = "Artist", album = "Album", cover = null)

        val boxes = parseTopLevel(tagged)
        assertEquals(listOf("ftyp", "moov", "mdat"), boxes.map { it.type })
        assertArrayEquals(mdatContent, boxes.first { it.type == "mdat" }.content)
    }

    @Test
    fun `tagged moov contains a parseable udta meta ilst with the right title and artist`() {
        val original = fakeMp4()
        val tagged = Mp4Tagger.tag(original, title = "My Title", artist = "My Artist", album = null, cover = null)

        val moovContent = parseTopLevel(tagged).first { it.type == "moov" }.content
        val moovChildren = parseTopLevel(moovContent) // moov's own children are boxes too, same shape
        val udta = moovChildren.firstOrNull { it.type == "udta" }
        assertTrue("expected a udta box inside moov", udta != null)

        val metaBox = parseTopLevel(udta!!.content).first { it.type == "meta" }
        val metaChildren = parseTopLevel(metaBox.content.copyOfRange(4, metaBox.content.size)) // skip meta's 4-byte version/flags
        val ilst = metaChildren.first { it.type == "ilst" }
        val ilstTags = parseTopLevel(ilst.content)

        val nameTag = ilstTags.first { it.type == "\u00A9nam" }
        val nameData = parseTopLevel(nameTag.content).first { it.type == "data" }
        val nameText = String(nameData.content, 8, nameData.content.size - 8, Charsets.UTF_8)
        assertEquals("My Title", nameText)

        val artistTag = ilstTags.first { it.type == "\u00A9ART" }
        val artistData = parseTopLevel(artistTag.content).first { it.type == "data" }
        val artistText = String(artistData.content, 8, artistData.content.size - 8, Charsets.UTF_8)
        assertEquals("My Artist", artistText)
    }

    @Test
    fun `tagging with cover art embeds the exact cover bytes`() {
        val cover = byteArrayOf(-1, -40, -1, -32, 1, 2, 3) // fake JPEG-ish bytes, content doesn't need to be a real image for this test
        val tagged = Mp4Tagger.tag(fakeMp4(), title = "T", artist = "A", album = "Al", cover = cover)

        val moovContent = parseTopLevel(tagged).first { it.type == "moov" }.content
        val udta = parseTopLevel(moovContent).first { it.type == "udta" }
        val metaBox = parseTopLevel(udta.content).first { it.type == "meta" }
        val meta = metaBox.content.copyOfRange(4, metaBox.content.size)
        val ilst = parseTopLevel(meta).first { it.type == "ilst" }
        val covrTag = parseTopLevel(ilst.content).first { it.type == "covr" }
        val covrData = parseTopLevel(covrTag.content).first { it.type == "data" }
        val extractedCover = covrData.content.copyOfRange(8, covrData.content.size)
        assertArrayEquals(cover, extractedCover)
    }

    @Test
    fun `re-tagging a file that already has a udta replaces it rather than duplicating ilst`() {
        val firstPass = Mp4Tagger.tag(fakeMp4(), title = "Old Title", artist = "Old Artist", album = null, cover = null)
        val secondPass = Mp4Tagger.tag(firstPass, title = "New Title", artist = "New Artist", album = null, cover = null)

        val moovContent = parseTopLevel(secondPass).first { it.type == "moov" }.content
        val moovChildren = parseTopLevel(moovContent)
        val udtaCount = moovChildren.count { it.type == "udta" }
        assertEquals("expected exactly one udta after re-tagging, not a duplicate", 1, udtaCount)

        val udta = moovChildren.first { it.type == "udta" }
        val metaBox = parseTopLevel(udta.content).first { it.type == "meta" }
        val meta = metaBox.content.copyOfRange(4, metaBox.content.size)
        val ilst = parseTopLevel(meta).first { it.type == "ilst" }
        val nameTag = parseTopLevel(ilst.content).first { it.type == "\u00A9nam" }
        val nameData = parseTopLevel(nameTag.content).first { it.type == "data" }
        val nameText = String(nameData.content, 8, nameData.content.size - 8, Charsets.UTF_8)
        assertEquals("New Title", nameText)
    }

    @Test
    fun `a file with no ftyp box is returned completely unchanged`() {
        val notMp4 = "this is definitely not an mp4 file, just plain bytes".toByteArray(Charsets.UTF_8)
        val result = Mp4Tagger.tag(notMp4, title = "T", artist = "A", album = null, cover = null)
        assertArrayEquals(notMp4, result)
    }

    @Test
    fun `a truncated malformed box is returned completely unchanged rather than throwing or corrupting`() {
        // A box that declares a size larger than the remaining bytes (malformed) — should be detected and left alone.
        val ftyp = box("ftyp", "isom".toByteArray(Charsets.US_ASCII))
        val malformedMoov = u32(999_999L) + "moov".toByteArray(Charsets.US_ASCII) + byteArrayOf(1, 2, 3) // declares a huge size it doesn't have
        val truncated = ftyp + malformedMoov

        val result = Mp4Tagger.tag(truncated, title = "T", artist = "A", album = null, cover = null)
        assertArrayEquals(truncated, result)
    }

    @Test
    fun `empty artist does not crash and still produces a valid tagged file`() {
        val tagged = Mp4Tagger.tag(fakeMp4(), title = "Title Only", artist = "", album = null, cover = null)
        val boxes = parseTopLevel(tagged)
        assertEquals(listOf("ftyp", "moov", "mdat"), boxes.map { it.type })
    }
}
