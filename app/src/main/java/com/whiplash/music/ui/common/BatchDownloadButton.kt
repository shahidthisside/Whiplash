package com.whiplash.music.ui.common

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassConfirmDialog
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import kotlinx.coroutines.launch

/**
 * The three possible states of a whole-album/artist/playlist "Download"
 * control, derived from how many of its downloadable tracks are already
 * downloaded or currently in flight (section: batch download control
 * should reflect real progress, not just start a fire-and-forget batch
 * with no way to see or cancel it, or to know it already finished).
 */
private enum class BatchDownloadState { DOWNLOAD, DOWNLOADING, DOWNLOADED }

/**
 * Shared state + confirmation-dialog plumbing behind both
 * [BatchDownloadButton] (the pill-shaped control used on Album/Artist
 * detail, next to their existing pill "Play"/"Shuffle" buttons) and
 * [BatchDownloadIconButton] (the icon-only control used on the manual
 * Playlist detail screen, next to its existing [PlainIconButton]
 * Play/Shuffle icons — see [com.whiplash.music.ui.playlists.PlaylistDetailScreen]
 * and the same icon-button style [com.whiplash.music.ui.playlists.PlaylistsScreen]
 * already uses for "New playlist"/"Import playlist").
 *
 * Reuses the exact same [com.whiplash.music.data.download.DownloadManager]
 * state ([DownloadManager.inFlightTracks] /
 * [com.whiplash.music.data.repository.LibraryRepository.observeDownloadedIds])
 * already driving the per-song "Download"/"Downloaded" row in
 * [com.whiplash.music.ui.player.SongActionsContent] — no separate
 * tracking mechanism, so this can never drift out of sync with what a
 * long-press sheet shows for the same tracks.
 *
 * - **Download**: none of this batch's downloadable tracks are in
 *   flight, and at least one isn't downloaded yet. Tapping downloads
 *   only the *not-yet-downloaded* tracks (already-downloaded ones are
 *   silently skipped, same as [com.whiplash.music.data.download.DownloadManager.downloadAll]
 *   already does on its own) — so re-tapping after a partial download
 *   only fetches what's actually missing, never re-downloads or
 *   duplicates anything.
 * - **Downloading**: at least one of this batch's tracks is currently
 *   downloading. Tapping shows a confirmation naming the batch and how
 *   many songs are in flight; confirming cancels only *this batch's*
 *   in-flight downloads (any unrelated download elsewhere in the app
 *   keeps running untouched).
 * - **Downloaded**: every downloadable track in the batch is already
 *   downloaded. Tapping shows a confirmation naming the batch and how
 *   many songs will be removed; confirming deletes only *this batch's*
 *   downloaded tracks (their audio/artwork files and Room rows, via
 *   [com.whiplash.music.data.download.DownloadManager.removeDownload])
 *   — any of the same tracks appearing elsewhere (Downloads tab, other
 *   playlists) is affected identically since it's the same underlying
 *   download, not a separate copy.
 *
 * Only [PlayableItem.YoutubeTrack] and [PlayableItem.DownloadedTrack]
 * entries count as downloadable candidates — a [PlayableItem.LocalTrack]
 * is already on-device and can never be downloaded, so it's excluded
 * entirely (mirroring the exact same filtering [PlaylistDetailScreen]
 * already used for its own download-confirmation message). A
 * [PlayableItem.DownloadedTrack] is counted as an already-downloaded
 * candidate (its id is always present in [downloadedIds] anyway) rather
 * than being dropped — without this, a playlist built entirely from
 * songs added straight from the Downloads tab would show no download
 * control at all instead of "Downloaded".
 */
private class BatchDownloadController(
    val batchName: String,
    val notDownloaded: List<PlayableItem.YoutubeTrack>,
    val inFlightInBatch: List<PlayableItem.YoutubeTrack>,
    val downloadedInBatch: List<PlayableItem>,
    val state: BatchDownloadState,
)

@Composable
private fun rememberBatchDownloadController(batchName: String, tracks: List<PlayableItem>): BatchDownloadController? {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication

    val downloadedIds by app.libraryRepository.observeDownloadedIds().collectAsState(initial = emptySet())
    val inFlightTracks by app.downloadManager.inFlightTracks.collectAsState()

    val candidates = remember(tracks) {
        tracks.filter { it is PlayableItem.YoutubeTrack || it is PlayableItem.DownloadedTrack }
    }
    if (candidates.isEmpty()) return null

    // All of the below is derived purely from (candidates, downloadedIds,
    // inFlightTracks), so it is memoised on exactly those three inputs.
    //
    // Unmemoised, this ran six full traversals of the batch — building six
    // new lists — on *every* recomposition. That is cheap for an album, and
    // genuinely expensive for a large imported playlist: at 250 tracks it
    // meant ~1,500 element visits and six list allocations per frame, and
    // because the playlist-detail enter transition is an AnimatedContent it
    // recomposes on every frame of the animation. Measured on-device with a
    // 250-track playlist before this change: 90th percentile frame time
    // 81ms against a 16.7ms budget, which is what made the slide look like
    // it was jumping/racing rather than sliding.
    //
    // Behaviour is unchanged — the same values, computed only when one of
    // the three inputs actually differs. Sets compare by content, so a
    // downloads or in-flight change still recomputes immediately.
    return remember(batchName, candidates, downloadedIds, inFlightTracks) {
        val inFlightInBatch = candidates.filterIsInstance<PlayableItem.YoutubeTrack>().filter { it.id in inFlightTracks }
        // Real, reported bug: this used to derive "is everything downloaded"
        // purely from candidates.filterIsInstance<YoutubeTrack>() being
        // fully downloaded — but a track added to the playlist as a
        // DownloadedTrack (straight from the Downloads tab) is never a
        // YoutubeTrack, so it could never appear in that not-yet-downloaded
        // list even after its own download was removed. That permanently
        // pinned the button on "Downloaded" the moment any DownloadedTrack
        // candidate existed, regardless of live downloadedIds state.
        // Checking every candidate (of either type) against the live
        // downloadedIds set — not just the YoutubeTrack subset — is what
        // actually reflects whether anything in the batch still needs
        // downloading.
        val notDownloadedAny = candidates.filterNot { it.id in downloadedIds }
        val notDownloaded = candidates.filterIsInstance<PlayableItem.YoutubeTrack>().filterNot { it.id in downloadedIds }
        val downloadedInBatch = candidates.filter { it.id in downloadedIds }

        val state = when {
            inFlightInBatch.isNotEmpty() -> BatchDownloadState.DOWNLOADING
            notDownloadedAny.isEmpty() -> BatchDownloadState.DOWNLOADED
            else -> BatchDownloadState.DOWNLOAD
        }

        BatchDownloadController(batchName, notDownloaded, inFlightInBatch, downloadedInBatch, state)
    }
}

/** The three confirmation dialogs shared by both [BatchDownloadButton] and [BatchDownloadIconButton]. */
@Composable
private fun BatchDownloadDialogs(
    controller: BatchDownloadController,
    showDownloadConfirm: Boolean,
    showCancelConfirm: Boolean,
    showRemoveConfirm: Boolean,
    onDismissDownload: () -> Unit,
    onDismissCancel: () -> Unit,
    onDismissRemove: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val scope = rememberCoroutineScope()

    if (showDownloadConfirm) {
        if (controller.notDownloaded.isEmpty()) {
            // Edge case: every not-yet-downloaded candidate in this batch
            // is a DownloadedTrack whose underlying file/row was removed
            // (e.g. via "Remove downloads" or the Downloads tab) rather
            // than a fresh YoutubeTrack — there's no retained YouTube
            // metadata to re-resolve a stream from, so it genuinely can't
            // be re-downloaded from here. Says so rather than silently
            // showing "0 songs will be downloaded" and doing nothing.
            GlassConfirmDialog(
                title = "Can't download",
                message = "These songs were removed from Downloads and can't be re-downloaded from here. Add them again from Search to download them.",
                confirmLabel = "OK",
                onConfirm = onDismissDownload,
                onDismiss = onDismissDownload,
            )
        } else {
            GlassConfirmDialog(
                title = "Download?",
                message = "${controller.notDownloaded.size} song${if (controller.notDownloaded.size == 1) "" else "s"} in \"${controller.batchName}\" will be downloaded for offline playback.",
                confirmLabel = "Download",
                onConfirm = {
                    app.downloadManager.downloadAll(controller.notDownloaded)
                    onDismissDownload()
                },
                onDismiss = onDismissDownload,
            )
        }
    }

    if (showCancelConfirm) {
        GlassConfirmDialog(
            title = "Cancel download?",
            message = "${controller.inFlightInBatch.size} song${if (controller.inFlightInBatch.size == 1) "" else "s"} downloading from \"${controller.batchName}\" will be cancelled. Any progress will be lost.",
            confirmLabel = "Cancel download",
            dismissLabel = "Keep downloading",
            onConfirm = {
                scope.launch {
                    controller.inFlightInBatch.forEach { app.downloadManager.cancelDownload(it.id) }
                }
                onDismissCancel()
            },
            onDismiss = onDismissCancel,
        )
    }

    if (showRemoveConfirm) {
        GlassConfirmDialog(
            title = "Remove downloads?",
            message = "${controller.downloadedInBatch.size} downloaded song${if (controller.downloadedInBatch.size == 1) "" else "s"} from \"${controller.batchName}\" will be removed from Downloads.",
            confirmLabel = "Remove",
            onConfirm = {
                scope.launch {
                    controller.downloadedInBatch.forEach { app.downloadManager.removeDownload(it.id) }
                }
                onDismissRemove()
            },
            onDismiss = onDismissRemove,
        )
    }
}

/**
 * Pill-shaped "Download"/"Cancel download"/"Downloaded" button — used on
 * Album and Artist detail screens, next to their existing pill-shaped
 * [GlassButton] "Play"/"Shuffle" controls, matching that surrounding
 * style. See [BatchDownloadController]'s doc for the full state
 * semantics (shared with [BatchDownloadIconButton]).
 */
@Composable
fun BatchDownloadButton(
    batchName: String,
    tracks: List<PlayableItem>,
    modifier: Modifier = Modifier,
) {
    val controller = rememberBatchDownloadController(batchName, tracks) ?: return

    var showDownloadConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val label = when (controller.state) {
        BatchDownloadState.DOWNLOAD -> "Download"
        BatchDownloadState.DOWNLOADING -> "Cancel download"
        BatchDownloadState.DOWNLOADED -> "Downloaded"
    }

    GlassButton(
        text = label,
        onClick = {
            when (controller.state) {
                BatchDownloadState.DOWNLOAD -> showDownloadConfirm = true
                BatchDownloadState.DOWNLOADING -> showCancelConfirm = true
                BatchDownloadState.DOWNLOADED -> showRemoveConfirm = true
            }
        },
        modifier = modifier,
    )

    BatchDownloadDialogs(
        controller = controller,
        showDownloadConfirm = showDownloadConfirm,
        showCancelConfirm = showCancelConfirm,
        showRemoveConfirm = showRemoveConfirm,
        onDismissDownload = { showDownloadConfirm = false },
        onDismissCancel = { showCancelConfirm = false },
        onDismissRemove = { showRemoveConfirm = false },
    )
}

/**
 * Icon-only "Download"/"Cancel download"/"Downloaded" control — used on
 * the manual Playlist detail screen, matching the [PlainIconButton] icon
 * style its own Play/Shuffle controls (and [com.whiplash.music.ui.playlists.PlaylistsScreen]'s
 * "New playlist"/"Import playlist" buttons) already use, rather than the
 * pill [BatchDownloadButton] used elsewhere. Same three-state semantics
 * and confirmation dialogs, just a different visual: a plain
 * [Icons.Filled.Download] icon, an indeterminate [CircularProgressIndicator]
 * while downloading (tapping it opens the cancel confirmation, same as
 * tapping the pill button's "Cancel download" label would), and
 * [Icons.Filled.DownloadDone] once complete (tapping it opens the remove
 * confirmation).
 */
@Composable
fun BatchDownloadIconButton(
    batchName: String,
    tracks: List<PlayableItem>,
    modifier: Modifier = Modifier,
) {
    val controller = rememberBatchDownloadController(batchName, tracks) ?: return

    var showDownloadConfirm by remember { mutableStateOf(false) }
    var showCancelConfirm by remember { mutableStateOf(false) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    val contentDescription = when (controller.state) {
        BatchDownloadState.DOWNLOAD -> "Download"
        BatchDownloadState.DOWNLOADING -> "Cancel download"
        BatchDownloadState.DOWNLOADED -> "Remove downloads"
    }

    PlainIconButton(
        contentDescription = contentDescription,
        onClick = {
            when (controller.state) {
                BatchDownloadState.DOWNLOAD -> showDownloadConfirm = true
                BatchDownloadState.DOWNLOADING -> showCancelConfirm = true
                BatchDownloadState.DOWNLOADED -> showRemoveConfirm = true
            }
        },
        modifier = modifier,
    ) {
        when (controller.state) {
            BatchDownloadState.DOWNLOAD -> Icon(Icons.Filled.Download, contentDescription = null, tint = WhiplashColors.textPrimary)
            BatchDownloadState.DOWNLOADING -> CircularProgressIndicator(
                color = WhiplashColors.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
            BatchDownloadState.DOWNLOADED -> Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = WhiplashColors.accent)
        }
    }

    BatchDownloadDialogs(
        controller = controller,
        showDownloadConfirm = showDownloadConfirm,
        showCancelConfirm = showCancelConfirm,
        showRemoveConfirm = showRemoveConfirm,
        onDismissDownload = { showDownloadConfirm = false },
        onDismissCancel = { showCancelConfirm = false },
        onDismissRemove = { showRemoveConfirm = false },
    )
}
