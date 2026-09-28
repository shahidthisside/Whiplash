package com.whiplash.music.data.download

import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Writes title / artist / album (and the cover, when given) into an Ogg Opus
 * file's OpusTags header, so other players show the song properly.
 *
 * An Ogg file is a run of pages; the second logical packet (OpusTags) holds
 * the tags. This rebuilds just that packet's pages, then renumbers and
 * re-checksums the pages after it. The audio packets themselves are copied
 * byte for byte. If the file isn't laid out exactly as expected, it's left
 * untouched (it still plays; it just shows the file name instead of tags).
 */
internal object OggOpusTagger {

    fun tagInPlace(file: File, title: String, artist: String, album: String?, cover: ByteArray? = null) {
        val original = file.readBytes()
        val tagged = runCatching { tag(original, title, artist, album, cover) }.getOrNull() ?: return
        val temp = File(file.parentFile, "${file.name}.tag")
        try {
            temp.writeBytes(tagged)
            if (!temp.renameTo(file)) temp.delete()
        } catch (t: Throwable) {
            temp.delete()
        }
    }

    private class Page(val headerType: Int, val granule: Long, val serial: Int, val segments: IntArray, val body: ByteArray)

    /** Returns the retagged bytes, or null to leave the file as it is. */
    internal fun tag(bytes: ByteArray, title: String, artist: String, album: String?, cover: ByteArray?): ByteArray? {
        val pages = parsePages(bytes) ?: return null
        if (pages.size < 3) return null
        if (!startsWith(pages[0].body, "OpusHead")) return null
        if (!startsWith(pages[1].body, "OpusTags")) return null

        // OpusTags runs from page 1 until the page whose last lacing value is < 255.
        var tagsEnd = 1
        while (pages[tagsEnd].segments.isNotEmpty() && pages[tagsEnd].segments.last() == 255) {
            tagsEnd++
            if (tagsEnd >= pages.size) return null
        }
        // Must hold nothing but OpusTags (audio always starts on a fresh page).
        for (i in 1..tagsEnd) if (pages[i].segments.count { it < 255 } > (if (i == tagsEnd) 1 else 0)) return null
        val serial = pages[0].serial

        val vendor = readVendor(pages.subList(1, tagsEnd + 1)) ?: "Whiplash"
        val packet = tagsPacket(vendor, title, artist, album, cover)

        val newTagPages = packetToPages(packet, serial)
        val rebuilt = ArrayList<Page>(pages.size)
        rebuilt.add(pages[0])
        rebuilt.addAll(newTagPages)
        for (i in tagsEnd + 1 until pages.size) rebuilt.add(pages[i])

        val out = ByteArrayOutputStream(bytes.size + packet.size)
        rebuilt.forEachIndexed { seq, p -> out.write(serializePage(p, seq)) }
        return out.toByteArray()
    }

    /** An OpusTags header packet holding the given tags. */
    internal fun tagsPacket(vendor: String, title: String, artist: String, album: String?, cover: ByteArray?): ByteArray {
        val comments = buildList {
            if (title.isNotBlank()) add("TITLE=$title")
            if (artist.isNotBlank()) add("ARTIST=$artist")
            if (!album.isNullOrBlank()) add("ALBUM=$album")
            if (cover != null && cover.isNotEmpty()) add("METADATA_BLOCK_PICTURE=" + pictureBlockBase64(cover))
        }
        return ByteArrayOutputStream().apply {
            write("OpusTags".toByteArray(Charsets.US_ASCII))
            val v = vendor.toByteArray(Charsets.UTF_8)
            writeLe32(v.size); write(v)
            writeLe32(comments.size)
            comments.forEach { c -> val b = c.toByteArray(Charsets.UTF_8); writeLe32(b.size); write(b) }
        }.toByteArray()
    }

    /**
     * Writes a complete Ogg Opus stream: the OpusHead and OpusTags headers,
     * then the Opus [packets] exactly as given, several per page. Each
     * packet is (bytes, duration in 48 kHz samples); page granule positions
     * count samples from the start including the head's pre-skip, as the
     * Ogg Opus spec requires.
     */
    internal fun writeStream(
        out: java.io.OutputStream,
        opusHead: ByteArray,
        tags: ByteArray,
        serial: Int,
        packets: Sequence<Pair<ByteArray, Int>>,
    ) {
        var seq = 0
        out.write(serializePage(Page(0x02, 0L, serial, lacingFor(opusHead.size), opusHead), seq++))
        packetToPages(tags, serial).forEach { out.write(serializePage(it, seq++)) }

        val body = ByteArrayOutputStream(MAX_PAGE_BODY + 2048)
        val segs = ArrayList<Int>(255)
        var granule = 0L
        var pending: Pair<ByteArray, Int>? = null
        fun flush(last: Boolean) {
            if (segs.isEmpty()) return
            out.write(serializePage(Page(if (last) 0x04 else 0, granule, serial, segs.toIntArray(), body.toByteArray()), seq++))
            body.reset(); segs.clear()
        }
        val it = packets.iterator()
        while (it.hasNext()) {
            val (data, samples) = it.next()
            val lacing = lacingFor(data.size)
            if (segs.size + lacing.size > 255 || body.size() + data.size > MAX_PAGE_BODY) flush(last = false)
            lacing.forEach { segs.add(it) }
            body.write(data)
            granule += samples
            pending = data to samples
        }
        if (pending == null) throw java.io.IOException("No audio packets")
        flush(last = true)
    }

    private fun lacingFor(size: Int): IntArray {
        val n = size / 255 + 1
        return IntArray(n) { if (it < n - 1) 255 else size % 255 }
    }

    private const val MAX_PAGE_BODY = 16 * 1024

    private fun readVendor(tagPages: List<Page>): String? {
        val data = ByteArrayOutputStream().apply { tagPages.forEach { write(it.body) } }.toByteArray()
        if (data.size < 12) return null
        val len = le32(data, 8)
        if (len < 0 || 12 + len > data.size) return null
        return String(data, 12, len, Charsets.UTF_8)
    }

    /** Splits [packet] into pages of up to 255 segments of 255 bytes (granule 0, as header pages require). */
    private fun packetToPages(packet: ByteArray, serial: Int): List<Page> {
        val lacing = ArrayList<Int>()
        var remaining = packet.size
        while (remaining >= 255) { lacing.add(255); remaining -= 255 }
        lacing.add(remaining) // terminating value (< 255, possibly 0)
        val pages = ArrayList<Page>()
        var seg = 0
        var offset = 0
        var first = true
        while (seg < lacing.size) {
            val count = minOf(255, lacing.size - seg)
            val segs = IntArray(count) { lacing[seg + it] }
            val size = segs.sum()
            val body = packet.copyOfRange(offset, offset + size)
            pages.add(Page(if (first) 0 else 0x01, 0L, serial, segs, body))
            first = false
            seg += count
            offset += size
        }
        return pages
    }

    private fun parsePages(bytes: ByteArray): List<Page>? {
        val pages = ArrayList<Page>()
        var pos = 0
        while (pos < bytes.size) {
            if (pos + 27 > bytes.size) return null
            if (bytes[pos] != 'O'.code.toByte() || bytes[pos + 1] != 'g'.code.toByte() ||
                bytes[pos + 2] != 'g'.code.toByte() || bytes[pos + 3] != 'S'.code.toByte()
            ) return null
            val headerType = bytes[pos + 5].toInt() and 0xFF
            val granule = le64(bytes, pos + 6)
            val serial = le32(bytes, pos + 14)
            val nSeg = bytes[pos + 26].toInt() and 0xFF
            if (pos + 27 + nSeg > bytes.size) return null
            val segs = IntArray(nSeg) { bytes[pos + 27 + it].toInt() and 0xFF }
            val bodyStart = pos + 27 + nSeg
            val bodyEnd = bodyStart + segs.sum()
            if (bodyEnd > bytes.size) return null
            // Reject a damaged file rather than rewriting it.
            val stored = le32(bytes, pos + 22)
            val check = bytes.copyOfRange(pos, bodyEnd).also { for (i in 22..25) it[i] = 0 }
            if (crc(check) != stored) return null
            pages.add(Page(headerType, granule, serial, segs, bytes.copyOfRange(bodyStart, bodyEnd)))
            pos = bodyEnd
        }
        return pages
    }

    private fun serializePage(p: Page, sequence: Int): ByteArray {
        val out = ByteArrayOutputStream(27 + p.segments.size + p.body.size)
        out.write("OggS".toByteArray(Charsets.US_ASCII))
        out.write(0)
        out.write(p.headerType)
        for (i in 0 until 8) out.write(((p.granule ushr (8 * i)) and 0xFF).toInt())
        out.writeLe32(p.serial)
        out.writeLe32(sequence)
        out.writeLe32(0) // checksum placeholder
        out.write(p.segments.size)
        p.segments.forEach { out.write(it) }
        out.write(p.body)
        val page = out.toByteArray()
        val c = crc(page)
        for (i in 0 until 4) page[22 + i] = ((c ushr (8 * i)) and 0xFF).toByte()
        return page
    }

    /** FLAC-style picture block (front cover, JPEG), base64 as Ogg comments carry it. */
    private fun pictureBlockBase64(image: ByteArray): String {
        val mime = if (image.size > 3 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte()) "image/png" else "image/jpeg"
        val b = ByteArrayOutputStream()
        fun be32(v: Int) { b.write(v ushr 24 and 0xFF); b.write(v ushr 16 and 0xFF); b.write(v ushr 8 and 0xFF); b.write(v and 0xFF) }
        be32(3) // front cover
        val m = mime.toByteArray(Charsets.US_ASCII); be32(m.size); b.write(m)
        be32(0) // no description
        be32(0); be32(0); be32(0); be32(0) // width, height, depth, colours: unknown
        be32(image.size); b.write(image)
        return java.util.Base64.getEncoder().encodeToString(b.toByteArray())
    }

    private fun startsWith(body: ByteArray, magic: String): Boolean {
        val m = magic.toByteArray(Charsets.US_ASCII)
        return body.size >= m.size && m.indices.all { body[it] == m[it] }
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun le64(b: ByteArray, o: Int): Long =
        (le32(b, o).toLong() and 0xFFFFFFFFL) or (le32(b, o + 4).toLong() shl 32)

    private fun ByteArrayOutputStream.writeLe32(v: Int) {
        write(v and 0xFF); write(v ushr 8 and 0xFF); write(v ushr 16 and 0xFF); write(v ushr 24 and 0xFF)
    }

    private val CRC_TABLE = IntArray(256) { i ->
        var r = i shl 24
        repeat(8) { r = if (r and 0x80000000.toInt() != 0) (r shl 1) xor 0x04C11DB7 else r shl 1 }
        r
    }

    /** Ogg's CRC-32 (polynomial 0x04C11DB7, no reflection, initial 0). */
    private fun crc(data: ByteArray): Int {
        var c = 0
        for (x in data) c = (c shl 8) xor CRC_TABLE[((c ushr 24) xor (x.toInt() and 0xFF)) and 0xFF]
        return c
    }
}
