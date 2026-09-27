package com.whiplash.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Full player's overflow menu — a real, reported crowding problem: adding
 * Playback speed and Add to playlist as their own top-row icons (6 total,
 * up from 4) made the row visibly denser, and the "Add to playlist" icon
 * (a generic kebab, reused from elsewhere in the app to open the existing
 * AddToPlaylistContent sheet) was correctly called out as reading like a
 * second, redundant "more options" control rather than what it actually
 * did.
 *
 * The fix: Sleep timer and Playback speed — two lower-frequency,
 * "look up / configure" actions rather than the moment-to-moment ones
 * (Lyrics, Favorite, Queue, all kept as their own dedicated top-row
 * icons) — move into a single overflow sheet opened from one "More"
 * icon, and "Add to playlist" gets its own correctly-labeled row inside
 * that same sheet instead of a confusing dedicated top-level icon.
 */
@Composable
fun PlayerOverflowContent(
    sleepTimerActive: Boolean,
    playbackSpeed: Float,
    onOpenSleepTimer: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    onOpenAddToPlaylist: () -> Unit,
    onOpenAudioOutput: () -> Unit = {},
    // Offline download (Library > Downloads, YouTube-Music-style) for
    // whatever is currently playing — null when there's nothing
    // meaningful to download (a LocalTrack, or no current item at all).
    isDownloaded: Boolean = false,
    onDownload: (() -> Unit)? = null,
    onRemoveDownload: (() -> Unit)? = null,
    // The song playing now, so the playlist and download rows can follow
    // its live state (Saved in …, Cancel download).
    item: com.whiplash.music.domain.model.PlayableItem? = null,
) {
    val live = rememberSongLibraryState(item)
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.whiplash.music.WhiplashApplication
    OverflowRow(
        icon = {
            Icon(
                Icons.Filled.Bedtime,
                contentDescription = null,
                tint = if (sleepTimerActive) WhiplashColors.accent else WhiplashColors.textPrimary,
            )
        },
        label = if (sleepTimerActive) "Sleep timer active" else "Sleep timer",
        onClick = onOpenSleepTimer,
    )
    OverflowRow(
        icon = {
            Icon(
                Icons.Filled.Speed,
                contentDescription = null,
                tint = if (playbackSpeed != 1.0f) WhiplashColors.accent else WhiplashColors.textPrimary,
            )
        },
        label = if (playbackSpeed != 1.0f) "Playback speed: ${com.whiplash.music.domain.model.PlaybackTuning.formatSpeed(playbackSpeed)}" else "Playback speed",
        onClick = onOpenPlaybackSpeed,
    )
    // Where the audio is going right now (updates live as devices connect);
    // tapping opens the system output picker.
    val output = rememberAudioOutput()
    OverflowRow(
        icon = {
            Icon(
                output.icon(),
                contentDescription = null,
                tint = if (output.kind != com.whiplash.music.domain.model.AudioOutput.Kind.SPEAKER) WhiplashColors.accent else WhiplashColors.textPrimary,
            )
        },
        label = "Audio output: ${output.name}",
        onClick = onOpenAudioOutput,
    )
    val saved = live.inPlaylists.isNotEmpty()
    OverflowRow(
        icon = {
            Icon(
                if (saved) Icons.Filled.LibraryAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                tint = if (saved) WhiplashColors.accent else WhiplashColors.textPrimary,
            )
        },
        label = "Add to playlist",
        subtitle = inPlaylistsSubtitle(live.inPlaylists),
        onClick = onOpenAddToPlaylist,
    )
    if (live.downloading && item != null && app != null) {
        OverflowRow(
            icon = { Icon(Icons.Filled.Close, contentDescription = null, tint = WhiplashColors.textPrimary) },
            label = "Cancel download",
            onClick = {
                app.downloadManager.cancelDownload(item.id)
                com.whiplash.music.ui.common.ToastController.show("Download cancelled")
            },
        )
    } else if (onDownload != null) {
        OverflowRow(
            icon = {
                Icon(
                    imageVector = if (isDownloaded) Icons.Filled.DownloadDone else Icons.Filled.Download,
                    contentDescription = null,
                    tint = if (isDownloaded) WhiplashColors.accent else WhiplashColors.textPrimary,
                )
            },
            label = if (isDownloaded) "Downloaded" else "Download",
            onClick = onDownload,
        )
    }
    if (onRemoveDownload != null) {
        OverflowRow(
            icon = { Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = WhiplashColors.error) },
            label = "Remove download",
            onClick = onRemoveDownload,
        )
    }
}

@Composable
private fun OverflowRow(icon: @Composable () -> Unit, label: String, onClick: () -> Unit, subtitle: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = GlassTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(start = GlassTokens.spaceMd)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = WhiplashColors.textPrimary,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = WhiplashColors.accent,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}
