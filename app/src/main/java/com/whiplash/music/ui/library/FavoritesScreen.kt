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
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

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

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        // Header row renders in both the populated and empty states, so the
        // Back affordance never disappears just because there is nothing to
        // show yet — the previous early return for the empty case skipped this
        // row entirely.
        //
        // Back mirrors what the system back gesture already does from any
        // non-Home tab, and puts this screen's header in the same shape as the
        // playlist detail header: navigation on the left, actions on the right.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = GlassTokens.spaceSm, bottom = GlassTokens.spaceSm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = WhiplashColors.textPrimary,
                )
            }
            if (favorites.isNotEmpty()) {
                // Same reasoning as the playlist detail header: a PlainIconButton
                // is a 48dp box around a 24dp icon and already carries 12dp of
                // padding per side, so extra spacing here pushed the icons 32dp
                // apart. Butted together they sit the standard 24dp apart, and
                // these two rows stay visually identical to each other.
                Row {
                    PlainIconButton(contentDescription = "Shuffle play", onClick = { onPlayQueue(favorites.shuffled(), 0) }) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = WhiplashColors.textPrimary)
                    }
                    PlainIconButton(contentDescription = "Play all", onClick = { onPlayQueue(favorites, 0) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = WhiplashColors.textPrimary)
                    }
                }
            }
        }

        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No favorites yet. Long-press any song to add it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textSecondary,
                    modifier = Modifier.padding(GlassTokens.spaceLg),
                )
            }
            return
        }

        PlayableItemsList(
            items = favorites,
            onPlayQueue = onPlayQueue,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
