package com.whiplash.music.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/** "Add to playlist" sub-sheet (section 38): pick an existing playlist, or create a new one. */
@Composable
fun AddToPlaylistContent(
    playlists: List<Playlist>,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit,
    title: String = "Add to playlist",
    // False only for the "Move to other playlist" picker (section:
    // Playlist detail screen) — moving into a brand-new empty playlist
    // is exactly equivalent to adding it there via the regular add flow
    // and then removing it from the current playlist, so offering a
    // second, differently-worded path to the same outcome here would
    // just be a redundant, confusing entry point rather than a real
    // additional capability.
    showCreateNew: Boolean = true,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(bottom = GlassTokens.spaceSm),
        )

        if (showCreateNew) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreateNew)
                    .padding(vertical = GlassTokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = WhiplashColors.accent)
                Text(
                    text = "New playlist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = WhiplashColors.accent,
                    modifier = Modifier.padding(start = GlassTokens.spaceMd),
                )
            }
        }

        if (playlists.isEmpty()) {
            Text(
                text = if (showCreateNew) "No playlists yet." else "No other playlists to move to.",
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                modifier = Modifier.padding(vertical = GlassTokens.spaceSm),
            )
        }

        playlists.forEach { playlist ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = { onSelectPlaylist(playlist) })
                    .padding(vertical = GlassTokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = WhiplashColors.textPrimary)
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = GlassTokens.spaceMd),
                )
            }
        }

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
    }
}
