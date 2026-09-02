package com.whiplash.music.ui.localmusic

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.whiplash.music.data.download.DownloadManager
import com.whiplash.music.data.download.DownloadProgress
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.LocalLibraryRepository
import com.whiplash.music.domain.model.LocalAlbum
import com.whiplash.music.domain.model.LocalArtist
import com.whiplash.music.domain.model.PlayableItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives the local music library screens (section 23-30).
 *
 * Exposes reactive song/album/artist lists straight from Room (populated by
 * [LocalLibraryRepository.refresh]) so the UI shows cached content
 * immediately and updates in place after a rescan (section 53).
 *
 * Section 30 ("device media can change outside the application... refresh
 * the local index intelligently, do not rescan everything on every app
 * launch"): registers a real [ContentObserver] on the MediaStore audio
 * collection so newly added/deleted/moved/edited songs are detected while
 * the app is open — not just on permission-grant or a manual "Rescan" tap,
 * which was the only trigger before this. Debounced so a burst of rapid
 * MediaStore change notifications (e.g. a multi-file copy) coalesces into
 * a single rescan instead of one per notification.
 */
class LocalLibraryViewModel(
    private val repository: LocalLibraryRepository,
    context: Context,
    private val libraryRepository: LibraryRepository? = null,
    private val downloadManager: DownloadManager? = null,
) : ViewModel() {

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning

    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted

    val songs: StateFlow<List<PlayableItem.LocalTrack>> =
        repository.observeSongs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums: StateFlow<List<LocalAlbum>> =
        repository.observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists: StateFlow<List<LocalArtist>> =
        repository.observeArtists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Downloaded tracks (Library > Downloads, YouTube-Music-style offline downloads). */
    val downloads: StateFlow<List<PlayableItem.DownloadedTrack>> =
        libraryRepository?.observeDownloads()?.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
            ?: MutableStateFlow(emptyList())

    /** In-flight download progress, keyed by track id — drives the small progress indicator on a downloading row. */
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> =
        downloadManager?.progress ?: MutableStateFlow(emptyMap())

    /** Full track metadata for every in-flight download, keyed by id — lets a downloading row show real title/artist/artwork instead of a placeholder. */
    val inFlightTracks: StateFlow<Map<String, PlayableItem.YoutubeTrack>> =
        downloadManager?.inFlightTracks ?: MutableStateFlow(emptyMap())

    fun startDownload(track: PlayableItem.YoutubeTrack) {
        downloadManager?.startDownload(track)
    }

    /** Cancels an in-flight download (tap-the-progress-ring "Cancel download" confirmation) — instantly deletes any partial file. */
    fun cancelDownload(trackId: String) {
        downloadManager?.cancelDownload(trackId)
    }

    fun removeDownload(track: PlayableItem.DownloadedTrack) {
        viewModelScope.launch {
            downloadManager?.removeDownload(track.id)
        }
    }

    /** "Clear all downloads" (Downloads tab) — cancels in-flight downloads and deletes every completed one from disk. */
    fun clearAllDownloads() {
        viewModelScope.launch {
            downloadManager?.clearAllDownloads()
        }
    }

    val songCount: StateFlow<Int> =
        repository.observeSongCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Local search (section 32: "Local: songs, artists, albums, filenames
     * where useful"). Reuses [LocalLibraryRepository.search], which was
     * already built (a simple, real Room LIKE query on title/artist/album)
     * but had never been wired to any UI until now. Debounced the same way
     * as the online SearchViewModel, though a local Room query is fast
     * enough that this is mostly for consistency rather than necessity.
     */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<PlayableItem.LocalTrack>>(emptyList())
    val searchResults: StateFlow<List<PlayableItem.LocalTrack>> = _searchResults

    private var searchJob: Job? = null

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            repository.search(query).collect { results -> _searchResults.value = results }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
        if (granted) {
            rescan()
            registerContentObserverIfNeeded()
        }
    }

    fun rescan() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            try {
                repository.refresh()
            } finally {
                _isScanning.value = false
            }
        }
    }

    // --- Section 30: automatic refresh on real MediaStore changes ---

    private val contentResolver = context.applicationContext.contentResolver
    private var mediaStoreObserver: ContentObserver? = null
    private var autoRescanJob: Job? = null

    private fun registerContentObserverIfNeeded() {
        if (mediaStoreObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                // Debounced: MediaStore fires one onChange per affected row,
                // so copying/deleting several files at once would otherwise
                // trigger a rescan per file. Coalesce into a single rescan
                // after changes settle, matching section 30's explicit
                // "do not rescan everything on every [event]" guidance.
                autoRescanJob?.cancel()
                autoRescanJob = viewModelScope.launch {
                    delay(AUTO_RESCAN_DEBOUNCE_MS)
                    rescan()
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer,
        )
        mediaStoreObserver = observer
    }

    override fun onCleared() {
        mediaStoreObserver?.let { contentResolver.unregisterContentObserver(it) }
        mediaStoreObserver = null
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val AUTO_RESCAN_DEBOUNCE_MS = 2_000L
    }
}
