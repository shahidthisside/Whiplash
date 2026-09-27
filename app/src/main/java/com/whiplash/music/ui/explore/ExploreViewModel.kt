package com.whiplash.music.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.domain.model.ExploreGenre
import com.whiplash.music.domain.model.PlayableItem
import com.whiplash.music.domain.model.ShelfKind
import com.whiplash.music.domain.model.ShelfSpec
import com.whiplash.music.ui.home.HomeViewModel.HomeShelf
import com.whiplash.music.ui.home.HomeViewModel.ShelfItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Loads Explore's two fixed shelves once; [retry] reloads any that came back empty, [refresh] reloads everything. */
class ExploreViewModel(private val search: YoutubeSearchRepository) : ViewModel() {

    private val _shelves = MutableStateFlow<List<HomeShelf>>(emptyList())
    val shelves: StateFlow<List<HomeShelf>> = _shelves

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /** Cover art for each genre tile (genre id → image URL), filled in as tiles ask for it. */
    private val _genreArt = MutableStateFlow<Map<String, String>>(emptyMap())
    val genreArt: StateFlow<Map<String, String>> = _genreArt
    private val requestedArt = mutableSetOf<String>()
    // At most three genre lookups at a time, so 20 tiles don't fire 20 searches at once.
    private val artLimit = kotlinx.coroutines.sync.Semaphore(3)

    /** Looks up a genre's artwork (the cover of its top playlist) once per session. */
    fun requestGenreArt(genre: ExploreGenre) {
        if (!requestedArt.add(genre.id)) return
        viewModelScope.launch {
            val url = artLimit.withPermitSafe {
                runCatching { search.searchPlaylists(genre.playlistQuery) }.getOrNull()
                    ?.firstNotNullOfOrNull { it.artworkUrl }
            }
            if (url != null) _genreArt.value = _genreArt.value + (genre.id to url)
            else requestedArt.remove(genre.id) // allow a retry next time the tile appears
        }
    }

    private suspend fun <T> kotlinx.coroutines.sync.Semaphore.withPermitSafe(block: suspend () -> T): T {
        acquire()
        try { return block() } finally { release() }
    }

    init { retry() }

    /** True while a manual refresh (button or pull-down) is running; shelves stay on screen meanwhile. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    /**
     * Manual refresh: reloads new releases and charts, and retries any genre
     * artwork that didn't load. What's on screen stays put until the new
     * shelves arrive; if the reload fails, the old shelves are kept.
     */
    fun refresh() {
        if (_isRefreshing.value || _isLoading.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val specs = listOf(com.whiplash.music.domain.model.EXPLORE_NEW_RELEASES, com.whiplash.music.domain.model.EXPLORE_CHARTS)
                val loaded = runCatching {
                    coroutineScope { specs.map { async { loadShelf(search, it) } }.map { it.await() } }.filterNotNull()
                }.getOrDefault(emptyList())
                if (loaded.isNotEmpty()) {
                    _shelves.value = loaded
                } else {
                    com.whiplash.music.ui.common.ToastController.show("Couldn't refresh Explore")
                }
                // Genre tiles that never got a cover ask again when next drawn.
                requestedArt.retainAll(_genreArt.value.keys)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retry() {
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val specs = listOf(com.whiplash.music.domain.model.EXPLORE_NEW_RELEASES, com.whiplash.music.domain.model.EXPLORE_CHARTS)
                val loaded = coroutineScope { specs.map { async { loadShelf(search, it) } }.map { it.await() } }
                    .filterNotNull()
                if (loaded.isNotEmpty()) _shelves.value = loaded
            } finally {
                _isLoading.value = false
            }
        }
    }
}

/** One genre page: its playlists shelf and a song list. */
class GenreViewModel(private val search: YoutubeSearchRepository, private val genre: ExploreGenre) : ViewModel() {

    private val _playlists = MutableStateFlow<HomeShelf?>(null)
    val playlists: StateFlow<HomeShelf?> = _playlists

    private val _songs = MutableStateFlow<List<PlayableItem.YoutubeTrack>>(emptyList())
    val songs: StateFlow<List<PlayableItem.YoutubeTrack>> = _songs

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _failed = MutableStateFlow(false)
    val failed: StateFlow<Boolean> = _failed

    private val playlistSpec = ShelfSpec(ShelfKind.PLAYLISTS, "${genre.title} playlists", genre.playlistQuery)
    private val songQuery = genre.songQuery

    /** True while a pull-to-refresh runs; the current page stays on screen meanwhile. */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    init { load() }

    /**
     * Pull-to-refresh: reloads the playlists and songs. Each part is only
     * replaced if its reload returned something, so a failed refresh never
     * blanks a page that was showing fine.
     */
    fun refresh() {
        if (_isLoading.value || _isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val hadContent = _playlists.value != null || _songs.value.isNotEmpty()
                coroutineScope {
                    val p = async { loadShelf(search, playlistSpec) }
                    val s = async { runCatching { search.search(songQuery) }.getOrDefault(emptyList()) }
                    val newPlaylists = p.await()
                    val newSongs = s.await()
                    if (newPlaylists != null) _playlists.value = newPlaylists
                    if (newSongs.isNotEmpty()) _songs.value = newSongs
                    if (newPlaylists == null && newSongs.isEmpty() && hadContent) {
                        com.whiplash.music.ui.common.ToastController.show("Couldn't refresh ${genre.title}")
                    }
                }
                _failed.value = _playlists.value == null && _songs.value.isEmpty()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            _failed.value = false
            coroutineScope {
                val p = async { loadShelf(search, playlistSpec) }
                val s = async { runCatching { search.search(songQuery) }.getOrDefault(emptyList()) }
                _playlists.value = p.await()
                _songs.value = s.await()
            }
            _failed.value = _playlists.value == null && _songs.value.isEmpty()
            _isLoading.value = false
        }
    }
}

private suspend fun loadShelf(search: YoutubeSearchRepository, spec: ShelfSpec): HomeShelf? {
    val items: List<ShelfItem> = runCatching {
        when (spec.kind) {
            ShelfKind.ALBUMS -> search.searchAlbums(spec.query).map { ShelfItem.Collection(it) }
            ShelfKind.PLAYLISTS -> search.searchPlaylists(spec.query).map { ShelfItem.Collection(it) }
            ShelfKind.SONGS -> search.search(spec.query).map { ShelfItem.Track(it) }
        }
    }.getOrDefault(emptyList()).distinctBy { it.key }.take(12)
    return if (items.isEmpty()) null else HomeShelf(spec, items)
}

class ExploreViewModelFactory(private val search: YoutubeSearchRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return ExploreViewModel(search) as T
    }
}

class GenreViewModelFactory(private val search: YoutubeSearchRepository, private val genre: ExploreGenre) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        @Suppress("UNCHECKED_CAST")
        return GenreViewModel(search, genre) as T
    }
}
