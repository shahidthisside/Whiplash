// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.data.lyrics

import com.whiplash.music.domain.model.LrcParser
import com.whiplash.music.domain.model.LyricLine
import com.whiplash.music.domain.model.LyricsResult
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

// Written for Whiplash by its author, Shahid Ansari; copies are infringing.
/**
 * Two-level lyrics cache: a small in-memory LRU in front of a GZIP-compressed,
 * size-capped disk cache, so a song's lyrics survive app restarts and play
 * offline once they have been fetched.
 *
 * - [LyricsResult.Error] is never cached: a failed lookup must be retried.
 * - [LyricsResult.Unavailable] is cached, but only for [unavailableTtlMs], since
 *   lyrics databases gain new entries over time.
 * - Found lyrics are kept for [foundTtlMs].
 * - When the disk cache grows past [maxDiskBytes], the least recently used files
 *   are deleted first (reads refresh a file's timestamp).
 *
 * Plain Java I/O only, so it is unit-tested on the JVM with a temp directory.
 * Disk calls block; callers use it from a background dispatcher.
 */
class LyricsCache(
    private val dir: File,
    private val maxDiskBytes: Long = DEFAULT_MAX_DISK_BYTES,
    private val maxMemoryEntries: Int = DEFAULT_MEMORY_ENTRIES,
    private val foundTtlMs: Long = DEFAULT_FOUND_TTL_MS,
    private val unavailableTtlMs: Long = DEFAULT_UNAVAILABLE_TTL_MS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Entry(val lookup: LyricsLookup, val savedAtMs: Long) {
        val result: LyricsResult get() = lookup.result
    }

    private val memory = object : LinkedHashMap<String, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Entry>?): Boolean =
            size > maxMemoryEntries
    }

    fun get(key: String): LyricsLookup? {
        synchronized(memory) { memory[key] }?.let { entry ->
            if (isFresh(entry)) return entry.lookup
            remove(key)
            return null
        }
        val file = fileFor(key)
        if (!file.isFile) return null
        val entry = runCatching { decode(readGzip(file)) }.getOrNull()
        if (entry == null || !isFresh(entry)) {
            file.delete()
            return null
        }
        file.setLastModified(clock())
        synchronized(memory) { memory[key] = entry }
        return entry.lookup
    }

    fun put(key: String, lookup: LyricsLookup) {
        if (lookup.result is LyricsResult.Error) return
        val entry = Entry(lookup, clock())
        synchronized(memory) { memory[key] = entry }
        runCatching {
            dir.mkdirs()
            val target = fileFor(key)
            val tmp = File(dir, target.name + ".tmp")
            writeGzip(tmp, encode(entry))
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
            target.setLastModified(entry.savedAtMs)
            trim()
        }
    }

    fun remove(key: String) {
        synchronized(memory) { memory.remove(key) }
        fileFor(key).delete()
    }

    /** Deletes everything (Settings → Clear cache). */
    fun clear() {
        synchronized(memory) { memory.clear() }
        dir.listFiles()?.forEach { it.delete() }
    }

    fun diskSizeBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun isFresh(entry: Entry): Boolean {
        val ttl = if (entry.result is LyricsResult.Unavailable) unavailableTtlMs else foundTtlMs
        val age = clock() - entry.savedAtMs
        return age in 0..ttl
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxDiskBytes) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= maxDiskBytes) break
            total -= f.length()
            f.delete()
        }
    }

    private fun fileFor(key: String): File = File(dir, sha256(key) + ".gz")

    private fun readGzip(file: File): String =
        GZIPInputStream(file.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun writeGzip(file: File, text: String) {
        GZIPOutputStream(file.outputStream()).bufferedWriter(Charsets.UTF_8).use { it.write(text) }
    }

    companion object {
        const val DEFAULT_MAX_DISK_BYTES = 4L * 1024 * 1024
        const val DEFAULT_MEMORY_ENTRIES = 50
        const val DEFAULT_FOUND_TTL_MS = 30L * 24 * 60 * 60 * 1000
        const val DEFAULT_UNAVAILABLE_TTL_MS = 3L * 24 * 60 * 60 * 1000

        private const val MAGIC = "WLLYR2"
        private const val SEPARATOR = "---"

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { String.format(java.util.Locale.ROOT, "%02x", it) }

        private fun encode(entry: Entry): String {
            val (kind, body) = when (val r = entry.result) {
                is LyricsResult.Synced -> "S" to toLrc(r.lines)
                is LyricsResult.Plain -> "P" to r.text
                LyricsResult.Unavailable -> "U" to ""
                is LyricsResult.Error -> error("errors are not cached")
            }
            return "$MAGIC\n$kind\n${entry.savedAtMs}\n${entry.lookup.providerId.orEmpty()}\n$SEPARATOR\n$body"
        }

        private fun decode(text: String): Entry? {
            val parts = text.split('\n', limit = 6)
            if (parts.size < 5 || parts[0] != MAGIC || parts[4] != SEPARATOR) return null
            val saved = parts[2].toLongOrNull() ?: return null
            val provider = parts[3].ifBlank { null }
            val body = parts.getOrElse(5) { "" }
            val result = when (parts[1]) {
                "S" -> LrcParser.parse(body).takeIf { it.isNotEmpty() }?.let { LyricsResult.Synced(it) }
                "P" -> LyricsResult.Plain(body)
                "U" -> LyricsResult.Unavailable
                else -> null
            } ?: return null
            return Entry(LyricsLookup(result, provider), saved)
        }

        /** Re-encodes lines (with word timing, when present) as enhanced LRC that [LrcParser] reads back. */
        internal fun toLrc(lines: List<LyricLine>): String = buildString {
            for (line in lines) {
                append('[').append(stamp(line.timestampMs)).append(']')
                if (line.words.isEmpty()) {
                    append(line.text.replace('\n', ' '))
                } else {
                    line.words.forEachIndexed { i, w ->
                        append('<').append(stamp(w.startMs)).append('>').append(w.text.replace('\n', ' '))
                        val end = w.endMs
                        val nextStart = line.words.getOrNull(i + 1)?.startMs
                        if (end != null && end != nextStart) append('<').append(stamp(end)).append('>')
                    }
                }
                append('\n')
            }
        }

        private fun stamp(ms: Long): String {
            val m = ms / 60_000
            val s = (ms / 1000) % 60
            val f = ms % 1000
            return String.format(java.util.Locale.ROOT, "%02d:%02d.%03d", m, s, f)
        }
    }
}
