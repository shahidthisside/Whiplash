package com.whiplash.music.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.whiplash.music.domain.model.ShelfKind
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.ShimmerBox
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius

/** Card width on shelves; wide enough for two lines of text, narrow enough to hint at more. */
private val SHELF_CARD_WIDTH = 148.dp

/**
 * 4.1 hero card: the first shelf's first album/playlist, shown wide. A blurred
 * copy of the cover fills the card (Android 12+; older devices get the plain
 * cover under a darker scrim), with the sharp cover and text on top.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ShelfHeroCard(
    label: String,
    item: HomeViewModel.ShelfItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (title, subtitle, art) = item.display()
    val shape = RoundedCornerShape(WhiplashRadius.large)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(WhiplashColors.surfaceElevated)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "More options")
            .semantics(mergeDescendants = true) { contentDescription = "$label: $title, $subtitle" },
    ) {
        if (art != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(art).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(28.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = if (android.os.Build.VERSION.SDK_INT >= 31) 0.65f else 0.8f)),
                    ),
                ),
        )
        Row(
            modifier = Modifier.fillMaxSize().padding(GlassTokens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(WhiplashRadius.medium))
                    .background(WhiplashColors.surfaceGlass),
                contentAlignment = Alignment.Center,
            ) {
                CardArtwork(art)
            }
            Column(
                modifier = Modifier.weight(1f).padding(start = GlassTokens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
            ) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 1,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** One titled, horizontally scrolling shelf. */
@Composable
internal fun HomeShelfRow(
    shelf: HomeViewModel.HomeShelf,
    items: List<HomeViewModel.ShelfItem>,
    onClick: (HomeViewModel.ShelfItem) -> Unit,
    onLongClick: (HomeViewModel.ShelfItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        Text(
            text = shelf.spec.title,
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = GlassTokens.spaceSm),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
            contentPadding = PaddingValues(end = GlassTokens.spaceMd),
        ) {
            items(items, key = { it.key }) { item ->
                ShelfCard(item = item, onClick = { onClick(item) }, onLongClick = { onLongClick(item) })
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShelfCard(
    item: HomeViewModel.ShelfItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val (title, subtitle, art) = item.display()
    // Songs get round-cornered squares, collections the same; a small play
    // badge marks the cards that play straight away instead of opening.
    Column(
        modifier = Modifier
            .width(SHELF_CARD_WIDTH)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = if (item is HomeViewModel.ShelfItem.Track) "Play" else "Open",
                onLongClickLabel = "More options",
            )
            .semantics(mergeDescendants = true) {},
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(WhiplashRadius.medium))
                .background(WhiplashColors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            CardArtwork(art)
            if (item is HomeViewModel.ShelfItem.Track) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(GlassTokens.spaceSm)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = WhiplashColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = GlassTokens.spaceSm),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CardArtwork(art: String?) {
    if (art != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(art).crossfade(true).build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        Icon(
            Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = null,
            tint = WhiplashColors.textSecondary,
            modifier = Modifier.size(40.dp),
        )
    }
}

/** Placeholder hero shaped exactly like [ShelfHeroCard]. */
@Composable
internal fun ShelfHeroSkeleton() {
    ShimmerBox(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = RoundedCornerShape(WhiplashRadius.large),
    )
}

/** Placeholder shelf shaped exactly like [HomeShelfRow] (title + three cards). */
@Composable
internal fun ShelfSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        ShimmerBox(modifier = Modifier.padding(top = GlassTokens.spaceSm).width(160.dp).height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd)) {
            repeat(3) {
                Column(modifier = Modifier.width(SHELF_CARD_WIDTH)) {
                    ShimmerBox(
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        shape = RoundedCornerShape(WhiplashRadius.medium),
                    )
                    ShimmerBox(modifier = Modifier.padding(top = GlassTokens.spaceSm).fillMaxWidth(0.8f).height(14.dp))
                    ShimmerBox(modifier = Modifier.padding(top = GlassTokens.spaceXs).fillMaxWidth(0.5f).height(12.dp))
                }
            }
        }
    }
}

/** Long-press menu for an album or playlist card. */
@Composable
internal fun CollectionActionsContent(
    collection: YoutubePlaylistResult,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onShare: () -> Unit,
) {
    Column {
        Text(
            text = collection.title,
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        collection.subtitle().takeIf { it.isNotBlank() }?.let {
            Text(text = it, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary, maxLines = 1)
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(GlassTokens.spaceSm))
        val kind = if (collection.isAlbum) "album" else "playlist"
        ActionRow(Icons.AutoMirrored.Filled.OpenInNew, "Open $kind", onOpen)
        ActionRow(Icons.Filled.PlayArrow, "Play", onPlay)
        ActionRow(Icons.Filled.Shuffle, "Shuffle", onShuffle)
        ActionRow(Icons.Filled.Share, "Share", onShare)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = GlassTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = WhiplashColors.textPrimary)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = WhiplashColors.textPrimary,
            modifier = Modifier.padding(start = GlassTokens.spaceMd),
        )
    }
}

private fun YoutubePlaylistResult.subtitle(): String {
    val kind = if (isAlbum) "Album" else "Playlist"
    val count = trackCount?.takeIf { it > 0 }?.let { if (it == 1L) "1 song" else "$it songs" }
    return listOfNotNull(kind, uploaderName?.takeIf { it.isNotBlank() }, count).joinToString(" · ")
}

/** (title, subtitle, artwork URL) for any card type. */
private fun HomeViewModel.ShelfItem.display(): Triple<String, String, String?> = when (this) {
    is HomeViewModel.ShelfItem.Track -> Triple(track.title, track.artist, track.artworkUri)
    is HomeViewModel.ShelfItem.Collection -> Triple(collection.title, collection.subtitle(), collection.artworkUrl)
}

/** Hero label for a shelf, e.g. "Featured album". */
internal fun heroLabel(kind: ShelfKind): String = when (kind) {
    ShelfKind.ALBUMS -> "Featured album"
    ShelfKind.PLAYLISTS -> "Featured playlist"
    ShelfKind.SONGS -> "Featured song"
}
