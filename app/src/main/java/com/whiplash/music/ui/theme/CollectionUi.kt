package com.whiplash.music.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.whiplash.music.domain.model.PlayableItem

/*
 * Shared pieces for the modern Library, Favorites and Playlists pages: the
 * hero header, pill buttons, empty state and sort sheet. They use the same
 * flat card colour as Settings and the mini player so these pages read as
 * part of one set.
 */

/** Flat card colour shared with Settings and the mini player. */
@Composable
internal fun collectionCardColor(): Color = lerp(WhiplashColors.background, Color.White, 0.06f)

/** "12 songs", "1 song". */
internal fun songCountLabel(count: Int): String = if (count == 1) "1 song" else "$count songs"

/** "48 min", "1 hr 12 min"; null when there's nothing to add up. */
internal fun totalDurationLabel(totalMs: Long): String? {
    if (totalMs <= 0L) return null
    val minutes = ((totalMs + 30_000L) / 60_000L).coerceAtLeast(1L)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0L -> "$minutes min"
        rest == 0L -> "$hours hr"
        else -> "$hours hr $rest min"
    }
}

/** "12 songs · 48 min". */
internal fun collectionSummary(items: List<PlayableItem>): String =
    listOfNotNull(songCountLabel(items.size), totalDurationLabel(items.sumOf { it.durationMs.coerceAtLeast(0L) }))
        .joinToString(" · ")

/**
 * Rounded pill with an icon and label. [primary] fills it with the theme
 * accent (Play); otherwise it's a soft tonal pill (Shuffle). Scales down a
 * touch while pressed.
 */
@Composable
fun CollectionPillButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(GlassTokens.animFast), label = "pillScale")
    val container = if (primary) WhiplashColors.accent else lerp(WhiplashColors.background, Color.White, 0.10f)
    val content = if (primary) WhiplashColors.onAccent else WhiplashColors.textPrimary
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else GlassTokens.opacityDisabled }
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(container)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Header for a song collection (Liked songs, a playlist, a local album or
 * artist): cover on the left, a small kind label, title and summary on the
 * right, then Play / Shuffle pills and any extra [actions]. A soft wash of
 * [tint] fades down behind it.
 */
@Composable
fun CollectionHero(
    eyebrow: String,
    title: String,
    subtitle: String?,
    tint: Color,
    cover: @Composable (Modifier) -> Unit,
    onPlay: (() -> Unit)?,
    onShuffle: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = GlassTokens.spaceXs, bottom = GlassTokens.spaceSm)
            .clip(RoundedCornerShape(WhiplashRadius.large))
            .background(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.26f),
                    1f to collectionCardColor(),
                ),
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            cover(
                Modifier
                    .size(124.dp)
                    .clip(RoundedCornerShape(WhiplashRadius.medium)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold),
                    color = lerp(tint, Color.White, 0.45f),
                    maxLines = 1,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = WhiplashColors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (onPlay != null || onShuffle != null) {
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onPlay != null) {
                    CollectionPillButton("Play", Icons.Filled.PlayArrow, onPlay, primary = true, modifier = Modifier.weight(1f))
                }
                if (onShuffle != null) {
                    CollectionPillButton("Shuffle", Icons.Filled.Shuffle, onShuffle, primary = false, modifier = Modifier.weight(1f))
                }
                actions()
            }
        }
    }
}

/** A square tile filled with a diagonal [tint] gradient and a centred icon — cover for collections with no artwork. */
@Composable
fun GradientIconCover(icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(listOf(lerp(tint, Color.White, 0.15f), lerp(tint, Color.Black, 0.55f))),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize(0.42f))
    }
}

/**
 * Centred empty state: a tinted round icon, a title, a short hint and
 * optional buttons. Scrolls if the text is large on a short screen.
 */
@Composable
fun CollectionEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    tint: Color,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GlassTokens.spaceXl, vertical = GlassTokens.spaceLg)
                .padding(bottom = GlassTokens.miniPlayerReservedHeight / 2),
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = lerp(tint, Color.White, 0.35f), modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = WhiplashColors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = WhiplashColors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            actions()
        }
    }
}

/** Sort orders offered for song collections. */
enum class TrackSort(val label: String) {
    DEFAULT("Default"),
    TITLE("Title (A–Z)"),
    ARTIST("Artist (A–Z)"),
    LONGEST("Longest first"),
    ;

    fun <T : PlayableItem> apply(items: List<T>): List<T> = when (this) {
        DEFAULT -> items
        TITLE -> items.sortedBy { it.title.lowercase() }
        ARTIST -> items.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        LONGEST -> items.sortedByDescending { it.durationMs }
    }
}

/**
 * Bottom sheet listing sort options with a check on the current one.
 * [defaultLabel] names the natural order ("Recently added", "Playlist order").
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun TrackSortSheet(
    current: TrackSort,
    defaultLabel: String,
    onSelect: (TrackSort) -> Unit,
    onDismiss: () -> Unit,
) {
    GlassSheet(onDismissRequest = onDismiss) {
        Column {
            Text(
                text = "Sort by",
                style = MaterialTheme.typography.titleMedium,
                color = WhiplashColors.textPrimary,
                modifier = Modifier.padding(bottom = GlassTokens.spaceSm),
            )
            TrackSort.entries.forEach { sort ->
                val selected = sort == current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(WhiplashRadius.small))
                        .clickable(role = Role.RadioButton) { onSelect(sort) }
                        .padding(horizontal = GlassTokens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (sort == TrackSort.DEFAULT) defaultLabel else sort.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (selected) WhiplashColors.accent else WhiplashColors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = WhiplashColors.accent)
                }
            }
        }
    }
}

/** Stable, pleasant tint picked from a name, for covers with no artwork. */
internal fun tintForName(name: String): Color {
    val palette = listOf(
        Color(0xFF7C6CF2), Color(0xFFE0648B), Color(0xFF3FA7D6), Color(0xFF4DB88A),
        Color(0xFFE39B4B), Color(0xFFB36BD8), Color(0xFF5B8DEF), Color(0xFFD9695F),
    )
    return palette[(name.hashCode() and 0x7fffffff) % palette.size]
}

/** 46dp round tonal button used beside the Play / Shuffle pills on collection pages. */
@Composable
fun RoundActionButton(
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    PlainIconButton(contentDescription = contentDescription, onClick = onClick, size = 46.dp, enabled = enabled) {
        RoundActionSurface { content() }
    }
}

/** The tonal circle behind [RoundActionButton]; also wraps ready-made icon buttons so they match. */
@Composable
fun RoundActionSurface(content: @Composable () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(androidx.compose.foundation.shape.CircleShape).background(lerp(WhiplashColors.background, Color.White, 0.10f)),
        contentAlignment = Alignment.Center,
    ) { content() }
}
