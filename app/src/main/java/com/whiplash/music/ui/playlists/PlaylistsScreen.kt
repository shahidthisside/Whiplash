package com.whiplash.music.ui.playlists

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.Playlist
import com.whiplash.music.ui.theme.GlassCard
import com.whiplash.music.ui.theme.GlassIconButton
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTextInputDialog
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/**
 * Playlists list screen (section 38). The repository layer for playlists
 * (create/rename/delete/add/remove/reorder tracks) was built earlier in the
 * project; this is the first UI surface for it.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun PlaylistsScreen(onOpenPlaylist: (Playlist) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: PlaylistsViewModel = viewModel(factory = PlaylistsViewModelFactory(app.libraryRepository))
    val playlists by viewModel.playlists.collectAsState()
    val haptic = LocalHapticFeedback.current

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Playlists", style = MaterialTheme.typography.titleMedium, color = WhiplashColors.textPrimary)
            GlassIconButton(contentDescription = "New playlist", onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No playlists yet. Tap + to create one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textSecondary,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    val interactionSource = remember { MutableInteractionSource() }
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { onOpenPlaylist(playlist) },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    playlistPendingDelete = playlist
                                },
                            ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = null,
                                tint = WhiplashColors.textSecondary,
                                modifier = Modifier.padding(end = GlassTokens.spaceMd),
                            )
                            Text(text = playlist.name, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        GlassTextInputDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    val toDelete = playlistPendingDelete
    if (toDelete != null) {
        GlassSheet(onDismissRequest = { playlistPendingDelete = null }) {
            Column {
                Text(
                    text = toDelete.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(bottom = GlassTokens.spaceSm),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = GlassTokens.spaceSm)
                        .clickable(
                            onClick = {
                                viewModel.deletePlaylist(toDelete.id)
                                playlistPendingDelete = null
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = WhiplashColors.error)
                    Text(
                        text = "Delete playlist",
                        style = MaterialTheme.typography.bodyLarge,
                        color = WhiplashColors.error,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
            }
        }
    }
}
