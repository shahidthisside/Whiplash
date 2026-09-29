package com.whiplash.music.ui.album

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/** Album/playlist detail screen (section 39): large artwork, real track listing, play/shuffle. */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AlbumDetailScreen(
    url: String,
    onBack: () -> Unit,
    onPlayQueue: (queue: List<PlayableItem>, startIndex: Int) -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: AlbumDetailViewModel = viewModel(
        key = "album:$url",
        factory = AlbumDetailViewModelFactory(app.youtubeDetailProvider, url),
    )
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        // Back on the left, Share on the right once the page has loaded.
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
            if (state is AlbumDetailUiState.Loaded) {
                val loadedDetail = (state as AlbumDetailUiState.Loaded).detail
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                PlainIconButton(
                    contentDescription = "Share",
                    onClick = { shareYoutubePlaylist(context, loadedDetail) },
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = WhiplashColors.textPrimary)
                }
            }
        }

        // Loading -> Error/Loaded crossfade — a plain fade (this is a
        // vertical, one-directional progression with no "back" concept),
        // so the loading spinner doesn't just vanish/appear abruptly once
        // the real network fetch resolves. Keyed on the state's own class
        // via a small string tag rather than the whole state instance, so
        // two different Loaded emissions (shouldn't normally happen for
        // the same screen instance, but harmless either way) don't
        // needlessly replay the crossfade.
        AnimatedContent(
            // Target is the state itself so each pane renders its OWN state
            // while crossfading (the outgoing spinner stays a spinner instead
            // of redrawing the new content twice); contentKey keeps the
            // animation keyed by kind only, as before.
            targetState = state,
            contentKey = { s ->
                when (s) {
                    is AlbumDetailUiState.Loading -> "loading"
                    is AlbumDetailUiState.Error -> "error"
                    is AlbumDetailUiState.Loaded -> "loaded"
                }
            },
            transitionSpec = {
                fadeIn(animationSpec = tween(GlassTokens.animRegular))
                    .togetherWith(fadeOut(animationSpec = tween(GlassTokens.animFast)))
            },
            label = "albumDetailState",
        ) { s ->
            when (s) {
                is AlbumDetailUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    com.whiplash.music.ui.theme.CollectionPageSkeleton()
                }
                is AlbumDetailUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Couldn't load this album",
                            style = MaterialTheme.typography.titleMedium,
                            color = WhiplashColors.textPrimary,
                        )
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = WhiplashColors.textSecondary,
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                        GlassButton(text = "Retry", onClick = viewModel::load)
                    }
                }
                is AlbumDetailUiState.Loaded -> AlbumDetailContent(s.detail, onPlayQueue)
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AlbumDetailContent(
    detail: com.whiplash.music.domain.model.YoutubePlaylistDetail,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    if (detail.tracks.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlbumDetailHeader(detail, onPlayQueue)
            Text(
                text = "No tracks found for this album.",
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
            )
        }
        return
    }

    // The header (artwork/title/Play/Shuffle) is rendered as the first
    // item of this same LazyColumn — not a separate non-scrolling Column
    // above it — so the whole screen scrolls together. Previously the
    // header lived outside the list, and the artwork's aspectRatio(1f)
    // box (as tall as the screen is wide) could push the track list below
    // the viewport with no way to scroll back up past it — reported as
    // "album art stuck on screen, not scrolling."
    PlayableItemsList(
        items = detail.tracks,
        onPlayQueue = onPlayQueue,
        modifier = Modifier.fillMaxSize(),
        header = { AlbumDetailHeader(detail, onPlayQueue) },
    )
}

/** YouTube titles albums "Album – Name"; the page shows the kind as its own label instead. */
private val ALBUM_PREFIX = Regex("^Album\\s*[–-]\\s*", RegexOption.IGNORE_CASE)

private fun isAlbum(detail: com.whiplash.music.domain.model.YoutubePlaylistDetail): Boolean =
    ALBUM_PREFIX.containsMatchIn(detail.title) || "list=OLAK5uy" in detail.url

/**
 * Album / playlist header: the cover large and centred (sharpest available,
 * see [AlbumCover]) over a wash of the cover's own colours, then the kind,
 * title, "artist · songs · length", and Play / Shuffle with round Save to
 * Playlists and Download buttons beside them.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AlbumDetailHeader(
    detail: com.whiplash.music.domain.model.YoutubePlaylistDetail,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    val album = isAlbum(detail)
    val title = detail.title.replace(ALBUM_PREFIX, "").ifBlank { detail.title }
    val candidates = (detail.artworkCandidates + listOfNotNull(detail.artworkUrl, detail.tracks.firstOrNull()?.artworkUri)).distinct()
    val palette by com.whiplash.music.ui.player.rememberArtworkPalette(candidates.lastOrNull(), enabled = true)
    val wash by androidx.compose.animation.animateColorAsState(
        palette?.mesh?.firstOrNull()?.let { androidx.compose.ui.graphics.Color(it) }
            ?: com.whiplash.music.ui.theme.tintForName(title).copy(alpha = 0.35f),
        tween(GlassTokens.animSlow),
        label = "albumWash",
    )
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = GlassTokens.spaceMd)
            .clip(RoundedCornerShape(28.dp))
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(wash, WhiplashColors.background)))
            .padding(horizontal = 18.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .shadow(24.dp, shape)
                .clip(shape)
                .background(WhiplashColors.surfaceElevated),
        ) {
            AlbumCover(candidates = candidates)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
        Text(
            text = if (album) "ALBUM" else "PLAYLIST",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing * 1.5f,
            ),
            color = WhiplashColors.textSecondary,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = WhiplashColors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp).semantics { heading() },
        )
        val summary = listOfNotNull(
            detail.uploaderName?.takeIf { it.isNotBlank() },
            detail.tracks.takeIf { it.isNotEmpty() }?.let { com.whiplash.music.ui.theme.collectionSummary(it) },
        ).joinToString(" · ")
        if (summary.isNotBlank()) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (detail.tracks.isNotEmpty()) {
            androidx.compose.foundation.layout.Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.whiplash.music.ui.theme.CollectionPillButton(
                    "Play", Icons.Filled.PlayArrow, { onPlayQueue(detail.tracks, 0) }, primary = true, modifier = Modifier.weight(1f),
                )
                // Round buttons beside the wide Play pill, so every label fits
                // even at large font sizes.
                com.whiplash.music.ui.theme.RoundActionButton(
                    contentDescription = "Shuffle $title",
                    onClick = { onPlayQueue(detail.tracks.shuffled(), 0) },
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null, tint = WhiplashColors.textPrimary, modifier = Modifier.size(22.dp))
                }
                com.whiplash.music.ui.search.SaveCollectionIconButton(
                    url = detail.url,
                    title = title,
                    isAlbum = album,
                    tracks = detail.tracks,
                )
                com.whiplash.music.ui.theme.RoundActionSurface {
                    com.whiplash.music.ui.common.FavoriteAllIconButton(name = title, tracks = detail.tracks)
                }
                com.whiplash.music.ui.theme.RoundActionSurface {
                    com.whiplash.music.ui.common.BatchDownloadIconButton(batchName = title, tracks = detail.tracks)
                }
            }
        }
    }
}

/**
 * Shares a real, working YouTube playlist/album URL via Android's native
 * share sheet — mirrors [com.whiplash.music.ui.player.shareYoutubeTrack]'s
 * exact pattern for a single track, just for a whole playlist/album.
 */
private fun shareYoutubePlaylist(context: android.content.Context, detail: com.whiplash.music.domain.model.YoutubePlaylistDetail) {
    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, "${detail.title} — ${detail.url}")
    }
    context.startActivity(android.content.Intent.createChooser(sendIntent, "Share playlist"))
}

/**
 * Album cover that never settles for less than it can get, and never shows
 * nothing when any cover exists.
 *
 * [candidates] are sharpest first. The sharpest one still working is drawn on
 * top; the next one down is drawn underneath it. The smaller image usually
 * arrives first (and is often already cached from the Home card), so the
 * cover appears straight away and then sharpens as the big one lands. If the
 * big one doesn't exist (YouTube has no 1200px cover for some albums), it is
 * dropped and the next size moves up, so the result is always the best
 * available — never blank, and never a lower size than YouTube offers.
 */
@Composable
private fun AlbumCover(candidates: List<String>) {
    val context = LocalContext.current
    var failed by remember(candidates) { mutableStateOf(emptySet<String>()) }
    val working = candidates.filter { it !in failed }
    val top = working.getOrNull(0)
    val under = working.getOrNull(1)
    if (under != null) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(under).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = failed + under },
            modifier = Modifier.fillMaxSize(),
        )
    }
    if (top != null) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(top).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            onError = { failed = failed + top },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
