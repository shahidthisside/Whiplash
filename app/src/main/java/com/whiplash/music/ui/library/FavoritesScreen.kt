// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.DeleteOutline
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

// ref wl-sa26-7f3c92
/**
 * Favorites/Liked Songs screen (sections 26, 33, 37). Backed by
 * [com.whiplash.music.data.repository.LibraryRepository.observeFavorites],
 * built in the queue/favorites work but not yet exposed in any screen.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun FavoritesScreen(
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: FavoritesViewModel = viewModel(factory = FavoritesViewModelFactory(app.libraryRepository))
    val favorites by viewModel.favorites.collectAsState()
    ModernFavorites(favorites, onPlayQueue)
}

/**
 * Liked songs as a collection page: gradient heart cover, song count and
 * running time, Play / Shuffle / Download, filter and sort. The system Back
 * gesture still returns to Home, as on the other tabs.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ModernFavorites(
    favorites: List<PlayableItem>,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    val tint = LIKED_TINT
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as? com.whiplash.music.WhiplashApplication
    var confirmClear by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmClear && app != null) {
        val count = favorites.size
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Remove all from Favorites?",
            message = "${if (count == 1) "1 song" else "$count songs"} will be removed from Favorites.",
            confirmLabel = "Remove all",
            onConfirm = {
                confirmClear = false
                com.whiplash.music.ui.common.UiActionScope.scope.launch {
                    runCatching { app.libraryRepository.clearFavorites() }
                        .onSuccess { com.whiplash.music.ui.common.ToastController.show("Favorites cleared") }
                        .onFailure { com.whiplash.music.ui.common.ToastController.show("Couldn't clear Favorites") }
                }
            },
            onDismiss = { confirmClear = false },
        )
    }
    com.whiplash.music.ui.common.TrackCollectionPage(
        items = favorites,
        eyebrow = "Collection",
        title = "Liked songs",
        tint = tint,
        cover = { m -> com.whiplash.music.ui.theme.GradientIconCover(Icons.Filled.Favorite, tint, m) },
        onPlayQueue = onPlayQueue,
        sortKey = "favorites",
        defaultSortLabel = "Recently liked",
        modifier = Modifier.padding(horizontal = GlassTokens.spaceMd),
        heroActions = {
            if (favorites.isNotEmpty()) {
                com.whiplash.music.ui.common.BatchDownloadIconButton(batchName = "Liked songs", tracks = favorites)
                com.whiplash.music.ui.theme.PlainIconButton(contentDescription = "Remove all from Favorites", onClick = { confirmClear = true }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.DeleteOutline,
                        contentDescription = null,
                        tint = com.whiplash.music.ui.theme.WhiplashColors.textSecondary,
                    )
                }
            }
        },
        emptyContent = {
            com.whiplash.music.ui.theme.CollectionEmptyState(
                icon = Icons.Outlined.FavoriteBorder,
                title = "No liked songs yet",
                message = "Tap the heart in the player, or long-press any song and choose Add to favorites.",
                tint = tint,
            )
        },
    )
}

/** Pink used for the Liked songs page. */
internal val LIKED_TINT = androidx.compose.ui.graphics.Color(0xFFE0648B)
