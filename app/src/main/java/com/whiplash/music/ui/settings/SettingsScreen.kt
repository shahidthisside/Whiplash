package com.whiplash.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.whiplash.music.ui.theme.GlassCard
import com.whiplash.music.ui.theme.GlassTokens
import com.whiplash.music.ui.theme.ThemeVariant
import com.whiplash.music.ui.theme.WhiplashColors
import com.whiplash.music.ui.theme.WhiplashRadius
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable

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
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.settingsRepository, app.audioCacheManager, app.backupManager))

    val audioQuality by viewModel.audioQuality.collectAsState()
    val downloadQuality by viewModel.downloadQuality.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val themeVariant by viewModel.themeVariant.collectAsState()
    val seekBarStyle by viewModel.seekBarStyle.collectAsState()
    val audioCacheEnabled by viewModel.audioCacheEnabled.collectAsState()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsState()
    val perNetworkQualityEnabled by viewModel.perNetworkQualityEnabled.collectAsState()
    val audioQualityWifi by viewModel.audioQualityWifi.collectAsState()
    val audioQualityCellular by viewModel.audioQualityCellular.collectAsState()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsState()
    val lastBackupTimeMs by viewModel.lastBackupTimeMs.collectAsState()
    val backupResult by viewModel.backupResult.collectAsState()

    var showRestoreConfirm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }

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

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = GlassTokens.spaceMd),
        verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = GlassTokens.miniPlayerReservedHeight),
    ) {
        item {
            SectionLabel("Playback")
            Spacer(Modifier.height(GlassTokens.spaceSm))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg)) {
                    // --- Audio Quality ---
                    SettingRow(
                        title = "Audio Quality",
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

                    Divider()

                    // --- Per-network audio quality (adapted from BitChord) ---
                    // Off by default so the single Audio Quality control above
                    // keeps working exactly as before for anyone who never
                    // opens this; turning it on lets Wi-Fi and cellular each
                    // keep their own ceiling, so a data plan isn't spent at
                    // the same bitrate used at home.
                    SettingToggleRow(
                        title = "Per-Network Audio Quality",
                        subtitle = "Use separate quality ceilings for Wi-Fi and mobile data, instead of one setting for both.",
                        checked = perNetworkQualityEnabled,
                        onCheckedChange = viewModel::setPerNetworkQualityEnabled,
                    )
                    if (perNetworkQualityEnabled) {
                        SettingRow(
                            title = "Wi-Fi Quality",
                            subtitle = "Used only when connected to Wi-Fi.",
                        )
                        AudioQualitySelector(
                            selected = audioQualityWifi,
                            onSelect = viewModel::setAudioQualityWifi,
                        )
                        SettingRow(
                            title = "Cellular Quality",
                            subtitle = "Used only on mobile data. Lower this to save your data plan.",
                        )
                        AudioQualitySelector(
                            selected = audioQualityCellular,
                            onSelect = viewModel::setAudioQualityCellular,
                        )
                    }

                    Divider()

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
                        subtitle = "Applies to new downloads. Higher quality uses more storage.",
                    )
                    downloadQuality?.let { quality ->
                        AudioQualitySelector(
                            selected = quality,
                            onSelect = viewModel::setDownloadQuality,
                        )
                    }

                    Divider()

                    // --- Autoplay ---
                    SettingToggleRow(
                        title = "Autoplay",
                        subtitle = "Automatically queue related songs when your queue is about to end.",
                        checked = autoplayEnabled,
                        onCheckedChange = viewModel::setAutoplayEnabled,
                    )

                    Divider()

                    // --- Gapless ---
                    SettingToggleRow(
                        title = "Gapless Playback",
                        subtitle = "Pre-load the next track so there's no pause between songs.",
                        checked = gaplessEnabled,
                        onCheckedChange = viewModel::setGaplessEnabled,
                    )

                    Divider()

                    // --- Skip Silence (adapted from BitChord) ---
                    // Uses Media3's own built-in SilenceSkippingAudioProcessor
                    // (no custom DSP) — genuinely shortens silent passages
                    // during playback rather than just detecting them, so
                    // this is off by default like every other setting that
                    // audibly changes what's heard.
                    SettingToggleRow(
                        title = "Skip Silence",
                        subtitle = "Automatically speed through quiet passages during playback.",
                        checked = skipSilenceEnabled,
                        onCheckedChange = viewModel::setSkipSilenceEnabled,
                    )

                    Divider()

                    // --- Crossfade / fade duration ---
                    SettingRow(
                        title = "Crossfade",
                        subtitle = if (crossfadeDurationMs == 0) {
                            "Off — songs switch instantly."
                        } else {
                            "Fades out the current song and fades in the next over ${crossfadeDurationMs / 1000}s."
                        },
                    )
                    CrossfadeSelector(
                        selectedMs = crossfadeDurationMs,
                        onSelect = viewModel::setCrossfadeDurationMs,
                    )

                    Divider()

                    // --- Playback speed ---
                    SettingRow(
                        title = "Playback Speed",
                        subtitle = "Applies to the currently playing track immediately.",
                    )
                    PlaybackSpeedSelector(
                        selected = playbackSpeed,
                        onSelect = { speed ->
                            viewModel.setPlaybackSpeed(speed)
                            app.playbackController.setPlaybackSpeed(speed)
                        },
                    )

                    Divider()

                    // --- System Equalizer (adapted from BitChord) ---
                    // Hands off to whichever equalizer app is installed
                    // (system EQ, Wavelet, Poweramp EQ, etc.) rather than
                    // building custom DSP — the standard, documented way
                    // for a media app to support this at all.
                    SettingActionRow(
                        title = "Equalizer",
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

        item {
            SectionLabel("Storage")
            Spacer(Modifier.height(GlassTokens.spaceSm))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg)) {
                    // --- Audio cache toggle ---
                    SettingToggleRow(
                        title = "Cache Songs",
                        subtitle = "Store recently played songs on this device so they start instantly next time, instead of streaming again. Off frees up storage but replays always re-download.",
                        checked = audioCacheEnabled,
                        onCheckedChange = viewModel::setAudioCacheEnabled,
                    )

                    Divider()

                    // --- Cache size + clear ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingRow(
                            title = "Cached data",
                            subtitle = formatCacheSize(cacheSizeBytes),
                        )
                        GlassButton(
                            text = "Clear cache",
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
        }

        item {
            SectionLabel("Backup & Restore")
            Spacer(Modifier.height(GlassTokens.spaceSm))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg)) {
                    SettingRow(
                        title = "Local backup",
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

        item {
            SectionLabel("Appearance")
            Spacer(Modifier.height(GlassTokens.spaceSm))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(GlassTokens.spaceLg)) {
                    Column {
                        SettingRow(
                            title = "Theme",
                            subtitle = "Currently using ${themeVariant.displayName}.",
                        )
                        Spacer(Modifier.height(GlassTokens.spaceMd))
                        ThemeGrid(selected = themeVariant, onSelect = viewModel::setThemeVariant)
                    }

                    Divider()

                    Column {
                        SettingRow(
                            title = "Progress Bar Style",
                            subtitle = "Choose how the full player's seek bar looks. Currently using ${seekBarStyle.displayName}.",
                        )
                        Spacer(Modifier.height(GlassTokens.spaceMd))
                        SeekBarStylePicker(selected = seekBarStyle, onSelect = viewModel::setSeekBarStyle)
                    }
                }
            }
        }

        item {
            GithubFooter()
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
    val shape = RoundedCornerShape(WhiplashRadius.pill)
    val tint = if (selected) WhiplashColors.accent else WhiplashColors.surfaceElevated
    val opacity = if (selected) 1.0f else GlassTokens.opacityRegular
    val contentColor = if (selected) WhiplashColors.onAccent else WhiplashColors.textPrimary

    Box(
        modifier = Modifier
            .clip(shape)
            .background(tint.copy(alpha = opacity))
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, shape)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(horizontal = GlassTokens.spaceMd, vertical = GlassTokens.spaceXs),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides contentColor) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
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

/** Swatch grid for picking the full player's seek bar style — 4 real, distinct styles, each with a small live preview matching its actual on-screen look. */
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
                    .border(
                        width = GlassTokens.borderWidth,
                        color = if (isSelected) WhiplashColors.accent else WhiplashColors.glassBorder,
                        shape = RoundedCornerShape(WhiplashRadius.medium),
                    )
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
                val splitX = size.width * previewFraction
                val amplitudePx = 4.dp.toPx()
                val wavelengthPx = 16.dp.toPx()
                val wavePath = androidx.compose.ui.graphics.Path()
                wavePath.moveTo(0f, midY)
                var x = 0f
                while (x < splitX) {
                    val nextX = (x + wavelengthPx / 2f).coerceAtMost(splitX)
                    val progress = (x / wavelengthPx) * (2 * Math.PI).toFloat()
                    val y = midY + amplitudePx * kotlin.math.sin(progress)
                    wavePath.lineTo(nextX, y)
                    x = nextX
                }
                drawPath(wavePath, color = activeColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                drawLine(
                    color = inactiveColor,
                    start = androidx.compose.ui.geometry.Offset(splitX, midY),
                    end = androidx.compose.ui.geometry.Offset(size.width, midY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
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
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = WhiplashColors.textPrimary,
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
private fun SettingRow(title: String, subtitle: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
    }
}

/** A tappable settings row with no toggle/selector — just a title/subtitle that launches [onClick] (section 57: accessible 48dp+ touch target via the Row's own padding). */
@Composable
private fun SettingActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = androidx.compose.ui.semantics.Role.Button) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = GlassTokens.spaceSm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
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
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
        }
        Spacer(Modifier.width(GlassTokens.spaceSm))
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                // Section 57: subtle haptic feedback on toggles.
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(newValue)
            },
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

@Composable
private fun Divider() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WhiplashColors.glassBorder),
    ) {}
}

/**
 * Premium pill-segment quality selector — replaces the earlier bare
 * horizontal-scrolling [com.whiplash.music.ui.theme.GlassChip] row (the
 * "poor/cheap" look flagged explicitly) with a single continuous rounded
 * track and an animated selection indicator, matching the same visual
 * language as [com.whiplash.music.ui.theme.GlassBottomBar]'s selection pill.
 */
@Composable
private fun AudioQualitySelector(selected: AudioQuality, onSelect: (AudioQuality) -> Unit) {
    val options = AudioQuality.entries.toList()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.surfaceGlass)
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, RoundedCornerShape(WhiplashRadius.pill))
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

private val CROSSFADE_OPTIONS = listOf(0, 3_000, 6_000, 10_000)

@Composable
private fun CrossfadeSelector(selectedMs: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.surfaceGlass)
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, RoundedCornerShape(WhiplashRadius.pill))
            .padding(3.dp),
    ) {
        CROSSFADE_OPTIONS.forEach { ms ->
            val isSelected = ms == selectedMs
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.accent else Color.Transparent,
                label = "crossfadeSegmentBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.onAccent else WhiplashColors.textSecondary,
                label = "crossfadeSegmentFg",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(bg)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(ms) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = GlassTokens.spaceSm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = if (ms == 0) "Off" else "${ms / 1000}s",
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                )
            }
        }
    }
}

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@Composable
private fun PlaybackSpeedSelector(selected: Float, onSelect: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WhiplashRadius.pill))
            .background(WhiplashColors.surfaceGlass)
            .border(GlassTokens.borderWidth, WhiplashColors.glassBorder, RoundedCornerShape(WhiplashRadius.pill))
            .padding(3.dp),
    ) {
        SPEED_OPTIONS.forEach { speed ->
            val isSelected = speed == selected
            val bg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.accent else Color.Transparent,
                label = "speedSegmentBg",
            )
            val fg by androidx.compose.animation.animateColorAsState(
                targetValue = if (isSelected) WhiplashColors.onAccent else WhiplashColors.textSecondary,
                label = "speedSegmentFg",
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(WhiplashRadius.pill))
                    .background(bg)
                    .clickable(role = androidx.compose.ui.semantics.Role.Button) { onSelect(speed) }
                    .semantics { this.selected = isSelected }
                    .padding(vertical = GlassTokens.spaceSm),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "${speed}x", style = MaterialTheme.typography.labelMedium, color = fg)
            }
        }
    }
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
                    color = if (isSelected) variant.palette.accent else WhiplashColors.glassBorder,
                    shape = CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(variant.palette.background),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(variant.palette.accent),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = variant.palette.onAccent,
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
