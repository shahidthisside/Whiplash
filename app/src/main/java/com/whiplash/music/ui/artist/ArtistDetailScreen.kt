package com.whiplash.music.ui.artist

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistDetail
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.common.ToastController
import com.whiplash.music.ui.common.artworkAtSize
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.CollectionPillButton
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.tintForName

/**
 * Artist/channel page: a large photo banner with the name over it, Play /
 * Shuffle / Radio / Download, the popular songs (the app's usual song rows,
 * with their long-press and ⋮ menus and download badges), then an Albums
 * shelf whose covers open the album and long-press for the album menu.
 *
 * Radio plays the artist's top song and lets autoplay carry on with related
 * music, the same as "Start radio" on a song.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun ArtistDetailScreen(
    channelUrl: String,
    onBack: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
    onOpenAlbum: (YoutubePlaylistResult) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: ArtistDetailViewModel = viewModel(
        key = "artist:$channelUrl",
        factory = ArtistDetailViewModelFactory(app.youtubeDetailProvider, channelUrl, app.youtubeSearchRepository),
    )
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
            val loaded = (state as? ArtistDetailUiState.Loaded)?.detail
            if (loaded != null) {
                Spacer(Modifier.weight(1f))
                PlainIconButton(contentDescription = "Share", onClick = { shareLink(context, displayName(loaded.name), loaded.channelUrl) }) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = WhiplashColors.textPrimary)
                }
            }
        }

        // Loading -> Error/Loaded crossfade, so the spinner doesn't vanish abruptly.
        AnimatedContent(
            // Target is the state itself so each pane renders its OWN state
            // while crossfading (the outgoing spinner stays a spinner instead
            // of redrawing the new content twice); contentKey keeps the
            // animation keyed by kind only, as before.
            targetState = state,
            contentKey = { s ->
                when (s) {
                    is ArtistDetailUiState.Loading -> "loading"
                    is ArtistDetailUiState.Error -> "error"
                    is ArtistDetailUiState.Loaded -> "loaded"
                }
            },
            transitionSpec = {
                fadeIn(animationSpec = tween(GlassTokens.animRegular))
                    .togetherWith(fadeOut(animationSpec = tween(GlassTokens.animFast)))
            },
            label = "artistDetailState",
        ) { s ->
            when (s) {
                is ArtistDetailUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    com.whiplash.music.ui.theme.CollectionPageSkeleton(round = true)
                }
                is ArtistDetailUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Couldn't load this artist",
                            style = MaterialTheme.typography.titleMedium,
                            color = WhiplashColors.textPrimary,
                        )
                        Text(text = s.message, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
                        Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                        GlassButton(text = "Retry", onClick = viewModel::load)
                    }
                }
                is ArtistDetailUiState.Loaded -> {
                    val found by viewModel.foundAlbums.collectAsState()
                    ArtistDetailContent(s.detail.copy(albums = s.detail.albums.ifEmpty { found }), onPlayQueue, onOpenAlbum)
                }
            }
        }
    }
}

/** Auto-generated YouTube Music channels are named "Artist - Topic"; show just the artist. */
private fun displayName(name: String): String = name.removeSuffix(" - Topic").ifBlank { name }

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ArtistDetailContent(
    detail: YoutubeArtistDetail,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    onOpenAlbum: (YoutubePlaylistResult) -> Unit,
) {
    val songs = detail.popularSongs
    var albumMenu by remember { mutableStateOf<YoutubePlaylistResult?>(null) }

    PlayableItemsList(
        items = songs,
        onPlayQueue = onPlayQueue,
        modifier = Modifier.fillMaxSize(),
        header = {
            Column {
                ArtistHero(detail, onPlayQueue)
                when {
                    songs.isNotEmpty() -> SectionTitle("Popular songs")
                    detail.albums.isEmpty() -> Text(
                        text = "No songs or albums found for this artist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceLg),
                    )
                }
            }
        },
        footer = if (detail.albums.isNotEmpty()) ({
            Column(Modifier.padding(top = GlassTokens.spaceMd)) {
                SectionTitle("Albums")
                AlbumShelf(detail.albums, onOpenAlbum, onLongPress = { albumMenu = it })
            }
        }) else null,
    )

    com.whiplash.music.ui.search.CollectionMenuSheet(albumMenu, onDismiss = { albumMenu = null }, onOpen = onOpenAlbum)
}

/**
 * Photo banner: the artist picture at full width (fetched large, so it
 * stays sharp), fading into the page at the bottom where the name sits,
 * then the action row.
 */
@Composable
private fun ArtistHero(detail: YoutubeArtistDetail, onPlayQueue: (List<PlayableItem>, Int) -> Unit) {
    val context = LocalContext.current
    val name = displayName(detail.name)
    val songs = detail.popularSongs
    Column(Modifier.fillMaxWidth().padding(bottom = GlassTokens.spaceSm)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.1f)
                .clip(RoundedCornerShape(28.dp))
                .background(lerp(WhiplashColors.background, tintForName(name), 0.45f)),
        ) {
            if (detail.artworkUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(artworkAtSize(detail.artworkUrl, 1200)).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = name.take(1).uppercase(),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = com.whiplash.music.ui.theme.inkOn(lerp(WhiplashColors.background, tintForName(name), 0.45f)),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Fade to the page colour so the name reads on any photo.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to WhiplashColors.background.copy(alpha = 0.92f),
                        ),
                    ),
            )
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                Text(
                    text = "ARTIST",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
                    ),
                    color = WhiplashColors.textSecondary,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                    color = WhiplashColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                val facts = listOfNotNull(
                    detail.subscriberCount?.let { "${com.whiplash.music.ui.common.formatCompactCount(it)} subscribers" },
                    songs.size.takeIf { it > 0 }?.let { if (it == 1) "1 song" else "$it songs" },
                    detail.albums.size.takeIf { it > 0 }?.let { if (it == 1) "1 album" else "${it} albums" },
                ).joinToString(" · ")
                if (facts.isNotBlank()) {
                    Text(text = facts, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
                }
            }
        }
        if (songs.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollectionPillButton("Play", Icons.Filled.PlayArrow, { onPlayQueue(songs, 0) }, primary = true, modifier = Modifier.weight(1f))
                com.whiplash.music.ui.theme.RoundActionButton(
                    contentDescription = "Shuffle $name",
                    onClick = { onPlayQueue(songs.shuffled(), 0) },
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(22.dp))
                }
                com.whiplash.music.ui.theme.RoundActionButton(
                    contentDescription = "Start $name radio",
                    onClick = { onPlayQueue(listOf(songs.first()), 0) },
                ) {
                    Icon(Icons.Filled.Radio, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(22.dp))
                }
                com.whiplash.music.ui.theme.RoundActionSurface {
                    com.whiplash.music.ui.common.BatchDownloadIconButton(batchName = name, tracks = songs)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        color = WhiplashColors.textPrimary,
        modifier = Modifier.padding(top = GlassTokens.spaceSm, bottom = GlassTokens.spaceXs).semantics { heading() },
    )
}

/** Sideways row of album covers. Tap opens the album; long-press opens its menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumShelf(
    albums: List<YoutubePlaylistResult>,
    onOpen: (YoutubePlaylistResult) -> Unit,
    onLongPress: (YoutubePlaylistResult) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = GlassTokens.spaceXs),
    ) {
        // Index-prefixed so a repeated album URL (YouTube channel album
        // lists are raw extraction and can contain the same playlist twice)
        // can't produce a duplicate LazyRow key and crash the page.
        itemsIndexed(albums, key = { index, album -> "$index:${album.url}" }) { _, album ->
            // Only the cover is clipped, so the title's first letter is never shaved.
            Column(
                Modifier
                    .width(150.dp)
                    .combinedClickable(
                        onClickLabel = "Open ${album.title}",
                        onLongClickLabel = "More options",
                        onClick = { onOpen(album) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(album)
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(WhiplashColors.surfaceElevated),
                ) {
                    if (album.artworkUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(artworkAtSize(album.artworkUrl, 544)).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = WhiplashColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun shareLink(context: android.content.Context, title: String, url: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, url)
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "Share $title")) }
        .onFailure { ToastController.show("Couldn't open share") }
}
