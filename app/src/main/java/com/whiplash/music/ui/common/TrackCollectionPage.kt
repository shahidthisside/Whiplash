package com.whiplash.music.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.player.PlaylistContext
import com.whiplash.music.ui.theme.CollectionHero
import com.whiplash.music.ui.theme.GlassSearchField
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.TrackSort
import com.whiplash.music.ui.theme.TrackSortSheet
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.collectionSummary

/**
 * A song collection page body: the [CollectionHero] header, then a filter
 * field and sort button, then the songs. The header scrolls with the list
 * so large covers never trap the songs below the fold.
 *
 * Play and Shuffle use what's on screen, so a sorted or filtered view plays
 * in the order shown. [emptyContent] replaces the list when there are no
 * songs at all.
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun TrackCollectionPage(
    items: List<PlayableItem>,
    eyebrow: String,
    title: String,
    tint: Color,
    cover: @Composable (Modifier) -> Unit,
    onPlayQueue: (List<PlayableItem>, Int) -> Unit,
    sortKey: String,
    defaultSortLabel: String,
    emptyContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    subtitlePrefix: String? = null,
    playlistContext: PlaylistContext? = null,
    heroActions: @Composable RowScope.() -> Unit = {},
) {
    var sortName by rememberSaveable(sortKey) { mutableStateOf(TrackSort.DEFAULT.name) }
    val sort = TrackSort.valueOf(sortName)
    var query by rememberSaveable(sortKey) { mutableStateOf("") }
    var showSort by remember { mutableStateOf(false) }

    val visible = remember(items, sort, query) {
        val q = query.trim()
        val filtered = if (q.isEmpty()) items else items.filter {
            it.title.contains(q, ignoreCase = true) ||
                it.artist.contains(q, ignoreCase = true) ||
                it.album?.contains(q, ignoreCase = true) == true
        }
        sort.apply(filtered)
    }
    val summary = listOfNotNull(subtitlePrefix, collectionSummary(items)).joinToString(" · ")

    val header: @Composable () -> Unit = {
        Column {
            CollectionHero(
                eyebrow = eyebrow,
                title = title,
                subtitle = if (items.isEmpty()) listOfNotNull(subtitlePrefix, "No songs yet").joinToString(" · ") else summary,
                tint = tint,
                cover = cover,
                onPlay = if (visible.isNotEmpty()) ({ onPlayQueue(visible, 0) }) else null,
                onShuffle = if (visible.isNotEmpty()) ({ onPlayQueue(visible.shuffled(), 0) }) else null,
                actions = heroActions,
            )
            if (items.size >= 2) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = GlassTokens.spaceXs, bottom = GlassTokens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlassSearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Filter songs",
                        modifier = Modifier.weight(1f),
                    )
                    PlainIconButton(
                        contentDescription = "Sort songs, ${if (sort == TrackSort.DEFAULT) defaultSortLabel else sort.label}",
                        onClick = { showSort = true },
                        size = 48.dp,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = if (sort == TrackSort.DEFAULT) WhiplashColors.textSecondary else WhiplashColors.accent,
                        )
                    }
                }
            }
            if (items.isNotEmpty() && visible.isEmpty()) {
                Text(
                    text = "No songs match \"${query.trim()}\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceLg),
                )
            }
        }
    }

    if (items.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            header()
            Box(modifier = Modifier.weight(1f)) { emptyContent() }
        }
    } else {
        PlayableItemsList(
            items = visible,
            onPlayQueue = onPlayQueue,
            modifier = modifier.fillMaxSize(),
            header = header,
            playlistContext = playlistContext,
        )
    }

    if (showSort) {
        TrackSortSheet(
            current = sort,
            defaultLabel = defaultSortLabel,
            onSelect = { sortName = it.name; showSort = false },
            onDismiss = { showSort = false },
        )
    }
}
