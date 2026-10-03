// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
package com.whiplash.music.ui.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.EXPLORE_GENRES
import com.whiplash.music.domain.model.ExploreGenre
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.common.ToastController
import com.whiplash.music.ui.home.HomeShelfRow
import com.whiplash.music.ui.home.HomeViewModel.ShelfItem
import com.whiplash.music.ui.home.ShelfSkeleton
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassSheet
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import kotlinx.coroutines.launch

/** Genre tile colours (start, end), dark enough for white text. */
private val TILE_COLORS = listOf(
    Color(0xFF1E6F5C) to Color(0xFF0F3D33),
    Color(0xFFB23A48) to Color(0xFF5E1A24),
    Color(0xFF3A5BA0) to Color(0xFF1C2E55),
    Color(0xFF8E44AD) to Color(0xFF4A1F5C),
    Color(0xFFC0506F) to Color(0xFF65233A),
    Color(0xFF2C3E70) to Color(0xFF141C38),
    Color(0xFF9A6B1E) to Color(0xFF4F370E),
    Color(0xFFD46A2E) to Color(0xFF6E3413),
    Color(0xFF1F8AA8) to Color(0xFF0E4656),
    Color(0xFF5B5B5B) to Color(0xFF262626),
    Color(0xFF7A2E2E) to Color(0xFF3B1414),
    Color(0xFFB8860B) to Color(0xFF5C4305),
)

/**
 * 4.3 Explore, shown on Search's start screen: New releases and Charts
 * shelves, then a Moods & genres grid. Tapping an album/playlist opens it;
 * long-press opens the full album/playlist menu; a genre opens its page.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ExploreSection(
    onOpenCollection: (YoutubePlaylistResult) -> Unit,
    onOpenGenre: (ExploreGenre) -> Unit,
    onPlayTrack: (PlayableItem) -> Unit,
) {
    val app = LocalContext.current.applicationContext as WhiplashApplication
    val viewModel: ExploreViewModel = viewModel(factory = ExploreViewModelFactory(app.youtubeSearchRepository))
    val shelves by viewModel.shelves.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val genreArt by viewModel.genreArt.collectAsState()
    var sheetCollection by remember { mutableStateOf<YoutubePlaylistResult?>(null) }
    val haptic = LocalHapticFeedback.current

    val onClick: (ShelfItem) -> Unit = { item ->
        when (item) {
            is ShelfItem.Collection -> onOpenCollection(item.collection)
            is ShelfItem.Track -> onPlayTrack(item.track)
        }
    }
    val onLongClick: (ShelfItem) -> Unit = { item ->
        if (item is ShelfItem.Collection) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sheetCollection = item.collection
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg)) {
        when {
            shelves.isNotEmpty() -> shelves.forEach { HomeShelfRow(it, it.items, onClick, onLongClick) }
            isLoading -> { ShelfSkeleton(); ShelfSkeleton() }
            else -> Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Couldn't load new releases and charts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                GlassButton(text = "Retry", onClick = viewModel::retry)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
            Text(
                text = "Moods & genres",
                style = MaterialTheme.typography.titleMedium,
                color = WhiplashColors.textPrimary,
            )
            EXPLORE_GENRES.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
                    pair.forEach { genre ->
                        androidx.compose.runtime.LaunchedEffect(genre.id) { viewModel.requestGenreArt(genre) }
                        GenreTile(genre, genreArt[genre.id], { onOpenGenre(genre) }, Modifier.weight(1f))
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }

    com.whiplash.music.ui.search.CollectionMenuSheet(sheetCollection, onDismiss = { sheetCollection = null }, onOpen = onOpenCollection)
}

/**
 * Genre tile: colour gradient with the name, and (once found) the cover of the
 * genre's top playlist tucked tilted into the right edge, Spotify-style. The
 * tile looks complete without artwork, so nothing jumps when it arrives.
 */
@Composable
private fun GenreTile(genre: ExploreGenre, artworkUrl: String?, onClick: () -> Unit, modifier: Modifier) {
    val (start, end) = TILE_COLORS[genre.colorIndex % TILE_COLORS.size]
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(WhiplashRadius.medium))
            .background(Brush.linearGradient(listOf(start, end)))
            .clickable(role = Role.Button, onClickLabel = "Open ${genre.title}", onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (artworkUrl != null) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current).data(artworkUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 14.dp, y = 10.dp)
                    .size(64.dp)
                    .graphicsLayer { rotationZ = 22f }
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Text(
            text = genre.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 2,
            modifier = Modifier.padding(start = GlassTokens.spaceMd, end = 64.dp),
        )
    }
}

// provenance: V2hpcGxhc2ggLSBTaGFoaWQgQW5zYXJp
/** A mood/genre page: its playlists shelf, then songs (with the usual song menus). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun GenreScreen(
    genre: ExploreGenre,
    onBack: () -> Unit,
    onOpenCollection: (YoutubePlaylistResult) -> Unit,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    val app = LocalContext.current.applicationContext as WhiplashApplication
    val viewModel: GenreViewModel = viewModel(
        key = "genre:${genre.id}",
        factory = GenreViewModelFactory(app.youtubeSearchRepository, genre),
    )
    val playlists by viewModel.playlists.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val failed by viewModel.failed.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var sheetCollection by remember { mutableStateOf<YoutubePlaylistResult?>(null) }
    val haptic = LocalHapticFeedback.current
    // Pull down to reload this genre's playlists and songs (same spinner as
    // Home and Explore, held until the reload finishes).
    val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            com.whiplash.music.ui.home.HomeRefreshIndicator(pullState, isRefreshing, Modifier.align(Alignment.TopCenter))
        },
    ) {
    PlayableItemsList(
        items = songs,
        onPlayQueue = onPlayQueue,
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd),
        header = {
            Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd)) {
                PlainIconButton(contentDescription = "Back", onClick = onBack, size = 48.dp) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
                }
                val shelf = playlists
                val heroArt = (shelf?.items?.firstOrNull() as? ShelfItem.Collection)?.collection?.artworkUrl
                GenreHero(
                    genre = genre,
                    artworkUrl = heroArt,
                    summary = listOfNotNull(
                        songs.size.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
                        shelf?.items?.size?.takeIf { it > 0 }?.let { if (it == 1) "1 playlist" else "$it playlists" },
                    ).joinToString(" · ").ifBlank { if (isLoading) "Loading…" else "" },
                    onPlay = if (songs.isNotEmpty()) ({ onPlayQueue(songs, 0) }) else null,
                    onShuffle = if (songs.isNotEmpty()) ({ onPlayQueue(songs.shuffled(), 0) }) else null,
                )
                when {
                    shelf != null -> HomeShelfRow(
                        shelf, shelf.items,
                        onClick = { (it as? ShelfItem.Collection)?.let { c -> onOpenCollection(c.collection) } },
                        onLongClick = {
                            (it as? ShelfItem.Collection)?.let { c ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                sheetCollection = c.collection
                            }
                        },
                    )
                    isLoading -> ShelfSkeleton()
                }
                if (failed) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Couldn't load ${genre.title}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WhiplashColors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        GlassButton(text = "Retry", onClick = viewModel::load)
                    }
                } else if (songs.isNotEmpty()) {
                    Text(
                        text = "Songs",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = WhiplashColors.textPrimary,
                        modifier = Modifier.padding(top = GlassTokens.spaceSm).semantics { heading() },
                    )
                } else if (isLoading) {
                    repeat(4) { com.whiplash.music.ui.theme.ShimmerSkeletonRow() }
                }
            }
        },
    )
    }

    com.whiplash.music.ui.search.CollectionMenuSheet(sheetCollection, onDismiss = { sheetCollection = null }, onOpen = onOpenCollection)
}

/**
 * Genre page banner in the genre's own tile colours: its top playlist's
 * cover tilted into the right edge (larger than on the tile), a small
 * "Mood & genre" label, the name, what's on the page, and Play / Shuffle.
 */
@Composable
private fun GenreHero(
    genre: ExploreGenre,
    artworkUrl: String?,
    summary: String,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
) {
    val (start, end) = TILE_COLORS[genre.colorIndex % TILE_COLORS.size]
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(start, end))),
    ) {
        if (artworkUrl != null) {
            coil.compose.AsyncImage(
                model = coil.request.ImageRequest.Builder(LocalContext.current)
                    .data(com.whiplash.music.ui.common.artworkAtSize(artworkUrl, 544)).crossfade(true).build(),
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = 18.dp)
                    .size(132.dp)
                    .graphicsLayer { rotationZ = 18f }
                    .clip(RoundedCornerShape(14.dp)),
            )
        }
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = "MOOD & GENRE",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                ),
                color = Color.White.copy(alpha = 0.75f),
            )
            Text(
                text = genre.title,
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 2,
                modifier = Modifier
                    .padding(top = 2.dp, end = 96.dp)
                    .semantics { heading(); contentDescription = "${genre.title} page" },
            )
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                modifier = Modifier.padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                com.whiplash.music.ui.theme.CollectionPillButton(
                    text = "Play",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay?.invoke() },
                    primary = true,
                    enabled = onPlay != null,
                )
                com.whiplash.music.ui.theme.CollectionPillButton(
                    text = "Shuffle",
                    icon = Icons.Filled.Shuffle,
                    onClick = { onShuffle?.invoke() },
                    primary = false,
                    enabled = onShuffle != null,
                )
            }
        }
    }
}
