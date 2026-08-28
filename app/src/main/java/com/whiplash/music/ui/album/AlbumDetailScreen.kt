package com.whiplash.music.ui.album

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
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassIconButton
import com.whiplash.music.ui.theme.GlassTokens
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
        factory = AlbumDetailViewModelFactory(app.youtubeDetailProvider, url),
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
            is AlbumDetailUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WhiplashColors.accent)
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

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun AlbumDetailContent(
    detail: com.whiplash.music.domain.model.YoutubePlaylistDetail,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = GlassTokens.spaceMd),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(WhiplashRadius.extraLarge))
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
    }

    Text(
        text = detail.title,
        style = MaterialTheme.typography.headlineSmall,
        color = WhiplashColors.textPrimary,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    detail.uploaderName?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = WhiplashColors.textSecondary,
        )
    }

    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

    if (detail.tracks.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
            GlassButton(text = "Play", onClick = { onPlayQueue(detail.tracks, 0) })
            GlassButton(text = "Shuffle", onClick = { onPlayQueue(detail.tracks.shuffled(), 0) })
        }
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
        PlayableItemsList(
            items = detail.tracks,
            onPlayQueue = onPlayQueue,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Text(
            text = "No tracks found for this album.",
            style = MaterialTheme.typography.bodyMedium,
            color = WhiplashColors.textSecondary,
        )
    }
}
