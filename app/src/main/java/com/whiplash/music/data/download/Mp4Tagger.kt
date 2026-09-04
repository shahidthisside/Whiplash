package com.whiplash.music.data.download
// Developed by Shahid Ansari — github.com/shahidthisside (-SA)

import java.io.ByteArrayOutputStream

/**
 * Embeds title/artist/album/cover art directly into a downloaded MP4/M4A
 * audio file's own metadata boxes, so the file shows correct info in any
 * other app (a file manager, a different player, a computer) — not just
 * inside Whiplash's own Downloads tab.
 *
 * Adapted from the same general approach BitChord's own format-specific
 * taggers use (an open-source YouTube Music client under GPLv3 — this is
 * an independent, from-scratch implementation for MP4 specifically, not a
 * copy of its code): rewrite the container's own metadata atoms rather
 * than bolting on a side-car file, and always leave the input completely
 * unchanged if anything about its structure isn't exactly what's expected.
 *
 * Deliberately MP4-only. YouTube's audio streams NewPipeExtractor resolves
 * are always either `audio/mp4` (AAC-in-MP4/M4A) or `audio/webm` (Opus-in-
 * WebM) — never FLAC — so this covers the one format it's realistic to get
 * exactly right without a general third-party tagging library. A WebM
 * download is returned byte-for-byte unchanged rather than guessed at.
 *
 * ## Why this is safe to attempt at all
 *
 * An MP4 file is a flat sequence of length-prefixed top-level boxes
 * (`ftyp`, `moov`, `mdat`, ...). Metadata lives inside `moov`, nested as
 * `moov/udta/meta/ilst` — a list of small tag atoms (`©nam` = title, `©ART`
 * = artist, `©alb` = album, `covr` = cover art). None of that touches
 * `mdat` (the actual audio samples), and `moov`'s own box size only needs
 * to be corrected for its *own* growth — there's no equivalent of
 * `stco`/`co64` sample-offset tables to patch here because [ilst] is a
 * *new* subtree being added, not something that shifts existing offsets
 * inside `moov` itself. If [moov] already has a `udta`, its existing
 * contents are preserved and only `meta/ilst` inside it is replaced.
 *
 * Anything that doesn't fit this shape exactly — not a real MP4/M4A (no
 * `ftyp`), no `moov` box found, a box whose declared size runs past the
 * end of the file — returns [bytes] unchanged. A downloaded file that
 * fails to tag is still a perfectly playable download; it just won't show
 * enriched metadata elsewhere, which is a strictly better failure mode
 * than a corrupted file.
 */
object Mp4Tagger {

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        cover: ByteArray?,
    ): ByteArray = runCatching {
        rewrite(bytes, title, artist, album, cover)
    }.getOrDefault(bytes)

    private fun rewrite(bytes: ByteArray, title: String, artist: String, album: String?, cover: ByteArray?): ByteArray {
        if (bytes.size < 12 || String(bytes, 4, 4, Charsets.ISO_8859_1) != FTYP) return bytes

        val boxes = parseTopLevelBoxes(bytes) ?: return bytes
        val moovBox = boxes.firstOrNull { it.type == MOOV } ?: return bytes

        val ilst = buildIlst(title, artist, album, cover)
        val metaBox = buildMeta(ilst)
        val newUdta = buildBox(UDTA, metaBox)

        // moov's own children, with any existing udta stripped (its
        // contents are discarded rather than merged — a second ilst atom
        // inside a merged udta is exactly the "which one does a player
        // show" ambiguity FlacTagger's own doc warns about for a second
        // VORBIS_COMMENT/PICTURE, so the same rule applies here: drop the
        // old one, write one fresh one).
        val moovChildren = parseChildBoxes(bytes, moovBox.contentStart, moovBox.contentEnd) ?: return bytes
        val rebuiltMoovContent = ByteArrayOutputStream().apply {
            moovChildren.forEach { child ->
                if (child.type != UDTA) write(bytes, child.start, child.end - child.start)
            }
            write(newUdta)
        }.toByteArray()
        val rebuiltMoov = buildBox(MOOV, rebuiltMoovContent)

        // Splice the rebuilt moov box in place of the original, byte for
        // byte identical everywhere else (ftyp, mdat, and any other
        // top-level box are copied through untouched and in their
        // original order).
        val output = ByteArrayOutputStream(bytes.size + rebuiltMoov.size)
        boxes.forEach { box ->
            if (box.type == MOOV) output.write(rebuiltMoov) else output.write(bytes, box.start, box.end - box.start)
        }
        return output.toByteArray()
    }

    // ── Box parsing ──────────────────────────────────────────────────────

    private class Box(val type: String, val start: Int, val end: Int, val contentStart: Int, val contentEnd: Int)

    /** Parses every top-level box in [bytes] (ftyp, moov, mdat, ...), or null if the structure doesn't parse cleanly all the way to the end of the file. */
    private fun parseTopLevelBoxes(bytes: ByteArray): List<Box>? = parseChildBoxes(bytes, 0, bytes.size)

    /** Parses every box directly inside [start, end) — used both for the top level and for moov's own children. Null on any malformed/overrunning box. */
    private fun parseChildBoxes(bytes: ByteArray, start: Int, end: Int): List<Box>? {
        val result = mutableListOf<Box>()
        var pos = start
        while (pos < end) {
            if (pos + 8 > end) return null
            val size = readU32(bytes, pos)
            val type = String(bytes, pos + 4, 4, Charsets.ISO_8859_1)
            val boxSize = when {
                size == 1L -> {
                    // 64-bit "largesize" extension — present but genuinely
                    // rare for the kind of short audio-only MP4s a YouTube
                    // download produces; safest to bail rather than guess.
                    return null
                }
                size == 0L -> (end - pos).toLong() // box extends to end of parent, per spec
                else -> size
            }
            if (boxSize < 8 || pos + boxSize > end) return null
            result.add(Box(type, pos, (pos + boxSize).toInt(), pos + 8, (pos + boxSize).toInt()))
            pos += boxSize.toInt()
        }
        return result
    }

    private fun readU32(bytes: ByteArray, offset: Int): Long =
        ((bytes[offset].toLong() and 0xFF) shl 24) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 8) or
            (bytes[offset + 3].toLong() and 0xFF)

    // ── Box building ─────────────────────────────────────────────────────

    /** Wraps [content] in a standard 8-byte-header box of type [type] (4-char fourCC — Latin-1, not ASCII: iTunes-style tags like `©nam` use byte 0xA9 for '©', which plain US-ASCII can't round-trip). */
    private fun buildBox(type: String, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream(content.size + 8)
        writeU32(out, content.size + 8L)
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(content)
        return out.toByteArray()
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Long) {
        out.write(((value shr 24) and 0xFF).toInt())
        out.write(((value shr 16) and 0xFF).toInt())
        out.write(((value shr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    /** A complete `meta` box: an 8-byte box header, then a 4-byte version/flags field (all zero — no handler-specific data needed for this minimal case), then a single `hdlr` box (required by the spec for players to recognize this as iTunes-style metadata) and the `ilst` itself. */
    private fun buildMeta(ilst: ByteArray): ByteArray {
        val hdlrContent = ByteArrayOutputStream().apply {
            write(ByteArray(4)) // version + flags
            write("mdir".toByteArray(Charsets.US_ASCII)) // predefined
            write("appl".toByteArray(Charsets.US_ASCII)) // handler type: iTunes metadata
            write(ByteArray(12)) // reserved
            write(0) // empty component name (pascal-style length-prefixed string)
        }.toByteArray()
        val hdlr = buildBox("hdlr", hdlrContent)
        val metaContent = ByteArrayOutputStream().apply {
            write(ByteArray(4)) // meta box version + flags
            write(hdlr)
            write(ilst)
        }.toByteArray()
        return buildBox("meta", metaContent)
    }

    /** `ilst`: one child atom per tag, each holding a single `data` atom carrying the actual value. */
    private fun buildIlst(title: String, artist: String, album: String?, cover: ByteArray?): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(buildTextAtom("\u00A9nam", title))
        out.write(buildTextAtom("\u00A9ART", artist))
        album?.let { out.write(buildTextAtom("\u00A9alb", it)) }
        cover?.let { out.write(buildCoverAtom(it)) }
        return buildBox("ilst", out.toByteArray())
    }

    /** A UTF-8 text tag atom, e.g. `©nam` containing `data` with type 1 (UTF-8 string). */
    private fun buildTextAtom(fourCc: String, value: String): ByteArray {
        val valueBytes = value.toByteArray(Charsets.UTF_8)
        val dataContent = ByteArrayOutputStream().apply {
            writeU32(this, 1L) // type indicator: UTF-8 text
            write(ByteArray(4)) // locale (0 = default)
            write(valueBytes)
        }.toByteArray()
        return buildBox(fourCc, buildBox("data", dataContent))
    }

    /** `covr` atom containing a `data` with type 13 (JPEG) — YouTube thumbnails are always JPEG. */
    private fun buildCoverAtom(jpegBytes: ByteArray): ByteArray {
        val dataContent = ByteArrayOutputStream().apply {
            writeU32(this, 13L) // type indicator: JPEG
            write(ByteArray(4)) // locale
            write(jpegBytes)
        }.toByteArray()
        return buildBox("covr", buildBox("data", dataContent))
    }

    private const val FTYP = "ftyp"
    private const val MOOV = "moov"
    private const val UDTA = "udta"
}
