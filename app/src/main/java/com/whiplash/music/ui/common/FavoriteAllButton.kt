// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.data.repository.favoriteKey
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.theme.GlassConfirmDialog
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import kotlinx.coroutines.launch

/** Whether every song of a collection is already in Favorites (null while unknown). */
@Composable
fun rememberAllFavorited(tracks: List<PlayableItem>?): Boolean? {
    val app = LocalContext.current.applicationContext as? WhiplashApplication ?: return null
    val keys by remember { app.libraryRepository.observeFavoriteKeys() }.collectAsState(initial = null)
    val favs = keys ?: return null
    if (tracks.isNullOrEmpty()) return null
    return tracks.all { favoriteKey(it.id, it.source) in favs }
}

/** Confirmed "add all" / "remove all": asks first, then applies and says what happened. */
object FavoriteAll {
    fun add(app: WhiplashApplication, name: String, tracks: List<PlayableItem>) {
        UiActionScope.scope.launch {
            val added = runCatching { app.libraryRepository.addAllToFavorites(tracks) }.getOrElse {
                ToastController.show("Couldn't add to Favorites")
                return@launch
            }
            ToastController.show(
                when (added) {
                    0 -> "Already in Favorites"
                    1 -> "Added 1 song to Favorites"
                    else -> "Added $added songs to Favorites"
                },
            )
        }
    }

    fun remove(app: WhiplashApplication, tracks: List<PlayableItem>) {
        UiActionScope.scope.launch {
            runCatching { app.libraryRepository.removeAllFromFavorites(tracks) }
                .onSuccess { ToastController.show("Removed from Favorites") }
                .onFailure { ToastController.show("Couldn't remove from Favorites") }
        }
    }
}

/** The two confirmations shared by the heart button and the collection menus. */
@Composable
fun FavoriteAllDialogs(
    name: String,
    tracks: List<PlayableItem>,
    confirmAdd: Boolean,
    confirmRemove: Boolean,
    onDismiss: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as? WhiplashApplication ?: return
    val count = tracks.distinctBy { favoriteKey(it.id, it.source) }.size
    val songs = if (count == 1) "1 song" else "$count songs"
    if (confirmAdd) {
        GlassConfirmDialog(
            title = "Add to Favorites?",
            message = "$songs from $name will be added to Favorites.",
            confirmLabel = "Add",
            destructive = false,
            onConfirm = { onDismiss(); FavoriteAll.add(app, name, tracks) },
            onDismiss = onDismiss,
        )
    }
    if (confirmRemove) {
        GlassConfirmDialog(
            title = "Remove from Favorites?",
            message = "$songs from $name will be removed from Favorites.",
            confirmLabel = "Remove",
            onConfirm = { onDismiss(); FavoriteAll.remove(app, tracks) },
            onDismiss = onDismiss,
        )
    }
}

/**
 * Heart button for an album or playlist page: outlined until every song is
 * a favorite, then filled. Tapping asks to add all (or, when all are
 * already in, to remove them).
 */
@Composable
fun FavoriteAllIconButton(name: String, tracks: List<PlayableItem>, modifier: Modifier = Modifier) {
    if (tracks.isEmpty()) return
    val all = rememberAllFavorited(tracks) ?: false
    var confirmAdd by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    PlainIconButton(
        contentDescription = if (all) "Remove all from Favorites" else "Add all to Favorites",
        onClick = { if (all) confirmRemove = true else confirmAdd = true },
        modifier = modifier,
    ) {
        Icon(
            if (all) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = null,
            tint = if (all) WhiplashColors.accent else WhiplashColors.textPrimary,
            modifier = Modifier,
        )
    }
    FavoriteAllDialogs(name, tracks, confirmAdd, confirmRemove) { confirmAdd = false; confirmRemove = false }
}
