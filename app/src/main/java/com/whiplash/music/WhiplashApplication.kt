package com.whiplash.music

import android.app.Application
import com.whiplash.music.data.local.WhiplashDatabase
import com.whiplash.music.data.repository.LibraryRepository
import com.whiplash.music.data.repository.SettingsRepository
import com.whiplash.music.data.repository.YoutubeSearchRepository
import com.whiplash.music.playback.controller.PlaybackController
import com.whiplash.music.playback.provider.PlaybackManager
import com.whiplash.music.playback.provider.ProviderHealthTracker
import com.whiplash.music.playback.provider.newpipe.NewPipePlaybackProvider
import com.whiplash.music.playback.provider.newpipe.OkHttpNewPipeDownloader
import com.whiplash.music.playback.provider.newpipe.YoutubeSearchProvider
import com.whiplash.music.playback.provider.lrclib.LrcLibProvider
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import java.util.concurrent.TimeUnit

/**
 * Application entry point.
 *
 * Owns the singleton Room database instance (section 35), the single
 * [PlaybackController] used across the app (section 12), and initializes
 * NewPipeExtractor (Provider A for YouTube/YouTube Music, section 7).
 * Dependency injection framework and other cross-cutting systems are
 * introduced in later phases as those systems are built.
 */
class WhiplashApplication : Application() {

    val database: WhiplashDatabase by lazy { WhiplashDatabase.getInstance(this) }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val audioCacheManager: com.whiplash.music.playback.cache.AudioCacheManager by lazy {
        com.whiplash.music.playback.cache.AudioCacheManager(this)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            historyDao = database.historyDao(),
            favoriteDao = database.favoriteDao(),
            playlistDao = database.playlistDao(),
            songDao = database.songDao(),
            localSongDao = database.localSongDao(),
            pinnedDao = database.pinnedDao(),
        )
    }

    val providerHealthTracker: ProviderHealthTracker by lazy {
        ProviderHealthTracker(database.providerHealthDao())
    }

    val newPipePlaybackProvider: NewPipePlaybackProvider by lazy {
        NewPipePlaybackProvider(providerHealthTracker)
    }

    val youtubeSearchProvider: YoutubeSearchProvider by lazy {
        YoutubeSearchProvider(providerHealthTracker)
    }

    val youtubeDetailProvider: com.whiplash.music.playback.provider.newpipe.YoutubeDetailProvider by lazy {
        com.whiplash.music.playback.provider.newpipe.YoutubeDetailProvider(providerHealthTracker, youtubeSearchProvider)
    }

    val youtubeSearchRepository: YoutubeSearchRepository by lazy {
        YoutubeSearchRepository(youtubeSearchProvider, database.searchCacheDao())
    }

    val lrcLibProvider: LrcLibProvider by lazy { LrcLibProvider(okHttpClient) }

    /**
     * Provider priority list for automatic fallback (section 7/8). Provider
     * A (NewPipeExtractor) is the only entry today; a future RustyPipe-based
     * Provider B slots in here as a second list element with no other
     * call-site changes required.
     */
    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(providers = listOf(newPipePlaybackProvider))
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(this, playbackManager, settingsRepository, libraryRepository, newPipePlaybackProvider)
    }

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpNewPipeDownloader(okHttpClient))
        playbackController.connect()

        // Apply the persisted Appearance theme (section 59) as early as
        // possible — at the true application entry point, not lazily via
        // whichever screen happens to first create SettingsViewModel. The
        // previous approach meant the app rendered with the default
        // Classic Graphite theme on every cold start until the user
        // happened to open Settings, silently reverting their choice for
        // the rest of the session's first minutes — a real, user-visible
        // bug, not just a cosmetic delay. WhiplashColors is a global
        // mutable object (see Color.kt), so this single collector keeps
        // it in sync regardless of which screens are ever opened.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            settingsRepository.themeVariant.collect { variant ->
                com.whiplash.music.ui.theme.WhiplashColors.applyVariant(variant)
            }
        }
    }
}
