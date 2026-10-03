// Copyright (c) 2026 Shahid Ansari. All rights reserved.
// Proprietary software. Copying, modification, rebuilding or redistribution
// is not permitted without written permission. See LICENSE.
// Media3 caching/data-source/forwarding APIs used here are @UnstableApi;
// opting in file-wide records that this is a deliberate dependency.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.whiplash.music.ui.settings

import com.whiplash.music.ui.theme.glassFill
import com.whiplash.music.ui.theme.appBackground
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.launch
import com.whiplash.music.ui.theme.GlassSearchField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whiplash.music.WhiplashApplication
import com.whiplash.music.domain.model.AudioQuality
import com.whiplash.music.ui.theme.GlassButton
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.ThemeVariant
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.layout.layout

/**
 * Settings screen (section 59). Two real, fully-implemented sections:
 *
 * - Playback: audio quality, autoplay, gapless, crossfade/fade duration,
 *   playback speed — every one of these is backed by a real DataStore
 *   setting AND a real effect on [com.whiplash.music.playback.controller.PlaybackController]
 *   (section 73: "never show a setting that is not implemented").
 * - Appearance: the current theme plus 6 selectable palettes, backed by
 *   [ThemeVariant]/[WhiplashColors.applyVariant] — switching is instant and
 *   affects every screen, since every Glass* component reads WhiplashColors
 *   reactively.
 */
/** Request code for the Equalizer's startActivityForResult call — the result itself is never consulted, only the launch mechanism it enables (see the Equalizer SettingActionRow's onClick). */
private const val EQUALIZER_REQUEST_CODE = 4242

@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun SettingsScreen(resetKey: Int = 0, backEnabled: Boolean = true) {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.settingsRepository, app.audioCacheManager, app.backupManager, app.lyricsCache, app.lyricsProviderChain, app.downloadManager, app.playbackManager.lastStreamSource))

    val audioQuality by viewModel.audioQuality.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val themeVariant by viewModel.themeVariant.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val customThemeColors by viewModel.customThemeColors.collectAsState()
    val glassOpacity by viewModel.glassOpacity.collectAsState()
    val glassLens by viewModel.glassLens.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    val audioCacheEnabled by viewModel.audioCacheEnabled.collectAsState()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsState()
    val downloadWifiOnly by viewModel.downloadWifiOnly.collectAsState()
    val statsForNerdsEnabled by viewModel.statsForNerdsEnabled.collectAsState()
    val reduceAnimations by viewModel.reduceAnimations.collectAsState()
    val playerArtworkColors by viewModel.playerArtworkColors.collectAsState()
    val playerLyricStrip by viewModel.playerLyricStrip.collectAsState()
    val swipeUpForLyrics by viewModel.swipeUpForLyrics.collectAsState()
    val lyricsSource by viewModel.lyricsSource.collectAsState()
    val streamSource by viewModel.streamSource.collectAsState()
    val lastStreamSource by viewModel.lastStreamSource.collectAsState()
    val lyricsBlurUnfocused by viewModel.lyricsBlurUnfocused.collectAsState()
    val homeShelvesEnabled by viewModel.homeShelvesEnabled.collectAsState()
    val exploreEnabled by viewModel.exploreEnabled.collectAsState()
    val replayEnabled by viewModel.replayEnabled.collectAsState()
    val quickPicksGridCount by viewModel.quickPicksGridCount.collectAsState()
    val speedDialPaging by viewModel.speedDialPaging.collectAsState()
    val tasteArtists by app.settingsRepository.tasteArtists.collectAsState(initial = emptyList())
    val tasteGenres by app.settingsRepository.tasteGenres.collectAsState(initial = emptyList())
    val tasteLanguages by app.settingsRepository.tasteLanguages.collectAsState(initial = emptyList())
    val speedDialPeek by viewModel.speedDialPeek.collectAsState()
    val speedDialPageCount by viewModel.speedDialPageCount.collectAsState()
    val speedDialGridCount by viewModel.speedDialGridCount.collectAsState()
    val quickPicksPeek by viewModel.quickPicksPeek.collectAsState()
    val lyricsProviderHealth by viewModel.lyricsProviderHealth.collectAsState()
    val playerHeroArtwork by viewModel.playerHeroArtwork.collectAsState()
    val perNetworkQualityEnabled by viewModel.perNetworkQualityEnabled.collectAsState()
    val audioQualityWifi by viewModel.audioQualityWifi.collectAsState()
    val audioQualityCellular by viewModel.audioQualityCellular.collectAsState()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val downloadsUsage by viewModel.downloadsUsage.collectAsState()
    var showClearDownloadsConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showResetRecsConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showQuitConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val lastBackupTimeMs by viewModel.lastBackupTimeMs.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()

    var showRestoreConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }

    // Optional Google account sync (Settings > Account & sync).
    val cloudSync = app.cloudSyncManager
    val cloudSyncEnabled by app.settingsRepository.cloudSyncEnabled.collectAsState(initial = null)
    val showAccountEmail by app.settingsRepository.showAccountEmail.collectAsState(initial = true)
    val showSyncStatus by app.settingsRepository.showSyncStatus.collectAsState(initial = true)
    val showAccountPhoto by app.settingsRepository.showAccountPhoto.collectAsState(initial = true)
    val cloudScope = androidx.compose.runtime.rememberCoroutineScope()
    val rawCloudState by cloudSync.state.collectAsState()
    val customProfile by cloudSync.profileStore.profile.collectAsState()
    // Your Whiplash name and photo, when set, replace Google's everywhere in the app.
    val cloudState = rawCloudState.copy(
        account = rawCloudState.account?.let { a ->
            a.copy(
                name = customProfile?.name ?: a.name,
                // Hidden photo: the avatar falls back to your initial.
                photoUrl = if (!showAccountPhoto) null else cloudSync.profileStore.photoFile()?.takeIf { customProfile?.photoJpeg != null }?.path ?: a.photoUrl,
            )
        },
    )
    val photoVersion = customProfile?.updatedAtEpochMs ?: 0L
    var showEditNameDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    // The picked picture, waiting in the Adjust photo screen.
    var cropBitmap by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null) }
    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            cloudScope.launch {
                val bitmap = cloudSync.profileStore.decodeForCrop(context, uri)
                if (bitmap == null) {
                    com.whiplash.music.ui.common.ToastController.show("Couldn't use that picture")
                } else {
                    cropBitmap = bitmap
                }
            }
        }
    }

    cropBitmap?.let { bitmap ->
        PhotoCropDialog(
            bitmap = bitmap,
            onSave = { left, top, size ->
                cropBitmap = null
                cloudScope.launch {
                    val ok = cloudSync.profileStore.setCroppedPhoto(bitmap, left, top, size)
                    com.whiplash.music.ui.common.ToastController.show(if (ok) "Profile photo updated" else "Couldn't save that photo")
                }
            },
            onDismiss = { cropBitmap = null },
        )
    }
    val pickPhoto: () -> Unit = {
        runCatching {
            photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }.onFailure { com.whiplash.music.ui.common.ToastController.show("No photo picker on this device") }
    }

    if (showEditNameDialog) {
        com.whiplash.music.ui.theme.GlassTextInputDialog(
            title = "Edit name",
            initialValue = cloudState.account?.name.orEmpty(),
            placeholder = "Your name",
            onConfirm = { name ->
                showEditNameDialog = false
                cloudSync.profileStore.setName(name)
                com.whiplash.music.ui.common.ToastController.show("Name updated")
            },
            onDismiss = { showEditNameDialog = false },
        )
    }
    var showCloudOffConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showSignOutConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showDeleteCloudConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var showResetConfirm by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    var resetting by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val signInLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            cloudScope.launch { showSignInResult(cloudSync.finishSignIn(result.data)) }
        } else {
            cloudSync.cancelSignIn()
        }
    }
    val startSignIn: () -> Unit = {
        cloudScope.launch {
            when (val step = cloudSync.beginSignIn()) {
                is com.whiplash.music.data.sync.CloudSyncManager.SignInStep.NeedsUi -> runCatching {
                    signInLauncher.launch(androidx.activity.result.IntentSenderRequest.Builder(step.intent).build())
                }.onFailure {
                    cloudSync.cancelSignIn()
                    com.whiplash.music.ui.common.ToastController.show("Couldn't sign in")
                }
                else -> showSignInResult(step)
            }
        }
    }
    val syncNow: () -> Unit = {
        cloudScope.launch {
            when (val r = cloudSync.sync()) {
                com.whiplash.music.data.sync.CloudSyncManager.SyncResult.Synced -> com.whiplash.music.ui.common.ToastController.show("Library synced")
                is com.whiplash.music.data.sync.CloudSyncManager.SyncResult.Failed -> com.whiplash.music.ui.common.ToastController.show(r.message)
            }
        }
    }
    val setCloudSyncEnabled: (Boolean) -> Unit = { enabled ->
        if (!enabled && cloudState.account != null) {
            showCloudOffConfirm = true
        } else {
            cloudScope.launch { app.settingsRepository.setCloudSyncEnabled(enabled) }
        }
    }

    if (showCloudOffConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Turn off Account & sync?",
            message = "You'll be signed out. Your library stays on this phone and in your Google Drive.",
            confirmLabel = "Turn off",
            destructive = false,
            onConfirm = {
                showCloudOffConfirm = false
                cloudScope.launch {
                    app.settingsRepository.setCloudSyncEnabled(false)
                    cloudSync.signOut()
                }
            },
            onDismiss = { showCloudOffConfirm = false },
        )
    }

    if (showResetRecsConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Reset recommendations?",
            message = "Autoplay and Quick Picks forget what they learned from your skips and finishes. Your history, favorites and playlists stay.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = {
                showResetRecsConfirm = false
                cloudScope.launch {
                    app.playbackController.resetRecommendations()
                    app.quickPicksSnapshot.clear()
                    com.whiplash.music.ui.common.ToastController.show("Recommendations reset")
                }
            },
            onDismiss = { showResetRecsConfirm = false },
        )
    }

    if (showSignOutConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Sign out?",
            message = "Your library stays on this phone and in your Google Drive.",
            confirmLabel = "Sign out",
            destructive = false,
            onConfirm = {
                showSignOutConfirm = false
                cloudScope.launch {
                    cloudSync.signOut()
                    com.whiplash.music.ui.common.ToastController.show("Signed out")
                }
            },
            onDismiss = { showSignOutConfirm = false },
        )
    }

    if (showResetConfirm) {
        val resetContext = androidx.compose.ui.platform.LocalContext.current
        ResetAppDialog(
            signedIn = cloudState.account != null,
            onConfirm = { alsoDrive ->
                showResetConfirm = false
                resetting = true
                cloudScope.launch {
                    // Account first: if the Drive delete fails, nothing is erased.
                    when (val r = cloudSync.prepareAppReset(alsoDrive)) {
                        com.whiplash.music.data.sync.CloudSyncManager.SyncResult.Synced -> wipeAppData(resetContext)
                        is com.whiplash.music.data.sync.CloudSyncManager.SyncResult.Failed -> {
                            resetting = false
                            com.whiplash.music.ui.common.ToastController.show("Couldn't reach Drive. Nothing was erased.")
                        }
                    }
                }
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    if (showDeleteCloudConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Delete synced data?",
            message = "This removes your Whiplash data from Google Drive and signs you out. This phone keeps everything.",
            confirmLabel = "Delete",
            onConfirm = {
                showDeleteCloudConfirm = false
                cloudScope.launch {
                    when (val r = cloudSync.deleteCloudCopy()) {
                        com.whiplash.music.data.sync.CloudSyncManager.SyncResult.Synced -> com.whiplash.music.ui.common.ToastController.show("Synced data deleted")
                        is com.whiplash.music.data.sync.CloudSyncManager.SyncResult.Failed -> com.whiplash.music.ui.common.ToastController.show(r.message)
                    }
                }
            },
            onDismiss = { showDeleteCloudConfirm = false },
        )
    }

    // Advanced backup category selection — replaces the old unconditional
    // "back up literally everything" tap-and-go flow with real per-
    // category checkboxes, rendered inline in the Backup & Restore card
    // itself (between the description and the action buttons — not a
    // separate sheet/screen). Every category defaults to checked, so a
    // user who just wants the old all-or-nothing behavior still gets it
    // with zero extra taps beyond the existing "Back up now" press.
    var selectedBackupCategories by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(com.whiplash.music.data.backup.BackupCategory.entries.toSet())
    }

    // Toast feedback for backup/restore, matching the existing simple,
    // generic "no internet" / "couldn't play this song" Toast pattern in
    // MainActivity — never raw exception text (section: keep user-facing
    // errors simple, like Spotify/YouTube Music).
    androidx.compose.runtime.LaunchedEffect(backupResult) {
        val message = when (backupResult) {
            SettingsViewModel.BackupResult.BackupSuccess -> "Backup saved"
            SettingsViewModel.BackupResult.BackupFailed -> "Couldn't create backup"
            SettingsViewModel.BackupResult.RestoreSuccess -> "Backup restored"
            SettingsViewModel.BackupResult.RestoreFailed -> "Couldn't restore backup"
            null -> null
        }
        if (message != null) {
            com.whiplash.music.ui.common.ToastController.show(message)
            viewModel.onBackupResultShown()
        }
    }

    val backupLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> if (uri != null) viewModel.backup(uri, selectedBackupCategories) }

    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) showRestoreConfirm = uri }

    if (showClearDownloadsConfirm) {
        val (count, bytes) = downloadsUsage
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Delete all downloads?",
            message = "This removes ${if (count == 1) "1 downloaded song" else "$count downloaded songs"} (${formatBytes(bytes)}) from this device and cancels any download in progress. They won't play offline until you download them again.",
            confirmLabel = "Delete",
            onConfirm = {
                showClearDownloadsConfirm = false
                viewModel.clearDownloads()
            },
            onDismiss = { showClearDownloadsConfirm = false },
        )
    }

    if (showQuitConfirm) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Quit Whiplash?",
            message = "Playback stops, the current queue is cleared and any download in progress is cancelled. Your library, playlists and settings are kept.",
            confirmLabel = "Quit",
            onConfirm = {
                showQuitConfirm = false
                (context as? android.app.Activity)?.let { quitApp(it) }
            },
            onDismiss = { showQuitConfirm = false },
        )
    }

    if (showRestoreConfirm != null) {
        com.whiplash.music.ui.theme.GlassConfirmDialog(
            title = "Restore this backup?",
            message = "This adds the backed-up data on top of what you already have (playlists, favorites, history, pinned songs, downloads, and/or settings — whichever categories the backup file actually contains). If it's an older full backup, this replaces everything instead and restarts the app. This can't be undone.",
            confirmLabel = "Restore",
            onConfirm = {
                val uri = showRestoreConfirm!!
                showRestoreConfirm = null
                viewModel.restore(uri) {
                    // A live Room connection can't have its backing file
                    // replaced out from under it (see BackupManager.restore
                    // doc), so a full process restart is the only correct
                    // way to pick up the restored data everywhere at once.
                    // Only reached for a legacy full-DB backup — a
                    // selective restore is a plain additive DAO merge with
                    // no file replacement, so it needs no restart at all.
                    val restartIntent = android.content.Intent(context, com.whiplash.music.MainActivity::class.java)
                    restartIntent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(restartIntent)
                    kotlin.system.exitProcess(0)
                }
            },
            onDismiss = { showRestoreConfirm = null },
        )
    }

    // The ViewModel is scoped to the Activity (no navigation-graph store
    // separation for this simple tab structure), so its init{} only runs
    // once ever — but this composable itself leaves and re-enters
    // composition every time the user switches away from and back to the
    // Settings tab, so re-checking here keeps the displayed size accurate
    // after playing more tracks or clearing the cache elsewhere, without
    // needing a continuous poll while the screen isn't even visible.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.refreshCacheSize()
    }

    var settingsQuery by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    // A section page's Search button: back to the start page with the cursor in the search field.
    val settingsSearchFocus = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    var focusSearchRequest by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val settingsKeyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    androidx.compose.runtime.LaunchedEffect(focusSearchRequest) {
        if (focusSearchRequest > 0) {
            // The start page is already composed underneath the section page.
            runCatching { settingsSearchFocus.requestFocus() }
            settingsKeyboard?.show()
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val settingsScope = androidx.compose.runtime.rememberCoroutineScope()
    // Folder mode: the section page that's open (null = the start page).
    var openSection by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<SettingsSection?>(null) }
    // Re-tapping the Settings tab returns to the start page.
    androidx.compose.runtime.LaunchedEffect(resetKey) { if (resetKey > 0) openSection = null }
    // Off while something covers Settings (the full player): Back must close
    // that first, not the folder page hidden behind it.
    androidx.activity.compose.BackHandler(enabled = backEnabled && openSection != null) { openSection = null }
    val reduceMotion = com.whiplash.music.ui.common.isReducedMotionEnabled()
    val rootListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // The settings rows for [sections], filtered by [query]. [flat] is the
    // original single page (section titles and footer); a folder page leaves
    // both out because its header already names the section.
    @Composable
    fun SettingsList(
        state: androidx.compose.foundation.lazy.LazyListState,
        sections: List<SettingsSection>,
        query: String,
        flat: Boolean,
        topPadding: androidx.compose.ui.unit.Dp,
        modifier: Modifier = Modifier,
        top: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
    ) {
        val shown: (SettingEntry) -> Boolean = { settingMatches(it, query) }
        LazyColumn(
            state = state,
            modifier = modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd),
            // Sections must be separated by more than the rows inside them, or the
            // whole screen reads as one continuous list. This was previously 24dp
            // between sections while rows within a section sat 32dp apart — the
            // groups were more tightly packed than their own contents, so nothing
            // marked where "Playback" ended and "Storage" began. With the cards and
            // dividers gone this gap is the only thing carrying that boundary, so it
            // is deliberately larger than spaceXl rather than a token value.
            verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg),
            // Matches the breathing room Home leaves between its title and its
            // first section label. Home's "Quick Picks" label sits inside a 48dp
            // row (it shares that row with the Play all / Refresh buttons) so its
            // text is pushed down about 22dp; Settings' plain SectionLabel has no
            // such row, which left "Playback" 62px tighter under the title than
            // "Quick Picks" is under "Whiplash". Adding the inset here affects only
            // the space above the first section, rather than compounding with the
            // gap between sections.
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = topPadding,
                bottom = GlassTokens.miniPlayerReservedHeight,
            ),
    ) {
            top()

            if (SettingsSection.AUDIO_QUALITY in sections) {
                item(key = "section:AUDIO_QUALITY") {
                    if (flat) {
                        SectionLabel(SettingsSection.AUDIO_QUALITY.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.AUDIO_QUALITY) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.AUDIO_QUALITY)) {
                            SettingItem(divider = rows.next()) {
                                // --- Audio Quality ---
                                SettingRow(
                                    title = "Audio Quality",
                                    icon = Icons.Filled.GraphicEq,
                                    subtitle = "Applies to playback. Higher quality uses more data.",
                                )
                                // Only rendered once the real persisted value is known
                                // (audioQuality is null for at most one frame right
                                // after this screen is first created) — this is what
                                // actually prevents the "flashes Auto, then jumps to
                                // the real saved value" glitch, rather than merely
                                // shortening it.
                                audioQuality?.let { quality ->
                                    AudioQualitySelector(
                                        selected = quality,
                                        onSelect = viewModel::setAudioQuality,
                                    )
                                }
                            }
                        }

                        if (shown(SettingEntry.PER_NETWORK)) {
                            SettingItem(divider = rows.next()) {
                                // --- Per-network audio quality (adapted from BitChord) ---
                                // Off by default so the single Audio Quality control above
                                // keeps working exactly as before for anyone who never
                                // opens this; turning it on lets Wi-Fi and cellular each
                                // keep their own ceiling, so a data plan isn't spent at
                                // the same bitrate used at home.
                                SettingToggleRow(
                                    title = "Per-Network Audio Quality",
                                    icon = Icons.Filled.NetworkCheck,
                                    subtitle = "Use separate quality ceilings for Wi-Fi and mobile data, instead of one setting for both.",
                                    checked = perNetworkQualityEnabled,
                                    onCheckedChange = viewModel::setPerNetworkQualityEnabled,
                                )
                                if (perNetworkQualityEnabled) {
                                    SettingRow(
                                        title = "Wi-Fi Quality",
                                        icon = Icons.Filled.Wifi,
                                        subtitle = "Used only when connected to Wi-Fi.",
                                    )
                                    AudioQualitySelector(
                                        selected = audioQualityWifi,
                                        onSelect = viewModel::setAudioQualityWifi,
                                    )
                                    SettingRow(
                                        title = "Cellular Quality",
                                        icon = Icons.Filled.SignalCellularAlt,
                                        subtitle = "Used only on mobile data. Lower this to save your data plan.",
                                    )
                                    AudioQualitySelector(
                                        selected = audioQualityCellular,
                                        onSelect = viewModel::setAudioQualityCellular,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (SettingsSection.STREAM_SOURCE in sections) {
                item(key = "section:STREAM_SOURCE") {
                    if (flat) {
                        SectionLabel(SettingsSection.STREAM_SOURCE.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.STREAM_SOURCE) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.STREAM_SOURCE)) {
                            SettingItem(divider = rows.next()) {
                                SettingRow(
                                    title = "Stream source",
                                    icon = Icons.Filled.Hub,
                                    subtitle = when (streamSource) {
                                        com.whiplash.music.playback.provider.StreamSourcePreference.NEWPIPE ->
                                            "Songs are looked up with NewPipe only."
                                        com.whiplash.music.playback.provider.StreamSourcePreference.YOUTUBE_DIRECT ->
                                            "Songs are looked up by asking YouTube directly. Faster, with no NewPipe backup."
                                        else -> "Automatic uses NewPipe and asks YouTube directly if a song won't play."
                                    },
                                )
                                // Shown once the saved choice is read, like Audio Quality above.
                                streamSource?.let { source ->
                                    SegmentedChoice(
                                        options = com.whiplash.music.playback.provider.StreamSourcePreference.entries,
                                        selected = source,
                                        label = { it.label },
                                        onSelect = viewModel::setStreamSource,
                                    )
                                }
                                Text(
                                    text = "Last song: " + when (lastStreamSource) {
                                        com.whiplash.music.playback.provider.StreamSourcePreference.NEWPIPE_ID -> "NewPipe"
                                        com.whiplash.music.playback.provider.StreamSourcePreference.DIRECT_ID -> "YouTube direct"
                                        else -> "not looked up yet"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = WhiplashColors.textSecondary,
                                    modifier = Modifier.padding(start = GlassTokens.spaceSm),
                                )
                            }
                        }
                    }
                }
            }

            if (SettingsSection.PLAYBACK in sections) {
                item(key = "section:PLAYBACK") {
                    if (flat) {
                        SectionLabel(SettingsSection.PLAYBACK.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.PLAYBACK) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.AUTOPLAY)) {
                            SettingItem(divider = rows.next()) {
                                // --- Autoplay ---
                                SettingToggleRow(
                                    title = "Autoplay",
                                    icon = Icons.Filled.PlaylistPlay,
                                    subtitle = "Automatically queue related songs when your queue is about to end.",
                                    checked = autoplayEnabled,
                                    onCheckedChange = viewModel::setAutoplayEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.RESET_RECOMMENDATIONS)) {
                            SettingItem(divider = rows.next()) {
                                SettingActionRow(
                                    title = "Reset recommendations",
                                    icon = Icons.Filled.RestartAlt,
                                    subtitle = "Forget what autoplay learned. History stays.",
                                    onClick = { showResetRecsConfirm = true },
                                )
                            }
                        }

                        if (shown(SettingEntry.GAPLESS)) {
                            SettingItem(divider = rows.next()) {
                                // --- Gapless ---
                                SettingToggleRow(
                                    title = "Gapless Playback",
                                    icon = Icons.Filled.FastForward,
                                    subtitle = "Pre-load the next track so there's no pause between songs.",
                                    checked = gaplessEnabled,
                                    onCheckedChange = viewModel::setGaplessEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.SKIP_SILENCE)) {
                            SettingItem(divider = rows.next()) {
                                // --- Skip Silence (adapted from BitChord) ---
                                // Uses Media3's own built-in SilenceSkippingAudioProcessor
                                // (no custom DSP) — genuinely shortens silent passages
                                // during playback rather than just detecting them, so
                                // this is off by default like every other setting that
                                // audibly changes what's heard.
                                SettingToggleRow(
                                    title = "Skip Silence",
                                    icon = Icons.Filled.VolumeOff,
                                    subtitle = "Automatically speed through quiet passages during playback.",
                                    checked = skipSilenceEnabled,
                                    onCheckedChange = viewModel::setSkipSilenceEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.CROSSFADE)) {
                            SettingItem(divider = rows.next()) {
                                // --- Crossfade / fade duration ---
                                SettingRow(
                                    title = "Crossfade",
                                    icon = Icons.Filled.Tune,
                                    subtitle = if (crossfadeDurationMs == 0) {
                                        "Off — songs switch instantly."
                                    } else {
                                        "Fades out the current song and fades in the next over ${crossfadeDurationMs / 1000}s."
                                    },
                                )
                                com.whiplash.music.ui.common.CrossfadeSlider(
                                    selectedMs = crossfadeDurationMs,
                                    onSelect = viewModel::setCrossfadeDurationMs,
                                )
                            }
                        }

                        if (shown(SettingEntry.SPEED)) {
                            SettingItem(divider = rows.next()) {
                                // --- Playback speed ---
                                SettingRow(
                                    title = "Playback Speed",
                                    icon = Icons.Filled.Speed,
                                    subtitle = "Applies to the currently playing track immediately.",
                                )
                                com.whiplash.music.ui.common.PlaybackSpeedControl(
                                    selected = playbackSpeed,
                                    onSelect = { speed ->
                                        viewModel.setPlaybackSpeed(speed)
                                        app.playbackController.setPlaybackSpeed(speed)
                                    },
                                    onPreview = app.playbackController::previewPlaybackSpeed,
                                )
                            }
                        }

                        if (shown(SettingEntry.EQUALIZER)) {
                            SettingItem(divider = rows.next()) {
                                // --- System Equalizer (adapted from BitChord) ---
                                // Hands off to whichever equalizer app is installed
                                // (system EQ, Wavelet, Poweramp EQ, etc.) rather than
                                // building custom DSP — the standard, documented way
                                // for a media app to support this at all.
                                SettingActionRow(
                                    title = "Equalizer",
                                    icon = Icons.Filled.Equalizer,
                                    subtitle = "Open the system or a third-party equalizer app for this audio session.",
                                    onClick = onClick@{
                                        val sessionId = app.playbackController.audioSessionId()
                                        if (sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                                            com.whiplash.music.ui.common.ToastController.show("Start playing a song first")
                                            return@onClick
                                        }
                                        val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                            putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                            putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, sessionId)
                                            putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                                        }
                                        runCatching {
                                            // Some equalizer apps (confirmed on-device: AOSP's own
                                            // MusicFX) derive the calling package from the launching
                                            // Activity's own identity via startActivityForResult
                                            // rather than trusting EXTRA_PACKAGE_NAME alone — a plain
                                            // startActivity() left MusicFX logging "Package name is
                                            // null" even though the intent otherwise launched
                                            // correctly. Prefer startActivityForResult when this
                                            // context is (or wraps) a real Activity; fall back to
                                            // plain startActivity if it's some other Context type.
                                            val activity = context as? android.app.Activity
                                                ?: (context as? android.content.ContextWrapper)?.baseContext as? android.app.Activity
                                            if (activity != null) {
                                                activity.startActivityForResult(intent, EQUALIZER_REQUEST_CODE)
                                            } else {
                                                context.startActivity(intent)
                                            }
                                        }.onFailure { com.whiplash.music.ui.common.ToastController.show("No equalizer app found") }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (SettingsSection.DOWNLOADS in sections) {
                item(key = "section:DOWNLOADS") {
                    if (flat) {
                        SectionLabel(SettingsSection.DOWNLOADS.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.DOWNLOADS) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.DOWNLOAD_QUALITY)) {
                            SettingItem(divider = rows.next()) {
                                // --- Download Quality ---
                                // Deliberately separate from Audio Quality above: a
                                // download is a one-time, permanent fetch (storage +
                                // one-time data cost) rather than a repeated streaming
                                // cost, so a user may reasonably want a different
                                // quality for offline downloads than for live
                                // streaming playback (e.g. small downloads for
                                // offline listening while still streaming at a
                                // higher quality when online).
                                SettingRow(
                                    title = "Download Quality",
                                    icon = Icons.Filled.Download,
                                    subtitle = "Applies to new downloads. Higher quality uses more storage.",
                                )
                                downloadQuality?.let { quality ->
                                    AudioQualitySelector(
                                        selected = quality,
                                        onSelect = viewModel::setDownloadQuality,
                                    )
                                }
                            }
                        }

                        if (shown(SettingEntry.DOWNLOAD_WIFI)) {
                            SettingItem(divider = rows.next()) {
                                // --- Download on Wi-Fi only ---
                                // Sits with Download Quality because both decide what a
                                // download costs; streaming has its own per-network quality.
                                SettingToggleRow(
                                    title = "Download on Wi-Fi only",
                                    icon = Icons.Filled.Wifi,
                                    subtitle = "Don't start downloads on mobile data or metered networks.",
                                    checked = downloadWifiOnly,
                                    onCheckedChange = viewModel::setDownloadWifiOnly,
                                )
                            }
                        }
                    }
                }
            }

            if (SettingsSection.NOW_PLAYING in sections) {
                item(key = "section:NOW_PLAYING") {
                    if (flat) {
                        SectionLabel(SettingsSection.NOW_PLAYING.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.NOW_PLAYING) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.SEEK_BAR)) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Progress Bar Style",
                                        icon = Icons.Filled.LinearScale,
                                        subtitle = "Choose how the full player's seek bar looks. Currently using ${seekBarStyle.displayName}.",
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceMd))
                                    SeekBarStylePicker(selected = seekBarStyle, onSelect = viewModel::setSeekBarStyle)
                                }
                            }
                        }

                        if (shown(SettingEntry.ARTWORK_COLOURS)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Artwork colours in player",
                                    icon = Icons.Filled.ColorLens,
                                    subtitle = "Tint the Now Playing screen with colours from the album art.",
                                    checked = playerArtworkColors,
                                    onCheckedChange = viewModel::setPlayerArtworkColors,
                                )
                            }
                        }

                        if (shown(SettingEntry.FULL_BLEED)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Full-bleed artwork",
                                    icon = Icons.Filled.Fullscreen,
                                    subtitle = "Show the album art edge to edge across the top of the full player.",
                                    checked = playerHeroArtwork,
                                    onCheckedChange = viewModel::setPlayerHeroArtwork,
                                )
                            }
                        }

                        if (shown(SettingEntry.STATS)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Stats for Nerds",
                                    icon = Icons.Filled.Info,
                                    subtitle = "Show the codec, sample rate and bitrate of what's playing under the artwork.",
                                    checked = statsForNerdsEnabled,
                                    onCheckedChange = viewModel::setStatsForNerdsEnabled,
                                )
                            }
                        }
                    }
                }
            }

            if (SettingsSection.LYRICS in sections) {
                item(key = "section:LYRICS") {
                    if (flat) {
                        SectionLabel(SettingsSection.LYRICS.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.LYRICS) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.LYRIC_LINE)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Lyric line in player",
                                    icon = Icons.Filled.Lyrics,
                                    subtitle = "Show the current lyric above the seek bar in the full player.",
                                    checked = playerLyricStrip,
                                    onCheckedChange = viewModel::setPlayerLyricStrip,
                                )
                            }
                        }

                        if (shown(SettingEntry.SWIPE_UP_LYRICS)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Swipe up for lyrics",
                                    icon = Icons.Filled.Lyrics,
                                    subtitle = "Swipe up on the full player to open the lyrics.",
                                    checked = swipeUpForLyrics,
                                    onCheckedChange = viewModel::setSwipeUpForLyrics,
                                )
                            }
                        }

                        if (shown(SettingEntry.LYRICS_BLUR)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Blur other lyric lines",
                                    icon = Icons.Filled.Lyrics,
                                    subtitle = if (android.os.Build.VERSION.SDK_INT >= 31) {
                                        "Softly blur the lines around the one being sung. Clears while you scroll."
                                    } else {
                                        "Needs Android 12 or newer; other lines are dimmed instead."
                                    },
                                    checked = lyricsBlurUnfocused,
                                    onCheckedChange = viewModel::setLyricsBlurUnfocused,
                                )
                            }
                        }

                        if (shown(SettingEntry.LYRICS_SOURCE)) {
                            SettingItem(divider = rows.next()) {
                                SettingRow(
                                    title = "Lyrics source",
                                    icon = Icons.Filled.Lyrics,
                                    subtitle = "Automatic tries LRCLIB (synced) first, then lyrics.ovh (plain text).",
                                )
                                LyricsSourceSelector(selected = lyricsSource, onSelect = viewModel::setLyricsSource)
                                LyricsProviderHealthLine(lyricsProviderHealth)
                            }
                        }
                    }
                }
            }

            if (SettingsSection.APPEARANCE in sections) {
                item(key = "section:APPEARANCE") {
                    if (flat) {
                        SectionLabel(SettingsSection.APPEARANCE.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.APPEARANCE) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.APP_THEME)) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Theme",
                                        icon = Icons.Filled.Palette,
                                        subtitle = "${appTheme.displayName} — ${appTheme.description}.",
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceMd))
                                    AppThemeGrid(selected = appTheme, onSelect = viewModel::setAppTheme)
                                }
                            }
                        }

                        if (shown(SettingEntry.GLASS_OPACITY) && appTheme == com.whiplash.music.ui.theme.AppTheme.LIQUID_GLASS) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Glass opacity",
                                        icon = Icons.Filled.Tune,
                                        subtitle = "0% is fully clear glass; higher frosts and tints it.",
                                    )
                                    GlassOpacitySlider(
                                        value = glassOpacity,
                                        onPreview = viewModel::previewGlassOpacity,
                                        onCommit = viewModel::saveGlassOpacity,
                                    )
                                }
                            }
                        }

                        if (shown(SettingEntry.GLASS_LENS) && appTheme == com.whiplash.music.ui.theme.AppTheme.LIQUID_GLASS) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Lens bending",
                                        icon = Icons.Filled.Tune,
                                        subtitle = "How strongly the glass edges bend what's behind them.",
                                    )
                                    GlassOpacitySlider(
                                        value = glassLens,
                                        onPreview = viewModel::previewGlassLens,
                                        onCommit = viewModel::saveGlassLens,
                                        startLabel = "Flat",
                                        endLabel = "Strong",
                                        description = "Lens bending",
                                        showSupportNote = false,
                                        defaultValue = com.whiplash.music.ui.theme.WhiplashColors.DEFAULT_GLASS_LENS,
                                    )
                                }
                            }
                        }

                        if (shown(SettingEntry.GLASS_BACKGROUND) && appTheme == com.whiplash.music.ui.theme.AppTheme.LIQUID_GLASS) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Glass background",
                                        icon = Icons.Filled.Palette,
                                        subtitle = "What shows through the glass behind every page.",
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceSm))
                                    GlassBackgroundPicker(colors = customThemeColors, onChange = viewModel::setCustomThemeColors, onPreview = viewModel::previewCustomThemeColors)
                                }
                            }
                        }

                        if (shown(SettingEntry.CUSTOM_THEME) && appTheme == com.whiplash.music.ui.theme.AppTheme.CUSTOM) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Custom theme",
                                        icon = Icons.Filled.Palette,
                                        subtitle = "Choose a background and an accent. Changes apply as you pick.",
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceSm))
                                    CustomThemeEditor(colors = customThemeColors, onChange = viewModel::setCustomThemeColors, onPreview = viewModel::previewCustomThemeColors)
                                }
                            }
                        }

                        // Accent colour only applies to themes built around it;
                        // fixed designs (Catppuccin, Nord, Rosé Pine) and Custom
                        // have their own accent, so the picker would do nothing.
                        if (shown(SettingEntry.THEME) && appTheme.usesAccentChoice) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Accent colour",
                                        icon = Icons.Filled.Palette,
                                        subtitle = if (appTheme == com.whiplash.music.ui.theme.AppTheme.DARK) {
                                            "Colours buttons and highlights, with a faint matching tint in the background. Currently ${themeVariant.displayName}."
                                        } else {
                                            "Colours buttons and highlights. Currently ${themeVariant.displayName}."
                                        },
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceMd))
                                    ThemeGrid(selected = themeVariant, onSelect = viewModel::setThemeVariant)
                                    if (themeVariant == ThemeVariant.CUSTOM) {
                                        Spacer(Modifier.height(GlassTokens.spaceSm))
                                        CustomAccentEditor(colors = customThemeColors, onChange = viewModel::setCustomThemeColors, onPreview = viewModel::previewCustomThemeColors)
                                    }
                                }
                            }
                        }

                        if (shown(SettingEntry.HOME_SHELVES)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Home shelves",
                                    icon = Icons.Filled.ViewCarousel,
                                    subtitle = "Show album and playlist shelves on Home, picked from the artists you play.",
                                    checked = homeShelvesEnabled,
                                    onCheckedChange = viewModel::setHomeShelvesEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.REPLAY)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Monthly Replay",
                                    icon = Icons.Filled.Insights,
                                    subtitle = "Count what you play for a monthly recap of your top songs and artists, opened from Home. Counting stays on this device.",
                                    checked = replayEnabled,
                                    onCheckedChange = viewModel::setReplayEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.EXPLORE)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Explore in Search",
                                    icon = Icons.Filled.Explore,
                                    subtitle = "Show new releases, charts and moods & genres on the Search screen.",
                                    checked = exploreEnabled,
                                    onCheckedChange = viewModel::setExploreEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.MUSIC_TASTE)) {
                            SettingItem(divider = rows.next()) {
                                val picks = tasteArtists + tasteGenres + tasteLanguages
                                SettingActionRow(
                                    title = "Your music taste",
                                    icon = Icons.Filled.AutoAwesome,
                                    subtitle = when {
                                        picks.isEmpty() -> "Pick languages, genres and artists to personalise Home."
                                        picks.size <= 2 -> picks.joinToString(", ")
                                        else -> "${picks.take(2).joinToString(", ")} and ${picks.size - 2} more"
                                    },
                                    onClick = { com.whiplash.music.ui.onboarding.OnboardingController.openTaste() },
                                )
                            }
                        }

                        if (shown(SettingEntry.SPEED_DIAL_PAGES)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Speed dial pages",
                                    icon = Icons.Filled.ViewCarousel,
                                    subtitle = if (speedDialPaging) {
                                        "Swipe sideways through up to $speedDialPageCount pages of $speedDialGridCount songs."
                                    } else {
                                        "One page of $speedDialGridCount songs."
                                    },
                                    checked = speedDialPaging,
                                    onCheckedChange = viewModel::setSpeedDialPaging,
                                )
                            }
                        }

                        if (shown(SettingEntry.SPEED_DIAL_GRID)) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Speed dial grid size",
                                        icon = Icons.Filled.GridView,
                                        subtitle = "Grid view shows $speedDialGridCount songs per page.",
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceMd))
                                    CountSelector(
                                        options = com.whiplash.music.data.repository.SPEED_DIAL_GRID_COUNTS,
                                        selected = speedDialGridCount,
                                        describe = { "$it Speed dial songs per page" },
                                        onSelect = viewModel::setSpeedDialGridCount,
                                    )
                                }
                            }
                        }

                        if (speedDialPaging && shown(SettingEntry.SPEED_DIAL_PAGE_COUNT)) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Speed dial page count",
                                        icon = Icons.Filled.GridView,
                                        subtitle = "Up to ${speedDialPageCount * speedDialGridCount} songs across $speedDialPageCount pages.",
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceMd))
                                    CountSelector(
                                        options = com.whiplash.music.data.repository.SPEED_DIAL_PAGE_COUNTS,
                                        selected = speedDialPageCount,
                                        describe = { "$it Speed dial pages" },
                                        onSelect = viewModel::setSpeedDialPageCount,
                                    )
                                }
                            }
                        }

                        // Only meaningful while Speed dial has pages.
                        if (speedDialPaging && shown(SettingEntry.SPEED_DIAL_PEEK)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Speed dial: peek next page",
                                    icon = Icons.Filled.ViewCarousel,
                                    subtitle = "Show the edge of the next page so it's clear you can swipe.",
                                    checked = speedDialPeek,
                                    onCheckedChange = viewModel::setSpeedDialPeek,
                                )
                            }
                        }

                        if (shown(SettingEntry.QUICK_PICKS_GRID)) {
                            SettingItem(divider = rows.next()) {
                                Column {
                                    SettingRow(
                                        title = "Quick Picks grid size",
                                        icon = Icons.Filled.GridView,
                                        subtitle = if (quickPicksGridCount == 0) {
                                            "Grid view shows every song at once."
                                        } else {
                                            "Grid view shows $quickPicksGridCount songs at a time; swipe sideways for more."
                                        },
                                    )
                                    Spacer(Modifier.height(GlassTokens.spaceMd))
                                    QuickPicksGridCountSelector(quickPicksGridCount, viewModel::setQuickPicksGridCount)
                                }
                            }
                        }

                        // "All" is one tall grid with no pages, so there's nothing to peek at.
                        if (quickPicksGridCount != 0 && shown(SettingEntry.QUICK_PICKS_PEEK)) {
                            SettingItem(divider = rows.next()) {
                                SettingToggleRow(
                                    title = "Quick Picks: peek next page",
                                    icon = Icons.Filled.ViewCarousel,
                                    subtitle = "Show the edge of the next page so it's clear you can swipe.",
                                    checked = quickPicksPeek,
                                    onCheckedChange = viewModel::setQuickPicksPeek,
                                )
                            }
                        }

                        if (shown(SettingEntry.REDUCE_ANIMATIONS)) {
                            SettingItem(divider = rows.next()) {
                                // --- Reduce animations ---
                                // Same effect as the system's "Remove animations", but just
                                // for this app: screen transitions become instant and
                                // decorative motion (shimmer, artwork shrink) stops.
                                SettingToggleRow(
                                    title = "Reduce animations",
                                    icon = Icons.Filled.Animation,
                                    subtitle = "Turn off screen transitions and decorative motion in the app.",
                                    checked = reduceAnimations,
                                    onCheckedChange = viewModel::setReduceAnimations,
                                )
                            }
                        }
                    }
                }
            }

            if (SettingsSection.ACCOUNT in sections) {
                item(key = "section:ACCOUNT") {
                    if (flat) {
                        SectionLabel(SettingsSection.ACCOUNT.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    val enabled = cloudSyncEnabled == true
                    val signedIn = cloudState.account != null
                    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg)) {
                        if (enabled && shown(SettingEntry.ACCOUNT_SYNC)) {
                            SettingsCardColumn(SettingsSection.ACCOUNT) {
                                Box(Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                                    if (signedIn) {
                                        AccountProfileHero(
                                            state = cloudState,
                                            onSyncNow = syncNow,
                                            onSignOut = { showSignOutConfirm = true },
                                            onSignInAgain = startSignIn,
                                            onEditPhoto = pickPhoto,
                                            onEditName = { showEditNameDialog = true },
                                            photoVersion = photoVersion,
                                            showEmail = showAccountEmail,
                                        )
                                    } else {
                                        AccountSignInPrompt(cloudState, startSignIn)
                                    }
                                }
                            }
                        }
                        SettingsCardColumn(SettingsSection.ACCOUNT) {
                            val rows = SettingsRowCounter()
                            if (enabled && signedIn && shown(SettingEntry.PROFILE)) {
                                SettingItem(divider = rows.next()) {
                                    SettingActionRow(
                                        title = "Edit name",
                                        icon = Icons.Filled.Badge,
                                        subtitle = cloudState.account?.name ?: "Add a name",
                                        onClick = { showEditNameDialog = true },
                                    )
                                }
                                SettingItem(divider = rows.next()) {
                                    SettingActionRow(
                                        title = "Change profile photo",
                                        icon = Icons.Filled.AccountCircle,
                                        subtitle = "Pick a picture from your gallery.",
                                        onClick = pickPhoto,
                                    )
                                }
                                if (customProfile?.name != null || customProfile?.photoJpeg != null) {
                                    SettingItem(divider = rows.next()) {
                                        SettingActionRow(
                                            title = "Use Google name and photo",
                                            icon = Icons.Filled.Restore,
                                            subtitle = "Undo your changes to the profile.",
                                            onClick = {
                                                cloudSync.profileStore.reset()
                                                com.whiplash.music.ui.common.ToastController.show("Profile reset")
                                            },
                                        )
                                    }
                                }
                            }
                            if (shown(SettingEntry.ACCOUNT_SYNC)) {
                                SettingItem(divider = rows.next()) {
                                    SettingToggleRow(
                                        title = "Account & sync",
                                        icon = Icons.Filled.CloudSync,
                                        subtitle = "Sign in with Google to sync your library between devices. Off hides it and stops all syncing.",
                                        checked = enabled,
                                        onCheckedChange = setCloudSyncEnabled,
                                    )
                                }
                            }
                            if (enabled && signedIn && shown(SettingEntry.AUTO_SYNC)) {
                                SettingItem(divider = rows.next()) {
                                    SettingToggleRow(
                                        title = "Auto-sync",
                                        icon = Icons.Filled.Sync,
                                        subtitle = "Sync changes in the background while Whiplash is open.",
                                        checked = cloudState.autoSync,
                                        onCheckedChange = cloudSync::setAutoSync,
                                    )
                                }
                            }
                            if (enabled && signedIn && shown(SettingEntry.SHOW_SYNC_STATUS)) {
                                SettingItem(divider = rows.next()) {
                                    SettingToggleRow(
                                        title = "Show sync status",
                                        icon = Icons.Filled.CloudDone,
                                        subtitle = "Show when you last synced on your profile in Settings. Problems always show.",
                                        checked = showSyncStatus,
                                        onCheckedChange = { show -> cloudScope.launch { app.settingsRepository.setShowSyncStatus(show) } },
                                    )
                                }
                            }
                            if (enabled && signedIn && shown(SettingEntry.SHOW_EMAIL)) {
                                SettingItem(divider = rows.next()) {
                                    SettingToggleRow(
                                        title = "Show email",
                                        icon = Icons.Filled.AlternateEmail,
                                        subtitle = "Show your email address on your profile in Settings.",
                                        checked = showAccountEmail,
                                        onCheckedChange = { show -> cloudScope.launch { app.settingsRepository.setShowAccountEmail(show) } },
                                    )
                                }
                            }
                            if (enabled && signedIn && shown(SettingEntry.SHOW_PHOTO)) {
                                SettingItem(divider = rows.next()) {
                                    SettingToggleRow(
                                        title = "Show profile photo",
                                        icon = Icons.Filled.AccountCircle,
                                        subtitle = "Off shows your initial instead of your photo in Settings.",
                                        checked = showAccountPhoto,
                                        onCheckedChange = { show -> cloudScope.launch { app.settingsRepository.setShowAccountPhoto(show) } },
                                    )
                                }
                            }
                            if (enabled && signedIn && shown(SettingEntry.SYNC_CATEGORIES)) {
                                SettingItem(divider = rows.next()) {
                                    SettingToggleRow(
                                        title = "Choose what syncs",
                                        icon = Icons.Filled.Checklist,
                                        subtitle = if (cloudState.selective) {
                                            if (cloudState.excluded.size == com.whiplash.music.data.sync.SyncCategory.entries.size) {
                                                "Nothing is selected, so only your profile syncs."
                                            } else {
                                                "Only the parts selected below sync. The rest stays on this phone and in your Drive."
                                            }
                                        } else {
                                            "Off: everything syncs. On: pick which parts sync."
                                        },
                                        checked = cloudState.selective,
                                        onCheckedChange = cloudSync::setSelective,
                                    )
                                    if (cloudState.selective) {
                                        androidx.compose.foundation.layout.FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
                                            verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
                                        ) {
                                            val allOn = cloudState.excluded.isEmpty()
                                            BackupCategoryChip(
                                                text = "All",
                                                selected = allOn,
                                                onClick = { cloudSync.setAllCategories(!allOn) },
                                            )
                                            com.whiplash.music.data.sync.SyncCategory.entries.forEach { category ->
                                                val on = category !in cloudState.excluded
                                                BackupCategoryChip(
                                                    text = category.label,
                                                    selected = on,
                                                    onClick = { cloudSync.setCategoryEnabled(category, !on) },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (enabled && shown(SettingEntry.ACCOUNT_SYNC)) {
                                SettingItem(divider = rows.next()) {
                                    SettingRow(
                                        title = "What syncs",
                                        icon = Icons.Filled.Info,
                                        subtitle = "Playlists, favourites, history, Speed dial, Monthly Replay, lyric timing, settings and your profile. Downloaded songs and gallery covers stay on each device.",
                                    )
                                }
                            }
                            if (enabled && signedIn && shown(SettingEntry.ACCOUNT_SYNC)) {
                                SettingItem(divider = rows.next()) {
                                    SettingActionRow(
                                        title = "Delete synced data",
                                        icon = Icons.Filled.CloudOff,
                                        subtitle = "Remove your Whiplash data from Google Drive.",
                                        onClick = { showDeleteCloudConfirm = true },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (SettingsSection.STORAGE in sections) {
                item(key = "section:STORAGE") {
                    if (flat) {
                        SectionLabel(SettingsSection.STORAGE.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.STORAGE) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.CACHE_SONGS)) {
                            SettingItem(divider = rows.next()) {
                                // --- Audio cache toggle ---
                                SettingToggleRow(
                                    title = "Cache Songs",
                                    icon = Icons.Filled.Storage,
                                    subtitle = "Store recently played songs on this device so they start instantly next time, instead of streaming again. Off frees up storage but replays always re-download.",
                                    checked = audioCacheEnabled,
                                    onCheckedChange = viewModel::setAudioCacheEnabled,
                                )
                            }
                        }

                        if (shown(SettingEntry.DOWNLOADED_DATA)) {
                            SettingItem(divider = rows.next()) {
                                // --- Offline downloads size + delete all ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        SettingRow(
                                            title = "Downloaded songs",
                                            icon = Icons.Filled.DownloadForOffline,
                                            subtitle = formatDownloadsUsage(downloadsUsage),
                                        )
                                    }
                                    Spacer(Modifier.width(GlassTokens.spaceSm))
                                    GlassButton(
                                        text = "Clear",
                                        onClick = { showClearDownloadsConfirm = true },
                                        enabled = downloadsUsage.first > 0,
                                    )
                                }
                            }
                        }

                        if (shown(SettingEntry.CACHED_DATA)) {
                            SettingItem(divider = rows.next()) {
                                // --- Cache size + clear ---
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingRow(
                                        title = "Cached data",
                                        icon = Icons.Filled.DataUsage,
                                        subtitle = formatCacheSize(cacheSizeBytes),
                                    )
                                    GlassButton(
                                        text = "Clear",
                                        onClick = viewModel::clearCache,
                                        // Bright/pressable only when there's actually
                                        // something to clear — GlassButton's own
                                        // enabled=false state already fades it out via
                                        // GlassTokens.opacityDisabled, giving a real
                                        // "not currently actionable" affordance instead
                                        // of a button that always looks clickable but
                                        // silently does nothing when the cache is empty.
                                        enabled = cacheSizeBytes > 0L,
                                    )
                                }
                            }
                        }

                        if (shown(SettingEntry.RESET_APP)) {
                            SettingItem(divider = rows.next()) {
                                SettingActionRow(
                                    title = "Reset app",
                                    icon = Icons.Filled.RestartAlt,
                                    subtitle = if (resetting) "Resetting…" else "Erase everything and start fresh",
                                    titleColor = WhiplashColors.error,
                                    onClick = { if (!resetting) showResetConfirm = true },
                                )
                            }
                        }
                    }
                }
            }

            if (SettingsSection.BACKUP in sections) {
                item(key = "section:BACKUP") {
                    if (flat) {
                        SectionLabel(SettingsSection.BACKUP.label)
                        Spacer(Modifier.height(GlassTokens.spaceSm))
                    }
                    SettingsCardColumn(SettingsSection.BACKUP) {
                        val rows = SettingsRowCounter()
                        if (shown(SettingEntry.BACKUP)) {
                            SettingItem(divider = rows.next()) {
                                SettingRow(
                                    title = "Local backup",
                                    icon = Icons.Filled.Backup,
                                    subtitle = "Choose what to back up below, then save it to a file you choose.\n" +
                                        formatLastBackupSubtitle(lastBackupTimeMs),
                                )
                                // Per-category selector — between the description
                                // above and the action buttons below, per explicit
                                // steering on positioning. Uses GlassChip (this app's
                                // own existing filter/tag component, already used for
                                // Search's result tabs) in a wrapping FlowRow rather
                                // than a tall stack of full checkbox rows with
                                // descriptions — 6 categories' descriptions each on
                                // their own line pushed this card, and everything
                                // below it on the Settings screen, considerably
                                // further down with comparatively little benefit
                                // (the categories are largely self-explanatory from
                                // their names alone). All chips selected by default
                                // so "Back up now" still backs up everything with
                                // zero extra taps, exactly matching the old always-
                                // full behavior for anyone who doesn't touch these.
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
                                    verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm),
                                ) {
                                    val allSelected = selectedBackupCategories.size == com.whiplash.music.data.backup.BackupCategory.entries.size
                                    BackupCategoryChip(
                                        text = "All",
                                        selected = allSelected,
                                        onClick = {
                                            selectedBackupCategories = if (allSelected) {
                                                emptySet()
                                            } else {
                                                com.whiplash.music.data.backup.BackupCategory.entries.toSet()
                                            }
                                        },
                                    )
                                    com.whiplash.music.data.backup.BackupCategory.entries.forEach { category ->
                                        val checked = category in selectedBackupCategories
                                        BackupCategoryChip(
                                            text = category.displayName,
                                            selected = checked,
                                            onClick = {
                                                selectedBackupCategories = if (checked) {
                                                    selectedBackupCategories - category
                                                } else {
                                                    selectedBackupCategories + category
                                                }
                                            },
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
                                ) {
                                    GlassButton(
                                        text = "Back up now",
                                        modifier = Modifier.weight(1f),
                                        enabled = selectedBackupCategories.isNotEmpty(),
                                        onClick = { backupLauncher.launch(com.whiplash.music.data.backup.BackupManager.suggestedFileName()) },
                                    )
                                    GlassButton(
                                        text = "Restore",
                                        modifier = Modifier.weight(1f),
                                        onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (flat) {
                item {
                    GithubFooter()
                }
            }
        }
    }

    val noResults: androidx.compose.foundation.lazy.LazyListScope.(List<SettingsSection>) -> Unit = { sections ->
            if (sections.isEmpty()) {
                item(key = "no-results") {
                    Text(
                        text = "No settings match \u201C${settingsQuery.trim()}\u201D",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WhiplashColors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(vertical = GlassTokens.spaceXl),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

    }

    run {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    // Hidden from TalkBack while a section page covers it.
                    .then(if (openSection != null) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                // Pinned above both the folders and the results so typing
                // never loses focus when the list underneath swaps.
                GlassSearchField(
                    query = settingsQuery,
                    onQueryChange = { settingsQuery = it },
                    placeholder = "Search settings",
                    focusRequester = settingsSearchFocus,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GlassTokens.spaceMd)
                        .padding(top = GlassTokens.spaceLg),
                )
                if (settingsQuery.isBlank()) {
                    SettingsFolderList(
                        state = rootListState,
                        summary = { section ->
                            when (section) {
                                SettingsSection.AUDIO_QUALITY -> if (perNetworkQualityEnabled) {
                                    "Wi-Fi ${audioQualityWifi.shortLabel()} · Mobile ${audioQualityCellular.shortLabel()}"
                                } else {
                                    "Streaming ${audioQuality?.shortLabel() ?: "Auto"}"
                                }
                                SettingsSection.STREAM_SOURCE -> (streamSource?.label ?: "Automatic") + when (lastStreamSource) {
                                    com.whiplash.music.playback.provider.StreamSourcePreference.NEWPIPE_ID -> " · Last song: NewPipe"
                                    com.whiplash.music.playback.provider.StreamSourcePreference.DIRECT_ID -> " · Last song: YouTube direct"
                                    else -> ""
                                }
                                SettingsSection.PLAYBACK -> listOf(
                                    if (autoplayEnabled) "Autoplay on" else "Autoplay off",
                                    if (crossfadeDurationMs > 0) "Crossfade ${crossfadeDurationMs / 1000}s" else "No crossfade",
                                    "${formatSpeed(playbackSpeed)} speed",
                                ).joinToString(" · ")
                                SettingsSection.DOWNLOADS -> "Quality ${downloadQuality?.shortLabel() ?: "Auto"}" +
                                    if (downloadWifiOnly) " · Wi-Fi only" else ""
                                SettingsSection.NOW_PLAYING -> "${seekBarStyle.displayName} progress bar" +
                                    if (playerArtworkColors) " · Artwork colours" else ""
                                SettingsSection.LYRICS -> "Source: ${lyricsSource.label}"
                                SettingsSection.APPEARANCE -> appTheme.displayName
                                SettingsSection.STORAGE -> {
                                    val cache = if (cacheSizeBytes > 0L) "Cache ${formatBytes(cacheSizeBytes)}" else "No cache"
                                    val downloads = if (downloadsUsage.first > 0) "Downloads ${formatBytes(downloadsUsage.second)}" else "No downloads"
                                    "$cache · $downloads"
                                }
                                SettingsSection.BACKUP -> formatLastBackupSubtitle(lastBackupTimeMs).removeSuffix(".")
                                SettingsSection.ACCOUNT -> accountFolderSummary(cloudSyncEnabled == true, cloudState, showAccountEmail)
                            }
                        },
                        // Drawn inside the first group's list item rather than as its own
                        // item, so the list stays at the top when the card appears.
                        header = if (cloudSyncEnabled == true) {
                            {
                                AccountHeaderCard(
                                    state = cloudState,
                                    cardColor = settingsCardColor(),
                                    onOpen = { openSection = SettingsSection.ACCOUNT },
                                    onSignIn = startSignIn,
                                    photoVersion = photoVersion,
                                    showEmail = showAccountEmail,
                                    showStatus = showSyncStatus,
                                )
                            }
                        } else {
                            null
                        },
                        onOpen = { openSection = it },
                        onQuit = { showQuitConfirm = true },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    val sections = androidx.compose.runtime.remember(settingsQuery) { visibleSections(settingsQuery) }
                    SettingsList(
                        listState, sections, settingsQuery, flat = true,
                        topPadding = GlassTokens.spaceLg, modifier = Modifier.weight(1f),
                    ) { noResults(sections) }
                }
            }

            // Section page slides over the start page, which stays composed
            // (and scrolled) underneath, so Back lands exactly where you were.
            var shownSection by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(openSection) }
            if (openSection != null) shownSection = openSection
            val navSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(SETTINGS_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            val fadeSpec = androidx.compose.animation.core.tween<Float>(SETTINGS_NAV_MS, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            androidx.compose.animation.AnimatedVisibility(
                visible = openSection != null,
                enter = if (reduceMotion) androidx.compose.animation.EnterTransition.None else
                    androidx.compose.animation.slideInHorizontally(navSpec) { it } + androidx.compose.animation.fadeIn(fadeSpec),
                exit = if (reduceMotion) androidx.compose.animation.ExitTransition.None else
                    androidx.compose.animation.slideOutHorizontally(navSpec) { it } + androidx.compose.animation.fadeOut(fadeSpec),
            ) {
                val section = shownSection
                if (section != null) {
                    SettingsSectionPage(
                        section = section,
                        onBack = { openSection = null },
                        onSearch = {
                            openSection = null
                            focusSearchRequest++
                        },
                    ) {
                        androidx.compose.runtime.key(section) {
                            val pageState = androidx.compose.foundation.lazy.rememberLazyListState()
                            SettingsList(pageState, listOf(section), "", flat = false, topPadding = GlassTokens.spaceSm) {}
                        }
                    }
                }
            }
        }
    }
}

/**
 * A selectable chip for the Advanced Backup category picker (Settings >
 * Backup & Restore) — deliberately a local variant scoped only to this
 * screen rather than a change to the shared [com.whiplash.music.ui.theme.GlassChip]
 * component used elsewhere (Search's own result-tab chips, the theme
 * picker's swatches): per explicit steering, a brighter selected fill is
 * wanted specifically for backup category selection, not as an app-wide
 * change to every chip everywhere. [GlassChip]'s own selected state uses
 * [GlassTokens.opacityElevated] (0.65) — the same value used for elevated
 * card surfaces, not a primary action — which read as a muted, greyish
 * accent tint on a light-leaning theme rather than a clean, bright one.
 * This uses a near-opaque fill instead, matching [GlassButton]'s own
 * selected/enabled brightness (see its own doc: deliberately near-opaque
 * so a primary action reads as genuinely solid, not "elevated-surface"
 * translucent).
 */
@Composable
private fun BackupCategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    // Delegates to the app's own chip rather than restyling a private copy.
    //
    // The local version filled the selected state with full-opacity accent —
    // pixel-identical to the "Back up now"/"Restore" buttons directly beneath
    // it, so a multi-select filter and a primary action button were
    // indistinguishable. GlassChip is the same control (a selectable pill)
    // already used for the Search and Library tab rows, so using it makes
    // these read as filters and matches the rest of the app for free.
    com.whiplash.music.ui.theme.GlassChip(text = text, selected = selected, onClick = onClick)
}

/**
 * Small, understated footer at the very end of Settings — a single
 * tappable row linking out to the developer's GitHub profile. Deliberately
 * plain (secondary text color, no card/border) rather than styled as
 * another settings section, since it isn't a configurable option.
 */
@Composable
private fun GithubFooter() {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = GlassTokens.spaceMd)
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://github.com/shahidthisside"),
                )
                // Real, reproduced crash this fixes: this was a bare
                // context.startActivity(intent), which throws
                // ActivityNotFoundException — killing the whole app process —
                // on any device or profile with nothing registered to handle an
                // https VIEW intent (a bare AOSP build with no browser, a
                // managed/enterprise profile, or simply a user who disabled
                // their browser). Confirmed on-device: disabling Chrome and
                // tapping this footer produced
                // "FATAL EXCEPTION: main / android.content.ActivityNotFoundException"
                // and "Process com.whiplash.music has died". The app already
                // guards exactly this pattern elsewhere (the equalizer intent
                // and the share sheet); this one call site was missed.
                runCatching { context.startActivity(intent) }
                    .onFailure { com.whiplash.music.ui.common.ToastController.show("No app available to open links") }
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(com.whiplash.music.R.drawable.ic_github),
            contentDescription = "GitHub",
            tint = WhiplashColors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(GlassTokens.spaceSm))
        Text(
            text = "github.com/shahidthisside",
            style = MaterialTheme.typography.labelMedium,
            color = WhiplashColors.textSecondary,
        )
    }
}

/** Swatch grid for picking the full player's seek bar style — 5 real, distinct styles, each with a small live preview matching its actual on-screen look. */
@Composable
private fun SeekBarStylePicker(selected: com.whiplash.music.ui.theme.SeekBarStyle, onSelect: (com.whiplash.music.ui.theme.SeekBarStyle) -> Unit) {
    val options = com.whiplash.music.ui.theme.SeekBarStyle.entries.toList()
    Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceSm)) {
        options.forEach { style ->
            val isSelected = style == selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(WhiplashRadius.medium))
                    .background(if (isSelected) WhiplashColors.surfaceElevated else Color.Transparent)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(style) }
                    .semantics { this.selected = isSelected }
                    .padding(GlassTokens.spaceMd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
            ) {
                SeekBarStylePreview(style = style, modifier = Modifier.weight(1f))
                Text(
                    text = style.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
                )
                if (isSelected) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = WhiplashColors.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A small, static preview of each seek bar style at a fixed ~55% fraction, so the user can see the real shape before picking it. */
@Composable
private fun SeekBarStylePreview(style: com.whiplash.music.ui.theme.SeekBarStyle, modifier: Modifier = Modifier) {
    val previewFraction = 0.55f
    val activeColor = WhiplashColors.textPrimary
    val inactiveColor = WhiplashColors.glassBorderStrong
    androidx.compose.foundation.Canvas(modifier = modifier.height(20.dp)) {
        val midY = size.height / 2f
        when (style) {
            com.whiplash.music.ui.theme.SeekBarStyle.CLASSIC -> {
                val splitX = size.width * previewFraction
                drawRoundRect(
                    color = inactiveColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, midY - 1.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
                drawRoundRect(
                    color = activeColor,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, midY - 1.5.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(splitX, 3.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx()),
                )
                drawCircle(color = activeColor, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(splitX, midY))
            }
            com.whiplash.music.ui.theme.SeekBarStyle.WAVY -> {
                // Mirrors WavyTrack in FullPlayerScreen, including sampling the
                // sine at every pixel.
                //
                // This previously stepped by half a wavelength, which made the
                // preview a dead-flat line indistinguishable from Minimal — and
                // not merely coarse but exactly flat, because sampling at
                // multiples of wavelength/2 evaluates sin() at 0, PI, 2PI, ...,
                // every one of which is a zero crossing. So the wave was
                // sampled only at the points where it has no displacement.
                //
                // The wavelength, amplitude, stroke width and thumb dot are all
                // matched to the real track too, so this preview now shows what
                // the setting actually does rather than an approximation of it.
                val splitX = size.width * previewFraction
                val amplitudePx = 4.dp.toPx()
                val wavelengthPx = 20.dp.toPx()
                val wavePath = androidx.compose.ui.graphics.Path()
                wavePath.moveTo(0f, midY)
                var x = 1f
                while (x <= splitX) {
                    val radians = x / wavelengthPx * (2 * Math.PI).toFloat()
                    wavePath.lineTo(x, midY + amplitudePx * kotlin.math.sin(radians))
                    x += 1f
                }
                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 3.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
                drawLine(
                    color = inactiveColor,
                    start = androidx.compose.ui.geometry.Offset(splitX, midY),
                    end = androidx.compose.ui.geometry.Offset(size.width, midY),
                    strokeWidth = 3.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
                drawCircle(
                    color = activeColor,
                    radius = 5.dp.toPx(),
                    center = androidx.compose.ui.geometry.Offset(splitX, midY),
                )
            }
            com.whiplash.music.ui.theme.SeekBarStyle.WAVEFORM -> {
                val random = kotlin.random.Random(seed = 42)
                val barCount = 20
                val heights = List(barCount) { 0.35f + random.nextFloat() * 0.65f }
                val barWidth = size.width / (barCount * 1.6f)
                val gap = barWidth * 0.6f
                val activeBars = (previewFraction * barCount).toInt()
                for (i in 0 until barCount) {
                    val barHeightPx = size.height * heights[i]
                    val xOffset = i * (barWidth + gap)
                    val color = if (i <= activeBars) activeColor else inactiveColor
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(xOffset, (size.height - barHeightPx) / 2f),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeightPx),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2f, barWidth / 2f),
                    )
                }
            }
            com.whiplash.music.ui.theme.SeekBarStyle.MINIMAL -> {
                val splitX = size.width * previewFraction
                drawLine(inactiveColor, androidx.compose.ui.geometry.Offset(0f, midY), androidx.compose.ui.geometry.Offset(size.width, midY), strokeWidth = 1.5.dp.toPx())
                drawLine(activeColor, androidx.compose.ui.geometry.Offset(0f, midY), androidx.compose.ui.geometry.Offset(splitX, midY), strokeWidth = 1.5.dp.toPx())
            }
            com.whiplash.music.ui.theme.SeekBarStyle.HAIRLINE -> {
                val splitX = size.width * previewFraction
                drawLine(inactiveColor, androidx.compose.ui.geometry.Offset(0f, midY), androidx.compose.ui.geometry.Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                drawLine(activeColor, androidx.compose.ui.geometry.Offset(0f, midY), androidx.compose.ui.geometry.Offset(splitX, midY), strokeWidth = 1.dp.toPx())
                drawCircle(color = activeColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(splitX, midY))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    // Same small caps label as the folder groups on the start page.
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
        ),
        color = WhiplashColors.textSecondary,
        modifier = Modifier.padding(start = GlassTokens.spaceXs).semantics { heading() },
    )
}

/** Real cached-bytes size formatted for display (e.g. "42.3 MB"), matching Spotify's own storage settings pattern. */
private fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "No cached songs yet."
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.1f GB used".format(mb / 1024.0) else "%.1f MB used".format(mb)
}

/** "Last backup: <date> at <time>" if one exists, otherwise a plain "never backed up yet" state — never a fabricated/default timestamp. */
private fun formatLastBackupSubtitle(lastBackupTimeMs: Long?): String {
    if (lastBackupTimeMs == null) return "You haven't backed up yet."
    val formatter = java.text.SimpleDateFormat("MMM d, yyyy 'at' h:mm a", java.util.Locale.getDefault())
    return "Last backup: ${formatter.format(java.util.Date(lastBackupTimeMs))}."
}

@Composable
private fun SettingRow(title: String, subtitle: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SettingLeadingIcon(icon)
        Column {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
        }
    }
}

/**
 * Leading icon slot, occupying the same position and width as the artwork
 * thumbnail on every other list row in the app.
 *
 * Settings rows are the only ones in the app carrying two lines of
 * explanatory prose, and with nothing to the left of that text the screen
 * read as an undifferentiated wall no matter how much space was put between
 * rows. An icon gives each setting an anchor to scan by and makes the row
 * structurally the same shape as a Home or Library row: leading visual,
 * title, subtitle, trailing control.
 *
 * Reserves its width even when null so that a row without an icon still
 * aligns its text with the rows above and below it.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SettingLeadingIcon(icon: ImageVector?) {
    // A small tinted tile in the section's colour, the same shape as the
    // folder icons on the Settings start page. The slot is reserved even
    // without an icon so text in a card always lines up.
    val tint = com.whiplash.music.ui.theme.readableTint(LocalSettingTint.current)
    Box(modifier = Modifier.width(SETTING_ICON_SLOT), contentAlignment = Alignment.CenterStart) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(SETTING_TILE_SIZE)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(9.dp))
                    .background(tint.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(SETTING_ICON_SIZE),
                )
            }
        }
    }
}

/** Width reserved for [SettingLeadingIcon] — icon plus the gap to the text. */
private val SETTING_ICON_SLOT = 46.dp

/** Tinted tile behind each row icon. */
private val SETTING_TILE_SIZE = 32.dp

/** Drawn icon size — matches the app's other row-level icons. */
private val SETTING_ICON_SIZE = 18.dp



/** A tappable settings row with no toggle/selector — just a title/subtitle that launches [onClick] (section 57: accessible 48dp+ touch target via the Row's own padding). */
@Composable
private fun SettingActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    titleColor: androidx.compose.ui.graphics.Color = WhiplashColors.textPrimary,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingRowBleed()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = SETTING_ITEM_PAD_H, vertical = SETTING_ITEM_PAD_V)
            .padding(vertical = GlassTokens.spaceSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLeadingIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = titleColor)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
) {
    val haptic = LocalHapticFeedback.current
    // The whole row toggles, not just the switch: a far bigger target, and
    // TalkBack reads the title, subtitle and state as one control.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingRowBleed()
            .toggleable(
                value = checked,
                role = androidx.compose.ui.semantics.Role.Switch,
                onValueChange = { newValue ->
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onCheckedChange(newValue)
                },
            )
            .padding(horizontal = SETTING_ITEM_PAD_H, vertical = SETTING_ITEM_PAD_V),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingLeadingIcon(icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
        }
        Spacer(Modifier.width(GlassTokens.spaceSm))
        Switch(
            checked = checked,
            // Null: the row above owns the click (and the section 57 haptic),
            // so a tap on the switch itself toggles exactly once.
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = WhiplashColors.onAccent,
                checkedTrackColor = WhiplashColors.accent,
                uncheckedThumbColor = WhiplashColors.textSecondary,
                uncheckedTrackColor = WhiplashColors.surfaceGlass,
                uncheckedBorderColor = WhiplashColors.glassBorderStrong,
            ),
        )
    }
}

/**
 * Premium pill-segment quality selector — replaces the earlier bare
 * horizontal-scrolling [com.whiplash.music.ui.theme.GlassChip] row (the
 * "poor/cheap" look flagged explicitly) with a single continuous rounded
 * track and an animated selection indicator.
 */
@Composable
private fun AudioQualitySelector(selected: AudioQuality, onSelect: (AudioQuality) -> Unit) {
    val options = AudioQuality.entries.toList()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.surfaceGlass)
            .padding(3.dp),
    ) {
        options.forEach { quality ->
            val isSelected = quality == selected
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.accent else Color.Transparent,
                label = "qualitySegmentBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.onAccent else WhiplashColors.textSecondary,
                label = "qualitySegmentFg",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(bg)
                    .semantics { this.selected = isSelected }
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(quality) }
                    .padding(vertical = GlassTokens.spaceSm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = quality.shortLabel(), style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
}

@Composable
private fun QuickPicksGridCountSelector(selected: Int, onSelect: (Int) -> Unit) =
    CountSelector(
        options = com.whiplash.music.data.repository.QUICK_PICKS_GRID_COUNTS,
        selected = selected,
        describe = { count -> if (count == 0) "Show all Quick Picks" else "$count Quick Picks per page" },
        label = { count -> if (count == 0) "All" else count.toString() },
        onSelect = onSelect,
    )

/** Pill-shaped segmented picker for a small set of numbers (grid size, page count). */
@Composable
private fun CountSelector(
    options: List<Int>,
    selected: Int,
    describe: (Int) -> String,
    onSelect: (Int) -> Unit,
    label: (Int) -> String = { it.toString() },
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.surfaceGlass)
            .padding(3.dp),
    ) {
        options.forEach { count ->
            val isSelected = count == selected
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.accent else Color.Transparent,
                label = "qpCountBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.onAccent else WhiplashColors.textSecondary,
                label = "qpCountFg",
            )
            val text = label(count)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(bg)
                    .semantics {
                        this.selected = isSelected
                        contentDescription = describe(count)
                    }
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(count) }
                    .padding(vertical = GlassTokens.spaceSm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun LyricsSourceSelector(
    selected: com.whiplash.music.data.lyrics.LyricsSourcePreference,
    onSelect: (com.whiplash.music.data.lyrics.LyricsSourcePreference) -> Unit,
) = SegmentedChoice(
    options = com.whiplash.music.data.lyrics.LyricsSourcePreference.entries,
    selected = selected,
    label = { it.label },
    onSelect = onSelect,
)

/** A pill row of equal-width choices, one selected (lyrics source, stream source). */
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.surfaceGlass)
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.accent else Color.Transparent,
                label = "segmentBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.onAccent else WhiplashColors.textSecondary,
                label = "segmentFg",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(bg)
                    .semantics { this.selected = isSelected }
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                        if (!isSelected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(option)
                    }
                    .padding(vertical = GlassTokens.spaceSm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = label(option), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
            }
        }
    }
}

/** One line per provider: working / failing (skipped for a few minutes) / not used yet. */
@Composable
private fun LyricsProviderHealthLine(health: Map<String, com.whiplash.music.data.lyrics.ProviderHealth>) {
    val names = mapOf("LRCLIB" to "LRCLIB", "LYRICS_OVH" to "lyrics.ovh")
    val now = System.currentTimeMillis()
    val text = names.entries.joinToString("  ·  ") { (id, name) ->
        val h = health[id] ?: com.whiplash.music.data.lyrics.ProviderHealth()
        val status = when (h.state) {
            com.whiplash.music.data.lyrics.ProviderHealth.State.UNKNOWN -> "not used yet"
            com.whiplash.music.data.lyrics.ProviderHealth.State.WORKING -> "working"
            com.whiplash.music.data.lyrics.ProviderHealth.State.FAILING ->
                if (h.cooldownUntilMs > now) "failing, paused" else "failing"
        }
        "$name: $status"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = WhiplashColors.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = GlassTokens.spaceXs, start = GlassTokens.spaceSm)
            .semantics { contentDescription = "Lyrics provider status: $text" },
    )
}

private fun AudioQuality.shortLabel(): String = when (this) {
    AudioQuality.AUTO -> "Auto"
    AudioQuality.LOW -> "Low"
    AudioQuality.MEDIUM -> "Med"
    AudioQuality.HIGH -> "High"
    AudioQuality.HIGHEST -> "Max"
}

/** Swatch grid for Appearance theme selection — 6 real, distinct dark palettes. */
@Composable
private fun ThemeGrid(selected: ThemeVariant, onSelect: (ThemeVariant) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd)) {
        items(ThemeVariant.entries, key = { it.name }) { variant ->
            ThemeSwatch(variant = variant, isSelected = variant == selected, onClick = { onSelect(variant) })
        }
    }
}

@Composable
private fun ThemeSwatch(variant: ThemeVariant, isSelected: Boolean, onClick: () -> Unit) {
    // Drawn as it would actually look in the current theme (contrast-adjusted).
    val theme = WhiplashColors.theme
    val custom = WhiplashColors.customColors
    val palette = androidx.compose.runtime.remember(theme, variant, custom) {
        com.whiplash.music.ui.theme.resolvePalette(theme, variant, custom)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(GlassTokens.spaceXs),
    ) {
        Row(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 2.5.dp else GlassTokens.borderWidth,
                    color = if (isSelected) palette.accent else WhiplashColors.glassBorder,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(palette.background),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(palette.accent),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                } else if (variant == ThemeVariant.CUSTOM) {
                    Icon(
                        Icons.Filled.Palette,
                        contentDescription = null,
                        tint = palette.onAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(GlassTokens.spaceXs))
        Text(
            text = variant.displayName,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) WhiplashColors.textPrimary else WhiplashColors.textSecondary,
            maxLines = 2,
            modifier = Modifier.width(64.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Same slide as the app's detail pages (MainActivity's DETAIL_NAV_MS). */
private const val SETTINGS_NAV_MS = 380

private fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).toInt() / 100f
    return if (rounded % 1f == 0f) "${rounded.toInt()}x" else "${rounded}x"
}

/** Icon and tile colour for each section's folder row. */
private fun SettingsSection.folderIcon(): ImageVector = when (this) {
    SettingsSection.AUDIO_QUALITY -> Icons.Filled.GraphicEq
    SettingsSection.STREAM_SOURCE -> Icons.Filled.Hub
    SettingsSection.PLAYBACK -> Icons.Filled.PlayCircle
    SettingsSection.DOWNLOADS -> Icons.Filled.Download
    SettingsSection.NOW_PLAYING -> Icons.Filled.Album
    SettingsSection.LYRICS -> Icons.Filled.Lyrics
    SettingsSection.APPEARANCE -> Icons.Filled.Palette
    SettingsSection.STORAGE -> Icons.Filled.SdStorage
    SettingsSection.BACKUP -> Icons.Filled.Backup
    SettingsSection.ACCOUNT -> Icons.Filled.CloudSync
}

private fun SettingsSection.folderTint(): androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(
    when (this) {
        SettingsSection.AUDIO_QUALITY -> 0xFF8FA8FF
        SettingsSection.STREAM_SOURCE -> 0xFFB7D46E
        SettingsSection.PLAYBACK -> 0xFF6FD6A8
        SettingsSection.DOWNLOADS -> 0xFF6CC8F0
        SettingsSection.NOW_PLAYING -> 0xFFFF9A76
        SettingsSection.LYRICS -> 0xFFF2CC60
        SettingsSection.APPEARANCE -> 0xFFC29BFF
        SettingsSection.STORAGE -> 0xFF9DB0BA
        SettingsSection.BACKUP -> 0xFFF59BBE
        SettingsSection.ACCOUNT -> 0xFF7CC4FF
    },
)

/**
 * The folder start page: sections in rounded cards grouped under small
 * labels, each row showing its current values so most people never need
 * to open it.
 */
@Composable
private fun SettingsFolderList(
    state: androidx.compose.foundation.lazy.LazyListState,
    summary: (SettingsSection) -> String,
    onOpen: (SettingsSection) -> Unit,
    onQuit: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    val cardColor = settingsCardColor()
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.large)
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = GlassTokens.spaceMd,
            end = GlassTokens.spaceMd,
            top = GlassTokens.spaceLg,
            bottom = GlassTokens.miniPlayerReservedHeight,
        ),
    ) {
        SettingsGroup.entries.forEach { group ->
            item(key = "group:${group.name}") {
                Column {
                    if (header != null && group == SettingsGroup.entries.first()) {
                        header()
                        Spacer(Modifier.height(GlassTokens.spaceLg))
                    }
                    Text(
                        text = group.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
                        ),
                        color = WhiplashColors.textSecondary,
                        modifier = Modifier
                            .padding(start = GlassTokens.spaceXs, bottom = GlassTokens.spaceSm)
                            .semantics { heading() },
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassFill(shape, cardColor),
                    ) {
                        group.sections.forEachIndexed { index, section ->
                            if (index > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(start = FOLDER_TEXT_INSET)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(WhiplashColors.textPrimary.copy(alpha = 0.07f)),
                                )
                            }
                            SettingsFolderRow(section = section, summary = summary(section), onClick = { onOpen(section) })
                        }
                    }
                }
            }
        }
        // Quit: its own card at the end, styled like the folders but red and
        // without a chevron, since it's an action rather than a page.
        item(key = "quit") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassFill(shape, cardColor),
            ) {
                SettingsQuitRow(onClick = onQuit)
            }
        }
        item(key = "footer") {
            Box(Modifier.padding(top = GlassTokens.spaceSm)) { GithubFooter() }
        }
    }
}

/** Where a folder row's text starts: row padding + icon tile + gap. Dividers start here too. */
private val FOLDER_TEXT_INSET = 16.dp + 36.dp + 14.dp

@Composable
private fun SettingsFolderRow(section: SettingsSection, summary: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val tint = com.whiplash.music.ui.theme.readableTint(section.folderTint())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "Open") {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = section.folderIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = section.label, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(GlassTokens.spaceSm))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = WhiplashColors.textTertiary,
        )
    }
}

/** A section's own page: a back row naming the section, then its settings. */
@Composable
private fun SettingsSectionPage(section: SettingsSection, onBack: () -> Unit, onSearch: () -> Unit, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(Modifier.appBackground()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = GlassTokens.spaceXs, end = GlassTokens.spaceMd, top = GlassTokens.spaceSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to all settings",
                    tint = WhiplashColors.textPrimary,
                )
            }
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleLarge,
                color = WhiplashColors.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            // Jumps to any other option without stepping back first.
            androidx.compose.material3.IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search settings",
                    tint = WhiplashColors.textPrimary,
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) { content() }
    }
}

/** Colour of the icon tiles inside a settings card (its section's folder colour). */
private val LocalSettingTint = androidx.compose.runtime.staticCompositionLocalOf { androidx.compose.ui.graphics.Color.White }

/** Card fill shared by the folder start page and the section pages. */
@Composable
private fun settingsCardColor(): androidx.compose.ui.graphics.Color =
    WhiplashColors.tone(0.05f)

/**
 * One rounded card holding a section's settings, separated by hairlines —
 * the same card as the folder start page.
 */
@Composable
private fun SettingsCardColumn(section: SettingsSection, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSettingTint provides section.folderTint()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassFill(androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.large), settingsCardColor())
                // Rows' press highlights reach the card's edges; keep them inside its corners.
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(com.whiplash.music.ui.theme.WhiplashRadius.large)),
        ) {
            content()
        }
    }
}

/**
 * Hands out "draw a divider above me" for each visible row of a card: false
 * for the first, true after. A fresh one is made every time the card's
 * content runs, and it's read at the call site, so rows hidden by search
 * never leave a stray line at the top.
 */
private class SettingsRowCounter {
    private var count = 0
    fun next(): Boolean = count++ > 0
}

/** One setting inside a card: its row plus any control beneath it. */
@Composable
private fun SettingItem(divider: Boolean, content: @Composable () -> Unit) {
    if (divider) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp + SETTING_ICON_SLOT)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(WhiplashColors.textPrimary.copy(alpha = 0.07f)),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SETTING_ITEM_PAD_H, vertical = SETTING_ITEM_PAD_V),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceMd),
    ) {
        content()
    }
}

/** A setting's inner margin inside its card (see [SettingItem]). */
private val SETTING_ITEM_PAD_H = 16.dp
private val SETTING_ITEM_PAD_V = 14.dp

/**
 * Lets a tappable row's press highlight cover its whole slot in the card,
 * edge to edge and up to the dividers, instead of a box inset by
 * [SettingItem]'s margin. The row draws [SETTING_ITEM_PAD_H] /
 * [SETTING_ITEM_PAD_V] past its slot on every side (and pads its content
 * back by the same amount), while taking exactly the slot's space in the
 * layout. The card clips it to its rounded corners.
 */
private fun Modifier.settingRowBleed(): Modifier = this.layout { measurable, constraints ->
    val h = SETTING_ITEM_PAD_H.roundToPx()
    val v = SETTING_ITEM_PAD_V.roundToPx()
    val widened = constraints.copy(
        minWidth = constraints.minWidth + 2 * h,
        maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + 2 * h else constraints.maxWidth,
        minHeight = constraints.minHeight,
        maxHeight = if (constraints.hasBoundedHeight) constraints.maxHeight + 2 * v else constraints.maxHeight,
    )
    val placeable = measurable.measure(widened)
    val width = (placeable.width - 2 * h).coerceAtLeast(0)
    val height = (placeable.height - 2 * v).coerceAtLeast(0)
    layout(width, height) { placeable.place(-h, -v) }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.1f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

/** "12 songs · 84.2 MB used", or a plain empty state. */
private fun formatDownloadsUsage(usage: Pair<Int, Long>): String {
    val (count, bytes) = usage
    if (count == 0) return "No downloaded songs."
    val songs = if (count == 1) "1 song" else "$count songs"
    return "$songs · ${formatBytes(bytes)} used"
}

/** Red "Quit Whiplash" row for the Settings start page, same shape as a folder row. */
@Composable
private fun SettingsQuitRow(onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val red = androidx.compose.ui.graphics.Color(0xFFFF6B6B)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .background(red.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.PowerSettingsNew, contentDescription = null, tint = red, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Quit Whiplash", style = MaterialTheme.typography.titleSmall, color = red)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Stop playback and close the app completely",
                style = MaterialTheme.typography.bodySmall,
                color = WhiplashColors.textSecondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Fully quits: stops playback and the playback service (which removes the
 * media notification), closes the task, then ends the process a moment
 * later so pending small writes (like Replay listening time) can land.
 */
private fun quitApp(activity: android.app.Activity) {
    val app = activity.application as WhiplashApplication
    runCatching { app.playbackController.stopForQuit() }
    runCatching {
        activity.stopService(
            android.content.Intent(activity, com.whiplash.music.playback.service.WhiplashPlaybackService::class.java),
        )
    }
    activity.finishAndRemoveTask()
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        android.os.Process.killProcess(android.os.Process.myPid())
    }, 600L)
}

/** Toast for the end of a sign-in attempt. */
private fun showSignInResult(step: com.whiplash.music.data.sync.CloudSyncManager.SignInStep) {
    val message = when (step) {
        is com.whiplash.music.data.sync.CloudSyncManager.SignInStep.Done -> "Signed in as ${step.account.email}"
        is com.whiplash.music.data.sync.CloudSyncManager.SignInStep.Failed -> step.message
        is com.whiplash.music.data.sync.CloudSyncManager.SignInStep.NeedsUi -> "Couldn't sign in"
    }
    com.whiplash.music.ui.common.ToastController.show(message)
}


/**
 * Erases all of Whiplash's data exactly like Settings › Apps › Clear storage:
 * database, settings, downloads, caches and sync state. Android closes the
 * app afterwards; the next launch is a fresh install.
 */
private fun wipeAppData(context: android.content.Context) {
    val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    if (!am.clearApplicationUserData()) {
        com.whiplash.music.ui.common.ToastController.show("Couldn't reset the app")
    }
}
