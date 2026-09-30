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

    /**
     * A completely separate [OkHttpClient] instance (own connection pool +
     * dispatcher) for [com.whiplash.music.data.download.DownloadManager].
     *
     * Real, reported bug root cause: [okHttpClient] above is shared by
     * [OkHttpNewPipeDownloader] (NewPipeExtractor's metadata + stream-URL
     * resolution — small, fast requests that playback depends on for
     * *every* track) and, before this fix, by the download manager's own
     * large audio-byte GET requests (megabytes, held open for tens of
     * seconds to minutes on a slow connection). OkHttp's default
     * [okhttp3.Dispatcher] caps concurrent requests per host
     * (maxRequestsPerHost=5) and reuses a bounded connection pool — a
     * long-lived download to googlevideo.com could starve a *different*
     * song's stream-resolution or metadata call queued behind it on the
     * same shared client, which is exactly the reported symptom ("after
     * this none of the songs in app is playing, just loading loading").
     * A dedicated client for downloads means a slow/large download can
     * never block or starve anything playback needs, regardless of how
     * long it takes or how many are running.
     *
     * Also uses a longer read timeout (large files legitimately have
     * longer gaps between reads on a slow/throttled connection than a
     * small metadata request ever would) and no call timeout, since a
     * multi-minute download is expected and should not be treated as
     * "stuck" purely by wall-clock duration the way a metadata call
     * should be.
     */
    private val downloadOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }

    val audioCacheManager: com.whiplash.music.playback.cache.AudioCacheManager by lazy {
        com.whiplash.music.playback.cache.AudioCacheManager(this)
    }

    val backupManager: com.whiplash.music.data.backup.BackupManager by lazy {
        com.whiplash.music.data.backup.BackupManager(this, database, settingsRepository, lyricOffsetStore, radioLearnedStore)
    }

    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(
            historyDao = database.historyDao(),
            favoriteDao = database.favoriteDao(),
            playlistDao = database.playlistDao(),
            songDao = database.songDao(),
            localSongDao = database.localSongDao(),
            pinnedDao = database.pinnedDao(),
            downloadDao = database.downloadDao(),
            replayTallyDao = database.replayTallyDao(),
            playEventDao = database.playEventDao(),
            forgetRadioLearning = { radioLearnedStore.clear() },
        )
    }

    val providerHealthTracker: ProviderHealthTracker by lazy {
        ProviderHealthTracker(database.providerHealthDao())
    }

    /** What the radio learned about this listener; wiped with listening history. */
    val radioLearnedStore by lazy { com.whiplash.music.recommend.FileLearnedStore(java.io.File(filesDir, "radio_learned.json")) }

    private val innerTube by lazy { com.whiplash.music.innertube.InnerTubeClient(okHttpClient) }

    /** Radio and "related" songs: YouTube Music's own API, NewPipe as the fallback. */
    val musicSources: com.whiplash.music.recommend.MusicSources by lazy {
        com.whiplash.music.recommend.MusicSources(
            innerRadio = { id, cursor -> innerTube.radio(id, cursor) },
            innerRelated = { id -> innerTube.related(id) },
            fallbackRadio = { id, cursor -> newPipePlaybackProvider.getRadioPage(id, cursor) },
            fallbackRelated = { id -> newPipePlaybackProvider.getRelatedTracks(id) },
        )
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
        YoutubeSearchRepository(youtubeSearchProvider, database.searchCacheDao(), database.searchHistoryDao())
    }

    val lrcLibProvider: LrcLibProvider by lazy { LrcLibProvider(okHttpClient) }

    /** Lyrics providers in fallback order, with health tracking (Settings → Lyrics source). */
    val lyricsProviderChain: com.whiplash.music.data.lyrics.LyricsProviderChain by lazy {
        com.whiplash.music.data.lyrics.LyricsProviderChain(
            listOf(lrcLibProvider, com.whiplash.music.data.lyrics.LyricsOvhProvider(okHttpClient)),
        )
    }

    /** Memory + GZIP disk cache for fetched lyrics (cleared by Settings → Clear cache). */
    val lyricsCache: com.whiplash.music.data.lyrics.LyricsCache by lazy {
        com.whiplash.music.data.lyrics.LyricsCache(java.io.File(cacheDir, "lyrics"))
    }

    val lyricOffsetStore: com.whiplash.music.data.repository.LyricOffsetStore by lazy {
        com.whiplash.music.data.repository.LyricOffsetStore(this)
    }

    /**
     * Provider priority list for automatic fallback (section 7/8). Provider
     * A (NewPipeExtractor) is the only entry today; a future RustyPipe-based
     * Provider B slots in here as a second list element with no other
     * call-site changes required.
     */
    val playbackManager: PlaybackManager by lazy {
        PlaybackManager(
            providers = listOf(newPipePlaybackProvider),
            streamChoices = com.whiplash.music.playback.provider.StreamChoiceStore.sharedPrefs(this),
            // Same key as PlayableItemMediaItemMapper.mediaIdOf for a YouTube track.
            onFormatChanged = { videoId, knownChange ->
                val key = "YOUTUBE:$videoId"
                // A complete cached copy is one consistent file, so keep it
                // unless the format is known to have changed.
                if (knownChange || !audioCacheManager.isFullyCached(key)) audioCacheManager.removeResource(key)
            },
        )
    }

    val playbackController: PlaybackController by lazy {
        PlaybackController(this, playbackManager, settingsRepository, libraryRepository, newPipePlaybackProvider, audioCacheManager, database.playEventDao(), radioLearnedStore, musicSources)
    }

    val downloadManager: com.whiplash.music.data.download.DownloadManager by lazy {
        com.whiplash.music.data.download.DownloadManager(this, playbackManager, database.downloadDao(), downloadOkHttpClient, settingsRepository)
    }

    /** Saves downloaded songs to the phone's Download/Whiplash folder. */
    val deviceExporter: com.whiplash.music.data.download.DeviceExporter by lazy {
        com.whiplash.music.data.download.DeviceExporter(this, database.downloadDao())
    }

    /** Optional Google Drive sync (Settings > Account & sync). */
    val cloudSyncManager: com.whiplash.music.data.sync.CloudSyncManager by lazy {
        com.whiplash.music.data.sync.CloudSyncManager(
            context = this,
            settingsRepository = settingsRepository,
            database = database,
            lyricOffsetStore = lyricOffsetStore,
            localStore = com.whiplash.music.data.sync.LocalSyncStore(database, backupManager, lyricOffsetStore, profileStore) { id ->
                libraryRepository.deletePlaylist(id)
            },
            drive = com.whiplash.music.data.sync.DriveAppDataClient(okHttpClient),
            auth = com.whiplash.music.data.sync.GoogleDriveAuth(this),
            profileStore = profileStore,
        )
    }

    private val profileStore: com.whiplash.music.data.sync.ProfileStore by lazy {
        com.whiplash.music.data.sync.ProfileStore(this)
    }

    override fun onCreate() {
        super.onCreate()
        NewPipe.init(OkHttpNewPipeDownloader(okHttpClient))
        // YouTube asks for cookie consent before serving radio (Mix)
        // playlists in some regions; accepting it is what NewPipe's own
        // "accept YouTube cookies" setting does. Needed for song radio.
        org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper.setConsentAccepted(true)
        playbackController.connect()
        // Does nothing until the user turns on Account & sync and signs in.
        cloudSyncManager.start()

        // Clean up any download left in an inconsistent state by a
        // process death mid-download (section: offline downloads) —
        // otherwise a half-written file with no matching COMPLETED row
        // would silently linger in app-private storage forever.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { downloadManager.cleanUpIncompleteDownloads() }
        }

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
            kotlinx.coroutines.flow.combine(
                settingsRepository.appTheme,
                settingsRepository.themeVariant,
                settingsRepository.customThemeColors,
                settingsRepository.glassOpacity,
            ) { theme, accent, custom, opacity ->
                com.whiplash.music.ui.theme.WhiplashColors.applyTheme(theme, accent, custom, opacity)
            }.collect { }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate).launch {
            settingsRepository.glassLens.collect { com.whiplash.music.ui.theme.WhiplashColors.glassLens = it }
        }

        // Warm Home's Speed dial artwork and Quick Picks (both data and
        // artwork) in the background as early as possible in app startup
        // — see AppStartupPreloader doc for why this matters: without it,
        // opening the app after a while shows a real loading spinner and
        // black artwork boxes on Home rather than everything already
        // being ready by the time the user gets there. Runs on a plain
        // IO-dispatcher scope (not viewModelScope, since there's no
        // ViewModel yet at this point in the app's life) and is entirely
        // best-effort — HomeViewModel's own loading logic is completely
        // unaffected and still runs normally if this hasn't finished (or
        // failed) by the time Home actually opens.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                AppStartupPreloader(this@WhiplashApplication, libraryRepository, youtubeSearchRepository).preload()
            }
        }
    }
}
