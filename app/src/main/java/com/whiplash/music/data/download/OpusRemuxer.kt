// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.download

import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.IOException

/**
 * Moves the Opus audio of a WebM download into an Ogg Opus (`.opus`) file
 * without decoding it: the saved file holds bit-for-bit the same audio
 * packets, with the song's tags written straight into its header, and it
 * takes well under a second. (Android's own Ogg muxer took ~7 s for a song
 * because it flushes every 20 ms packet separately.)
 */
internal object OpusRemuxer {

    fun toOgg(input: File, output: File, title: String, artist: String, album: String?, cover: ByteArray?) {
        val tags = OggOpusTagger.tagsPacket("Whiplash", title, artist, album, cover)
        val serial = input.name.hashCode() xor 0x5EED
        // Fast path: read the WebM directly. Falls back to MediaExtractor
        // (slower, same packets) for any layout the direct reader declines.
        val direct = WebmOpusReader.read(input)
        if (direct != null) {
            val packets = direct.packets.asSequence().map { it to (opusPacketSamples(it) ?: throw IOException("Malformed Opus packet")) }
            write(output, direct.opusHead, tags, serial, packets)
            return
        }
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(input.absolutePath)
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) == MediaFormat.MIMETYPE_AUDIO_OPUS
            } ?: throw IOException("No Opus track in ${input.name}")
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val head = format.getByteBuffer("csd-0")?.let { b -> ByteArray(b.remaining()).also { b.duplicate().get(it) } }
                ?: throw IOException("No OpusHead in ${input.name}")
            if (head.size < 19 || String(head, 0, 8, Charsets.US_ASCII) != "OpusHead") throw IOException("Bad OpusHead")

            val buffer = java.nio.ByteBuffer.allocate(64 * 1024)
            val packets = generateSequence {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size <= 0) {
                    null
                } else {
                    val data = ByteArray(size).also { buffer.get(it, 0, size) }
                    extractor.advance()
                    data to (opusPacketSamples(data) ?: throw IOException("Malformed Opus packet"))
                }
            }
            write(output, head, tags, serial, packets)
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun write(output: File, head: ByteArray, tags: ByteArray, serial: Int, packets: Sequence<Pair<ByteArray, Int>>) {
        java.io.BufferedOutputStream(java.io.FileOutputStream(output), 256 * 1024).use { out ->
            OggOpusTagger.writeStream(out, head, tags, serial, packets)
        }
    }

    /** Samples (at 48 kHz) in one Opus packet, from its TOC byte (RFC 6716 §3.1). */
    internal fun opusPacketSamples(packet: ByteArray): Int? {
        if (packet.isEmpty()) return null
        val toc = packet[0].toInt() and 0xFF
        val config = toc ushr 3
        val frameSamples = when {
            config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3] // SILK 10/20/40/60 ms
            config < 16 -> intArrayOf(480, 960)[config and 1] // Hybrid 10/20 ms
            else -> intArrayOf(120, 240, 480, 960)[config and 3] // CELT 2.5/5/10/20 ms
        }
        val frames = when (toc and 3) {
            0 -> 1
            1, 2 -> 2
            else -> if (packet.size < 2) return null else (packet[1].toInt() and 0x3F)
        }
        if (frames == 0 || frames * frameSamples > 5760) return null // over 120 ms is invalid
        return frames * frameSamples
    }
}
