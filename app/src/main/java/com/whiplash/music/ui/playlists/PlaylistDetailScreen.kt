package com.whiplash.music.ui.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassIconButton
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/** Playlist detail screen (section 38): all tracks in the playlist, play/shuffle, remove. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: PlaylistDetailViewModel = viewModel(
        factory = PlaylistDetailViewModelFactory(app.libraryRepository, playlist.id),
    )
    val tracks by viewModel.tracks.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = WhiplashColors.textPrimary,
                modifier = Modifier.padding(start = GlassTokens.spaceSm).weight(1f),
            )
            if (tracks.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
                    GlassIconButton(contentDescription = "Shuffle play", onClick = { onPlayQueue(tracks.shuffled(), 0) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = WhiplashColors.textPrimary)
                    }
                    GlassIconButton(contentDescription = "Play all", onClick = { onPlayQueue(tracks, 0) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = WhiplashColors.textPrimary)
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            Text(
                text = "No tracks yet. Add songs via the long-press menu.",
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
                modifier = Modifier.padding(GlassTokens.spaceLg),
            )
        } else {
            PlayableItemsList(items = tracks, onPlayQueue = onPlayQueue, modifier = Modifier.fillMaxSize())
        }
    }
}
