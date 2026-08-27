package com.whiplash.music.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Small square artwork thumbnail for list rows (songs/albums/artists,
 * search results, queue) — section 29: "use appropriate image sizes for
 * lists versus full-screen player" (this requests a much smaller decode
 * target than the full player's artwork). Falls back to a plain tonal
 * placeholder box when no artwork URL exists, rather than leaving a gap.
 */
@Composable
fun GlassArtworkThumbnail(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(WhiplashRadius.small),
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(WhiplashColors.surfaceSheet),
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
