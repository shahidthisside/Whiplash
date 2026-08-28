package com.whiplash.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistDetail
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassIconButton
import com.whiplash.music.ui.theme.GlassListItem
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.WhiplashColors

/**
 * Artist/channel detail screen (section 40): artwork, popular songs, real
 * albums when the channel exposes that tab, radio (reuses the existing,
 * already-verified autoplay/recommendation system — starting radio from an
 * artist just plays their first popular song, and the real autoplay
 * mechanism takes over from there, same honest reuse pattern as
 * "Start radio" in [com.whiplash.music.ui.player.SongActionsContent]).
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
        factory = ArtistDetailViewModelFactory(app.youtubeDetailProvider, channelUrl),
    )
    val state by viewModel.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(contentDescription = "Back", onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = WhiplashColors.textPrimary)
            }
        }

        when (val s = state) {
            is ArtistDetailUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WhiplashColors.accent)
            }
            is ArtistDetailUiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Couldn't load this artist",
                        style = MaterialTheme.typography.titleMedium,
                        color = WhiplashColors.textPrimary,
                    )
                    Text(text = s.message, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                    GlassButton(text = "Retry", onClick = viewModel::load)
                }
            }
            is ArtistDetailUiState.Loaded -> ArtistDetailContent(s.detail, onPlayQueue, onOpenAlbum)
        }
    }
}

@Composable
private fun ArtistDetailContent(
    detail: YoutubeArtistDetail,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    onOpenAlbum: (YoutubePlaylistResult) -> Unit,
) {
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(WhiplashColors.surfaceElevated),
                ) {
                    if (detail.artworkUrl != null) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(detail.artworkUrl).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
                Text(text = detail.name, style = MaterialTheme.typography.headlineSmall, color = WhiplashColors.textPrimary)
                detail.subscriberCount?.let {
                    Text(
                        text = "${com.whiplash.music.ui.common.formatCompactCount(it)} subscribers",
                        style = MaterialTheme.typography.bodySmall,
                        color = WhiplashColors.textSecondary,
                    )
                }
                if (detail.popularSongs.isNotEmpty()) {
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                    GlassButton(
                        text = "Radio",
                        onClick = { onPlayQueue(listOf(detail.popularSongs.first()), 0) },
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceLg))
            }
        }

        if (detail.popularSongs.isNotEmpty()) {
            item {
                Text(
                    text = "Popular songs",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(bottom = GlassTokens.spaceSm),
                )
            }
            items(detail.popularSongs, key = { "song:${it.id}" }) { track ->
                GlassListItem(
                    title = track.title,
                    subtitle = track.artist,
                    onClick = { onPlayQueue(detail.popularSongs, detail.popularSongs.indexOf(track)) },
                    leading = { GlassArtworkThumbnail(artworkUri = track.artworkUri) },
                )
            }
        }

        if (detail.albums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleMedium,
                    color = WhiplashColors.textPrimary,
                    modifier = Modifier.padding(top = GlassTokens.spaceMd, bottom = GlassTokens.spaceSm),
                )
            }
            items(detail.albums, key = { "album:${it.url}" }) { album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenAlbum(album) }
                        .padding(vertical = GlassTokens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassArtworkThumbnail(artworkUri = album.artworkUrl)
                    Column(modifier = Modifier.padding(start = GlassTokens.spaceSm)) {
                        Text(
                            text = album.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = WhiplashColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (detail.popularSongs.isEmpty() && detail.albums.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceLg), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No songs or albums found for this artist.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textSecondary,
                    )
                }
            }
        }
    }
}
