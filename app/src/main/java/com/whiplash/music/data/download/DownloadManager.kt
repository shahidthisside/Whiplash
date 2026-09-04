package com.whiplash.music.data.download

import android.content.Context
import android.util.Log
import com.whiplash.music.data.local.dao.DownloadDao
import com.whiplash.music.data.local.entity.DownloadEntity
import com.whiplash.music.data.local.entity.DownloadStatus
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.playback.provider.FallbackResult
import com.whiplash.music.playback.provider.PlaybackManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream

/** In-flight progress for a single track currently being downloaded. */
data class DownloadProgress(
    val trackId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val failed: Boolean = false,
) {
    val fraction: Float get() = if (totalBytes > 0) (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads a [PlayableItem.YoutubeTrack]'s audio for offline playback
 * (YouTube-Music-style "Downloads"), matching the same real-file-on-disk
 * model as [com.whiplash.music.playback.cache.AudioCacheManager] but
 * permanent, user-visible, and never evicted automatically — the actual
 * distinction between a cache and a download (see that class's own doc).
 *
 * Reuses [PlaybackManager.resolveStream] (the same provider-fallback
 * stream resolution playback itself uses) to obtain a real, currently
 * valid audio URL, then streams the bytes to a file in app-private
 * storage (`context.filesDir/downloads/`) via a plain [OkHttpClient] GET
 * — no new extraction or networking mechanism, reusing exactly what
 * already exists elsewhere in the app.
 *
 * Progress for in-flight downloads is exposed via [progress] (a
 * StateFlow keyed by track id) so the UI can show a real progress
 * indicator; completed/failed downloads are persisted to Room via
 * [downloadDao] so they survive process death and appear in the
 * Downloads tab immediately on next launch.
 */
class DownloadManager(
    context: Context,
    private val playbackManager: PlaybackManager,
    private val downloadDao: DownloadDao,
    private val okHttpClient: OkHttpClient,
    private val settingsRepository: com.whiplash.music.data.repository.SettingsRepository? = null,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Job + the track it's downloading, so a cancel can locate the exact partial file to delete without re-deriving it. */
    private data class ActiveDownload(val job: Job, val track: PlayableItem.YoutubeTrack)
    // Real, reported race condition (UAT audit finding): this map is
    // written from two different threads with no synchronization —
    // startDownload()/cancelDownload() are called directly from Compose
    // UI callbacks (Main thread), while job.invokeOnCompletion's cleanup
    // lambda runs on whichever thread the coroutine actually completes
    // on, which is an IO-dispatcher thread here (scope uses
    // Dispatchers.IO). A plain mutableMapOf (backed by a regular
    // HashMap) has no thread-safety guarantee under concurrent
    // read/write from two threads — this could corrupt internal HashMap
    // state, lose an update, or (rarely) infinite-loop during a resize.
    // ConcurrentHashMap makes every individual operation
    // (get/put/remove) atomic and safe across threads with no behavior
    // change to any existing call site (all of them are simple, single-
    // key get/put/remove — no external synchronization was ever relied
    // on for multi-step invariants).
    private val activeDownloads = java.util.concurrent.ConcurrentHashMap<String, ActiveDownload>()

    private val _progress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val progress: StateFlow<Map<String, DownloadProgress>> = _progress

    /** Bounds how many downloads actually transfer bytes at once — see [runDownload]. */
    private val transferSemaphore = Semaphore(MAX_CONCURRENT_TRANSFERS)

    /**
     * Full track metadata (title/artist/artwork) for every currently
     * in-flight download, keyed by id — lets the Downloads tab render an
     * in-progress download as a complete track row (not just a bare
     * "Downloading…" placeholder), matching how a completed download
     * looks except for the progress ring in place of the checkmark.
     */
    private val _inFlightTracks = MutableStateFlow<Map<String, PlayableItem.YoutubeTrack>>(emptyMap())
    val inFlightTracks: StateFlow<Map<String, PlayableItem.YoutubeTrack>> = _inFlightTracks

    private val downloadsDir: File
        get() = File(appContext.filesDir, DOWNLOADS_DIR_NAME).apply { mkdirs() }

    private val artworkDir: File
        get() = File(appContext.filesDir, ARTWORK_DIR_NAME).apply { mkdirs() }

    private fun audioFileFor(trackId: String) = File(downloadsDir, "$trackId.audio")

    /**
     * Starts downloading [track] in the background. No-op (aside from a
     * toast) if a download for this id is already in flight — real,
     * reported gap: pressing Download again on a track that's already
     * downloading used to just silently do nothing, with zero feedback
     * that the tap was even registered. Now shows "Already downloading"
     * in that case, matching the same "never leave a tap silent" section
     * this function's own "Download started" toast already follows.
     * Both toasts are suppressed when called from [downloadAll] (showToast
     * = false) — that call site intentionally shows its own single batch
     * toast instead of one per song, and an already-in-flight track
     * being silently skipped as part of a bulk album/playlist download is
     * expected, not something that needs its own individual toast.
     */
    fun startDownload(track: PlayableItem.YoutubeTrack, showToast: Boolean = true) {
        if (activeDownloads[track.id]?.job?.isActive == true) {
            if (showToast) {
                com.whiplash.music.ui.common.ToastController.show("Already downloading")
            }
            return
        }
        _inFlightTracks.update { it + (track.id to track) }
        val job = scope.launch {
            runDownload(track)
        }
        activeDownloads[track.id] = ActiveDownload(job, track)
        job.invokeOnCompletion {
            activeDownloads.remove(track.id)
            _inFlightTracks.update { it - track.id }
        }
        if (showToast) {
            com.whiplash.music.ui.common.ToastController.show("Download started")
        }
    }

    /**
     * Starts downloading every track in [tracks] (an album's or
     * playlist's full track list, section: bulk album/playlist
     * downloads). Each track downloads independently via the same
     * [startDownload] path — already-downloaded or already-in-flight
     * tracks are silently skipped rather than re-downloaded, exactly as
     * a single [startDownload] call would behave on its own.
     *
     * Shows exactly one "Download started" toast for the whole batch
     * (not per song) — [startDownload] is called with `showToast =
     * false` here so it doesn't also post its own per-track toast.
     */
    fun downloadAll(tracks: List<PlayableItem.YoutubeTrack>) {
        if (tracks.isEmpty()) return
        tracks.forEach { startDownload(it, showToast = false) }
        com.whiplash.music.ui.common.ToastController.show("Download started")
    }

    /**
     * Cancels [trackId]'s in-flight download and instantly deletes
     * whatever partial audio bytes were already written — the tap-the-
     * progress-ring "Cancel download" confirmation's actual action
     * (section: Downloads tab redesign). Deleting the file here (rather
     * than relying solely on [runDownload]'s own cancellation cleanup) is
     * what makes the cleanup deterministic and immediate from the UI's
     * point of view: [Job.cancel] only requests cooperative cancellation
     * — the coroutine's own cleanup in [runDownload] still races with
     * this call, so both paths delete the same file (idempotent; a
     * missing file is silently ignored by [File.delete]).
     */
    fun cancelDownload(trackId: String) {
        activeDownloads.remove(trackId)?.job?.cancel()
        _progress.update { it - trackId }
        _inFlightTracks.update { it - trackId }
        runCatching { audioFileFor(trackId).delete() }
    }

    private suspend fun runDownload(track: PlayableItem.YoutubeTrack) {
        _progress.update { it + (track.id to DownloadProgress(track.id, 0L, 0L)) }

        // Real resource gap this closes: downloadAll() (the "Download
        // album/playlist" action) called startDownload() for every track in
        // a loop, and each of those launches its own independent coroutine
        // on Dispatchers.IO with no concurrency limit at all. A 50-track
        // playlist therefore opened ~50 simultaneous OkHttp connections and
        // ~50 FileOutputStreams, and because the metadata-tagging step reads
        // each finished file fully into memory (audioFile.readBytes(), plus a
        // second tagged copy), dozens of multi-megabyte byte arrays could be
        // live at once — saturating the network so every individual song
        // crawled, and risking an OutOfMemoryError on a large batch.
        //
        // The permit is taken here, inside the already-launched coroutine,
        // rather than in startDownload(), so nothing about the UI contract
        // changes: every track still registers in activeDownloads and
        // _inFlightTracks immediately, still shows its progress row, and is
        // still individually cancellable while it waits its turn.
        transferSemaphore.withPermit {
            runDownloadTransfer(track)
        }
    }

    private suspend fun runDownloadTransfer(track: PlayableItem.YoutubeTrack) {
        val audioFile = audioFileFor(track.id)
        // One automatic retry for a transient failure (a flaky mobile/
        // Wi-Fi connection dropping mid-transfer, or a stream URL that
        // failed to resolve on the first attempt) — this was a real,
        // reported reliability gap: any single network hiccup permanently
        // failed the whole download with zero retry, which is far more
        // failure-prone for a multi-megabyte transfer than the equivalent
        // small metadata/stream-resolve request most other network calls
        // in this app make. Not unbounded — exactly one retry, so a
        // genuinely broken/unavailable track still fails visibly rather
        // than looping forever.
        var lastFailure: Throwable? = null
        for (attempt in 0 until 2) {
            runCatching { audioFile.delete() } // clean slate for a retry; harmless no-op on attempt 0
            try {
                attemptDownload(track, audioFile)
                lastFailure = null
                break
            } catch (c: kotlinx.coroutines.CancellationException) {
                // User tapped the progress ring and chose "Cancel
                // download" — delete whatever partial bytes exist
                // immediately (also covered idempotently by
                // cancelDownload's own delete call) and stop retrying;
                // a cancellation is never a "transient failure."
                runCatching { audioFile.delete() }
                _progress.update { it - track.id }
                throw c
            } catch (t: Throwable) {
                lastFailure = t
                Log.w(TAG, "Download attempt ${attempt + 1} failed for ${track.id}", t)
            }
        }

        if (lastFailure != null) {
            Log.w(TAG, "Download failed for ${track.id} after retry", lastFailure)
            runCatching { audioFile.delete() }
            // Real, reported bug: the failed state used to be cleared
            // from _progress in the very same update cycle it was set
            // (an immediately-following `finally` block ran right after
            // this), so a failed download visually vanished with no
            // indication at all rather than showing a failed state —
            // "sometimes getting stopped from middle of download and
            // getting invisible from downloading options." Holding the
            // failed marker for a short, deliberate delay before clearing
            // it gives AnimatedContent/the UI a real chance to show it.
            _progress.update { it + (track.id to DownloadProgress(track.id, 0L, 0L, failed = true)) }
            com.whiplash.music.ui.common.ToastController.show("Download failed: ${track.title.truncateForToast()}")
            kotlinx.coroutines.delay(FAILED_STATE_VISIBLE_MS)
        }
        _progress.update { it - track.id }
    }

    /** A single download attempt: resolve stream, download bytes, download artwork, persist the completed row. Throws on any failure (including a non-eligible stream resolution failure). */
    private suspend fun attemptDownload(track: PlayableItem.YoutubeTrack, audioFile: File) {
        // Downloads use their own Settings > Download Quality preference
        // (deliberately separate from the streaming Audio Quality
        // setting — see SettingsRepository.downloadQuality) rather than
        // always resolving at AUTO/highest, so a lower quality tier here
        // genuinely fetches a smaller/lower-bitrate file, not just a
        // cosmetic label.
        val quality = settingsRepository?.downloadQuality?.first() ?: com.whiplash.music.domain.model.AudioQuality.AUTO
        val streamResult = playbackManager.resolveStream(track, quality)
        val resolved = when (streamResult) {
            is FallbackResult.Success -> streamResult.value
            is FallbackResult.Failure -> error("Stream resolution failed: ${streamResult.failure.message}")
        }

        downloadToFile(resolved.streamUrl, audioFile) { downloaded, total ->
            _progress.update { it + (track.id to DownloadProgress(track.id, downloaded, total)) }
        }

        // Best-effort artwork download alongside the audio, so the
        // Downloads tab and checkmark badges render with zero network
        // access afterward. Never fails the whole download if this
        // one step fails — artwork is a nice-to-have, not the point.
        val artworkPath = track.artworkUri?.let { url -> downloadArtwork(track.id, url) }

        // Embed title/artist/album/cover directly into the file's own
        // metadata (adapted from BitChord) so it shows correctly in any
        // other app — a file manager, a different player, a computer —
        // not only inside Whiplash's own Downloads tab. MP4/M4A only
        // (see Mp4Tagger doc): a WebM download is left byte-for-byte
        // untouched. Never fails the download itself — tag() always
        // returns the original bytes unchanged on any parsing doubt, and
        // this whole step is wrapped so even an unexpected I/O failure
        // here can't turn a successful download into a failed one.
        if (resolved.mimeType == MP4_MIME_TYPE) {
            runCatching {
                val cover = artworkPath?.let { path -> File(path).takeIf { it.exists() }?.readBytes() }
                val original = audioFile.readBytes()
                val tagged = Mp4Tagger.tag(
                    bytes = original,
                    title = track.title,
                    artist = track.artist ?: "",
                    album = track.album,
                    cover = cover,
                )
                if (!tagged.contentEquals(original)) {
                    // Real corruption risk this closes: this used to be a
                    // direct audioFile.writeBytes(tagged), which truncates
                    // the existing file and then writes. If the process died
                    // (force-stop, OS kill, battery) partway through that
                    // write, the user was left with a half-written,
                    // unplayable audio file — and since this runs *after* the
                    // bytes were successfully downloaded, the very next step
                    // persists a COMPLETED row for it. A fully downloaded
                    // song could therefore end up permanently broken while
                    // the Downloads tab showed a confident checkmark.
                    // Writing to a sibling temp file and then renaming makes
                    // the swap atomic: the original stays fully intact until
                    // the complete tagged copy exists on disk, so a kill at
                    // any point leaves either the untagged-but-valid original
                    // or the tagged-and-valid replacement, never a partial
                    // file.
                    val tempFile = File(audioFile.parentFile, "${audioFile.name}.tag.tmp")
                    runCatching { tempFile.delete() }
                    tempFile.writeBytes(tagged)
                    if (!tempFile.renameTo(audioFile)) {
                        runCatching { tempFile.delete() }
                        error("Could not replace ${audioFile.name} with its tagged copy")
                    }
                }
            }.onFailure { Log.w(TAG, "Metadata tagging failed for ${track.id}, download itself still succeeded", it) }
        }

        downloadDao.upsert(
            DownloadEntity(
                id = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkPath = artworkPath,
                durationMs = track.durationMs,
                filePath = audioFile.absolutePath,
                fileSizeBytes = audioFile.length(),
                status = DownloadStatus.COMPLETED,
                downloadedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun downloadToFile(url: String, destination: File, onProgress: (Long, Long) -> Unit) {
        withContext(Dispatchers.IO) {
            val downloadScope = this
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code} downloading $url")
                val body = response.body ?: error("Empty response body downloading $url")
                val totalBytes = body.contentLength()
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var downloaded = 0L
                        while (true) {
                            // Real, reported bug: this loop only ever
                            // called plain blocking I/O (InputStream.read,
                            // not a suspend function), so it never
                            // actually checked for cancellation — tapping
                            // "Cancel download" called Job.cancel() (which
                            // only *requests* cooperative cancellation)
                            // and deleted the file, but this loop kept
                            // running regardless, re-writing to the
                            // now-deleted file's fd and re-emitting
                            // onProgress() (which re-added the track to
                            // DownloadManager.progress) for as long as the
                            // underlying socket kept delivering bytes —
                            // "cancel download" visibly did nothing until
                            // the transfer happened to finish or stall on
                            // its own, sometimes tens of seconds later.
                            // ensureActive() makes this loop check the
                            // coroutine's own cancellation state on every
                            // iteration, so a cancelled job now stops
                            // reading (and therefore stops re-emitting
                            // progress) within one buffer-read's worth of
                            // latency instead of waiting for the whole
                            // response body to drain.
                            downloadScope.ensureActive()
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, totalBytes)
                        }
                        // Real silent-failure gap this closes: the loop above
                        // exits on read() == -1, and that was treated as
                        // unconditional success — attemptDownload went
                        // straight on to persist a COMPLETED row. But -1 only
                        // means "this stream produced no more bytes", which is
                        // also exactly what a connection dropped mid-transfer
                        // looks like once the body has been partially
                        // consumed. Content-Length was being read into
                        // totalBytes and used for the progress ring, yet never
                        // compared against what actually landed on disk, so a
                        // truncated file could be saved, marked COMPLETED with
                        // a full checkmark, and never retried — the user's
                        // "downloaded for offline" song silently cut off part
                        // way through, with no error anywhere.
                        //
                        // Throwing here routes into runDownload's existing
                        // retry-once-then-fail-visibly path, which already
                        // deletes the partial file and surfaces a toast.
                        // Guarded on totalBytes > 0 so a chunked/gzip response
                        // with no declared length (contentLength() == -1)
                        // keeps its previous behaviour rather than failing
                        // every time.
                        if (totalBytes > 0 && downloaded != totalBytes) {
                            error("Truncated download: wrote $downloaded of $totalBytes bytes for $url")
                        }
                    }
                }
            }
        }
    }

    private suspend fun downloadArtwork(trackId: String, url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val destination = File(artworkDir, "$trackId.jpg")
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                body.byteStream().use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output) }
                }
            }
            destination.absolutePath
        }.getOrNull()
    }

    /** Deletes a download's audio/artwork files and its Room row. */
    suspend fun removeDownload(id: String) {
        cancelDownload(id)
        val entity = downloadDao.getById(id)
        if (entity != null) {
            runCatching { File(entity.filePath).delete() }
            entity.artworkPath?.let { runCatching { File(it).delete() } }
        }
        downloadDao.delete(id)
    }

    /**
     * Cancels every in-flight download and deletes every completed
     * download's audio/artwork files and Room rows — the Downloads tab's
     * "Clear all downloads" action (shown behind a confirmation dialog
     * since this is destructive and irreversible, see DownloadList's
     * GlassConfirmDialog usage).
     */
    suspend fun clearAllDownloads() {
        activeDownloads.keys.toList().forEach { cancelDownload(it) }
        val all = downloadDao.getAll()
        all.forEach { entity ->
            runCatching { File(entity.filePath).delete() }
            entity.artworkPath?.let { runCatching { File(it).delete() } }
        }
        downloadDao.deleteAll()
    }

    /**
     * Called once at app startup: cleans up anything left behind by a
     * process that was killed mid-download (section: app killed
     * mid-download must not leave a stuck or orphaned state forever).
     *
     * Covers two distinct gaps, both real:
     *
     * 1. Any row left in DOWNLOADING status (would only happen if a
     *    future code path ever persists an in-progress row before
     *    completion) is marked FAILED and its partial file removed.
     *
     * 2. Real, reported gap: [attemptDownload] only ever calls
     *    [DownloadDao.upsert] once, at the very end, on success — a
     *    download that never gets that far (including one interrupted by
     *    the process being killed, e.g. force-stopped or swiped away by
     *    the OS while a large file was still transferring) never gets a
     *    DOWNLOADING row in the first place, so scanning the DB alone
     *    (case 1 above) can never find or clean it up. The partial
     *    `*.audio` file it already wrote to [downloadsDir] before being
     *    killed is real and stays on disk forever otherwise — a genuine,
     *    silent storage leak with zero corresponding UI entry to ever
     *    surface it. This sweeps [downloadsDir] directly and deletes any
     *    file whose id isn't a completed download's own file — safe
     *    because a legitimate in-flight download can only exist while
     *    this class's own coroutine scope is alive, which is never true
     *    at the app-startup call site this method is meant for.
     */
    suspend fun cleanUpIncompleteDownloads() {
        val stale = downloadDao.getAll().filter { it.status == DownloadStatus.DOWNLOADING }
        stale.forEach { entity ->
            runCatching { File(entity.filePath).delete() }
        }
        downloadDao.failAllInProgress()

        val rows = downloadDao.getAll()

        // Real silent-failure gap this closes (DB -> disk divergence): a row
        // is only ever written as COMPLETED, and nothing ever checked that
        // the file it points at still exists. app-private files can and do
        // disappear independently of the database — a restore from backup
        // recreates COMPLETED rows with absolute paths from a *different*
        // install, and storage pressure/manual cleanup can remove the bytes.
        // observeDownloads() maps every COMPLETED row straight to a
        // playable DownloadedTrack with no existence check, so the Downloads
        // tab confidently showed a full checkmark for a song whose audio was
        // gone, and tapping it just silently failed to play with no
        // explanation and no way to know it needed re-downloading. Dropping
        // these rows makes the tab honest and lets the user simply download
        // the track again.
        rows.filter { it.status == DownloadStatus.COMPLETED && !File(it.filePath).exists() }
            .forEach { orphanRow ->
                orphanRow.artworkPath?.let { runCatching { File(it).delete() } }
                downloadDao.delete(orphanRow.id)
            }

        // Real race this closes: the doc above used to claim this sweep was
        // safe because "a legitimate in-flight download can only exist while
        // this class's own coroutine scope is alive, which is never true at
        // the app-startup call site." That is not true — DownloadManager is a
        // process-lifetime singleton and this method is launched into a
        // detached Dispatchers.IO scope from Application.onCreate(), so it
        // runs *concurrently* with the UI coming up. A download the user
        // starts as soon as the first screen renders has no COMPLETED row
        // yet, so its live, actively-being-written *.audio file looked
        // exactly like process-death garbage and got deleted out from under
        // the open FileOutputStream. Excluding the files of everything
        // currently in flight makes the sweep safe no matter when it lands.
        val protectedPaths = rows.map { it.filePath }.toSet() +
            activeDownloads.keys.map { audioFileFor(it).absolutePath }
        downloadsDir.listFiles()?.forEach { file ->
            if (file.absolutePath !in protectedPaths) {
                runCatching { file.delete() }
            }
        }
    }

    private companion object {
        const val TAG = "DownloadManager"
        const val DOWNLOADS_DIR_NAME = "downloads"
        const val ARTWORK_DIR_NAME = "download_artwork"

        /**
         * How many downloads may transfer bytes simultaneously. Bulk
         * album/playlist downloads previously ran every track at once (see
         * [runDownload]); 3 keeps a batch genuinely parallel and fast without
         * saturating the connection or holding dozens of whole audio files in
         * memory for the tagging step at the same time.
         */
        const val MAX_CONCURRENT_TRANSFERS = 3

        /** How long a failed download's progress-ring badge stays visible (showing the failure) before the row disappears entirely. */
        const val FAILED_STATE_VISIBLE_MS = 2_500L

        /** Longest a track title is allowed to run inside a toast message before being ellipsized — a real YouTube video title can run 50+ characters, which previously stretched the "Download failed: <title>" toast into an oversized banner. */
        const val TOAST_TITLE_MAX_CHARS = 40

        /** [com.whiplash.music.playback.provider.ResolvedStream.mimeType] value NewPipeExtractor reports for AAC-in-MP4/M4A streams — the only container [Mp4Tagger] is written to handle. */
        const val MP4_MIME_TYPE = "audio/mp4"
    }

    /** Ellipsizes [this] to [TOAST_TITLE_MAX_CHARS] characters for use inside a short toast message, leaving long titles untouched everywhere else (list rows, notifications, etc.). */
    private fun String.truncateForToast(): String =
        if (length <= TOAST_TITLE_MAX_CHARS) this else take(TOAST_TITLE_MAX_CHARS - 1).trimEnd() + "\u2026"
}
