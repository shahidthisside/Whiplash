package com.whiplash.music.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as WhiplashApplication
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(app.settingsRepository))

    val audioQuality by viewModel.audioQuality.collectAsState()
    val autoplayEnabled by viewModel.autoplayEnabled.collectAsState()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsState()
    val crossfadeDurationMs by viewModel.crossfadeDurationMs.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val themeVariant by viewModel.themeVariant.collectAsState()

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
                        subtitle = "Applies to YouTube Music playback. Higher quality uses more data.",
                    )
                    AudioQualitySelector(
                        selected = audioQuality,
                        onSelect = viewModel::setAudioQuality,
                    )

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
                }
            }
        }

        item {
            SectionLabel("Appearance")
            Spacer(Modifier.height(GlassTokens.spaceSm))
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(
                        title = "Theme",
                        subtitle = "Currently using ${themeVariant.displayName}.",
                    )
                    Spacer(Modifier.height(GlassTokens.spaceMd))
                    ThemeGrid(selected = themeVariant, onSelect = viewModel::setThemeVariant)
                }
            }
        }

        item { Spacer(Modifier.height(GlassTokens.spaceLg)) }
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

@Composable
private fun SettingRow(title: String, subtitle: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleSmall, color = WhiplashColors.textPrimary)
        Spacer(Modifier.height(2.dp))
        Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = WhiplashColors.textSecondary)
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
        Spacer(Modifier.height(GlassTokens.spaceSm))
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
