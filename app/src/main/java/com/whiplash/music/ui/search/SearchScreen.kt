package com.whiplash.music.ui.search

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.YoutubeArtistResult
import com.whiplash.music.domain.model.YoutubePlaylistResult
import com.whiplash.music.ui.player.PlayableItemsList
import com.whiplash.music.ui.theme.GlassArtworkThumbnail
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassCard
import com.whiplash.music.ui.theme.GlassSearchField
import com.whiplash.music.ui.theme.GlassTabRow
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.PlainIconButton
import com.whiplash.music.ui.theme.WhiplashColors

enum class SearchResultTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists"),
}

/**
 * YouTube Music search screen — the app's primary user-facing entry point
 * into the online playback feature (search -> tap result -> play, wired to
 * the already-verified [com.whiplash.music.playback.controller.PlaybackController]
 * end-to-end path built in Phase 7d).
 *
 * Section 32/37: real Songs/Albums/Artists/Playlists tabs, each backed by a
 * distinct, genuine NewPipeExtractor search content filter (confirmed via
 * direct inspection of the library's compiled constants — music_songs/
 * music_albums/music_playlists/music_artists all exist and return real
 * metadata, not fabricated). Tapping a song result plays just that track
 * (not the whole result list — queuing every visible search result would
 * fight with the autoplay/recommendation system, which is the actual
 * source of "what plays next" once this track is resolved). Long-press on
 * a song gives play-next/add-to-queue/favorite actions via the shared
 * [PlayableItemsList].
 */
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun SearchScreen(
    onPlayTrack: (PlayableItem.YoutubeTrack) -> Unit,
    onOpenAlbum: (YoutubePlaylistResult) -> Unit = {},
    onOpenArtist: (YoutubeArtistResult) -> Unit = {},
    selectedTab: SearchResultTab = SearchResultTab.SONGS,
    onSelectedTabChange: (SearchResultTab) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModelFactory(app.youtubeSearchRepository))
    val state by viewModel.state.collectAsState()
    val recentSearches by viewModel.recentSearches.collectAsState()
    val trendingArtists by viewModel.trendingArtists.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // "Clear all" recent searches is destructive and irreversible, same as
    // Speed dial's own "Clear" button — same confirmation pattern (section:
    // destructive actions need a confirmation step) rather than wiping the
    // whole list on a single accidental tap.
    var showClearSearchHistoryConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Tapping any result to act on it (play a song, open an album/artist)
    // should close the keyboard automatically — leaving it open after the
    // user has clearly moved on to a result is a real, reported UX gap,
    // not just a cosmetic nit (it blocks part of the screen and requires
    // an extra manual dismiss for no benefit).
    fun dismissKeyboard() {
        keyboardController?.hide()
        focusManager.clearFocus()
    }
    val handlePlayTrack: (PlayableItem.YoutubeTrack) -> Unit = { track ->
        dismissKeyboard()
        onPlayTrack(track)
    }
    val handleOpenAlbum: (YoutubePlaylistResult) -> Unit = { album ->
        dismissKeyboard()
        onOpenAlbum(album)
    }
    val handleOpenArtist: (YoutubeArtistResult) -> Unit = { artist ->
        dismissKeyboard()
        onOpenArtist(artist)
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd)) {
        GlassSearchField(
            query = state.query,
            onQueryChange = viewModel::onQueryChanged,
            placeholder = "Search songs, artists...",
            onSearchAction = {
                dismissKeyboard()
                viewModel.submitSearch(state.query)
            },
            modifier = Modifier.fillMaxWidth(),
        )

        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))

        when {
            // A blank query always means idle/recent-searches, full stop.
            state.query.isBlank() -> IdleState(
                recentSearches = recentSearches,
                trendingArtists = trendingArtists,
                onSuggestionTap = { query ->
                    dismissKeyboard()
                    viewModel.submitSearch(query)
                },
                onRemoveRecentSearch = viewModel::removeRecentSearch,
                onClearRecentSearches = { showClearSearchHistoryConfirm = true },
            )
            // YouTube Music/Spotify-style: typing alone only ever narrows
            // live autocomplete suggestions — it never runs a real search.
            // A real search only happens once the user commits (tapping a
            // suggestion/recent search, or the keyboard's search action —
            // see submitSearch). isSearching is checked *before* this
            // suggestions branch (not after) — a real, reported bug:
            // the instant a suggestion is tapped, hasSearched is still
            // false for a frame or two (it only flips true once the real
            // search's results actually land), so with isSearching
            // checked second, that whole "actively searching but not
            // done yet" window matched this branch instead and rendered
            // a bare blank screen once submitSearch clears suggestions
            // (the same field this branch reads) — the loading skeleton
            // never got a chance to show at all, and for a search that
            // fully resolved in a few hundred ms, a plain black gap
            // was often all a real device/network combination made
            // actually visible.
            state.isSearching && state.results.isEmpty() && state.albums.isEmpty() &&
                state.artists.isEmpty() && state.playlists.isEmpty() -> LoadingState()
            !state.hasSearched -> SuggestionsState(
                suggestions = state.suggestions,
                onSuggestionTap = { suggestion ->
                    dismissKeyboard()
                    viewModel.onSuggestionTapped(suggestion)
                },
            )
            state.results.isEmpty() && state.errorMessage != null -> ErrorState(state.errorMessage!!, onRetry = viewModel::retry)
            state.results.isEmpty() && state.albums.isEmpty() && state.artists.isEmpty() &&
                state.playlists.isEmpty() && state.hasSearched -> NoResultsState()
            else -> {
                GlassTabRow(
                    items = SearchResultTab.entries,
                    selected = selectedTab,
                    onSelect = onSelectedTabChange,
                    label = { tab ->
                        val count = when (tab) {
                            SearchResultTab.SONGS -> state.results.size
                            SearchResultTab.ALBUMS -> state.albums.size
                            SearchResultTab.ARTISTS -> state.artists.size
                            SearchResultTab.PLAYLISTS -> state.playlists.size
                        }
                        if (count > 0) "${tab.label} ($count)" else tab.label
                    },
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                when (selectedTab) {
                    SearchResultTab.SONGS -> ResultsList(
                        results = state.results,
                        isRefreshing = state.isSearching,
                        onPlayTrack = handlePlayTrack,
                        onLoadMore = { viewModel.loadMore(SearchResultTab.SONGS) },
                        isLoadingMore = state.isLoadingMoreSongs,
                    )
                    SearchResultTab.ALBUMS -> PlaylistResultsList(
                        items = state.albums,
                        isAlbum = true,
                        onClick = handleOpenAlbum,
                        onLoadMore = { viewModel.loadMore(SearchResultTab.ALBUMS) },
                        isLoadingMore = state.isLoadingMoreAlbums,
                    )
                    SearchResultTab.PLAYLISTS -> PlaylistResultsList(
                        items = state.playlists,
                        isAlbum = false,
                        onClick = handleOpenAlbum,
                        onLoadMore = { viewModel.loadMore(SearchResultTab.PLAYLISTS) },
                        isLoadingMore = state.isLoadingMorePlaylists,
                    )
                    SearchResultTab.ARTISTS -> ArtistResultsList(
                        items = state.artists,
                        onClick = handleOpenArtist,
                        onLoadMore = { viewModel.loadMore(SearchResultTab.ARTISTS) },
                        isLoadingMore = state.isLoadingMoreArtists,
                    )
                }
            }
        }
    }

    if (showClearSearchHistoryConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Clear recent searches?",
            message = "This removes all of your recent searches. This can't be undone.",
            onConfirm = {
                viewModel.clearRecentSearches()
                showClearSearchHistoryConfirm = false
            },
            onDismiss = { showClearSearchHistoryConfirm = false },
        )
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun ResultsList(
    results: List<PlayableItem.YoutubeTrack>,
    isRefreshing: Boolean,
    onPlayTrack: (PlayableItem.YoutubeTrack) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isRefreshing) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(GlassTokens.spaceSm).size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        }
        if (results.isEmpty()) {
            EmptyTabState("No songs found")
        } else {
            PlayableItemsList(
                items = results,
                onPlayQueue = { _, index -> onPlayTrack(results[index] as PlayableItem.YoutubeTrack) },
                modifier = Modifier.fillMaxSize(),
                onLoadMore = onLoadMore,
                isLoadingMore = isLoadingMore,
            )
        }
    }
}

@Composable
private fun PlaylistResultsList(
    items: List<YoutubePlaylistResult>,
    isAlbum: Boolean,
    onClick: (YoutubePlaylistResult) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    if (items.isEmpty()) {
        EmptyTabState(if (isAlbum) "No albums found" else "No playlists found")
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(listState) {
            androidx.compose.runtime.snapshotFlow {
                Triple(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, isLoadingMore, items.size)
            }.collect { (lastVisibleIndex, currentlyLoadingMore, _) ->
                    if (lastVisibleIndex != null && !currentlyLoadingMore && lastVisibleIndex >= items.lastIndex - LOAD_MORE_THRESHOLD) {
                        onLoadMore()
                    }
                }
        }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        items(items, key = { it.url }) { result ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(result) }
                    .padding(vertical = GlassTokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassArtworkThumbnail(artworkUri = result.artworkUrl)
                Column(modifier = Modifier.padding(start = GlassTokens.spaceSm).weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = WhiplashColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        result.uploaderName,
                        result.trackCount?.let { "$it songs" },
                    ).joinToString(" · ")
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
        }
        if (isLoadingMore) {
            item(key = "__load_more_footer__") {
                Box(modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceMd), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ArtistResultsList(
    items: List<YoutubeArtistResult>,
    onClick: (YoutubeArtistResult) -> Unit,
    onLoadMore: () -> Unit,
    isLoadingMore: Boolean,
) {
    if (items.isEmpty()) {
        EmptyTabState("No artists found")
        return
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    androidx.compose.runtime.LaunchedEffect(listState) {
            androidx.compose.runtime.snapshotFlow {
                Triple(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index, isLoadingMore, items.size)
            }.collect { (lastVisibleIndex, currentlyLoadingMore, _) ->
                    if (lastVisibleIndex != null && !currentlyLoadingMore && lastVisibleIndex >= items.lastIndex - LOAD_MORE_THRESHOLD) {
                        onLoadMore()
                    }
                }
        }
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceXs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        items(items, key = { it.channelUrl }) { result ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(result) }
                    .padding(vertical = GlassTokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassArtworkThumbnail(artworkUri = result.artworkUrl, shape = CircleShape)
                Column(modifier = Modifier.padding(start = GlassTokens.spaceSm).weight(1f)) {
                    Text(
                        text = result.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = WhiplashColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    result.subscriberCount?.let { count ->
                        Text(
                            text = "${com.whiplash.music.ui.common.formatCompactCount(count)} subscribers",
                            style = MaterialTheme.typography.bodySmall,
                            color = WhiplashColors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (isLoadingMore) {
            item(key = "__load_more_footer__") {
                Box(modifier = Modifier.fillMaxWidth().padding(GlassTokens.spaceMd), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WhiplashColors.accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyTabState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = WhiplashColors.textSecondary)
    }
}

@Composable
private fun IdleState(
    recentSearches: List<String>,
    trendingArtists: List<String>,
    onSuggestionTap: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = GlassTokens.spaceLg, bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        if (recentSearches.isNotEmpty()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent searches",
                    style = MaterialTheme.typography.labelMedium,
                    color = WhiplashColors.textSecondary,
                )
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelMedium,
                    color = WhiplashColors.accent,
                    modifier = Modifier.clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClearRecentSearches),
                )
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
            Column {
                recentSearches.forEach { query ->
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSuggestionTap(query) }
                            .padding(vertical = GlassTokens.spaceSm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = WhiplashColors.textTertiary,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = query,
                            style = MaterialTheme.typography.bodyMedium,
                            color = WhiplashColors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = GlassTokens.spaceMd).weight(1f),
                        )
                        PlainIconButton(
                            contentDescription = "Remove '$query' from recent searches",
                            onClick = { onRemoveRecentSearch(query) },
                            size = 32.dp,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = WhiplashColors.textTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceLg))
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Trending artists",
                style = MaterialTheme.typography.labelMedium,
                color = WhiplashColors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceSm))
            // Plain vertical rows, matching the recent-searches list right
            // above (and how Spotify/YouTube Music present this kind of
            // "try searching" list) — a real, reported UI issue: the
            // previous version used bordered/pill-shaped GlassChip tags in
            // a wrapping row, which read as visually heavy/inconsistent
            // next to the plain rows used everywhere else on this screen.
            trendingArtists.forEach { artist ->
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionTap(artist) }
                        .padding(vertical = GlassTokens.spaceSm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = WhiplashColors.textTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = GlassTokens.spaceMd),
                    )
                }
            }
        }
    }
}

/**
 * Skeleton placeholders (section 49) shown while the first search results
 * for a query are loading, shaped like the real result rows (artwork +
 * two text lines) rather than a bare spinner — gives an immediate sense
 * of "content is arriving" instead of a blank loading state, and avoids
 * the perceived-latency jump from spinner to full content. Uses the
 * shared [com.whiplash.music.ui.theme.ShimmerSkeletonRow] (also used by
 * Home's Quick Picks loading state) so every list-row skeleton in the
 * app looks and pulses identically.
 */
@Composable
private fun LoadingState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(top = GlassTokens.spaceSm),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
    ) {
        repeat(6) { com.whiplash.music.ui.theme.ShimmerSkeletonRow() }
    }
}


/**
 * YouTube Music/Spotify-style live autocomplete suggestions — the only
 * thing shown while the user is typing but hasn't yet submitted a real
 * search (tapped a suggestion/recent search, or pressed the keyboard's
 * search action). Renders nothing while the lightweight suggestions
 * lookup for the current text is still in flight (it settles in ~150ms
 * plus one network round trip — genuinely too fast to warrant a loading
 * skeleton, which is reserved for the real, much longer multi-category
 * search once a query is actually submitted) rather than a stray flash
 * of an unrelated previous state.
 */
@Composable
private fun SuggestionsState(suggestions: List<String>, onSuggestionTap: (String) -> Unit) {
    if (suggestions.isEmpty()) return
    Column(modifier = Modifier.fillMaxSize()) {
        suggestions.forEach { suggestion ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSuggestionTap(suggestion) }
                    .padding(vertical = GlassTokens.spaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = WhiplashColors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = WhiplashColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = GlassTokens.spaceMd),
                )
            }
        }
    }
}

@Composable
private fun NoResultsState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No results found",
            style = MaterialTheme.typography.titleMedium,
            color = WhiplashColors.textSecondary,
        )
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Search failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = GlassTokens.spaceMd))
                GlassButton(text = "Retry", onClick = onRetry)
            }
        }
    }
}

/**
 * How many items from the end of a results list to start loading the
 * next page — see the matching constant/comment in PlayableItemsList.kt
 * (the Songs tab's own list) for the full rationale; kept as a separate
 * constant here since Albums/Playlists/Artists use their own local
 * LazyColumns directly in this file rather than PlayableItemsList.
 */
private const val LOAD_MORE_THRESHOLD = 5
